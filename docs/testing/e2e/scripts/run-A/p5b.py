from ui import *
s=Sess("master"); p=s.page
s.go("/studies/4/clinics"); p.click('[data-testid=study-runs-at-switch-5]'); p.wait_for_timeout(600); p.get_by_role("button",name="Stop running here").click(); p.wait_for_timeout(1200); s.shot("study4-clinics-final")
s.close()
from common import *
t=tok('master')
for sid in (4,5):
    r=api('GET',f'/sites/{sid}',t)[1]; print(sid, r['name'], [(x['project_id'],x.get('project_name')) for x in r.get('projects',[])], 'receives', r.get('receives'))
    json.dump({}, open('/dev/null','w')) if False else None
