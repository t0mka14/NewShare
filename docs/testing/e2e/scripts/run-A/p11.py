from ui import *
s=Sess("master"); p=s.page
s.go("/studies/3/protocols/2")
p.click('[data-testid=task-edit-4]'); p.wait_for_timeout(500)
cz=['E2E 1026 dotazník','Jak se dnes cítíte','Další poznámka','Kouříte','Ano','Ne','Které příznaky se vás týkají','Chrapot','Kašel','Žádné']
ins=p.locator('.qm-input')
for i,v in enumerate(cz): ins.nth(i).fill(v)
p.fill('.qm-textarea','Krátký dotazník pro běh A')
s.shot("speech-cs-questionnaire-translated")
p.click('[data-testid=modal-save]'); p.wait_for_timeout(400)
# info
p.get_by_role("button",name="Edit",exact=True).first.click(); p.wait_for_timeout(600)
p.locator('.ql-editor').first.click(); p.keyboard.press("Control+A"); p.keyboard.type("Vítejte. Tento protokol E2E 1026 nahrává fonaci, PATAKA, čtení, monolog a krátký dotazník.")
p.click('[data-testid=modal-save]'); p.wait_for_timeout(400)
reqs=[]
p.on("request", lambda r: reqs.append((r.method,r.url,r.post_data)) if r.method=="POST" else None)
p.click('[data-testid=protocol-save]'); p.wait_for_timeout(800); s.shot("speech-save-confirm-dialog")
print(p.locator('[role=dialog],.confirm-dialog,.ui-modal').last.inner_text())
p.get_by_role("button",name=re.compile("Update|Save|everyone",re.I)).last.click(); p.wait_for_timeout(3500)
print(p.url); s.shot("speech-cs-saved")
print(s.errors)
s.close()
