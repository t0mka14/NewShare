"""Run 06 steps 4/5: recordings list (filter E2E), status, hasVideo, detail checks, attempts for every session of the context file.
Env: E2E_STATE_DIR (tokens.json), PYTHONPATH=scripts/common, optional E2E_SEARCH (default 'E2E'). Writes logs/api-check.json (redacted)."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common06 import *
t = tok("master"); q = os.environ.get("E2E_SEARCH", "E2E")
c, r = api("GET", "/uploads?pageSize=100&q=" + q, t); print("list", c, "total", r["total"], "kpis", r["kpis"], "video facet", r["facets"].get("video"))
rows = {x["id"]: x for x in r["rows"]}
c, rv = api("GET", "/uploads?pageSize=100&hasVideo=1&q=" + q, t); print("hasVideo=1 ->", c, sorted(x["participant"]["patient_code"] for x in rv["rows"]))
dump = {}
for s in sessions():
    sid = s["sessionId"]; row = rows.get(sid)
    c, d = api("GET", "/uploads/" + sid, t); ca, att = api("GET", "/uploads/%s/attempts" % sid, t)
    bad = [k for k in d.get("checks", []) if k["result"] != "ok"]
    z = local_zip(s); lh = sha256_file(z) if z else None
    print(s["patient"], sid[:8], "inList", bool(row), "site", d["site"], "protocol", d["protocol"], "status", d["status"], "bytes", d["bytes"], "localBytes", os.path.getsize(z),
          "sha==local", d["zipSha256"] == lh, "badChecks", bad, "issues", d["issues"], "video", d["videoClips"], "attempts", [(a["code"], a["outcome"]) for a in att], "started", d["startedAt"])
    dump[sid] = {"detail": d, "attempts": att, "localSha": lh}
open(os.path.join(LOGS, "api-check.json"), "w", encoding="utf-8").write(redact(json.dumps(dump, indent=1, ensure_ascii=False)))
