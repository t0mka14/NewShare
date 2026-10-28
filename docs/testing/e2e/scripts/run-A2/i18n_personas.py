"""CS / DE pass over the persona screens: raw keys, 'undefined', and English leftovers (heuristic word list). Usage: i18n_personas.py"""
import re, json
from ui import *
RAW=re.compile(r"\b[a-z][a-zA-Z]+(?:\.[a-zA-Z_]+){2,}\b")
EN=re.compile(r"\b(Recordings|Studies|Clinics|Library|Not available|No recordings yet|Managed by|Received|Add study|Study not found|Page not found|Go to|Search|Download|Settings|Computers|Protocols)\b")
PLAN={"received":["/recordings","/studies/3","/clinics/4/studies","/clinics/4/computers","/clinics/4/settings","/library"],
      "nogrants":["/recordings","/studies","/clinics","/studies/3"],
      "editor":["/studies/3/editors","/clinics/5/studies","/library","/library/15"],"creator":["/studies","/clinics","/library"]}
for lang in ("cs","de"):
    for who,routes in PLAN.items():
        s=Sess(who,lang=lang); p=s.page
        for r in routes:
            s.go(r,1500); t=p.inner_text('body'); name=f"i18n-{lang}-{who}-"+re.sub(r'[^a-zA-Z0-9]+','_',r).strip('_')
            s.shot(name); print(lang,who,r,"raw=",sorted(set(RAW.findall(t))),"undef=",[m for m in('undefined','NaN','[object') if m in t],"english=",sorted(set(EN.findall(t))))
        s.close()
