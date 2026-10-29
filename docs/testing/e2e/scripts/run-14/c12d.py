"""F-035/F-036: expand the camera 'Details' on 05-3 and every session's text for gain/undefined; Czech and German view of 05-3 (check 14)."""
import re
from ui import *
from redact import redact
c,lst=api('GET','/uploads?limit=50',tok('master')); ids={r['id'][:8]:r['id'] for r in lst['rows']}
s=Sess("master"); p=s.page
s.go("/recordings/"+ids["d3b229fa"]); p.wait_for_timeout(1000)
p.locator('summary:has-text("Details")').first.click(); p.wait_for_timeout(400); redact(p); s.shot("12-4-camera-details-open")
print("DETAILS:", p.locator('details').first.inner_text().replace("\n"," | ")[:600])
for short,i in ids.items():
    s.go("/recordings/"+i); p.wait_for_timeout(700)
    for d in p.locator('summary').all(): 
        try: d.click(timeout=500)
        except Exception: pass
    t=p.inner_text('body'); print(short,"gain:",re.findall(r"(?i)[^\n]*gain[^\n]*",t),"undefined:","undefined" in t)
s.close()
for lang in ("cs","de"):
    s=Sess("master",lang=lang); p=s.page; s.go("/recordings/"+ids["d3b229fa"]); p.wait_for_timeout(800)
    p.locator('rows').count()
    rows=p.locator('table tbody tr'); rows.nth(0).locator('button').nth(1).click(); p.wait_for_timeout(800); redact(p); s.shot(f"14-recording-05-3-{lang}")
    t=p.inner_text('body'); print(lang,"undefined" in t, re.findall(r"\b[a-z]+[A-Za-z]*\.[a-z][A-Za-z.]+\b",t)[:5]); print([l for l in t.split("\n") if re.search(r"(?i)kamer|camera|přehr|abspiel|stáh|herunter",l)][:8]); s.close()
