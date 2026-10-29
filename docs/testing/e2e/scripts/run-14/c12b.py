"""Check 12: scan all recordings for gain/notice text, open the video clip on 05-1."""
import json, re
from ui import *
from redact import redact
c,lst=api('GET','/uploads?limit=50',tok('master')); 
items=lst['rows']
print('rows',len(items), list(items[0].keys()))
s=Sess("master"); p=s.page
for it in items:
    sid=it['id']
    s.go("/recordings/"+sid); p.wait_for_timeout(1000); t=p.inner_text('body')
    keys=[l for l in t.split("\n") if re.search(r"(?i)gain|undefined|interrupt|camera|notice|lost|partial|video",l)]
    print(sid[:8], (it.get('participant') or {}).get('patientCode') if isinstance(it.get('participant'),dict) else it.get('participant'), it['videoClips'], keys[:12])
s.close()
