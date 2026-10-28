from ui import *
from common import *
s=Sess("master"); p=s.page
s.go("/studies/3/protocols")
p.locator('[data-testid=study-protocols-table] tbody tr').first.locator('button').last.click(); p.wait_for_timeout(300)
p.get_by_role("menuitem",name="Update library entry").click(); p.wait_for_timeout(1500); s.shot("library-update-entry"); 
print([t for t in p.locator('[role=status],.ui-toast,[data-testid^=toast]').all_inner_texts()])
t=tok('master')
c,r=api('GET','/library',t); print(json.dumps(r,ensure_ascii=False)[:600])
s.go("/studies/4/protocols"); print(p.inner_text('[data-testid=study-protocols]')[-300:])
# config A
sec=json.load(open(REPORTS+r'\2026-10-26-secrets.json'))
cfg=api('GET','/site-config/'+sec['E2E-1026-Clinic-A'])[1]; print("A protocols:", [(x['name'],x['version']) for x in cfg['protocols']])
s.close()
