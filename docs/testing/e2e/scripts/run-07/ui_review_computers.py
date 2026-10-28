"""Run 07 step 4/5: follow the 'Review computers' banner of the Recordings list; screenshot where it lands (installation IDs masked), dump text.
Usage: python ui_review_computers.py <shotName>"""
import sys
from common07 import *
sys.path.insert(0, os.path.join(HERE, "..", "common"))
import ui
from ui import Sess
ui.SHOTS = os.path.join(RUN07, "screenshots"); os.makedirs(ui.SHOTS, exist_ok=True)
s = Sess("master"); s.go("/recordings", 2500)
b = s.page.locator("[data-testid=recordings-unassigned-banner]")
print("banner:", red(b.inner_text()))
link = b.locator("a,button").first
print("link text:", link.inner_text()); link.click(); s.page.wait_for_timeout(2000)
print("landed:", s.page.evaluate("location.hash"))
txt = s.page.evaluate("()=>document.body.innerText"); print(red(txt[:2500]))
print("buttons:", red(json.dumps(s.page.evaluate("()=>[...document.querySelectorAll('button')].filter(e=>e.offsetParent).map(e=>(e.getAttribute('data-testid')||'')+'|'+e.textContent.trim().slice(0,50))"), ensure_ascii=False)))
open(os.path.join(LOGS, "ui-%s.txt" % sys.argv[1]), "w", encoding="utf-8").write(red(txt))
s.page.evaluate(MASK_JS); print(s.shot(sys.argv[1])); s.close()
