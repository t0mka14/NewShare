"""End state: archive the throw-away study 'E2E 1030 run15 study' as its owner (nothing deleted)."""
import json
from ui import *
P=json.load(open(os.path.join(SP,'ids15.json')))['studyId']
c,r=api('POST',f'/projects/{P}/archive',tok('creator'),{'archived':True}); print("archive",c,r)
c,pr=api('GET','/projects',tok('creator')); print("active list has it:",[x['id'] for x in pr if x['id']==P])
s=Sess("creator"); s.go("/studies"); s.shot("15-studies-after-archive"); print("run15 visible in list:", 'run15' in s.page.inner_text('body')); s.close()
