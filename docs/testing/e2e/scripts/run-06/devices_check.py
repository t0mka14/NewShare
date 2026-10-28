"""Run 06 step 7: Computers of the clinic (GET /devices): pending/failed counts, last seen, versions. Installation IDs are redacted in the log.
Env: E2E_STATE_DIR, PYTHONPATH=scripts/common. Writes logs/devices.json."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common06 import *
c, r = api("GET", "/devices", tok("master")); print(c)
txt = json.dumps(r, indent=1); print(redact(txt)[:3000])
open(os.path.join(LOGS, "devices.json"), "w", encoding="utf-8").write(redact(txt))
