"""Run 02 step 4: screenshot the clinic's Computers tab with installation IDs masked in the DOM.
Env: E2E_STATE_DIR (auth.json), E2E_RUN_DIR (run folder), E2E_CONTEXT. Args: [clinic key, default A] [shot name]."""
import sys, json, re
sys.path.insert(0, __import__("os").path.join(__import__("os").path.dirname(__file__), "..", "common"))
from ui import *
key = sys.argv[1] if len(sys.argv) > 1 else "A"
name = sys.argv[2] if len(sys.argv) > 2 else "step-4-computers-page"
cid = json.load(open(CONTEXT))["sites"][key]["id"]
s = Sess("master")
s.go(f"/clinics/{cid}/computers", wait=2500)
s.page.evaluate("""()=>{const re=/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi;
 const w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);let n;while(n=w.nextNode()){n.nodeValue=n.nodeValue.replace(re,'<installationId>')}}""")
print(s.page.inner_text("body")[:3000].replace("\n"," | "))
s.shot(name)
s.close()
