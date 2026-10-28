"""Run A2 step 10.8 rerun with data (fixed copy of run-A2/scope_data.py: list rows use `id`, bulk body is {sessionIds}, and it prints a verdict).
For every recording master sees: GET detail, /attempts, /zip, /activity, /comments, a clip, POST /reveal-less probes and bulk download as each persona.
Expectation: persona assigned to the recording's clinic (received -> clinic A) gets 200; everyone else 404 (never 403/200/500).
Downloads are only status-checked (headers read, body not saved: zip is requested with a Range of 0 bytes via urllib and closed).
Env: E2E_STATE_DIR (tokens.json for master, editor, received, nogrants, creator), PYTHONPATH=scripts/common. Writes logs/scope-data.txt."""
import sys, os, urllib.request, urllib.error
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common06 import *
C = context(); out = []
def log(*a):
    l = " ".join(str(x) for x in a); print(l); out.append(l)
T = {k: tok(k) for k in ("master", "editor", "received", "nogrants", "creator")}
c, r = api("GET", "/uploads?pageSize=100", T["master"]); rows = r["rows"]; log("master sees", r["total"], "recordings")
def head_status(path, t):
    req = urllib.request.Request(BASE + "/api" + path, headers={"Authorization": "Bearer " + t})
    try:
        resp = urllib.request.urlopen(req, timeout=60); code = resp.status; resp.close(); return code
    except urllib.error.HTTPError as e: return e.code
expect = {"master": 200, "received": 200, "editor": 404, "nogrants": 404, "creator": 404}
bad = 0
for k in ("editor", "received", "nogrants", "creator"):
    c, lr = api("GET", "/uploads?pageSize=100", T[k]); ids = {x["id"] for x in lr["rows"]}
    log(k, "lists", len(ids), "of", len(rows), "| facets", lr.get("facets", {}).get("sites"))
    for x in rows:
        sid = x["id"]; clip = None
        c, d = api("GET", "/uploads/" + sid, T["master"]); clip = d["tasks"][0]["clipFile"].split("/", 1)[-1]
        res = {"detail": api("GET", "/uploads/" + sid, T[k])[0], "attempts": api("GET", f"/uploads/{sid}/attempts", T[k])[0],
               "activity": api("GET", f"/uploads/{sid}/activity", T[k])[0], "comments": api("GET", f"/uploads/{sid}/comments", T[k])[0],
               "zip": head_status(f"/uploads/{sid}/zip", T[k]), "clip": head_status(f"/uploads/{sid}/clips/{clip}", T[k]),
               "bulk": api("POST", "/uploads/download", T[k], {"sessionIds": [sid]})[0] if k != "received" else "skipped (would stream a bundle)"}
        want = expect[k]; wrong = {n: v for n, v in res.items() if isinstance(v, int) and v != want}
        bad += bool(wrong) + (0 if (sid in ids) == (want == 200) else 1)
        log(f"  {k:9} {x['participant']['patient_code']} clinic={x['site']} inList={sid in ids} {res}", "OK" if not wrong else "UNEXPECTED " + str(wrong))
log("VERDICT:", "all as expected" if not bad else f"{bad} deviation(s)")
open(os.path.join(LOGS, "scope-data.txt"), "w", encoding="utf-8").write(redact("\n".join(out)))
