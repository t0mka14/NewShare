"""Run 07 step 6/7: server UI after the re-upload: recording detail (clinic, checks, Upload history and Activity opened), the Recordings list row, and the Computers tab of Clinic A.
Usage: python ui_after_assign.py <shotPrefix>   (installation IDs masked in the DOM; text dumps redacted into logs/)"""
import sys
from common07 import *
sys.path.insert(0, os.path.join(HERE, "..", "common"))
import ui
from ui import Sess
ui.SHOTS = os.path.join(RUN07, "screenshots"); os.makedirs(ui.SHOTS, exist_ok=True)
pre = sys.argv[1]; sid = session07()["sessionId"]; cid = context()["sites"]["A"]["id"]
s = Sess("master"); p = s.page
def dump(name):
    t = p.evaluate("()=>document.body.innerText"); open(os.path.join(LOGS, "ui-%s.txt" % name), "w", encoding="utf-8").write(red(t)); return t
s.go("/recordings/" + sid, 2500)
for sm in p.locator("summary").all():
    try: sm.click()
    except Exception: pass
p.wait_for_timeout(800); t = dump(pre + "-detail"); i = t.find("Upload history"); print(red(t[:400])); print(red(t[i - 200:i + 1500]))
p.evaluate(MASK_JS); print(s.shot(pre + "-detail"))
s.go("/recordings", 2500); t = dump(pre + "-list"); print(red(t[t.find("Recordings\n\t"):][:900])); p.evaluate(MASK_JS); print(s.shot(pre + "-list"))
s.go("/clinics/%d/computers" % cid, 2500); t = dump(pre + "-computers"); print(red(t[t.find("Computers\nInst"):][:700]))
p.evaluate(MASK_JS); print(s.shot(pre + "-computers"))
s.go("/clinics", 2500); t = dump(pre + "-clinics"); p.evaluate(MASK_JS); print(s.shot(pre + "-clinics"))
s.close()
