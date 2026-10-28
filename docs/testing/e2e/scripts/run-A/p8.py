from ui import *
s=Sess("master"); p=s.page
s.go("/studies/3/protocols"); p.click('[data-testid=study-new-protocol]'); p.fill('[data-testid=new-protocol-name]','E2E 1026 Probe2'); p.click('[data-testid=new-protocol-create]'); p.wait_for_timeout(2000)
p.click('[data-testid=edit-identifiers]'); p.wait_for_timeout(500); s.shot("probe-identifiers-modal"); print(p.locator('.modal-settings').inner_text()[:1500])
p.click('[data-testid=modal-save]'); p.wait_for_timeout(300)
p.click('[data-testid=add-info-page]'); p.wait_for_timeout(600); s.shot("probe-info-modal"); print(p.locator('.ql-editor').count())
p.click('[data-testid=modal-cancel]')
p.click('[data-testid=add-questionnaire]'); p.wait_for_timeout(600); s.shot("probe-questionnaire-modal")
print(p.locator('.modal-content, .modal, [role=dialog]').first.inner_text()[:800])
s.close()
