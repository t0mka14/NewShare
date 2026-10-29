"""Check 12 (F-037, F-111, F-036, F-035) on the video recording E2E1026-05-1 (session id from the context file). Also i18n spot check (14) on the recording detail."""
import json, re
from ui import *
from redact import redact
C=json.load(open(CONTEXT,encoding='utf-8-sig')); sid=[x for x in C["sessions"] if x["run"]=="05"][0]["sessionId"]
s=Sess("master"); p=s.page
s.go("/recordings/"+sid); p.wait_for_timeout(1500); redact(p); s.shot("12-1-recording-05-1")
t=p.inner_text('body'); print("TEXT:", t[:2500].replace("\n"," | "))
print("undefined?", "undefined" in t, "| raw keys:", re.findall(r"\b[a-z]+\.[a-zA-Z]+\.[a-zA-Z.]+\b", t)[:5])
print("buttons:", p.get_by_role("button").all_inner_texts())
s.close()
