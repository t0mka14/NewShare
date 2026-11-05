"""Run 17: remove installationId from settings.json while the app is STOPPED, so the app generates a new one (InstallationIdProvider) and registers as a NEW device of the
Paris site. Needed because this machine's existing device is registered to another site (check-in 409, config from Paris not applied). Nothing is printed of the ID."""
import json, os
REPO=os.path.abspath(os.path.join(os.path.dirname(__file__),*[".."]*5))
p=os.environ.get("E2E_SETTINGS", os.path.join(REPO,"app","data","config","settings.json"))
d=json.load(open(p,encoding="utf-8-sig")); had=d.pop("installationId",None) is not None
open(p,"w",encoding="utf-8",newline="\n").write(json.dumps(d,ensure_ascii=False))
print("installationId removed (was present: %s) | keys now: %s"%(had,sorted(d.keys())))
