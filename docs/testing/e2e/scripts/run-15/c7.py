"""Check 7 F-112 (participant panel label) + check 8 (quarantine banner: look for any quarantined/invalid recording) + check 13 camera tile of recording 05-1."""
import json
from ui import *
from redact import redact
C=json.load(open(CONTEXT,encoding='utf-8-sig')); S={x['patient']:x['sessionId'] for x in C['sessions']}
for lang in ("en","cs","de"):
    s=Sess("master",lang=lang); p=s.page
    s.go("/recordings/"+S['E2E1026-03-1']); p.wait_for_timeout(1500)
    sh=p.get_by_role("button",name=re.compile("Show values|Zobrazit hodnoty|Werte anzeigen",re.I)); print(lang,"show-values btn:",sh.count(), p.get_by_role("button").all_inner_texts()[:15])
    if sh.count(): sh.first.click(); p.wait_for_timeout(500)
    redact(p); t=p.inner_text('body'); i=t.find('Year'); 
    print(lang,"panel text around:", [l for l in t.split("\n") if re.search(r"year|rok|jahr|birth|narození|geburt|visit|náv|besuch|_opt|\bopt\b",l,re.I)])
    s.shot(f"7-participant-panel-{lang}"); print(lang,"errors",s.errors[:3]); s.close()
