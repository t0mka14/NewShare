"""F-074 sticky header detail: computed styles and behaviour at 1440x420 (tables fit) and 1024x420."""
from ui import *
for w in (1440,1024):
    s=Sess("master",w=w,h=420); p=s.page
    for path in ("/recordings","/clinics","/users","/studies","/studies/3/protocols"):
        s.go(path); p.wait_for_timeout(500)
        info=p.evaluate("""()=>{const th=document.querySelector('thead th');if(!th)return null;const wrap=th.closest('.ui-tbl-wrap');const cs=getComputedStyle(th);const ws=wrap?getComputedStyle(wrap):null;return {thPos:cs.position,thTop:cs.top,wrapOverflowX:ws&&ws.overflowX,wrapOverflowY:ws&&ws.overflowY,wrapFits:wrap&&wrap.scrollWidth<=wrap.clientWidth,y0:Math.round(th.getBoundingClientRect().top),docH:document.scrollingElement.scrollHeight}}""")
        p.mouse.move(600,300); p.mouse.wheel(0,400); p.wait_for_timeout(400)
        y1=p.evaluate("()=>Math.round(document.querySelector('thead th')?.getBoundingClientRect().top)"); sy=p.evaluate("()=>scrollY")
        print(w,path,info,"-> after scroll th top",y1,"scrollY",sy)
        if path=="/recordings" : s.shot(f"9-4-sticky-{w}-recordings",full=False)
    s.close()
