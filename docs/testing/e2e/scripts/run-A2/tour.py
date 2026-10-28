"""Sanity tour per persona (§10.4/10.5 + step 6). Usage: tour.py editor received nogrants creator
Ids from the context file (study = speech, clinics A/B) and a2ids.json (throw-away study). Visits routes, screenshots (1440x900 full page),
records final URL, nav items, headline, primary buttons, edit controls, console errors, non-2xx API calls and raw-key / undefined text."""
import sys, json, re, os
from ui import *
C=json.load(open(CONTEXT,encoding='utf-8-sig')); S=C["projects"]["speech"]["id"]; V=C["projects"]["video"]["id"]; A=C["sites"]["A"]["id"]; B=C["sites"]["B"]["id"]
try: T6=json.load(open(os.path.join(SP,'a2ids.json')))['throwawayStudyId']
except Exception: T6=None
ROUTES=["/recordings","/studies",f"/studies/{S}",f"/studies/{S}/clinics",f"/studies/{S}/recordings",f"/studies/{S}/editors",f"/studies/{S}/settings",f"/studies/{V}",
        "/clinics",f"/clinics/{A}",f"/clinics/{A}/computers",f"/clinics/{A}/recordings",f"/clinics/{A}/settings",f"/clinics/{B}/studies","/library","/library/15","/users","/does-not-exist"]
if T6: ROUTES+= [f"/studies/{T6}",f"/studies/{T6}/editors"]
RAW=re.compile(r"\b[a-z][a-zA-Z]+(?:\.[a-zA-Z_]+){2,}\b")
EDIT_IDS=["study-save","new-protocol","study-archive","study-settings-save","study-add-editor","clinic-add-study","clinic-settings-save","library-add"]
for who in sys.argv[1:]:
    s=Sess(who); p=s.page; print("=====",who)
    p.goto(BASE+"/#/"); p.wait_for_timeout(1500); print("landing at #/ with a valid session:",p.url)
    for r in ROUTES:
        s.reqs.clear(); s.errors.clear(); s.go(r, 1500)
        nav=p.evaluate("()=>[...document.querySelectorAll('[data-testid^=nav-]')].map(e=>e.dataset.testid+':'+e.textContent.trim().replace(/\s+/g,' '))")
        h=p.evaluate("()=>{const e=document.querySelector('h1,[data-testid=page-title]');return e?e.textContent.trim():null}")
        txt=p.inner_text('body'); raw=sorted(set(RAW.findall(txt))); und=[m for m in ('undefined','null','NaN','[object') if m in txt]
        bad=[(m,u.split('/api')[-1],c) for m,u,c in s.reqs if c>=400]
        prim=s.primaries()
        ctl=[t for t in EDIT_IDS if p.locator(f'[data-testid={t}]').count()]
        name=who+"-"+(re.sub(r'[^a-zA-Z0-9]+','_',r).strip('_') or 'root'); s.shot(name)
        print(f"{r} -> {p.url.split('#')[-1]} | h={h} | nav={[n.split(':')[0] for n in nav]} | prim={prim} | editctl={ctl} | raw={raw} {und} | api>=400={bad} | consoleErr={len(s.errors)}")
    s.close()
