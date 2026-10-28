"""§10.6 in the UI as the editor persona (sees clinic B only), and as received persona (switch state at clinic A). Restores the state at the end."""
import json, os
from ui import *
C=json.load(open(CONTEXT,encoding='utf-8-sig')); S=C["projects"]["speech"]["id"]; B=C["sites"]["B"]["id"]; A=C["sites"]["A"]["id"]
sec=json.load(open(SECRETS,encoding='utf-8-sig')); TB=[v for k,v in sec.items() if k.endswith("Clinic-B")][0]
def cfg(): c,r=api('GET','/site-config/'+TB); return [p.get('name') for p in r.get('protocols',[])]
s=Sess("editor"); p=s.page
def sw(tid): return p.locator(f'[data-testid="{tid}"]')
def toggle(tid, label, shot=None):
    s.reqs.clear(); sw(tid).click(); p.wait_for_timeout(800)
    dlg=p.locator('[role=dialog],[role=alertdialog],.ui-modal')
    if dlg.count():
        print(label,"confirm dialog:",dlg.last.inner_text().replace(chr(10)," | ")[:300]); s.shot((shot or label.replace(" ","-"))+"-confirm")
        dlg.last.locator('button.ui-btn-danger, button:has-text("Stop")').first.click()
    p.wait_for_timeout(1500)
    print(label, "switch checked:", (sw(tid).evaluate("e=>e.checked") if sw(tid).count() else "row gone"), "api:", [(m,u.split('/api')[-1],c) for m,u,c in s.reqs if m in('PUT','DELETE')], "config B:", cfg())
# study side
s.go(f"/studies/{S}/clinics"); s.shot("editor-runsat-study-side-1-on"); print("rows:", p.locator('[data-testid^=study-runs-at-row-]').count(), "initial config B", cfg())
toggle(f"study-runs-at-switch-{B}","study side OFF"); s.shot("editor-runsat-study-side-2-off")
toggle(f"study-runs-at-switch-{B}","study side ON"); s.shot("editor-runsat-study-side-3-on-again")
# clinic side: the switch can only turn OFF (confirm); ON again goes through "Add study"
if os.environ.get("E2E_SKIP_STUDY_SIDE")!="1" or True:
    s.go(f"/clinics/{B}/studies"); s.shot("editor-runsat-clinic-side-1-on")
    if sw(f"clinic-studies-switch-{S}").count():
        toggle(f"clinic-studies-switch-{S}","clinic side OFF"); s.shot("editor-runsat-clinic-side-2-off")
    else: print("study not running at B, skipping OFF")
    print("config B after clinic-side OFF", cfg())
    p.click('[data-testid=clinic-add-study]'); p.wait_for_timeout(700); s.shot("editor-runsat-clinic-side-3-add-modal")
    print("add modal options:", p.locator('[data-testid=add-study-select] option').all_inner_texts())
    p.select_option('[data-testid=add-study-select]', str(S)); p.click('[data-testid=add-study-confirm]'); p.wait_for_timeout(1500); s.shot("editor-runsat-clinic-side-4-on-again")
    print("config B after clinic-side ON", cfg())
print("final config B", cfg(), "errors", s.errors[:3])
s.close()
# received persona: the switches at clinic A, are they usable?
s=Sess("received"); p=s.page; s.go(f"/clinics/{A}/studies")
print("received switches:", p.locator('[data-testid^=clinic-studies-switch-]').evaluate_all("els=>els.map(e=>({id:e.dataset.testid,disabled:e.disabled||e.getAttribute('aria-disabled')||e.querySelector('input')?.disabled}))"))
s.shot("received-runsat-clinic-A-switches"); s.close()
