"""Run 18: GET /api/site-config/<token> for paris|noproto and save it; tokens are redacted by construction (the config body holds none). Usage: python fetch_config.py <paris|noproto> <out.json>"""
import json, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "common"))
from common import api
which=sys.argv[1]
tok="paris000paris000paris000paris000" if which=="paris" else json.load(open(os.path.join(os.environ["E2E_STATE_DIR"],"site_tokens.json")))[which]
c,r=api("GET","/site-config/"+tok)
json.dump(r,open(sys.argv[2],"w",encoding="utf-8"),ensure_ascii=False,indent=1)
print(c,"protocols:",len(r.get("protocols",[])),"site:",r.get("siteName"))
