"""Checks 13, 11 (F-073), 1: create the one protocol in the throw-away study (no custom field), min>max warning, then save it."""
import json
from ui import *
P=json.load(open(os.path.join(SP,'ids14.json')))['studyId']
s=Sess("master"); p=s.page
s.go(f"/studies/{P}/protocols"); p.click('[data-testid=study-new-protocol]'); p.wait_for_timeout(500)
p.fill('[data-testid=new-protocol-name]','E2E 1026 run14 protocol'); p.click('[data-testid=new-protocol-create]'); p.wait_for_timeout(2500)
s.shot("13-7-editor-fresh")
p.click('[data-testid=task-option-phonation] > button'); p.wait_for_timeout(500)
mm=p.locator('.modal-form'); inp=mm.locator('.form-group input[type=number]')
inp.nth(1).fill('70'); inp.nth(2).fill('60'); p.wait_for_timeout(400)
print("warning (min>max):", p.locator('[role=alert]').all_inner_texts(), "save enabled:", p.locator('[data-testid=modal-save]').is_enabled())
inp.nth(1).fill('15'); p.wait_for_timeout(400)
print("alerts after fix:", p.locator('[role=alert]').all_inner_texts())
p.click('[data-testid=modal-save]'); p.wait_for_timeout(600)
s.shot("13-8-task-added")
p.click('[data-testid=protocol-save]'); p.wait_for_timeout(3000); print(p.url); s.shot("1-1-saved-new")
print(p.inner_text('body')[:500].replace("\n"," | "))
s.close()
