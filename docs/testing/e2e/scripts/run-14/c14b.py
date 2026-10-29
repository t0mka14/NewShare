"""DE: custom-field hint (first modal button = add custom field)."""
import re, json
from ui import *
P=json.load(open(os.path.join(SP,'ids14.json')))['studyId']
s=Sess("master",lang="de"); p=s.page; s.go(f"/studies/{P}/protocols/28"); p.wait_for_timeout(800)
p.click('[data-testid=edit-identifiers]'); p.wait_for_timeout(400); m=p.locator('.modal-form, [role=dialog], .ui-modal').last
print(m.get_by_role("button").all_inner_texts()); m.get_by_role("button").first.click(); p.wait_for_timeout(300)
print("DE hint:", [l for l in m.inner_text().split("\n") if re.search(r"(?i)sprache",l)]); s.shot("14-de-F076-hint")
m.get_by_role("button").filter(has_text=re.compile("Abbrechen")).click(); s.close()
