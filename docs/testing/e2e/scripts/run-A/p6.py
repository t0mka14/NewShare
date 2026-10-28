from ui import *
s=Sess("master"); p=s.page
s.go("/studies/3/protocols"); s.shot("study3-protocols-empty")
p.click('[data-testid=study-new-protocol]'); p.wait_for_timeout(500); s.shot("protocol-new-modal")
print(p.inner_text('[data-testid=new-protocol-modal]'))
p.fill('[data-testid=new-protocol-name]','E2E 1026 Speech protocol')
print(p.locator('[data-testid=new-protocol-language]').evaluate("e=>e.outerHTML")[:600])
p.click('[data-testid=new-protocol-create]'); p.wait_for_timeout(2500); print(p.url); s.shot("protocol-editor-empty")
print(p.locator('[data-testid^=task-option-]').evaluate_all('e=>e.map(x=>x.dataset.testid+"|"+x.textContent.trim().slice(0,60))'))
print(p.inner_text('body')[:1500])
s.close()
