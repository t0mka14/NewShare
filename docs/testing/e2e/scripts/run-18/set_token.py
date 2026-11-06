"""Run 18: set siteToken (and optionally drop installationId) in app/data/config/settings.json while the app is STOPPED. Never prints a token.
Usage: python set_token.py paris|noproto [--drop-installation-id]   (noproto token is read from $E2E_STATE_DIR/site_tokens.json; Paris token is published in the test plan)
Dropping installationId is needed because a device is bound to the first clinic it checks in with (HTTP 409 otherwise, F-17-2)."""
import json, os, sys
REPO=os.path.abspath(os.path.join(os.path.dirname(__file__),*[".."]*5))
p=os.environ.get("E2E_SETTINGS", os.path.join(REPO,"app","data","config","settings.json"))
which=sys.argv[1]
tok="paris000paris000paris000paris000" if which=="paris" else json.load(open(os.path.join(os.environ["E2E_STATE_DIR"],"site_tokens.json")))[which]
d=json.load(open(p,encoding="utf-8-sig")); d["siteToken"]=tok
if "--drop-installation-id" in sys.argv: d.pop("installationId",None)
open(p,"w",encoding="utf-8",newline="\n").write(json.dumps(d,ensure_ascii=False))
print("siteToken set to <token:%s> | keys now: %s"%(which,sorted(d.keys())))
