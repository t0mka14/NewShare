"""Part C step 8: screenshot of the web recording detail of a session (real UI login). Usage: c8_web_detail.py <sessionId>. The page shows the installation ID: it is blacked out before saving (any element whose text matches a UUID/hex id of a device)."""
import os, re, sys
from playwright.sync_api import sync_playwright
from common import *
sid=sys.argv[1]; SH=os.path.join(os.environ["E2E_RUN_DIR"],"screenshots")
with sync_playwright() as pw:
    b=pw.chromium.launch(headless=True); p=b.new_context(viewport={"width":1440,"height":1200}).new_page()
    p.goto(BASE+"/login"); p.fill('input[name=email]','master@test.com'); p.fill('input[name=password]','1234'); p.click('button[type=submit]'); p.wait_for_url(re.compile("recordings"))
    p.evaluate("localStorage.setItem('appLanguage','en')")
    p.goto(BASE+f"/#/recordings/{sid}"); p.reload(); p.wait_for_timeout(3000)
    p.get_by_text(re.compile(r"Show answers")).first.click(); p.wait_for_timeout(1000)
    text=p.inner_text("body")
    open(os.path.join(os.environ["E2E_STATE_DIR"],"detail_text.txt"),"w",encoding="utf-8").write(text)
    # hide anything that looks like an installation id (36-char uuid) except the session id
    p.evaluate("""(sid)=>{const w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);const n=[];while(w.nextNode())n.push(w.currentNode);
      n.forEach(t=>{ if(/^\s*[0-9a-f]{8}…\s*$/i.test(t.nodeValue)||/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i.test(t.nodeValue)) { t.parentElement.style.background='#000'; t.parentElement.style.color='#000'; }});}""", sid)
    p.screenshot(path=os.path.join(SH,"C4-web-recording-detail.png"),full_page=True)
    b.close()
print(text[:3000])
