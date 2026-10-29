"""Run 14 API checks (F-067, F-069, F-086, F-009, F-097). Tokens are never printed."""
import json, os, sys, urllib.request
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "common"))
from common import *
M=tok("master"); E=tok("editor")
secrets=json.load(open(SECRETS,encoding='utf-8-sig')); TA=secrets["E2E-1026-Clinic-A"]
if isinstance(TA,dict): TA=TA.get("token") or list(TA.values())[0]
out={}
c,projs=api('GET','/projects',M); print("projects",c,[ (p['id'],p['name'],p.get('site_count'),p.get('visible_site_count'),'count_sites' in p, p.get('is_archived')) for p in projs] if isinstance(projs,list) else projs)
for pid in (3,4):
    c,pr=api('GET',f'/projects/{pid}/protocols',M)
    print("protocols",pid,c)
    if isinstance(pr,list):
        for p in pr: print("  ",p.get('id'),p.get('name'),p.get('language'),'v',p.get('version'),'upd',p.get('updated_at'),'cr',p.get('created_at'),'desc',repr(p.get('description')))
    else: print(str(pr)[:300])
# F-097
c,ep=api('GET','/projects',E); print("editor projects",c,[(p['id'],p.get('site_count'),p.get('visible_site_count'),'count_sites' in p) for p in ep] if isinstance(ep,list) else ep)
# F-009/F-069 site-config
c,cfg=api('GET','/site-config/'+TA)
s=json.dumps(cfg,ensure_ascii=False); print("site-config",c,len(s),"Auto-created" in s)
json.dump(cfg,open(os.path.join(SP,"cfgA.json"),"w",encoding="utf-8"),ensure_ascii=False)
print("top keys",list(cfg.keys()) if isinstance(cfg,dict) else type(cfg))
# F-086 with Origin
body={"project_id":3,"name":"E2E 1026 run14 task_invalid probe","language":"en","tasks":[{"name":"x"}]}
req=urllib.request.Request(BASE+"/api/protocols",method="POST",data=json.dumps(body).encode(),headers={"Content-Type":"application/json","Authorization":"Bearer "+M,"Origin":BASE})
try: r=urllib.request.urlopen(req); print("F-086",r.status,r.read()[:300])
except urllib.error.HTTPError as e: print("F-086",e.code,e.read()[:300])
