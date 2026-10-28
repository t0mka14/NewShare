"""Run A2 10.8 UI side: as the persona given in E2E_PERSONA (default editor: not assigned to clinic A) open Recordings and the deep link of the first
context-file session; screenshot both. Env: E2E_STATE_DIR (auth.json), E2E_RUN_DIR."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common06 import *
from ui import Sess
who = os.environ.get("E2E_PERSONA", "editor"); s = Sess(who); p = s.page
s.go("/recordings", wait=2000); shot(s, "step-A2-10.8-%s-recordings" % who)
print(who, "recordings page:", p.inner_text("[data-testid=recordings-page]")[:300].replace("\n", " | "))
sid = sessions()[0]["sessionId"]; p.goto(BASE + "/#/recordings/" + sid); p.wait_for_timeout(2000); shot(s, "step-A2-10.8-%s-deeplink" % who)
print("deeplink body:", p.inner_text("body")[-300:].replace("\n", " | ")); print([r for r in s.reqs if "/uploads" in r[1]][-4:])
s.close()
