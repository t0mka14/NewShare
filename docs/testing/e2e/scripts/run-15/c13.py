"""Check 13 (F-109 web tile): camera tile of recording 05-1 renders as before (EN/CS/DE); check 8: any quarantined session? (uploads API)."""
import json
from ui import *
from redact import redact
C=json.load(open(CONTEXT,encoding='utf-8-sig')); S={x['patient']:x['sessionId'] for x in C['sessions']}
c,r=api('GET','/uploads?pageSize=200',tok('master')); rows=r['rows']
from collections import Counter
print("uploads:",len(rows),Counter((x['status'],bool(x.get('invalidReason')),x.get('siteId') is None) for x in rows))
for lang in ("en","cs","de"):
    s=Sess("master",lang=lang); p=s.page; s.go("/recordings/"+S['E2E1026-05-1'],1800)
    t=p.inner_text('body'); i=t.find('Camera') if lang=="en" else -1
    sess=p.locator('text=/Camera|Kamera/').first
    print(lang,"camera-ish lines:",[l for l in t.split("\n") if re.search(r"camera|kamera|webcam|video",l,re.I)][:10])
    print(lang,"undefined?", "undefined" in t, "Details btns:", p.get_by_role("button",name=re.compile("Details")).count())
    redact(p); s.shot(f"13-camera-tile-05-1-{lang}"); print(" errors",s.errors[:3]); s.close()
