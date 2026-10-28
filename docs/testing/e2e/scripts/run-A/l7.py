from ui import *
from common import *
s=Sess("master"); p=s.page
s.go("/studies/3/protocols")
p.locator('[data-testid=study-protocols-table] tbody tr').first.locator('button').last.click(); p.wait_for_timeout(300); print(p.locator('[role=menu]').inner_text())
s.shot("protocol-menu-after-withdraw")
mi=p.get_by_role("menuitem",name=re.compile("Share to library|Update library entry")); print(mi.all_inner_texts()); mi.first.click(); p.wait_for_timeout(1500)
t=tok('master'); print(json.dumps(api('GET','/library',t)[1],ensure_ascii=False)[:400])
# archive the copy in study 4
s.go("/studies/4/protocols")
row=p.locator('[data-testid=study-protocols-table] tbody tr').filter(has_text="E2E 1026 Speech copy")
row.locator('button').last.click(); p.wait_for_timeout(300); p.get_by_role("menuitem",name="Archive").click(); p.wait_for_timeout(600); s.shot("protocol-archive-confirm"); print(p.locator('.ui-modal,[role=dialog]').last.inner_text())
p.get_by_role("button",name=re.compile("^Archive")).last.click(); p.wait_for_timeout(1500); s.shot("study4-protocols-archived-section")
print(p.inner_text('[data-testid=study-protocols]'))
cfg=api('GET','/site-config/'+json.load(open(REPORTS+r'\2026-10-26-secrets.json'))['E2E-1026-Clinic-A'])[1]; print("A protocols:", [(x['name'],x['version']) for x in cfg['protocols']])
s.close()
