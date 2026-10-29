"""Check 7 F-096 (received persona), 8 F-087 (Computers config column), CS tab count."""
import re
from ui import *
s=Sess("received"); p=s.page
s.go("/clinics/4/studies"); s.shot("7-received-clinic4-studies"); print("URL",p.url.split('#')[-1])
print("TEXT:", p.inner_text('main, body')[:1200].replace("\n"," | "))
print("switches:", p.locator('[role=switch],input[type=checkbox],[data-testid*=switch]').count(), p.locator('[role=switch],input[type=checkbox],[data-testid*=switch]').evaluate_all("e=>e.map(x=>x.dataset.testid||x.outerHTML.slice(0,80))"))
s.close()
s=Sess("master"); p=s.page
s.go("/clinics/4/studies"); s.shot("7-master-clinic4-studies"); print("MASTER switches:", p.locator('[role=switch]').evaluate_all("e=>e.map(x=>x.dataset.testid)")); print("MASTER TEXT:", p.inner_text('main, body')[:900].replace("\n"," | "))
s.go("/clinics/4/computers"); s.shot("8-clinic4-computers"); print("COMPUTERS URL",p.url.split('#')[-1]); print(p.inner_text('main, body')[:1200].replace("\n"," | "))
print("headers:", p.locator('th').all_inner_texts())
print("tooltips:", p.locator('td [title], td[title]').evaluate_all("e=>e.map(x=>x.title+'::'+x.textContent.trim().slice(0,40))"))
s.close()
s=Sess("editor",lang="cs"); p=s.page; s.go("/studies/3/clinics"); print("cs tabs:", p.locator('a,button').all_inner_texts()[:40]); s.close()
