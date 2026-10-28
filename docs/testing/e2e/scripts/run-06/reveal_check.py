"""Run 06 step 5 (API): POST /uploads/:id/reveal for each context session with 'fields' and compare with the context values (writes an audit entry per call).
Env: E2E_STATE_DIR, PYTHONPATH=scripts/common."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common06 import *
for s in sessions():
    if not s.get("fields"): continue
    c, r = api("POST", "/uploads/%s/reveal" % s["sessionId"], tok("master"), {})
    f = (r or {}).get("fields", {}) if isinstance(r, dict) else {}
    print(s["patient"], c, {k: f.get(k) for k in s["fields"]}, "expected", s["fields"], "MATCH" if all(f.get(k) == v for k, v in s["fields"].items()) else "MISMATCH")
