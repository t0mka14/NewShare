"""§10.7 in the UI as the editor persona on the throw-away study (co-editor of it): add by email, unknown email, remove, creator not removable.
Also the received persona: editors tab absent."""
import json, os
from ui import *
P=json.load(open(os.path.join(SP,'a2ids.json')))['throwawayStudyId']
s=Sess("editor"); p=s.page
s.go(f"/studies/{P}/editors"); s.shot("editor-editors-1-list"); print("editors table:", p.locator('[data-testid=study-editors-table] tbody tr').all_inner_texts())
print("remove buttons:", p.locator('[data-testid^=editor-remove-]').evaluate_all("e=>e.map(x=>x.dataset.testid)"))
p.click('[data-testid=study-add-editor]'); p.wait_for_timeout(500); s.shot("editor-editors-2-add-modal")
p.fill('[data-testid=add-editor-email]','nobody-1026@test.com'); p.click('[data-testid=add-editor-submit]'); p.wait_for_timeout(1000); s.shot("editor-editors-3-unknown-email"); print("unknown:", p.inner_text('[data-testid=add-editor-modal]')[-200:].replace(chr(10)," | "))
p.fill('[data-testid=add-editor-email]','e2e-nogrants-1026@test.com'); p.click('[data-testid=add-editor-submit]'); p.wait_for_timeout(1500); s.shot("editor-editors-4-added"); print("after add:", p.locator('[data-testid=study-editors-table] tbody tr').all_inner_texts())
p.click('[data-testid=editor-remove-5]'); p.wait_for_timeout(600); s.shot("editor-editors-5-remove-confirm")
d=p.locator('[role=dialog],[role=alertdialog],.ui-modal'); print("confirm:", d.last.inner_text().replace(chr(10)," | ")[:250])
d.last.locator('button.ui-btn-danger').first.click(); p.wait_for_timeout(1200); s.shot("editor-editors-6-removed"); print("after remove:", p.locator('[data-testid=study-editors-table] tbody tr').all_inner_texts())
print("console errors:", s.errors[:3]); s.close()
s=Sess("received"); p=s.page; s.go("/studies/3/editors"); print("received on study 3 /editors ->", p.url.split('#')[-1], "| tabs:", p.locator('[role=tab],.ui-tab,nav a').all_inner_texts()[:12]); s.shot("received-study3-editors-route"); s.close()
