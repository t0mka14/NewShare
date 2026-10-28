"""Run 07: open an admin route as master, screenshot it (installation IDs masked in the DOM) and dump buttons / test ids / headings (redacted).
Usage: python ui_explore.py <route e.g. /recordings/<id>> <shotName> [width] [height]   Env: E2E_STATE_DIR, E2E_RUN_DIR, E2E_HEADLESS."""
import sys
from common07 import *
sys.path.insert(0, os.path.join(HERE, "..", "common"))
from ui import Sess
route, name = sys.argv[1], sys.argv[2]
route = "/" + route.lstrip("/")  # Git Bash rewrites a leading / in args; pass the route without it
w, h = (int(sys.argv[3]), int(sys.argv[4])) if len(sys.argv) > 4 else (1440, 900)
s = Sess("master", w, h)
s.shots = None
import ui; ui.SHOTS = os.path.join(RUN07, "screenshots"); os.makedirs(ui.SHOTS, exist_ok=True)
s.go(route, 2500)
info = s.page.evaluate("""()=>({url:location.hash,
 headings:[...document.querySelectorAll('h1,h2,h3')].filter(e=>e.offsetParent).map(e=>e.textContent.trim()),
 buttons:[...document.querySelectorAll('button,a[role=button]')].filter(e=>e.offsetParent).map(e=>(e.getAttribute('data-testid')||'')+'|'+e.textContent.trim().slice(0,60)),
 testids:[...document.querySelectorAll('[data-testid]')].filter(e=>e.offsetParent).map(e=>e.getAttribute('data-testid')).slice(0,80)})""")
txt = s.page.evaluate("()=>document.body.innerText")
print(red(json.dumps(info, ensure_ascii=False, indent=1))); print(red(txt[:3000]))
open(os.path.join(LOGS, "ui-%s.txt" % name), "w", encoding="utf-8").write(red(json.dumps(info, ensure_ascii=False, indent=1) + "\n" + txt))
s.page.evaluate(MASK_JS); print(s.shot(name))
s.close()
