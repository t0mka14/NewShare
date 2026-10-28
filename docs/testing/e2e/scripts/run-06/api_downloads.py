"""Run 06 step 6 (API side): download the whole ZIP of every session, one WAV and one MP4 clip, compare SHA-256 with the local ZIP, ffprobe the MP4s.
Usage: python api_downloads.py <empty download dir>   Env: E2E_STATE_DIR, PYTHONPATH=scripts/common. Writes logs/downloads.txt."""
import sys, os, subprocess, zipfile, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common06 import *
dest = sys.argv[1]; os.makedirs(dest, exist_ok=True)
t = tok("master"); out = []
def log(*a):
    l = " ".join(str(x) for x in a); print(l); out.append(l)
for s in sessions():
    sid = s["sessionId"]; z = local_zip(s); lh = sha256_file(z)
    p = os.path.join(dest, os.path.basename(z)); code, h = download("/uploads/%s/zip" % sid, t, p)
    dh = sha256_file(p) if code == 200 else None
    log("ZIP", s["patient"], sid, "HTTP", code, "local", lh, "downloaded", dh, "EQUAL" if dh == lh else "DIFFERENT", "disposition", h.get("Content-Disposition"), "size", os.path.getsize(p) if code == 200 else None)
    c, d = api("GET", "/uploads/" + sid, t)
    for task in d["tasks"]:
        for kind in ("clipFile", "videoFile"):
            f = task.get(kind)
            if not f or (kind == "clipFile" and task["taskIndex"] not in (0,)) : continue   # one WAV per session (task 0) and every MP4
            cp = os.path.join(dest, "%s_%s" % (sid[:8], os.path.basename(f)))
            code, h = download("/uploads/%s/clips/%s" % (sid, f.split("/", 1)[1] if f.startswith("clips/") else f), t, cp)
            if code != 200: code, h = download("/uploads/%s/clips/%s" % (sid, f), t, cp)
            # compare with the bytes inside the local zip
            zf = zipfile.ZipFile(z); inner = [n for n in zf.namelist() if n.endswith(os.path.basename(f))]
            ih = None
            if inner:
                import hashlib; hh = hashlib.sha256()
                with zf.open(inner[0]) as fi:
                    for b in iter(lambda: fi.read(1 << 20), b""): hh.update(b)
                ih = hh.hexdigest()
            dh = sha256_file(cp) if code == 200 else None
            pr = ""
            if code == 200:
                r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration:stream=codec_name,width,height", "-of", "json", cp], capture_output=True, text=True)
                j = json.loads(r.stdout or "{}") if r.returncode == 0 else {"err": r.stderr.strip()}
                pr = json.dumps({"dur": (j.get("format") or {}).get("duration"), "streams": [(x.get("codec_name"), x.get("width"), x.get("height")) for x in j.get("streams", [])]}) if "err" not in j else j["err"]
            log("CLIP", s["patient"], f, "HTTP", code, "sha==inside-zip", dh == ih, "ffprobe", pr)
open(os.path.join(LOGS, "downloads.txt"), "w", encoding="utf-8").write(redact("\n".join(out)))
