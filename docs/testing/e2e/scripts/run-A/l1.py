from ui import *
s=Sess("master"); p=s.page
s.go("/studies/3/protocols")
row=p.locator('[data-testid=study-protocols-table] tbody tr').first
print(row.inner_text())
btns=row.locator('button'); print(btns.count(), btns.evaluate_all('e=>e.map(x=>x.getAttribute("aria-label")||x.textContent)'))
btns.last.click(); p.wait_for_timeout(400); s.shot("protocol-row-menu"); print(p.locator('[role=menu]').inner_text())
s.close()
