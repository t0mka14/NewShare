"""Check 13 detail + F-076 hint + F-073 duration warning; protocol is created once and saved (one protocol in the throw-away study)."""
import json
from ui import *
P=json.load(open(os.path.join(SP,'ids14.json')))['studyId']
s=Sess("master"); p=s.page
s.go(f"/studies/{P}/protocols"); p.click('[data-testid=study-new-protocol]'); p.wait_for_timeout(500)
p.fill('[data-testid=new-protocol-name]','E2E 1026 run14 protocol'); p.click('[data-testid=new-protocol-create]'); p.wait_for_timeout(2500)
# F-006: field editor
p.click('[data-testid=edit-identifiers]'); p.wait_for_timeout(600); s.shot("13-4-fields-modal")
m=p.locator('.modal-form, [role=dialog], .ui-modal').last
print("FIELDS MODAL:", m.inner_text().replace("\n"," | ")[:900])
print("fields inputs:", m.locator('input,select').evaluate_all("e=>e.map(x=>(x.name||x.placeholder||x.type)+'='+x.value)"))
# F-076: add custom field
btns=m.get_by_role("button").all_inner_texts(); print("buttons:",btns)
m.get_by_role("button",name="Add custom field").click(); p.wait_for_timeout(500); s.shot("11-F076-custom-field-hint")
print("CUSTOM:", m.inner_text().replace("\n"," | ")[-700:])
p.click('[data-testid=modal-cancel]') if p.locator('[data-testid=modal-cancel]').count() else m.get_by_role("button",name="Cancel").click(); p.wait_for_timeout(400)
print("template:", p.locator('input').evaluate_all("e=>e.map(x=>x.value).filter(v=>v.includes('patient_code'))"))
s.shot("13-5-editor-settings")
# task modal
p.click('[data-testid=task-option-phonation] > button'); p.wait_for_timeout(500)
mm=p.locator('.modal-form'); print("TASK FORM:", mm.locator('.form-group').evaluate_all('''els=>els.map(g=>{const l=g.querySelector('label').textContent; const i=g.querySelector('input,select'); return l+"|"+(i?(i.dataset.testid||i.name||'')+"="+i.value:"")})'''))
inp=mm.locator('.form-group input[type=number]')
print("numbers:",inp.count())
inp.nth(1).fill('70'); inp.nth(2).fill('60'); p.wait_for_timeout(400); s.shot("11-F073-warning-min-gt-max")
print("MODAL TEXT:", mm.inner_text().replace("\n"," | ")[:700])
print("alerts:", p.locator('[role=alert],.warning,.ui-warning,[class*=warn]').all_inner_texts())
print("save enabled:", p.locator('[data-testid=modal-save]').is_enabled())
inp.nth(1).fill('10'); inp.nth(2).fill('60'); p.wait_for_timeout(400)
print("alerts after fix:", p.locator('[role=alert],.warning,.ui-warning,[class*=warn]').all_inner_texts())
s.shot("11-F073-warning-cleared")
p.click('[data-testid=modal-save]'); p.wait_for_timeout(600)
p.click('[data-testid=protocol-save]'); p.wait_for_timeout(2500); print(p.url); s.shot("1-saved-new")
print(p.inner_text('body')[:600].replace("\n"," | "))
s.close()
