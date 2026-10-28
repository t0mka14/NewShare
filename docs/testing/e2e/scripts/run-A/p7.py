from ui import *
s=Sess("master"); p=s.page
s.go("/studies/3/protocols"); p.click('[data-testid=study-new-protocol]'); p.fill('[data-testid=new-protocol-name]','E2E 1026 Probe'); p.click('[data-testid=new-protocol-create]'); p.wait_for_timeout(2000)
for cat in ["phonation","syllableRepeating","reading","monologue","pictureDescription"]:
    p.click(f'[data-testid=task-option-{cat}] > button'); p.wait_for_timeout(500)
    print("==",cat)
    print(p.locator('.modal-form .form-group').evaluate_all('''els=>els.map(g=>{const l=g.querySelector('label').textContent; const i=g.querySelector('input,select'); return l+" | "+(i?i.tagName+":"+(i.type||"")+"="+(i.type=="checkbox"?i.checked:i.value):"multi")})'''))
    if cat=="phonation": s.shot("task-modal-phonation")
    p.click('[data-testid=modal-cancel]'); p.wait_for_timeout(300)
s.close()
