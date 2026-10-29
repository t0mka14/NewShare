"""Last changed again (minutes later), then archive the throw-away study (nothing deleted)."""
import json, datetime
from ui import *
P=json.load(open(os.path.join(SP,'ids14.json')))['studyId']
print("now local", datetime.datetime.now().isoformat(timespec='seconds'), "UTC", datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='seconds'))
s=Sess("master"); p=s.page; s.go(f"/studies/{P}/protocols"); s.shot("1-5-last-changed-later"); print("LIST:", p.locator('[data-testid=study-protocols-table]').inner_text().replace("\n"," | "))
print("title attr:", p.locator('[data-testid=study-protocols-table] [title]').evaluate_all("e=>e.map(x=>x.title+'::'+x.textContent.trim())"))
c,r=api('POST',f'/projects/{P}/archive',tok('master'),{'archived':True}); print("archive",c,r)
c,pr=api('GET','/projects',tok('master')); print([(x['id'],x['name'],x.get('is_archived'),x.get('archived')) for x in pr if x['id']==P])
s.go("/studies"); s.shot("15-studies-after-archive"); print(p.inner_text('body')[:300].replace("\n"," | ")); s.close()
