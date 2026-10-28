from ui import *
s=Sess("master"); p=s.page
s.go("/studies/3/protocols/2"); s.shot("speech-editor-cs-variant")
print(p.locator('[data-testid=protocol-tasks]').inner_text()[:1500])
print(p.locator('[data-testid=protocol-settings]').inner_text())
p.click('[data-testid=task-edit-4]'); p.wait_for_timeout(500); s.shot("speech-cs-questionnaire-edit")
print(p.locator('.questionnaire-modal-form').input_value if False else p.locator('.qm-input').evaluate_all('e=>e.map(x=>x.value)'))
s.close()
