"""Run 06 step 8: re-send idempotency and 409 conflict with curl, form fields in the order of docs/dev/server-upload-integration.md §1
(installationId, sessionId, zipSha256, siteToken, appVersion, zip). The clinic token is read from the secrets file and given to curl on stdin
(never on the command line, never printed). installationId is read from app/data/config/settings.json (path via E2E_SETTINGS) and not printed.
Usage: python resend.py <resendSessionKey e.g. 05-2> <conflictZipKey e.g. 03-1> [conflictSessionKey]
  1) re-post the ZIP of session A with its own id/hash   -> expect HTTP 200 and the original receipt
  2) post the ZIP of session B under session A's id with B's correct hash -> expect HTTP 409 session_exists
Writes logs/resend.txt (redacted)."""
import sys, os, subprocess, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common06 import *
S = {x["patient"].replace("E2E1026-", ""): x for x in sessions()}
a, b = S[sys.argv[1]], S[sys.argv[2]]
settings = json.load(open(os.environ.get("E2E_SETTINGS", os.path.join(REPO, "app", "data", "config", "settings.json")), encoding="utf-8-sig"))
site = context()["sites"]["A"]["name"]; token = site_token(site)
out = []
def log(*x):
    l = " ".join(str(i) for i in x); print(l); out.append(l)
def post(sid, zip_path, sha):
    cmd = ["curl", "-s", "-w", "\nHTTP %{http_code}\n", "-X", "POST", BASE + "/api/upload",
           "-F", "installationId=" + settings["installationId"], "-F", "sessionId=" + sid, "-F", "zipSha256=" + sha,
           "-F", "siteToken=<-", "-F", "appVersion=1.0.0", "-F", "zip=@%s;type=application/zip" % zip_path]
    r = subprocess.run(cmd, input=token, capture_output=True, text=True)
    return redact(r.stdout.replace(settings["installationId"], "<installationId>")), r.stderr[:200]
az, bz = local_zip(a), local_zip(b)
c, before = api("GET", "/uploads/" + a["sessionId"] + "/attempts", tok("master"))
log("attempts before:", len(before))
log("== 1) re-send", a["patient"], a["sessionId"]); o, e = post(a["sessionId"], az, sha256_file(az)); log(o, e)
log("== 2) conflict: ZIP of", b["patient"], "under session id of", a["patient"], "with the correct hash of that ZIP"); o, e = post(a["sessionId"], bz, sha256_file(bz)); log(o, e)
c, after = api("GET", "/uploads/" + a["sessionId"] + "/attempts", tok("master"))
log("attempts after:", json.dumps([(x["code"], x["outcome"], x["bytes"]) for x in after]))
c, d = api("GET", "/uploads/" + a["sessionId"], tok("master")); log("record after: status", d["status"], "bytes", d["bytes"], "sha", d["zipSha256"], "issues", d["issues"])
open(os.path.join(LOGS, "resend.txt"), "w", encoding="utf-8").write(redact("\n".join(out)))
