"""Run 07 step 5: assign the unassigned computer to Clinic A as master in the new web.
Usage: python ui_assign.py <via: recording|clinics> <shotPrefix> [--confirm]
 recording: recording detail -> 'Assign computer...' (data-testid recording-assign); clinics: Clinics page -> Unassigned computers -> 'Assign to clinic...' (assign-<deviceId>)
Without --confirm it only opens the dialog (screenshot + dump). Clinic name from the context file (sites.A.name). Session from the context (run 07)."""
import sys
from common07 import *
sys.path.insert(0, os.path.join(HERE, "..", "common"))
import ui
from ui import Sess
ui.SHOTS = os.path.join(RUN07, "screenshots"); os.makedirs(ui.SHOTS, exist_ok=True)
via, pre = sys.argv[1], sys.argv[2]; confirm = "--confirm" in sys.argv
clinic = context()["sites"]["A"]["name"]; sid = session07()["sessionId"]
s = Sess("master"); p = s.page
if via == "recording":
    s.go("/recordings/" + sid, 2500); p.locator("[data-testid=recording-assign]").click()
else:
    s.go("/clinics", 2500); p.locator("[data-testid^=assign-]").first.click()
p.wait_for_timeout(1000)
dlg = p.locator("[role=dialog], dialog, [data-testid*=dialog], [data-testid*=modal]").first
print("dialog text:", red(dlg.inner_text() if dlg.count() else p.evaluate("()=>document.body.innerText")[:1500]))
print("controls:", red(json.dumps(p.evaluate("()=>[...document.querySelectorAll('select,input,button,[role=option],[role=combobox]')].filter(e=>e.offsetParent).map(e=>e.tagName+'|'+(e.getAttribute('data-testid')||'')+'|'+(e.textContent||e.value||'').trim().slice(0,50))"), ensure_ascii=False)))
p.evaluate(MASK_JS); print(s.shot(pre + "-dialog"))
if confirm:
    p.locator("[data-testid=assign-clinic]").select_option(label=clinic)
    p.wait_for_timeout(500); p.evaluate(MASK_JS); print(s.shot(pre + "-clinic-chosen"))
    p.locator("[data-testid=assign-confirm]").click(); p.wait_for_timeout(3000)
    p.evaluate(MASK_JS); print(s.shot(pre + "-after")); print(red(p.evaluate("()=>document.body.innerText")[:1800]))
s.close()
