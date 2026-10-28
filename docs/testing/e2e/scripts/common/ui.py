import json, os, re, time
from playwright.sync_api import sync_playwright
from common import *
REP=os.environ.get("E2E_RUN_DIR", os.path.join(REPORTS,"2026-10-26-run-A-new-web"))
SHOTS=os.path.join(REP,"screenshots"); os.makedirs(SHOTS,exist_ok=True)
LOG=os.path.join(SP,"log.jsonl")
def rec(kind, **kw):
    kw["kind"]=kind; kw["t"]=time.strftime("%H:%M:%S")
    open(LOG,"a",encoding="utf-8").write(json.dumps(kw,ensure_ascii=False)+"\n")
    print(kind, kw)
class Sess:
    def __init__(self, who="master", w=1440, h=900, lang="en"):
        self.pw=sync_playwright().start()
        self.b=self.pw.chromium.launch(headless=os.environ.get("E2E_HEADLESS","0")=="1")
        self.ctx=self.b.new_context(viewport={"width":w,"height":h})
        auth=json.load(open(os.path.join(SP,"auth.json")))[who]
        self.ctx.add_init_script("(()=>{try{if(!sessionStorage.getItem('e2eInit')){sessionStorage.setItem('e2eInit','1');const a=%s;localStorage.setItem('appLanguage','%s');localStorage.setItem('adminUser',JSON.stringify(a.user));localStorage.setItem('adminToken',a.token)}}catch(e){}})()" % (json.dumps(auth), lang))
        self.page=self.ctx.new_page()
        self.who=who
        self.errors=[]
        self.page.on("console", lambda m: self.errors.append((m.type,m.text)) if m.type in("error",) else None)
        self.page.on("pageerror", lambda e: self.errors.append(("pageerror",str(e))))
        self.reqs=[]
        self.page.on("response", lambda r: self.reqs.append((r.request.method,r.url,r.status)) if "/api/" in r.url else None)
        self.page.goto(BASE+"/#/recordings"); self.page.wait_for_timeout(1500)
    def go(self, hash_, wait=1200):
        self.page.goto(BASE+"/#"+hash_); self.page.reload(); self.page.wait_for_timeout(wait)
    def shot(self, name, full=True):
        p=os.path.join(SHOTS,name+".png"); self.page.screenshot(path=p, full_page=full); return p
    def primaries(self):
        return self.page.evaluate("()=>[...document.querySelectorAll('.ui-btn-primary')].filter(e=>e.offsetParent!==null).map(e=>(e.getAttribute('data-testid')||'')+'|'+e.textContent.trim())")
    def close(self):
        self.b.close(); self.pw.stop()
