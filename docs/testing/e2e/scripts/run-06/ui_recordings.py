"""Run 06 steps 4/5 (UI): Recordings list filtered by search 'E2E', flag chips (Has video, Computer event), a detail page per session
(full-page screenshots), masked fields with Show values / Hide on the session whose context 'fields' has masked values, 'Show answers'.
Env: E2E_STATE_DIR, PYTHONPATH=scripts/common, E2E_RUN_DIR (run folder; screenshots go to <run>/screenshots), E2E_HEADLESS=1 optional.
Window 1440x900. Writes logs/ui-recordings.txt."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common06 import *
import ui
from ui import Sess
out = []
def log(*a):
    l = " ".join(str(x) for x in a); print(l); out.append(l)
s = Sess("master"); p = s.page
s.go("/recordings")
p.fill("[data-testid=recordings-search]", "E2E"); p.wait_for_timeout(1500)
log("rows after search E2E:", p.locator("[data-testid=recordings-table] tbody tr").count(), "|", p.inner_text("[data-testid=recordings-count]"))
shot(s, "step-4-list-filter-E2E")
log("table text:", p.inner_text("[data-testid=recordings-table]").replace("\n", " | ")[:1500])
p.click("[data-testid=recordings-more-filters]"); p.wait_for_timeout(500)
for chip in ("Has video", "Computer event"):
    p.locator("button", has_text=chip).first.click(); p.wait_for_timeout(1200)
    names = p.locator("[data-testid=recordings-table] tbody tr").all_inner_texts()
    log("chip", chip, "->", [n.split("\n")[2] if len(n.split("\n")) > 2 else n[:40] for n in names])
    shot(s, "step-4-filter-" + chip.lower().replace(" ", "-"))
    p.locator("button", has_text=chip).first.click(); p.wait_for_timeout(800)
masked_sid = next((x["sessionId"] for x in sessions() if x.get("fields", {}).get("year_of_birth_opt") and x["patient"].endswith("03-1")), None)
for x in sessions():
    sid = x["sessionId"]; tag = x["patient"].replace("E2E1026-", "")
    p.goto(BASE + "/#/recordings/" + sid); p.wait_for_timeout(2000)
    log("detail", x["patient"], p.inner_text("body")[:0])
    shot(s, "step-5-detail-%s" % tag)
    txt = p.inner_text("[data-testid=app-shell]") if p.locator("[data-testid=app-shell]").count() else p.inner_text("body")
    open(os.path.join(LOGS, "detail-%s.txt" % tag), "w", encoding="utf-8").write(redact(txt))
    if sid == masked_sid:
        log("masked before:", {k: p.inner_text("[data-testid=masked-%s]" % k) for k in ("visit_code", "year_of_birth_opt")})
        p.locator("button", has_text="Show answers").first.click(); p.wait_for_timeout(600)
        shot(s, "step-5-detail-03-1-answers")
        log("answers modal text:", p.inner_text(".ui-modal-back").replace(chr(10), " | ")[:600])
        p.keyboard.press("Escape"); p.wait_for_timeout(500)
        if p.locator(".ui-modal-back").count(): p.locator(".ui-modal-back button").first.click(); p.wait_for_timeout(500)
        p.locator("button", has_text="Show values").click(); p.wait_for_timeout(1500)
        shot(s, "step-5-detail-03-1-values-shown")
        log("after Show values: buttons", p.locator("button", has_text="Hide").count(), "| text near fields:", [t for t in p.inner_text("body").split("\n") if "1975" in t or "V01" in t or "year" in t.lower()][:8])
        hb = p.locator("button", has_text="Hide values")
        if hb.count():
            hb.first.click(); p.wait_for_timeout(800)
            log("after Hide: masked spans", p.locator("[data-testid^=masked-]").count())
            shot(s, "step-5-detail-03-1-hidden-again")
    # deep link reload check
    p.reload(); p.wait_for_timeout(1500)
    log("reload on detail ->", "not-found" if p.locator("[data-testid=not-found-page]").count() else "ok")
log("console errors:", s.errors[:10]); log("failed/4xx api responses:", [r for r in s.reqs if r[2] >= 400][:10])
open(os.path.join(LOGS, "ui-recordings.txt"), "w", encoding="utf-8").write(redact("\n".join(out)))
s.close()
