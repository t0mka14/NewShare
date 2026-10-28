"""Read-only API overview per persona: visible clinics, studies, recordings, library, users."""
from common import *
for k in ("master","editor","received","nogrants","creator"):
    t=tok(k); print("==",k)
    for path in ("/sites","/projects","/library","/users","/uploads?limit=5","/uploads/summary","/mappings"):
        c,r=api('GET',path,t)
        if isinstance(r,list): d=[(x.get('id'),x.get('name'),x.get('can_edit')) for x in r][:12]
        elif isinstance(r,dict): d={kk:(vv if not isinstance(vv,(list,dict)) else (len(vv))) for kk,vv in r.items()}
        else: d=str(r)[:100]
        print(path,c,str(d)[:400])
