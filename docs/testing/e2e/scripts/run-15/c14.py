"""Check 14: regression spot checks (recording detail, studies list, protocol editor open/close, clinics list) in EN, plus CS; console errors collected (404/403 resource errors from expected-denied calls listed separately)."""
import json
from ui import *
from redact import redact
C=json.load(open(CONTEXT,encoding='utf-8-sig')); S={x['patient']:x['sessionId'] for x in C['sessions']}
P=json.load(open(os.path.join(SP,'ids15.json')))['studyId']
allerr=[]
for lang in ("en","cs","de"):
    s=Sess("master",lang=lang); p=s.page
    for name,route in (("recording","/recordings/"+S['E2E1026-04-1']),("recordings","/recordings"),("studies","/studies"),("study-protocols","/studies/3/protocols"),("protocol-editor","/studies/3/protocols/1"),("clinics","/clinics"),("clinic-studies","/clinics/4/studies")):
        s.go(route,1500); t=p.inner_text('body'); bad=[w for w in("undefined","NaN","[object","null") if w in t]
        redact(p)
        if lang in("en","cs") or name=="protocol-editor": s.shot(f"14-{name}-{lang}")
        print(lang,name,"bad:",bad,"len",len(t))
    # protocol editor: open task modal and close
    s.go("/studies/3/protocols/1",1200); p.click('[data-testid=task-edit-0]'); p.wait_for_timeout(500); print(lang,"task modal open:",p.locator('.modal-form').count()); p.click('[data-testid=modal-cancel]'); p.wait_for_timeout(300); print(lang,"closed:",p.locator('.modal-form').count()==0)
    ai=[x for x in p.locator('button').all_inner_texts() if 'nstruc' in x or 'Anleit' in x or 'Pokyn' in x or 'Instrukc' in x]; print(lang,"instructions-ish buttons:",ai)
    print(lang,"STUDIES list has run15 study:", end=" "); s.go("/studies",1200); print('run15' in p.inner_text('body'))
    allerr+= [(lang,)+e for e in s.errors]; s.close()
print("ALL console errors:",allerr)
