from ui import *
def new_protocol(s, study, name, lang="en"):
    p=s.page
    s.go(f"/studies/{study}/protocols"); p.click('[data-testid=study-new-protocol]'); p.wait_for_timeout(300)
    p.fill('[data-testid=new-protocol-name]',name)
    p.select_option('[data-testid=new-protocol-language]', lang)
    p.click('[data-testid=new-protocol-create]'); p.wait_for_timeout(2000)
def add_task(s, cat, **vals):
    """vals keyed by label text -> value (number/str/bool)"""
    p=s.page
    p.click(f'[data-testid=task-option-{cat}] > button'); p.wait_for_timeout(400)
    for label,v in vals.items():
        g=p.locator('.modal-form .form-group').filter(has=p.locator('label',has_text=re.compile('^'+re.escape(label)+'$')))
        inp=g.locator('input,select').first
        t=inp.evaluate("e=>e.type")
        if t=="checkbox":
            if inp.is_checked()!=bool(v): inp.click()
        elif inp.evaluate("e=>e.tagName")=="SELECT": inp.select_option(str(v))
        else: inp.fill(str(v))
    p.click('[data-testid=modal-save]'); p.wait_for_timeout(300)
def add_questionnaire(s, title, desc, questions):
    p=s.page
    p.click('[data-testid=add-questionnaire]'); p.wait_for_timeout(400)
    p.fill('.qm-input-group input.qm-input >> nth=0', title); p.fill('.qm-textarea', desc)
    for i,q in enumerate(questions):
        p.click('.qm-btn-add-question'); p.wait_for_timeout(200)
        card=p.locator('.qm-question-card').nth(i)
        card.locator('input.question-text').fill(q['text'])
        card.locator('select.type-select').first.select_option(q['type'])
        opt=card.locator('input[type=checkbox]').first
        if opt.is_checked()!=q.get('optional',False): opt.click()
        for j,o in enumerate(q.get('options',[])):
            card.locator('.qm-btn-add-option').click(); p.wait_for_timeout(100)
            card.locator('.qm-option-row input.qm-input').nth(j).fill(o)
    return p
