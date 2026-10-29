"""Check 12 detail: session d3b229fa (camera notice, 1 video clip) and 05-1 (3 video clips): notice layout, gain line, video click."""
import re
from ui import *
from redact import redact
c,lst=api('GET','/uploads?limit=50',tok('master')); ids={r['id'][:8]:r['id'] for r in lst['rows']}
s=Sess("master"); p=s.page
for short in ("d3b229fa","f1b25e4e"):
    sid=ids[short]; s.go("/recordings/"+sid); p.wait_for_timeout(1200); redact(p)
    tile=p.locator('text=Camera').first
    print(short,"session panel:", p.locator('text=Session').first.locator('xpath=ancestor::*[contains(@class,"card") or self::section][1]').inner_text().replace("\n"," | ")[:900])
    print(short,"gain mentions:", re.findall(r"(?i)[^\n]*gain[^\n]*", p.inner_text('body')))
    # notice geometry
    print(short,"notice geom:", p.evaluate("""()=>{const out=[];for(const e of document.querySelectorAll('*')){if(e.children.length===0&&/could not capture|OBS Virtual Camera/.test(e.textContent)){const b=e.getBoundingClientRect();out.push([e.textContent.trim().slice(0,50),Math.round(b.left),Math.round(b.top),Math.round(b.width),Math.round(b.height),getComputedStyle(e).display])}}return out}"""))
    s.shot(f"12-2-recording-{short}-session")
    vids=p.locator('table button').filter(has=p.locator('svg')); print(short,"clip buttons:", vids.count())
    s.close() if False else None
# open video clip on the one with notice, timing the fallback
sid=ids["d3b229fa"]; s.go("/recordings/"+sid); p.wait_for_timeout(1000)
rows=p.locator('table tbody tr'); print("rows",rows.count(), [rows.nth(i).inner_text().replace("\n"," | ") for i in range(rows.count())])
import time
for i in range(rows.count()):
    b=rows.nth(i).locator('button'); print("row",i,"buttons",b.count())
    if b.count()>=2:
        t0=time.time(); b.nth(1).click()
        try:
            p.wait_for_selector('text=/Download/', timeout=15000); print("fallback appeared after %.1fs"%(time.time()-t0))
        except Exception as e: print("no fallback within 15s")
        p.wait_for_timeout(500); redact(p); s.shot(f"12-3-video-{i}"); print("DIALOG/TEXT:", p.locator('[role=dialog],.ui-modal,.clip-player,video').first.evaluate("e=>e.outerHTML.slice(0,200)") if p.locator('[role=dialog],.ui-modal,.clip-player,video').count() else 'none'); 
        print("page text near:", [l for l in p.inner_text('body').split("\n") if re.search(r"(?i)play|download|browser",l)][:6]); break
s.close()
