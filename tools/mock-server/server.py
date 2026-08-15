#!/usr/bin/env python3
"""Mock configuration/upload server for the SHARE clinical recording app.

Demo tool, standard library only (the target container has no pip). It serves two
audiences:

* the app  -- ``GET /api/config/{installationId}`` returns the configuration JSON
  assigned to that installation ID (byte-verbatim), and ``POST /api/upload`` accepts
  a session ZIP (multipart: installationId, sessionId, zipSha256, zip).
* a human  -- ``GET /config`` is a small web UI for assigning a configuration JSON
  (file upload or copy-paste) to an installation ID, watching incoming requests and
  inspecting received uploads.

There is no authentication: this is a demo server for a private network.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
import re
import shutil
import threading
import time
import uuid
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, quote, unquote, urlparse

SUPPORTED_SCHEMA_VERSIONS = range(1, 2)  # mirrors ConfigValidator.SUPPORTED_SCHEMA_VERSIONS (1..1)
TASK_INDEX_PLACEHOLDER = "${taskIndex}"
REQUEST_LOG_LIMIT = 200          # lines kept in requests.jsonl
REQUEST_LOG_SHOWN = 40           # rows rendered in the web UI
FIELD_MEMORY_LIMIT = 8 * 1024 * 1024   # non-file multipart fields kept in memory


def now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def safe_name(raw: str, fallback: str = "config") -> str:
    """Sanitize a user-supplied name into a filesystem-safe file name."""
    cleaned = re.sub(r"[^A-Za-z0-9._-]", "_", (raw or "").strip()).strip("._-")
    return cleaned or fallback


# --------------------------------------------------------------------------------------
# Streaming multipart/form-data parsing
#
# `cgi` was removed in Python 3.13, and a session ZIP can be ~165 MiB, so binary parts are
# streamed straight to a temp file instead of being buffered in memory.
# --------------------------------------------------------------------------------------

class Part:
    def __init__(self, name: str, filename: str | None, content_type: str | None):
        self.name = name
        self.filename = filename
        self.content_type = content_type
        self.data: bytes = b""          # set for non-file parts
        self.path: Path | None = None   # set for file parts
        self.size = 0

    @property
    def text(self) -> str:
        return self.data.decode("utf-8", "replace")


class _StreamReader:
    def __init__(self, fp, remaining: int | None, chunk: int = 64 * 1024):
        self.fp = fp
        self.remaining = remaining  # None = length unknown (chunked); read until the source ends
        self.chunk = chunk
        self.buf = b""
        self.eof = False

    def _fill(self) -> bool:
        if self.eof or (self.remaining is not None and self.remaining <= 0):
            self.eof = True
            return False
        data = self.fp.read(self.chunk if self.remaining is None else min(self.chunk, self.remaining))
        if not data:
            self.eof = True
            return False
        if self.remaining is not None:
            self.remaining -= len(data)
        self.buf += data
        return True

    def read_exact(self, n: int) -> bytes:
        while len(self.buf) < n:
            if not self._fill():
                break
        out, self.buf = self.buf[:n], self.buf[n:]
        return out

    def read_line(self) -> bytes:
        while b"\r\n" not in self.buf:
            if not self._fill():
                out, self.buf = self.buf, b""
                return out
        i = self.buf.index(b"\r\n")
        out, self.buf = self.buf[: i + 2], self.buf[i + 2 :]
        return out

    def iter_until(self, delim: bytes):
        """Yield everything up to `delim`, consuming the delimiter itself."""
        keep = len(delim)
        while True:
            i = self.buf.find(delim)
            if i >= 0:
                yield self.buf[:i]
                self.buf = self.buf[i + len(delim) :]
                return
            if len(self.buf) > keep:
                cut = len(self.buf) - keep
                yield self.buf[:cut]
                self.buf = self.buf[cut:]
            if not self._fill():
                yield self.buf
                self.buf = b""
                return


class _ChunkedReader:
    """`read(n)`-compatible view over a `Transfer-Encoding: chunked` request body.

    `BaseHTTPRequestHandler` does not decode chunked bodies. A client that streams a part of
    unknown size sends one, and without this the server would read zero bytes and report the
    same misleading "missing fields" as the header bug above.
    """

    def __init__(self, fp):
        self.fp = fp
        self.buf = b""
        self.done = False

    def _next_chunk(self) -> bool:
        if self.done:
            return False
        size_line = self.fp.readline(64).strip()
        if not size_line:
            self.done = True
            return False
        try:
            size = int(size_line.split(b";", 1)[0], 16)
        except ValueError:
            self.done = True
            return False
        if size == 0:
            while True:                      # consume trailers up to the final CRLF
                line = self.fp.readline(1024)
                if line in (b"\r\n", b"\n", b""):
                    break
            self.done = True
            return False
        remaining = size
        while remaining > 0:
            data = self.fp.read(remaining)
            if not data:
                self.done = True
                break
            self.buf += data
            remaining -= len(data)
        self.fp.read(2)                      # CRLF after the chunk data
        return True

    def read(self, n: int = -1) -> bytes:
        while (n < 0 or len(self.buf) < n) and self._next_chunk():
            pass
        if n < 0 or n >= len(self.buf):
            out, self.buf = self.buf, b""
        else:
            out, self.buf = self.buf[:n], self.buf[n:]
        return out


def _header_param(value: str, key: str) -> str | None:
    """Read one parameter out of a header value, quoted or unquoted.

    The key must start at a parameter boundary: searching for `name` must not match inside
    `filename="…"`. Ktor writes the part name unquoted (`form-data; name=zip; filename="s.zip"`),
    so an unanchored search would read the part's name as its file name and the part would be
    lost — that is what made real uploads fail with "missing fields".
    """
    m = re.search(rf'(?:^|;|\s)\s*{re.escape(key)}\s*=\s*(?:"([^"]*)"|([^;]*))', value, re.IGNORECASE)
    if not m:
        return None
    return (m.group(1) if m.group(1) is not None else m.group(2)).strip()


def parse_multipart(rfile, content_type: str, content_length: int | None, tmp_dir: Path) -> dict[str, Part]:
    boundary = _header_param(content_type, "boundary")
    if not boundary:
        raise ValueError("missing multipart boundary")
    delim = b"--" + boundary.encode("latin-1")
    reader = _StreamReader(rfile, content_length)

    for _ in reader.iter_until(delim):  # skip preamble
        pass

    parts: dict[str, Part] = {}
    while True:
        marker = reader.read_exact(2)
        if marker != b"\r\n":       # b"--" terminates, anything else is a truncated body
            break

        # A client may split Content-Disposition parameters across repeated header lines, so
        # collect every value instead of letting the last one win.
        dispositions: list[str] = []
        ctype = None
        while True:
            line = reader.read_line()
            if line in (b"\r\n", b""):
                break
            text = line.decode("utf-8", "replace").strip()
            lowered = text.lower()
            if lowered.startswith("content-disposition:"):
                dispositions.append(text.split(":", 1)[1].strip())
            elif lowered.startswith("content-type:"):
                ctype = text.split(":", 1)[1].strip()
        disposition = "; ".join(dispositions)

        name = _header_param(disposition, "name") or ""
        filename = _header_param(disposition, "filename")
        part = Part(name, filename, ctype)

        body_delim = b"\r\n" + delim
        if filename is not None:
            tmp_dir.mkdir(parents=True, exist_ok=True)
            part.path = tmp_dir / f"upload-{uuid.uuid4().hex}.tmp"
            with open(part.path, "wb") as out:
                for chunk in reader.iter_until(body_delim):
                    out.write(chunk)
                    part.size += len(chunk)
        else:
            buf = bytearray()
            for chunk in reader.iter_until(body_delim):
                if len(buf) < FIELD_MEMORY_LIMIT:
                    buf += chunk
                part.size += len(chunk)
            part.data = bytes(buf)

        if name:
            parts[name] = part
    return parts


# --------------------------------------------------------------------------------------
# Storage
# --------------------------------------------------------------------------------------

class Store:
    """All server state on disk. Writes are atomic (tmp + rename) and lock-guarded."""

    def __init__(self, root: Path):
        self.root = root
        self.configs = root / "configs"
        self.uploads = root / "uploads"
        self.tmp = root / "tmp"
        self.assignments_file = root / "assignments.json"
        self.requests_file = root / "requests.jsonl"
        self.lock = threading.Lock()
        for d in (self.root, self.configs, self.uploads, self.tmp):
            d.mkdir(parents=True, exist_ok=True)
        for stale in self.tmp.glob("*.tmp"):
            stale.unlink(missing_ok=True)

    # -- assignments ---------------------------------------------------------------
    def assignments(self) -> dict:
        try:
            return json.loads(self.assignments_file.read_text("utf-8"))
        except (OSError, ValueError):
            return {}

    def _write_assignments(self, data: dict) -> None:
        tmp = self.assignments_file.with_suffix(".json.tmp")
        tmp.write_text(json.dumps(data, indent=2, sort_keys=True, ensure_ascii=False), "utf-8")
        os.replace(tmp, self.assignments_file)

    def assign(self, installation_id: str, config_name: str, note: str = "") -> None:
        with self.lock:
            data = self.assignments()
            previous = data.get(installation_id, {})
            data[installation_id] = {
                "config": config_name,
                "enabled": previous.get("enabled", True),
                "note": note or previous.get("note", ""),
                "updatedAt": now_iso(),
            }
            self._write_assignments(data)

    def unassign(self, installation_id: str, purge: bool) -> None:
        with self.lock:
            data = self.assignments()
            entry = data.pop(installation_id, None)
            self._write_assignments(data)
        if purge and entry:
            still_used = any(e.get("config") == entry.get("config") for e in self.assignments().values())
            if not still_used:
                (self.configs / entry["config"]).unlink(missing_ok=True)

    def toggle(self, installation_id: str) -> None:
        with self.lock:
            data = self.assignments()
            if installation_id in data:
                data[installation_id]["enabled"] = not data[installation_id].get("enabled", True)
                data[installation_id]["updatedAt"] = now_iso()
                self._write_assignments(data)

    # -- configs -------------------------------------------------------------------
    def save_config(self, name: str, body: bytes) -> str:
        name = safe_name(name)
        if not name.endswith(".json"):
            name += ".json"
        tmp = self.tmp / f"cfg-{uuid.uuid4().hex}.tmp"
        tmp.write_bytes(body)
        os.replace(tmp, self.configs / name)
        return name

    def config_bytes(self, name: str) -> bytes | None:
        path = self.configs / safe_name(name)
        try:
            return path.read_bytes()
        except OSError:
            return None

    # -- request log ---------------------------------------------------------------
    def log_request(self, entry: dict) -> None:
        entry = {"at": now_iso(), **entry}
        with self.lock:
            try:
                lines = self.requests_file.read_text("utf-8").splitlines()
            except OSError:
                lines = []
            lines.append(json.dumps(entry, ensure_ascii=False))
            self.requests_file.write_text("\n".join(lines[-REQUEST_LOG_LIMIT:]) + "\n", "utf-8")

    def recent_requests(self, limit: int = REQUEST_LOG_SHOWN) -> list[dict]:
        try:
            lines = self.requests_file.read_text("utf-8").splitlines()
        except OSError:
            return []
        out = []
        for line in lines[-limit:]:
            try:
                out.append(json.loads(line))
            except ValueError:
                continue
        return list(reversed(out))

    # -- uploads -------------------------------------------------------------------
    def upload_dir(self, session_id: str) -> Path:
        return self.uploads / safe_name(session_id, "session")

    def upload_meta(self, session_id: str) -> dict | None:
        try:
            return json.loads((self.upload_dir(session_id) / "meta.json").read_text("utf-8"))
        except (OSError, ValueError):
            return None

    def all_uploads(self) -> list[dict]:
        out = []
        for d in sorted(self.uploads.iterdir(), reverse=True) if self.uploads.exists() else []:
            meta_file = d / "meta.json"
            if meta_file.is_file():
                try:
                    out.append(json.loads(meta_file.read_text("utf-8")))
                except ValueError:
                    continue
        out.sort(key=lambda m: m.get("receivedAt", ""), reverse=True)
        return out


# --------------------------------------------------------------------------------------
# Advisory config validation (mirrors ConfigValidator.kt; never blocks storing)
# --------------------------------------------------------------------------------------

def inspect_config(body: bytes) -> tuple[dict | None, list[str]]:
    try:
        parsed = json.loads(body.decode("utf-8"))
    except (ValueError, UnicodeDecodeError) as e:
        return None, [f"not valid JSON: {e}"]
    if not isinstance(parsed, dict):
        return None, ["top-level JSON value is not an object"]

    warnings: list[str] = []
    schema = parsed.get("schemaVersion")
    if not isinstance(schema, int):
        warnings.append("missing or non-integer 'schemaVersion'")
    elif schema not in SUPPORTED_SCHEMA_VERSIONS:
        warnings.append(f"schemaVersion {schema} outside the app's supported range 1..1")
    for key in ("configVersion", "defaultLanguage"):
        if not parsed.get(key):
            warnings.append(f"missing required field '{key}'")

    protocols = parsed.get("protocols")
    if not isinstance(protocols, list) or not protocols:
        warnings.append("no 'protocols' defined")
        protocols = []
    for i, protocol in enumerate(protocols):
        if not isinstance(protocol, dict):
            warnings.append(f"protocol #{i} is not an object")
            continue
        label = protocol.get("name") or f"#{i}"
        if not protocol.get("name"):
            warnings.append(f"protocol {label}: missing 'name'")
        template = protocol.get("recordingsFileName")
        if not template:
            warnings.append(f"protocol {label}: missing 'recordingsFileName'")
        elif TASK_INDEX_PLACEHOLDER not in template:
            warnings.append(f"protocol {label}: recordingsFileName lacks {TASK_INDEX_PLACEHOLDER}")
        seen_calibration = False
        for task in protocol.get("tasks", []) or []:
            if not isinstance(task, dict):
                continue
            ttype = task.get("type")
            if ttype == "CALIBRATION":
                seen_calibration = True
            elif ttype == "VOCAL" and not seen_calibration:
                warnings.append(f"protocol {label}: VOCAL task before any CALIBRATION task")
                break
    return parsed, warnings


def config_summary(store: Store, name: str) -> dict:
    body = store.config_bytes(name)
    if body is None:
        return {"missing": True, "size": 0, "schemaVersion": "-", "configVersion": "-", "protocols": "-"}
    parsed, warnings = inspect_config(body)
    return {
        "missing": False,
        "size": len(body),
        "schemaVersion": (parsed or {}).get("schemaVersion", "-"),
        "configVersion": (parsed or {}).get("configVersion", "-"),
        "protocols": ", ".join(
            str(p.get("name", "?")) for p in (parsed or {}).get("protocols", []) if isinstance(p, dict)
        ) or "-",
        "warnings": warnings,
    }


# --------------------------------------------------------------------------------------
# Web UI
# --------------------------------------------------------------------------------------

PAGE_CSS = """
*{box-sizing:border-box}
body{margin:0;padding:24px;font:14px/1.5 -apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;
     background:#f4f5f7;color:#1c1e21}
h1{font-size:20px;margin:0 0 4px}h2{font-size:15px;margin:0 0 12px;text-transform:uppercase;
   letter-spacing:.06em;color:#5b6472}
.sub{color:#5b6472;margin:0 0 20px}
.card{background:#fff;border:1px solid #dfe3e8;border-radius:8px;padding:18px;margin-bottom:18px;
      max-width:1100px}
table{border-collapse:collapse;width:100%}
th,td{text-align:left;padding:7px 10px;border-bottom:1px solid #edeff2;vertical-align:top}
th{font-size:12px;text-transform:uppercase;letter-spacing:.04em;color:#5b6472}
td.mono,code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12.5px}
tr:last-child td{border-bottom:none}
input[type=text],textarea,input[type=file]{width:100%;padding:8px 10px;border:1px solid #ccd2d9;
   border-radius:6px;font:inherit;background:#fff}
textarea{min-height:150px;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12.5px}
label{display:block;font-weight:600;margin:12px 0 4px}
.hint{font-weight:400;color:#5b6472}
button{padding:7px 14px;border:1px solid #c7ccd3;border-radius:6px;background:#fff;cursor:pointer;font:inherit}
button.primary{background:#1a73e8;border-color:#1a73e8;color:#fff;font-weight:600}
button.link{border:none;background:none;color:#1a73e8;padding:2px 4px;text-decoration:underline}
.row{display:flex;gap:16px;flex-wrap:wrap}.row>div{flex:1 1 260px}
.flash{padding:10px 14px;border-radius:6px;margin-bottom:16px;max-width:1100px}
.flash.ok{background:#e6f4ea;border:1px solid #b7e0c2}
.flash.err{background:#fce8e6;border:1px solid #f3b8b2}
.badge{display:inline-block;padding:1px 8px;border-radius:999px;font-size:12px;font-weight:600}
.badge.on{background:#e6f4ea;color:#137333}.badge.off{background:#fce8e6;color:#c5221f}
.badge.s200{background:#e6f4ea;color:#137333}.badge.s404,.badge.s403,.badge.s409,.badge.s400{
   background:#fce8e6;color:#c5221f}
.warn{color:#b06000;font-size:12.5px;margin-top:4px}
.empty{color:#7a828c;font-style:italic}
form.inline{display:inline}
"""


def esc(value) -> str:
    return html.escape(str(value), quote=True)


def render_page(store: Store, msg: str, err: str, prefill: str) -> bytes:
    assignments = store.assignments()
    requests_log = store.recent_requests()
    uploads = store.all_uploads()
    known_ids = set(assignments)
    seen_ids = [r.get("installationId") for r in requests_log if r.get("installationId")]

    out = [f"<!doctype html><html><head><meta charset='utf-8'>",
           "<meta name='viewport' content='width=device-width,initial-scale=1'>",
           "<title>SHARE mock config server</title>",
           f"<style>{PAGE_CSS}</style></head><body>",
           "<h1>SHARE mock configuration server</h1>",
           "<p class='sub'>Assign a configuration JSON to an installation ID. The app fetches it from "
           "<code>GET /api/config/{installationId}</code> (alias <code>GET /serve/{installationId}</code>).</p>"]

    if msg:
        out.append(f"<div class='flash ok'>{esc(msg)}</div>")
    if err:
        out.append(f"<div class='flash err'>{esc(err)}</div>")

    # ---- assignments -----------------------------------------------------------------
    out.append("<div class='card'><h2>Assignments</h2>")
    if not assignments:
        out.append("<p class='empty'>No installation ID has a configuration yet.</p>")
    else:
        out.append("<table><tr><th>Installation ID</th><th>Config file</th><th>Schema / version</th>"
                   "<th>Protocols</th><th>Status</th><th>Updated</th><th></th></tr>")
        for iid in sorted(assignments):
            entry = assignments[iid]
            info = config_summary(store, entry.get("config", ""))
            enabled = entry.get("enabled", True)
            badge = "<span class='badge on'>serving</span>" if enabled else "<span class='badge off'>disabled → 403</span>"
            missing = " <span class='badge off'>file missing</span>" if info["missing"] else ""
            warns = "".join(f"<div class='warn'>⚠ {esc(w)}</div>" for w in info.get("warnings", []))
            out.append(
                f"<tr><td class='mono'>{esc(iid)}</td>"
                f"<td class='mono'><a href='/config/raw/{quote(entry.get('config',''))}'>{esc(entry.get('config',''))}</a>"
                f" <span class='hint'>({info['size']} B)</span>{missing}{warns}</td>"
                f"<td class='mono'>v{esc(info['schemaVersion'])} / {esc(info['configVersion'])}</td>"
                f"<td>{esc(info['protocols'])}</td>"
                f"<td>{badge}</td><td class='mono'>{esc(entry.get('updatedAt','-'))}</td>"
                f"<td>"
                f"<form class='inline' method='post' action='/config/toggle'>"
                f"<input type='hidden' name='installationId' value='{esc(iid)}'>"
                f"<button type='submit'>{'Disable' if enabled else 'Enable'}</button></form> "
                f"<form class='inline' method='post' action='/config/delete' "
                f"onsubmit=\"return confirm('Remove assignment for {esc(iid)}?')\">"
                f"<input type='hidden' name='installationId' value='{esc(iid)}'>"
                f"<button type='submit'>Remove</button></form>"
                f"</td></tr>")
        out.append("</table>")
    out.append("</div>")

    # ---- assign form -----------------------------------------------------------------
    datalist = "".join(f"<option value='{esc(i)}'>" for i in dict.fromkeys(seen_ids + sorted(known_ids)))
    out.append(f"""
<div class='card'><h2>Assign a configuration</h2>
<form method='post' action='/config' enctype='multipart/form-data'>
  <div class='row'>
    <div>
      <label>Installation ID <span class='hint'>(what the app sends)</span></label>
      <input type='text' name='installationId' list='seen-ids' required value='{esc(prefill)}'
             placeholder='DEMO-001' autofocus>
      <datalist id='seen-ids'>{datalist}</datalist>
    </div>
    <div>
      <label>Config file name <span class='hint'>(optional, defaults to the installation ID)</span></label>
      <input type='text' name='name' placeholder='demo_config.json'>
    </div>
  </div>
  <label>Upload a JSON file</label>
  <input type='file' name='file' accept='.json,application/json'>
  <label>…or paste the JSON here <span class='hint'>(used when no file is selected)</span></label>
  <textarea name='paste' placeholder='{{ "schemaVersion": 1, … }}'></textarea>
  <p><button class='primary' type='submit'>Save assignment</button>
     <span class='hint'>Stored byte-for-byte; invalid JSON is rejected, other problems are warnings only.</span></p>
</form></div>""")

    # ---- request log -----------------------------------------------------------------
    out.append("<div class='card'><h2>Recent app requests</h2>")
    if not requests_log:
        out.append("<p class='empty'>Nothing yet — the app has not called this server.</p>")
    else:
        out.append("<table><tr><th>Time (UTC)</th><th>Client</th><th>Path</th>"
                   "<th>Installation ID</th><th>Status</th><th></th></tr>")
        for r in requests_log:
            iid = r.get("installationId") or ""
            status = r.get("status", "")
            assign_btn = ""
            if iid and iid not in known_ids:
                assign_btn = (f"<form class='inline' method='get' action='/config'>"
                              f"<input type='hidden' name='prefill' value='{esc(iid)}'>"
                              f"<button class='link' type='submit'>assign…</button></form>")
            out.append(
                f"<tr><td class='mono'>{esc(r.get('at','-'))}</td><td class='mono'>{esc(r.get('ip','-'))}</td>"
                f"<td class='mono'>{esc(r.get('path','-'))}</td><td class='mono'>{esc(iid) or '-'}</td>"
                f"<td><span class='badge s{esc(status)}'>{esc(status)}</span> "
                f"<span class='hint'>{esc(r.get('note',''))}</span></td><td>{assign_btn}</td></tr>")
        out.append("</table>")
    out.append("</div>")

    # ---- uploads ---------------------------------------------------------------------
    out.append("<div class='card'><h2>Received session uploads</h2>")
    if not uploads:
        out.append("<p class='empty'>No session ZIP has been uploaded yet.</p>")
    else:
        out.append("<table><tr><th>Received (UTC)</th><th>Session ID</th><th>Installation ID</th>"
                   "<th>Size</th><th>SHA-256</th><th></th></tr>")
        for u in uploads:
            ok = "✓ matches" if u.get("sha256Match") else "✗ mismatch"
            out.append(
                f"<tr><td class='mono'>{esc(u.get('receivedAt','-'))}</td>"
                f"<td class='mono'>{esc(u.get('sessionId','-'))}</td>"
                f"<td class='mono'>{esc(u.get('installationId','-'))}</td>"
                f"<td class='mono'>{u.get('bytes',0):,} B</td>"
                f"<td class='mono'>{esc(ok)} <span class='hint'>{esc((u.get('sha256') or '')[:16])}…</span></td>"
                f"<td><a href='/uploads/{quote(u.get('sessionId',''))}/session.zip'>download</a></td></tr>")
        out.append("</table>")
    out.append("</div>")

    out.append("<p class='sub'>Endpoints: <code>GET /api/config/{id}</code> · "
               "<code>GET /serve/{id}</code> · <code>POST /api/upload</code> · <code>GET /health</code></p>")
    out.append("</body></html>")
    return "".join(out).encode("utf-8")


# --------------------------------------------------------------------------------------
# HTTP handler
# --------------------------------------------------------------------------------------

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "ShareMockServer/1.0"
    store: Store  # injected by main()

    # -- helpers ---------------------------------------------------------------------
    def _send(self, status: int, body: bytes, content_type: str, extra: dict | None = None) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def _send_json(self, status: int, payload: dict) -> None:
        self._send(status, json.dumps(payload).encode("utf-8") + b"\n", "application/json")

    def _redirect(self, location: str) -> None:
        self.send_response(HTTPStatus.SEE_OTHER)
        self.send_header("Location", location)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def _log_app_request(self, path: str, installation_id: str | None, status: int, note: str = "") -> None:
        self.store.log_request({
            "ip": self.client_address[0],
            "method": self.command,
            "path": path,
            "installationId": installation_id,
            "status": status,
            "note": note,
        })

    def log_message(self, fmt, *args):  # concise journald lines
        print(f"{now_iso()} {self.client_address[0]} {fmt % args}", flush=True)

    # -- GET ---------------------------------------------------------------------------
    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path = unquote(parsed.path)
        query = parse_qs(parsed.query)

        if path == "/health":
            self._send_json(HTTPStatus.OK, {"status": "ok", "at": now_iso()})
            return

        if path == "/":
            self._redirect("/config")
            return

        if path == "/config":
            body = render_page(
                self.store,
                msg=(query.get("msg") or [""])[0],
                err=(query.get("err") or [""])[0],
                prefill=(query.get("prefill") or [""])[0],
            )
            self._send(HTTPStatus.OK, body, "text/html; charset=utf-8")
            return

        if path.startswith("/config/raw/"):
            body = self.store.config_bytes(path[len("/config/raw/"):])
            if body is None:
                self._send_json(HTTPStatus.NOT_FOUND, {"error": "no such config file"})
            else:
                self._send(HTTPStatus.OK, body, "application/json; charset=utf-8")
            return

        # app-facing config fetch, plus the /serve alias
        installation_id = None
        if path.startswith("/api/config/"):
            installation_id = path[len("/api/config/"):].strip("/")
        elif path.startswith("/serve/"):
            installation_id = path[len("/serve/"):].strip("/")
        elif path.rstrip("/") == "/serve":
            installation_id = (query.get("installationId") or [""])[0].strip()
        if installation_id is not None:
            self._serve_config(path, installation_id)
            return

        m = re.fullmatch(r"/uploads/([^/]+)/session\.zip", path)
        if m:
            zip_path = self.store.upload_dir(m.group(1)) / "session.zip"
            if not zip_path.is_file():
                self._send_json(HTTPStatus.NOT_FOUND, {"error": "no such upload"})
                return
            size = zip_path.stat().st_size
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "application/zip")
            self.send_header("Content-Length", str(size))
            self.send_header("Content-Disposition", f'attachment; filename="{safe_name(m.group(1))}.zip"')
            self.end_headers()
            with open(zip_path, "rb") as f:
                shutil.copyfileobj(f, self.wfile, 64 * 1024)
            return

        self._send_json(HTTPStatus.NOT_FOUND, {"error": "not found"})

    def _serve_config(self, path: str, installation_id: str) -> None:
        if not installation_id:
            self._log_app_request(path, None, 404, "no installation id in request")
            self._send_json(HTTPStatus.NOT_FOUND, {"error": "missing installation id"})
            return
        entry = self.store.assignments().get(installation_id)
        if entry is None:
            self._log_app_request(path, installation_id, 404, "unknown installation id")
            self._send_json(HTTPStatus.NOT_FOUND, {"error": "unknown installation id"})
            return
        if not entry.get("enabled", True):
            self._log_app_request(path, installation_id, 403, "assignment disabled")
            self._send_json(HTTPStatus.FORBIDDEN, {"error": "installation id disabled"})
            return
        body = self.store.config_bytes(entry.get("config", ""))
        if body is None:
            self._log_app_request(path, installation_id, 404, "config file missing on disk")
            self._send_json(HTTPStatus.NOT_FOUND, {"error": "configuration not available"})
            return
        self._log_app_request(path, installation_id, 200, entry.get("config", ""))
        self._send(HTTPStatus.OK, body, "application/json; charset=utf-8")

    # -- POST --------------------------------------------------------------------------
    def do_POST(self) -> None:
        path = urlparse(self.path).path
        content_type = self.headers.get("Content-Type") or ""
        chunked = (self.headers.get("Transfer-Encoding") or "").lower().strip().endswith("chunked")
        body = _ChunkedReader(self.rfile) if chunked else self.rfile
        length = None if chunked else int(self.headers.get("Content-Length") or 0)

        # Always consume the body first so the connection stays usable.
        parts: dict[str, Part] = {}
        form: dict[str, list[str]] = {}
        try:
            if content_type.startswith("multipart/form-data"):
                parts = parse_multipart(body, content_type, length, self.store.tmp)
            else:
                raw = body.read(-1 if length is None else length) if (length is None or length) else b""
                form = parse_qs(raw.decode("utf-8", "replace"))
        except (ValueError, OSError) as e:
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": f"cannot read request body: {e}"})
            return

        def field(name: str) -> str:
            if name in parts:
                return parts[name].text.strip()
            return (form.get(name) or [""])[0].strip()

        try:
            if path in ("/api/upload", "/upload"):
                self._handle_upload(path, parts, field)
            elif path == "/config":
                self._handle_save_config(parts, field)
            elif path == "/config/delete":
                iid = field("installationId")
                self.store.unassign(iid, purge=bool(field("purge")))
                self._redirect(f"/config?msg={quote(f'Removed assignment for {iid}')}")
            elif path == "/config/toggle":
                iid = field("installationId")
                self.store.toggle(iid)
                self._redirect(f"/config?msg={quote(f'Toggled serving for {iid}')}")
            else:
                self._send_json(HTTPStatus.NOT_FOUND, {"error": "not found"})
        finally:
            for part in parts.values():
                if part.path is not None:
                    part.path.unlink(missing_ok=True)

    def _handle_save_config(self, parts: dict[str, Part], field) -> None:
        installation_id = field("installationId")
        if not installation_id:
            self._redirect("/config?err=" + quote("Installation ID is required"))
            return

        body: bytes | None = None
        source = ""
        file_part = parts.get("file")
        if file_part is not None and file_part.path is not None and file_part.size > 0:
            body = file_part.path.read_bytes()
            source = file_part.filename or "uploaded file"
        elif field("paste"):
            body = parts["paste"].data if "paste" in parts else field("paste").encode("utf-8")
            source = "pasted text"
        if not body:
            self._redirect("/config?err=" + quote("Provide a JSON file or paste the JSON") +
                           "&prefill=" + quote(installation_id))
            return

        parsed, warnings = inspect_config(body)
        if parsed is None:
            self._redirect("/config?err=" + quote(f"Rejected: {warnings[0]}") +
                           "&prefill=" + quote(installation_id))
            return

        name = field("name") or installation_id
        stored = self.store.save_config(name, body)
        self.store.assign(installation_id, stored, note=source)
        msg = f"Assigned {stored} ({len(body)} B, from {source}) to {installation_id}"
        if warnings:
            msg += f" — stored with {len(warnings)} warning(s), see the table"
        self._redirect("/config?msg=" + quote(msg))

    def _handle_upload(self, path: str, parts: dict[str, Part], field) -> None:
        installation_id = field("installationId")
        session_id = field("sessionId")
        zip_sha256 = field("zipSha256").lower()
        zip_part = parts.get("zip")

        if not installation_id or not session_id or zip_part is None or zip_part.path is None:
            # Name every part that did arrive: a client whose part names do not match the
            # contract is otherwise indistinguishable from one that sent nothing.
            received = sorted(parts)
            self._log_app_request(path, installation_id or None, 400,
                                  f"missing fields; received parts: {', '.join(received) or 'none'}")
            self._send_json(HTTPStatus.BAD_REQUEST, {
                "error": "installationId, sessionId and zip are required",
                "receivedParts": received,
            })
            return

        if installation_id not in self.store.assignments():
            self._log_app_request(path, installation_id, 404, "unknown installation id")
            self._send_json(HTTPStatus.NOT_FOUND, {"error": "unknown installation id"})
            return

        existing = self.store.upload_meta(session_id)
        if existing:
            self._log_app_request(path, installation_id, 409, f"duplicate session {session_id}")
            self._send_json(HTTPStatus.CONFLICT,
                            {"error": "session already uploaded", "sessionId": session_id,
                             "receivedAt": existing.get("receivedAt")})
            return

        digest = hashlib.sha256()
        with open(zip_part.path, "rb") as f:
            for chunk in iter(lambda: f.read(1024 * 1024), b""):
                digest.update(chunk)
        actual = digest.hexdigest()
        if zip_sha256 and actual != zip_sha256:
            self._log_app_request(path, installation_id, 400, "sha256 mismatch")
            self._send_json(HTTPStatus.BAD_REQUEST,
                            {"error": "checksum mismatch", "expected": zip_sha256, "actual": actual})
            return

        target_dir = self.store.upload_dir(session_id)
        target_dir.mkdir(parents=True, exist_ok=True)
        shutil.move(str(zip_part.path), target_dir / "session.zip")
        zip_part.path = None  # moved; nothing to clean up

        meta = {
            "sessionId": session_id,
            "installationId": installation_id,
            "bytes": zip_part.size,
            "sha256": actual,
            "declaredSha256": zip_sha256 or None,
            "sha256Match": bool(zip_sha256) and actual == zip_sha256,
            "receivedAt": now_iso(),
            "clientIp": self.client_address[0],
        }
        (target_dir / "meta.json").write_text(json.dumps(meta, indent=2), "utf-8")

        self._log_app_request(path, installation_id, 200, f"session {session_id}, {zip_part.size} B")
        self._send_json(HTTPStatus.OK, {
            "status": "ok",
            "sessionId": session_id,
            "bytes": zip_part.size,
            "sha256": actual,
            "receivedAt": meta["receivedAt"],
        })


def selftest() -> int:
    """Parse bodies in the shapes real clients send. Guards the multipart contract."""
    import io
    import tempfile

    failures: list[str] = []

    def check(label: str, condition: bool) -> None:
        print(f"  {'ok  ' if condition else 'FAIL'} {label}", flush=True)
        if not condition:
            failures.append(label)

    with tempfile.TemporaryDirectory() as tmp:
        tmp_dir = Path(tmp)
        payload = b"PK\x03\x04" + bytes(range(256)) * 4

        # 1. Ktor 3.x: unquoted part names, filename appended to the same Content-Disposition.
        b1 = "WebKitFormBoundaryKtor"
        body = (
            f"--{b1}\r\nContent-Disposition: form-data; name=installationId\r\n\r\nDEMO-001\r\n"
            f"--{b1}\r\nContent-Disposition: form-data; name=sessionId\r\n\r\nsess-42\r\n"
            f"--{b1}\r\nContent-Disposition: form-data; name=zip; filename=\"HC314_V0.zip\"\r\n"
            f"Content-Type: application/zip\r\n\r\n"
        ).encode() + payload + f"\r\n--{b1}--\r\n".encode()
        parts = parse_multipart(io.BytesIO(body), f"multipart/form-data; boundary={b1}", len(body), tmp_dir)
        print("ktor-style (unquoted name= plus filename=):")
        check("parts are named installationId/sessionId/zip", sorted(parts) == ["installationId", "sessionId", "zip"])
        check("installationId value", parts.get("installationId") and parts["installationId"].text == "DEMO-001")
        check("sessionId value", parts.get("sessionId") and parts["sessionId"].text == "sess-42")
        check("zip filename kept separately", parts.get("zip") and parts["zip"].filename == "HC314_V0.zip")
        check("zip streamed to disk intact",
              parts.get("zip") and parts["zip"].path is not None
              and parts["zip"].path.read_bytes() == payload and parts["zip"].size == len(payload))

        # 2. Browser/curl: everything quoted.
        b2 = "----WebKitFormBoundaryBrowser"
        body = (
            f"--{b2}\r\nContent-Disposition: form-data; name=\"installationId\"\r\n\r\nDEMO-002\r\n"
            f"--{b2}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"config.json\"\r\n"
            f"Content-Type: application/json\r\n\r\n"
        ).encode() + b'{"schemaVersion":1}' + f"\r\n--{b2}--\r\n".encode()
        parts = parse_multipart(io.BytesIO(body), f'multipart/form-data; boundary="{b2}"', len(body), tmp_dir)
        print("browser-style (quoted names, quoted boundary):")
        check("parts are named installationId/file", sorted(parts) == ["file", "installationId"])
        check("file body intact", parts.get("file") and parts["file"].path.read_bytes() == b'{"schemaVersion":1}')

        # 3. Text-only fields, filename split onto its own Content-Disposition line.
        b3 = "boundary-split"
        body = (
            f"--{b3}\r\nContent-Disposition: form-data; name=paste\r\n\r\n{{}}\r\n"
            f"--{b3}\r\nContent-Disposition: form-data; name=zip\r\n"
            f"Content-Disposition: filename=\"s.zip\"\r\n\r\n"
        ).encode() + payload + f"\r\n--{b3}--\r\n".encode()
        parts = parse_multipart(io.BytesIO(body), f"multipart/form-data; boundary={b3}", len(body), tmp_dir)
        print("split Content-Disposition headers:")
        check("both parts found", sorted(parts) == ["paste", "zip"])
        check("text field is not treated as a file", parts.get("paste") and parts["paste"].path is None)
        check("zip still recognized as a file", parts.get("zip") and parts["zip"].filename == "s.zip")

        # 4. Chunked transfer encoding.
        b4 = "boundary-chunked"
        body = (f"--{b4}\r\nContent-Disposition: form-data; name=sessionId\r\n\r\nsess-chunked\r\n"
                f"--{b4}--\r\n").encode()
        raw = bytearray()
        for i in range(0, len(body), 17):
            piece = body[i : i + 17]
            raw += f"{len(piece):x}\r\n".encode() + piece + b"\r\n"
        raw += b"0\r\n\r\n"
        reader = _ChunkedReader(io.BufferedReader(io.BytesIO(bytes(raw))))
        parts = parse_multipart(reader, f"multipart/form-data; boundary={b4}", None, tmp_dir)
        print("chunked transfer-encoding:")
        check("chunked body decoded", parts.get("sessionId") and parts["sessionId"].text == "sess-chunked")

    print(f"\n{'FAILED: ' + '; '.join(failures) if failures else 'all multipart self-tests passed'}", flush=True)
    return 1 if failures else 0


def main() -> None:
    ap = argparse.ArgumentParser(description="SHARE mock configuration/upload server")
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--data", default="./data", help="directory holding configs, assignments and uploads")
    ap.add_argument("--selftest", action="store_true",
                    help="parse the multipart shapes real clients send, then exit")
    args = ap.parse_args()

    if args.selftest:
        raise SystemExit(selftest())

    Handler.store = Store(Path(args.data).resolve())
    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    httpd.daemon_threads = True
    print(f"{now_iso()} share-mock-server listening on http://{args.host}:{args.port} "
          f"(data: {Handler.store.root})", flush=True)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()


if __name__ == "__main__":
    main()
