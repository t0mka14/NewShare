"""Check 13 (F-006): Create and edit in the throw-away study; inspect patient_code field and file name template. Then add a phonation task and save once (also feeds checks 1, 2, 11)."""
import json
from ui import *
P=json.load(open(os.path.join(SP,'ids14.json')))['studyId']
s=Sess("master"); p=s.page
s.go(f"/studies/{P}/protocols"); s.shot("13-1-study-empty")
p.click('[data-testid=study-new-protocol]'); p.wait_for_timeout(500)
p.fill('[data-testid=new-protocol-name]','E2E 1026 run14 protocol'); s.shot("13-2-new-modal")
print(p.inner_text('[data-testid=new-protocol-modal]').replace("\n"," | "))
p.click('[data-testid=new-protocol-create]'); p.wait_for_timeout(2500); print(p.url); s.shot("13-3-editor-new")
print(p.inner_text('body')[:2500].replace("\n"," | "))
print("inputs:", p.locator('input,textarea').evaluate_all("e=>e.map(x=>(x.dataset.testid||x.name||x.id||'')+'='+x.value.slice(0,80))"))
s.close()
