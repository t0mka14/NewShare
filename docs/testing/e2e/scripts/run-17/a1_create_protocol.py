"""Part A steps 1-2: real UI login, create the protocol in study 1 (Paris's study) with one QUESTIONNAIRE task (Q1-Q8, rules on Q5-Q8)
through the web editor, save, reload, reopen and screenshot. E2E_HEADLESS=1 for headless. E2E_SAVE=0 stops before the protocol save (dry run)."""
import os, json, re, time, sys
from playwright.sync_api import sync_playwright
from common import *
SHOTS=os.path.join(os.environ["E2E_RUN_DIR"],"screenshots")
NAME="E2E run 17 questionnaire rules"
STUDY=os.environ.get("E2E_STUDY","1")
SAVE=os.environ.get("E2E_SAVE","1")=="1"
QS=[
 dict(text="Q1 open, required", type="open"),
 dict(text="Q2 source: single Yes/No/Maybe", type="single", options=["Yes","No","Maybe"]),
 dict(text="Q3 source: multiple A/B/C", type="multiple", options=["A","B","C"]),
 dict(text="Q4 plain: single Red/Blue", type="single", options=["Red","Blue"]),
 dict(text="Q5 (if Q2 is Yes or Maybe), open, required", type="open", rule=(2,["Yes","Maybe"])),
 dict(text="Q6 (if Q3 includes A), single Up/Down", type="single", options=["Up","Down"], rule=(3,["A"])),
 dict(text="Q7 (if Q2 is Yes), single Yes/No", type="single", options=["Yes","No"], rule=(2,["Yes"])),
 dict(text="Q8 (if Q7 is Yes), open, required", type="open", rule=(7,["Yes"])),
]
def shot(p,n,full=True): p.screenshot(path=os.path.join(SHOTS,n+".png"),full_page=full)
with sync_playwright() as pw:
    b=pw.chromium.launch(headless=os.environ.get("E2E_HEADLESS","1")=="1")
    p=b.new_context(viewport={"width":1440,"height":1000}).new_page()
    p.goto(BASE+"/login"); p.fill('input[name=email]','master@test.com'); p.fill('input[name=password]','1234'); p.click('button[type=submit]')
    p.wait_for_url(re.compile("recordings"))
    p.evaluate("localStorage.setItem('appLanguage','en')")
    p.goto(BASE+f"/#/studies/{STUDY}/protocols"); p.reload(); p.wait_for_timeout(1500)
    shot(p,"A1-study-protocols-before")
    p.click('[data-testid=study-new-protocol]'); p.wait_for_timeout(400)
    p.fill('[data-testid=new-protocol-name]',NAME)
    p.select_option('[data-testid=new-protocol-language]','en')
    shot(p,"A1-new-protocol-dialog",False)
    p.click('[data-testid=new-protocol-create]'); p.wait_for_timeout(2500)
    print("editor url:",p.url)
    p.click('[data-testid=add-questionnaire]'); p.wait_for_timeout(500)
    p.fill('.qm-input-group input.qm-input >> nth=0','Rules questionnaire'); p.fill('.qm-textarea','E2E run 17 showIf test')
    for i,q in enumerate(QS):
        p.click('.qm-btn-add-question'); p.wait_for_timeout(200)
        card=p.locator('.qm-question-card').nth(i)
        card.locator('input.question-text').fill(q['text'])
        card.locator('select.type-select').first.select_option(q['type'])
        opt=card.locator('.qm-checkbox-group input[type=checkbox]')
        print(i+1,q['type'],"optional checkbox default/now:",opt.is_checked())   # left at the default
        for j,o in enumerate(q.get('options',[])):
            card.locator('.qm-btn-add-option').click(); p.wait_for_timeout(100)
            card.locator('.qm-option-row input.qm-input').nth(j).fill(o)
    # rules, after all questions exist
    for i,q in enumerate(QS):
        if 'rule' not in q: continue
        card=p.locator('.qm-question-card').nth(i); rule=card.get_by_test_id('qm-condition')
        rule.locator('input[type=checkbox]').first.check(); p.wait_for_timeout(150)
        src,ans=q['rule']
        sel=rule.locator('select')
        val=sel.evaluate("(e,pfx)=>[...e.options].find(o=>o.text.startsWith(pfx)).value", f"Q{src}:")
        sel.select_option(val); p.wait_for_timeout(150)
        for a in ans: rule.locator('.qm-condition-values label',has_text=re.compile('^\s*'+a+'$')).locator('input').check()
    shot(p,"A1-questionnaire-modal-full")
    for i in range(len(QS)):
        c=p.locator('.qm-question-card').nth(i); c.scroll_into_view_if_needed(); c.screenshot(path=os.path.join(SHOTS,f"A1-modal-q{i+1}.png"))
    p.click('[data-testid=modal-save]'); p.wait_for_timeout(500)
    shot(p,"A1-editor-with-task")
    if not SAVE: b.close(); sys.exit(0)
    p.click('[data-testid=protocol-save]'); p.wait_for_timeout(1500)
    dlg=p.locator('[role="dialog"] .ui-btn-primary')
    if dlg.count(): shot(p,"A1-save-confirm",False); dlg.first.click()
    p.wait_for_timeout(2500)
    print("after save url:",p.url, "version:", p.locator('[data-testid=protocol-version]').inner_text() if p.locator('[data-testid=protocol-version]').count() else None)
    shot(p,"A2-after-save")
    # reload and verify persisted
    p.reload(); p.wait_for_timeout(2500)
    shot(p,"A2-after-reload")
    p.locator('[data-testid^=task-edit-]').first.click(); p.wait_for_timeout(800)
    res=[]
    for i,q in enumerate(QS):
        card=p.locator('.qm-question-card').nth(i); card.scroll_into_view_if_needed()
        rule=card.get_by_test_id('qm-condition')
        if rule.count()==0: res.append((i+1,None)); continue
        on=rule.locator('input[type=checkbox]').first.is_checked()
        if not on: res.append((i+1,None)); continue
        srcsel=rule.locator('select').evaluate("e=>e.options[e.selectedIndex].text")
        picked=[l.inner_text().strip() for l in rule.locator('.qm-condition-values label').all() if l.locator('input').is_checked()]
        res.append((i+1,srcsel,picked))
    print(json.dumps(res))
    shot(p,"A2-reloaded-modal-full")
    for i in range(len(QS)):
        c=p.locator('.qm-question-card').nth(i); c.scroll_into_view_if_needed(); c.screenshot(path=os.path.join(SHOTS,f"A2-reloaded-q{i+1}.png"))
    p.click('[data-testid=modal-cancel]')
    print("final url:",p.url)
    b.close()
