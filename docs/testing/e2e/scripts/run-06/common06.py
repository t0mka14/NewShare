"""Shared helpers for run 06 (sessions from the context file, local SHA-256, redaction, raw HTTP download)."""
import json, os, hashlib, re, urllib.request, urllib.error
from common import *   # api, tok, BASE, CONTEXT, SECRETS, SP, REPORTS
REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..", ".."))
RUN06 = os.environ.get("E2E_RUN_DIR", os.path.join(REPORTS, "2026-10-26-run-06-upload"))
LOGS = os.path.join(RUN06, "logs"); os.makedirs(LOGS, exist_ok=True)

def context(): return json.load(open(CONTEXT, encoding="utf-8-sig"))

def sessions():
    """Sessions of the context file, de-duplicated by sessionId (later entries win), only those with a zip path or hash."""
    out = {}
    for s in context()["sessions"]: out[s["sessionId"]] = s
    return list(out.values())

def sha256_file(p, buf=1 << 20):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(buf), b""): h.update(b)
    return h.hexdigest()

def local_zip(s):
    """Path of the session ZIP in app/data (found by session id, since 05-2 has no zip path in the context)."""
    base = os.path.join(REPO, "app", "data", "sessions")
    for d in os.listdir(base):
        if s["sessionId"] in d:
            arch = os.path.join(base, d, "archive")
            for f in os.listdir(arch):
                if f.endswith(".zip"): return os.path.join(arch, f)

def redact(text):
    text = re.sub(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<installationId>", text)
    sec = json.load(open(SECRETS, encoding="utf-8-sig"))
    for k, v in sec.items():
        if isinstance(v, str) and v: text = text.replace(v, "<token:%s>" % k.replace("-", " "))
    return text

def site_token(site_name):
    sec = json.load(open(SECRETS, encoding="utf-8-sig")); return sec[site_name.replace(" ", "-")]

def download(path, token, dest):
    """GET BASE/api<path> with a bearer token streamed to dest; returns (status, headers dict)."""
    req = urllib.request.Request(BASE + "/api" + path, headers={"Authorization": "Bearer " + token})
    try:
        r = urllib.request.urlopen(req, timeout=600)
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers)
    with open(dest, "wb") as f:
        while True:
            b = r.read(1 << 20)
            if not b: break
            f.write(b)
    return r.status, dict(r.headers)

MASK_JS = """()=>{const re=/\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi;
 const w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);let n;
 while(n=w.nextNode()){n.nodeValue=n.nodeValue.replace(re,'<installationId>').replace(/^\s*[0-9a-f]{8}\u2026\s*$/,'<installationId>')}}"""
def shot(s, name):
    """Screenshot with installation IDs (full, or the 8-char prefix shown with an ellipsis on the detail page) replaced in the DOM first."""
    s.page.evaluate(MASK_JS); return s.shot(name)
