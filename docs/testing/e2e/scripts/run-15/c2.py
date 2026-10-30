"""Checks 2 (F-070) and 3 (F-071): questionnaire modal in the protocol editor (study 3 protocol 1 task 4), EN/CS/DE; cancel, nothing saved."""
from ui import *
EN=["Allow write-in","Remove option","Exclusive Option","Exclusive option","-- None --","None"]
for lang in ("en","cs","de"):
    s=Sess("master",lang=lang); p=s.page
    s.go("/studies/3/protocols/1"); p.wait_for_timeout(600)
    p.click('[data-testid=task-edit-4]'); p.wait_for_timeout(700)
    m=p.locator('.questionnaire-modal-form')
    # make sure a question with options exists: open the first card's type select options
    cards=m.locator('.qm-question-card'); print(lang,"cards:",cards.count())
    # add a transient question (cancelled afterwards) and give it a choice type to expose option controls
    p.click('.qm-btn-add-question'); p.wait_for_timeout(300)
    card=m.locator('.qm-question-card').last
    types=card.locator('select.type-select').first.evaluate("e=>[...e.options].map(o=>o.value+'='+o.textContent)"); print(lang,"types:",types)
    for v in ("multiple_choice","multi","checkbox","single","radio","select"):
        try:
            card.locator('select.type-select').first.select_option(v,timeout=500); break
        except Exception: pass
    for q in range(m.locator('.qm-question-card').count()):
        c=m.locator('.qm-question-card').nth(q)
        if c.locator('.qm-btn-add-option').count():
            c.locator('.qm-btn-add-option').click(); p.wait_for_timeout(200)
    t=m.inner_text(); full=p.inner_text('body')
    print(lang,"TEXT:", t.replace("\n"," | ")[:1500])
    print(lang,"English strings present:", [w for w in EN if w in t])
    print(lang,"selects options (all):", m.locator('select').evaluate_all("els=>els.map(s=>[...s.options].map(o=>o.textContent).join('/'))")[:8])
    print(lang,"checkbox labels:", m.locator('label').evaluate_all("e=>e.map(x=>x.textContent.trim()).filter(x=>x)")[:20])
    print(lang,"remove btn titles/aria:", m.locator('[title],[aria-label]').evaluate_all("e=>e.map(x=>(x.title||'')+'/'+(x.getAttribute('aria-label')||''))")[:12])
    s.shot(f"2-questionnaire-modal-{lang}")
    if lang=="en":
        b=m.locator('.qm-btn-add-question'); st=b.evaluate("e=>{const c=getComputedStyle(e);const r=e.getBoundingClientRect();return {cls:e.className,bg:c.backgroundColor,w:r.width,pw:e.parentElement.getBoundingClientRect().width,radius:c.borderRadius,tt:c.textTransform,fs:c.fontSize,fw:c.fontWeight}}"); print("ADD QUESTION BTN:",st)
        tts=m.locator('label,.qm-label,h3,h4,span,button').evaluate_all("els=>[...new Set(els.map(e=>getComputedStyle(e).textTransform))]"); print("text-transforms in modal:",tts)
        upper=m.locator('*').evaluate_all("els=>els.filter(e=>getComputedStyle(e).textTransform==='uppercase').map(e=>e.className+'|'+e.textContent.trim().slice(0,30))"); print("uppercase elems:",upper)
        m.locator('.qm-btn-add-question').scroll_into_view_if_needed(); s.shot("3-add-question-button")
    p.click('[data-testid=modal-cancel]'); p.wait_for_timeout(400)
    print(lang,"modal closed:", p.locator('.questionnaire-modal-form').count()==0, "errors", s.errors[:3]); s.close()
c,r=api('GET','/protocols/1',tok('master')); print("API protocol 1 updated_at:", (r.get('updated_at') if isinstance(r,dict) else r))
