"""Run 17: set siteToken in app/data/config/settings.json to the Paris token (published in test-plan section 2; not in the secrets file) while the app is STOPPED.
Never prints the token. Usage: python set_paris_token.py   (E2E_SETTINGS overrides the path)"""
import json, os, sys
REPO=os.path.abspath(os.path.join(os.path.dirname(__file__),*[".."]*5))
p=os.environ.get("E2E_SETTINGS", os.path.join(REPO,"app","data","config","settings.json"))
d=json.load(open(p,encoding="utf-8-sig"))
old=d.get("siteToken")
d["siteToken"]="paris000paris000paris000paris000"
open(p,"w",encoding="utf-8",newline="\n").write(json.dumps(d,ensure_ascii=False))
print("siteToken set to <token:Paris> (was %s) | keys now: %s"%("present" if old else "absent",sorted(d.keys())))
