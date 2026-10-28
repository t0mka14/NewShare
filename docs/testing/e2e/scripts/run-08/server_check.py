"""Run 08 part 3: server-side check after the blocked upload was retried.
Usage: python server_check.py <label> <dir for downloaded ZIPs (empty folder outside the repo)> <sessionId>[=<local zip path relative to repo>] ...
For each session: GET /uploads/<id> (status etc.), GET /uploads/<id>/attempts (count and codes), and when a local zip path is given
download the ZIP and compare SHA-256. Output redacted (installation ID, tokens) to $E2E_RUN_DIR/logs/server-<label>.txt.
Env: E2E_STATE_DIR (tokens.json), PYTHONPATH=scripts/common;scripts/run-06;scripts/run-07, E2E_RUN_DIR (default reports/2026-10-26-run-08-errors)."""
import sys, os
HERE = os.path.dirname(os.path.abspath(__file__))
os.environ.setdefault("E2E_RUN_DIR", os.path.abspath(os.path.join(HERE, "..", "..", "reports", "2026-10-26-run-08-errors")))
sys.path.insert(0, os.path.join(HERE, "..", "run-07"))
from common07 import *
label, dest = sys.argv[1], sys.argv[2]; os.makedirs(dest, exist_ok=True)
m = tok("master"); out = []
def log(*x):
    l = red(" ".join(str(i) for i in x)); print(l); out.append(l)
for arg in sys.argv[3:]:
    sid, _, zp = arg.partition("=")
    c, r = api("GET", "/uploads/" + sid, m); log("GET /uploads/%s" % sid, c)
    if c == 200:
        for k in ("status", "clinicName", "quarantined", "receivedAt", "bytes", "zipSha256", "issues", "recovered", "flags"):
            if k in r: log("  ", k, "=", json.dumps(r[k], ensure_ascii=False))
        log("  keys", sorted(r.keys()))
    c, a = api("GET", "/uploads/%s/attempts" % sid, m); log("GET attempts", c, "count =", len(a) if c == 200 else "-")
    if c == 200:
        for x in a: log("  attempt", json.dumps(x, ensure_ascii=False))
    if zp:
        local = os.path.join(REPO, zp); p = os.path.join(dest, os.path.basename(local))
        code, h = download("/uploads/%s/zip" % sid, m, p)
        a1, b1 = sha256_file(local), (sha256_file(p) if code == 200 else None)
        log("  zip download", code, "local", a1, "server", b1, "MATCH" if a1 == b1 else "MISMATCH")
open(os.path.join(LOGS, "server-%s.txt" % label), "w", encoding="utf-8").write("\n".join(out))
