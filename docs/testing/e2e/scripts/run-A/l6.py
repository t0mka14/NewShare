from ui import *
from common import *
s=Sess("master"); p=s.page
s.go("/library/13"); print(p.url)
print(p.locator('button').evaluate_all('e=>e.map(x=>(x.getAttribute("aria-label")||x.textContent).trim())'))
p.locator('button[aria-label*="ore"],button[aria-label*="ctions"]').last.click(); p.wait_for_timeout(300); s.shot("library-entry-menu"); print(p.locator('[role=menu]').inner_text())
p.get_by_role("menuitem",name=re.compile("Withdraw")).click(); p.wait_for_timeout(600); s.shot("library-withdraw-confirm"); print(p.locator('.ui-modal,[role=dialog]').last.inner_text())
p.get_by_role("button",name=re.compile("Withdraw")).last.click(); p.wait_for_timeout(1500); print(p.url); s.shot("library-after-withdraw")
print(p.inner_text('body')[-300:])
t=tok('master'); print(api('GET','/library',t)); print(api('GET','/library/13',t)[0])
s.go("/studies/4/protocols"); print(p.inner_text('[data-testid=study-protocols]')[-200:])
s.close()
