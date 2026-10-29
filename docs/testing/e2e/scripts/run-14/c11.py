"""Check 11 F-068: save-confirm dialog text (EN, CS, DE), cancel. Also Last changed again."""
import json
from ui import *
P=json.load(open(os.path.join(SP,'ids14.json')))['studyId']; PID=28
for lang in ("en","cs","de"):
    s=Sess("master",lang=lang); p=s.page
    s.go(f"/studies/{P}/protocols/{PID}"); p.wait_for_timeout(800)
    p.fill('[data-testid=protocol-description]','trivial change run14'); p.wait_for_timeout(300)
    p.click('[data-testid=protocol-save]'); p.wait_for_timeout(700); s.shot(f"11-F068-confirm-{lang}")
    d=p.locator('[role=dialog],[role=alertdialog],.ui-modal').last; print(lang,"CONFIRM:", d.inner_text().replace("\n"," | ")[:500]); print("buttons", d.get_by_role("button").all_inner_texts())
    d.get_by_role("button").first.click() if False else None
    # cancel: choose the button that is not primary/danger
    cancel=d.locator('button:not(.ui-btn-primary):not(.ui-btn-danger)').first; print("cancel btn:",cancel.inner_text()); cancel.click(); p.wait_for_timeout(500)
    print("url after cancel", p.url.split('#')[-1]); s.close()
c,pr=api('GET',f'/projects/{P}/protocols',tok('master')); print("API after cancel:",[(x['id'],x.get('updated_at'),x.get('description')) for x in pr])
