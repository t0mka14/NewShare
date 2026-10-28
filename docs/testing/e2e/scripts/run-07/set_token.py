"""Run 07 step 7 (and end state): set or clear siteToken in app/data/config/settings.json while the app is STOPPED. Synthetic keyboard input (SendKeys, keybd_event, long-press menu)
did not reach the Compose window in this RDP session, and the token must not be typed through a tool call, so the token is written by script and the app's start-up
config refresh + check-in is the trigger (instead of typing it in Settings). The token is read from the secrets file and never printed.
Usage: python set_token.py <secrets key e.g. E2E-1026-Clinic-B | none>   (E2E_SETTINGS = settings.json path)"""
import sys, json, os
from common07 import *
key = sys.argv[1]
p = os.environ.get("E2E_SETTINGS", os.path.join(REPO, "app", "data", "config", "settings.json"))
d = json.load(open(p, encoding="utf-8-sig"))
if key == "none": d.pop("siteToken", None)
else:
    d["siteToken"] = json.load(open(SECRETS, encoding="utf-8-sig"))[key]
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, ensure_ascii=False))
print("siteToken set from %s" % key if key != "none" else "siteToken removed", "| keys now:", sorted(d.keys()))
