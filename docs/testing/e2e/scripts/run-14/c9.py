"""Check 9 F-074: 1024x768 Clinics list overflow; row menu (Computers tab, last row, innermost scroll wrap) visible; sticky header when page scrolls."""
from ui import *
from redact import redact
MB="()=>{const m=document.querySelector('[role=menu]');if(!m)return null;const b=m.getBoundingClientRect();return {l:Math.round(b.left),r:Math.round(b.right),t:Math.round(b.top),b:Math.round(b.bottom),vw:innerWidth,vh:innerHeight}}"
s=Sess("master",w=1024,h=768); p=s.page
s.go("/clinics"); s.shot("9-1-clinics-1024")
print("scroll:", p.evaluate("()=>({sw:document.documentElement.scrollWidth,cw:document.documentElement.clientWidth})"))
print("table vs card:", p.evaluate("""()=>{const t=document.querySelector('table');const wrap=t.closest('.ui-tbl-wrap');const card=wrap.parentElement;const r=e=>{const b=e.getBoundingClientRect();return [Math.round(b.left),Math.round(b.right)]};return {table:r(t),wrap:r(wrap),card:r(card),wrapScroll:wrap.scrollWidth+'/'+wrap.clientWidth}}"""))
for path,tag in (("/clinics/4/computers","computers"),("/studies/3/protocols","protocols")):
    s.go(path); redact(p); rows=p.locator('table tbody tr'); n=rows.count(); print(tag,"rows",n)
    for idx in (n-1,0):
        b=rows.nth(idx).locator('button[aria-haspopup],button[aria-label^=Actions],button').last; b.click(); p.wait_for_timeout(500); redact(p)
        print(tag,"row",idx,"menu:",p.evaluate(MB), p.locator('[role=menu]').first.inner_text().replace("\n"," | ") if p.locator('[role=menu]').count() else None)
        s.shot(f"9-2-row-menu-{tag}-row{idx}",full=False); p.keyboard.press("Escape"); p.wait_for_timeout(300)
s.close()
# sticky header: short viewport so the page must scroll
s=Sess("master",w=1024,h=420); p=s.page
for path in ("/recordings","/clinics","/users"):
    s.go(path); p.wait_for_timeout(500)
    info=p.evaluate("""()=>{const th=document.querySelector('thead th');if(!th)return null;const t0=Math.round(th.getBoundingClientRect().top);const sc=document.scrollingElement;const mainScroll=[...document.querySelectorAll('*')].filter(e=>e.scrollHeight>e.clientHeight+5&&['auto','scroll'].includes(getComputedStyle(e).overflowY)).map(e=>e.tagName+'.'+e.className).slice(0,3);return {t0,docScrollable:sc.scrollHeight>sc.clientHeight,mainScroll}}""")
    print(path,info)
    p.mouse.move(600,300); p.mouse.wheel(0,250); p.wait_for_timeout(500)
    print("  after wheel: th top",p.evaluate("()=>Math.round(document.querySelector('thead th').getBoundingClientRect().top)"),"scrollY",p.evaluate("()=>scrollY"))
    redact(p); s.shot("9-3-sticky"+path.replace("/","-"),full=False)
s.close()
