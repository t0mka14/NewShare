"""§10.16 in the UI with two users: creator (author) and editor (second user). Needs a2ids.json and a2lib.json (api_library.py).
Withdraws the creator's entry at the end only when E2E_WITHDRAW=1."""
import json, os
from ui import *
L=json.load(open(os.path.join(SP,'a2lib.json'))); P=json.load(open(os.path.join(SP,'a2ids.json')))['throwawayStudyId']; E=L['entry']
def menu_items(p):
    return p.locator('[role=menuitem]').all_inner_texts()
# author
s=Sess("creator"); p=s.page
s.go("/library"); s.shot("creator-library-list"); print("creator list rows:", p.locator('[data-testid=library-table] tbody tr').all_inner_texts())
p.check('[data-testid=library-mine]'); p.wait_for_timeout(800); s.shot("creator-library-shared-by-me"); print("mine rows:", p.locator('[data-testid=library-table] tbody tr').count())
s.go(f"/library/{E}"); s.shot("creator-library-entry"); print("entry version:", p.inner_text('[data-testid=library-entry-version]'))
p.get_by_role("button",name=re.compile("More|⋯|\.\.\.")).first.click() if p.get_by_role("button",name=re.compile("More")).count() else None
p.wait_for_timeout(400); print("author menu:", menu_items(p)); s.shot("creator-library-entry-menu"); s.close()
# second user
s=Sess("editor"); p=s.page
s.go("/library"); s.shot("editor-library-list"); print("editor list rows:", p.locator('[data-testid=library-table] tbody tr').count())
s.go(f"/library/{E}"); s.shot("editor-library-entry"); print("editor entry: Add button", p.locator('[data-testid=library-add]').count(), "More menu", p.get_by_role("button",name=re.compile("More")).count())
p.click('[data-testid=library-add]'); p.wait_for_timeout(700)
p.select_option('[data-testid=library-add-study]', str(P)); p.wait_for_timeout(300); print("add modal name prefilled:", p.input_value('[data-testid=library-add-name]'))
p.fill('[data-testid=library-add-name]','E2E 1026 A2 library copy'); p.click('[data-testid=library-add-submit]'); p.wait_for_timeout(1200); s.shot("editor-library-add-name-taken"); print("modal text:", p.inner_text('[data-testid=library-add-modal]')[-220:].replace(chr(10)," | "))
s.go(f"/library/15"); print("editor on master's entry 15: More menu", p.get_by_role("button",name=re.compile("More")).count())
s.go(f"/studies/{P}/protocols"); s.shot("editor-throwaway-study-protocols"); print(p.locator('[data-testid=study-protocols-table] tbody tr').all_inner_texts())
s.close()
if os.environ.get("E2E_WITHDRAW")=="1":
    s=Sess("creator"); p=s.page; s.go(f"/library/{E}")
    p.get_by_role("button",name=re.compile("More")).first.click(); p.wait_for_timeout(300); print("menu:", menu_items(p))
    p.get_by_role("menuitem",name=re.compile("Withdraw")).click(); p.wait_for_timeout(800); s.shot("creator-library-withdraw-confirm")
    d=p.locator('[role=dialog],[role=alertdialog],.ui-modal')
    if d.count(): print("confirm:", d.last.inner_text().replace(chr(10)," | ")[:250]); d.last.locator('button.ui-btn-danger').first.click(); p.wait_for_timeout(1200)
    s.go("/library"); s.shot("creator-library-after-withdraw"); print("after withdraw rows:", p.locator('[data-testid=library-table] tbody tr').all_inner_texts()); s.close()
