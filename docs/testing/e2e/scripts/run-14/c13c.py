"""Does Cancel in the patient-field editor discard an added blank custom field? (side observation from c13b)"""
import json
from ui import *
P=json.load(open(os.path.join(SP,'ids14.json')))['studyId']
s=Sess("master"); p=s.page
s.go(f"/studies/{P}/protocols"); p.click('[data-testid=study-new-protocol]'); p.wait_for_timeout(500)
p.fill('[data-testid=new-protocol-name]','E2E 1026 run14 protocol'); p.click('[data-testid=new-protocol-create]'); p.wait_for_timeout(2500)
def state(tag):
    print(tag,"| settings text:", p.locator('[data-testid=protocol-settings]').inner_text().replace("\n"," | ")[:500], "| save enabled:", p.locator('[data-testid=protocol-save]').is_enabled())
state("initial")
p.click('[data-testid=edit-identifiers]'); p.wait_for_timeout(400)
m=p.locator('.modal-form, [role=dialog], .ui-modal').last
m.get_by_role("button",name="Add custom field").click(); p.wait_for_timeout(300)
m.get_by_role("button",name="Cancel").click(); p.wait_for_timeout(500)
s.shot("13-6-after-cancel-custom-field"); state("after cancel")
s.close()
