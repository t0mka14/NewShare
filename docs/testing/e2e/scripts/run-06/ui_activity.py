"""Run 06 steps 5/6 (UI): Activity (audit) and Upload history sections of the 03-1 and 05-3 detail pages, Camera details of the partial-MP4 session,
and a play attempt for an audio clip and the video clip (is a player shown? does the video element report an error?).
Env: as ui_recordings.py. Pass session ids via E2E_SESSIONS=<id>,<id> or default = every session of the context file. Writes logs/ui-activity.txt."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common06 import *
from ui import Sess
out = []
def log(*a):
    l = " ".join(str(x) for x in a); print(l); out.append(l)
s = Sess("master"); p = s.page
ids = os.environ.get("E2E_SESSIONS")
for x in sessions():
    if ids and x["sessionId"] not in ids.split(","): continue
    tag = x["patient"].replace("E2E1026-", "")
    p.goto(BASE + "/#/recordings/" + x["sessionId"]); p.wait_for_timeout(2000)
    for summ in p.locator("summary").all():
        try: summ.click(); p.wait_for_timeout(300)
        except Exception as e: log("summary click failed", str(e)[:80])
    p.wait_for_timeout(800)
    act = p.locator("[data-testid=recording-activity]")
    log(tag, "activity:", act.inner_text().replace(chr(10), " | ")[:700] if act.count() else "none")
    shot(s, "step-5-activity-%s" % tag)
    hist = p.locator("details", has_text="Upload history")
    log(tag, "history:", hist.first.inner_text().replace(chr(10), " | ")[:500] if hist.count() else "none")
    if tag in ("03-1",):
        p.locator("tr[data-testid=task-row]").first.locator("button").first.click(); p.wait_for_timeout(2500)
        log(tag, "audio element after play click:", p.evaluate("()=>[...document.querySelectorAll('video,audio')].map(v=>({tag:v.tagName,err:v.error&&v.error.code,ready:v.readyState,dur:v.duration,paused:v.paused}))"), "| cant-play notice:", p.locator("text=can't play").count())
        shot(s, "step-6-play-audio-%s" % tag)
    if tag in ("05-3", "05-1"):
        vb = p.locator("tr[data-testid=task-row]").first.locator("button").nth(1)
        vb.click(); p.wait_for_timeout(2500)
        info = p.evaluate("()=>[...document.querySelectorAll('video,audio')].map(v=>({tag:v.tagName,err:v.error&&v.error.code,ready:v.readyState,dur:v.duration,src:(v.currentSrc||'').split('?')[0].slice(-40)}))")
        log(tag, "video element after play click:", info); shot(s, "step-6-play-video-%s" % tag)
log("console errors:", s.errors[:10])
open(os.path.join(LOGS, os.environ.get("E2E_LOG_NAME", "ui-activity.txt")), "w", encoding="utf-8").write(redact("\n".join(out)))
s.close()
