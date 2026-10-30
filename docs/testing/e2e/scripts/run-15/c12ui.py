"""Check 12 UI part: as creator add the existing editor (user 3) again in the UI -> toast 'already an editor'. Then (check 14) archive happens in c99."""
import json
from ui import *
P=json.load(open(os.path.join(SP,'ids15.json')))['studyId']
for lang in ("en","cs","de"):
    s=Sess("creator",lang=lang); p=s.page
    s.go(f"/studies/{P}/editors"); print(lang,"table:", p.locator('[data-testid=study-editors-table] tbody tr').all_inner_texts())
    p.click('[data-testid=study-add-editor]'); p.wait_for_timeout(500)
    p.fill('[data-testid=add-editor-email]','e2e-editor-1026@test.com'); p.click('[data-testid=add-editor-submit]'); p.wait_for_timeout(700); s.shot(f"12-already-editor-toast-{lang}")
    print(lang,"toast/alerts:", p.locator('[role=status],[role=alert],.ui-toast,[data-testid*=toast]').all_inner_texts())
    print(lang,"rows after:", p.locator('[data-testid=study-editors-table] tbody tr').count(), "errors", s.errors[:3]); s.close()
