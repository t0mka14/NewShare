"""Run 07: server-side state of this installation's computer and the run-07 recording.
Usage: python server_state.py <label> [sessionId]   (sessionId default: the run-07 session of the context file)
Prints and writes logs/state-<label>.txt (redacted): all computers (GET /devices), the recording (GET /uploads/:id) incl. status/clinic/receivedAt,
its attempts (GET /uploads/:id/attempts) and, if present, the list row (GET /uploads?q=<patient>)."""
import sys
from common07 import *
label = sys.argv[1]
s = session07(); sid = sys.argv[2] if len(sys.argv) > 2 else s["sessionId"]
m = tok("master"); out = []
def log(*x):
    l = " ".join(str(i) for i in x); print(red(l)); out.append(red(l))
c, d = api("GET", "/devices", m); log("GET /devices", c)
for dev in d["devices"] if c == 200 else []:
    log("  device", {k: dev.get(k) for k in ("id", "installationId", "siteId", "siteName", "lastSeenAt", "lastConfigVersion", "pending", "failed", "uploads30", "neverUploaded", "health")})
if c == 200: log("  stats", d["stats"])
c, r = api("GET", "/uploads/" + sid, m); log("GET /uploads/<sessionId>", c)
if c == 200:
    log("  keys", sorted(r.keys()))
    for k in ("id", "status", "siteId", "siteName", "clinicId", "clinicName", "deviceId", "quarantined", "receivedAt", "bytes", "zipSha256", "issues", "patientCode", "protocolName"):
        if k in r: log("  ", k, "=", json.dumps(r[k], ensure_ascii=False))
else: log("  ", json.dumps(r)[:300])
c, a = api("GET", "/uploads/" + sid + "/attempts", m); log("GET attempts", c)
if c == 200:
    for x in a: log("  attempt", json.dumps(x, ensure_ascii=False))
c, l = api("GET", "/uploads?q=" + s["patient"] + "&pageSize=10", m); log("GET /uploads?q=", c)
if c == 200:
    rows = l.get("items") or l.get("uploads") or l.get("rows") or (l if isinstance(l, list) else [])
    for x in rows: log("  row", json.dumps({k: v for k, v in x.items() if not isinstance(v, (dict, list)) or k in ("site", "flags")}, ensure_ascii=False))
    if not rows: log("  keys", list(l.keys()) if isinstance(l, dict) else type(l))
open(os.path.join(LOGS, "state-%s.txt" % label), "w", encoding="utf-8").write("\n".join(out))
