"""§10.7 editors: throw-away study made by the creator persona. Study stays (archived at the end); records are never deleted."""
import json,sys,os
from common import *
T={k:tok(k) for k in ("master","editor","received","nogrants","creator")}
def show(l,r): print(f"{l}: {r[0]} {str(r[1])[:200]}")
c,r=api('POST','/projects',T['creator'],{'name':'E2E 1026 A2 throwaway study','description':'Run A2 editors probe'}); show('creator create study',(c,r))
pid=r.get('id') or r.get('project_id') or (r.get('project') or {}).get('id'); print('pid',pid)
show('editors list',api('GET',f'/projects/{pid}/editors',T['creator']))
show('add editor by email (editor persona)',api('POST',f'/projects/{pid}/editors',T['creator'],{'email':'e2e-editor-1026@test.com'}))
show('add same again',api('POST',f'/projects/{pid}/editors',T['creator'],{'email':'e2e-editor-1026@test.com'}))
show('add unknown email',api('POST',f'/projects/{pid}/editors',T['creator'],{'email':'nobody-1026@test.com'}))
show('add by partial email',api('POST',f'/projects/{pid}/editors',T['creator'],{'email':'e2e-editor'}))
show('add by upper-case email',api('POST',f'/projects/{pid}/editors',T['creator'],{'email':'E2E-EDITOR-1026@TEST.COM'}))
show('add without email',api('POST',f'/projects/{pid}/editors',T['creator'],{}))
show('non-editor (received) add',api('POST',f'/projects/{pid}/editors',T['received'],{'email':'e2e-nogrants-1026@test.com'}))
show('non-editor (nogrants) add',api('POST',f'/projects/{pid}/editors',T['nogrants'],{'email':'e2e-nogrants-1026@test.com'}))
show('non-editor (received) list',api('GET',f'/projects/{pid}/editors',T['received']))
ed=api('GET',f'/projects/{pid}/editors',T['creator'])[1]; print(ed)
creator_id=6
show('co-editor removes creator',api('DELETE',f'/projects/{pid}/editors/{creator_id}',T['editor']))
show('creator removes self',api('DELETE',f'/projects/{pid}/editors/{creator_id}',T['creator']))
show('master removes creator',api('DELETE',f'/projects/{pid}/editors/{creator_id}',T['master']))
show('co-editor removes co-editor (self)',api('DELETE',f'/projects/{pid}/editors/3',T['editor']))
show('remove non-editor user 5',api('DELETE',f'/projects/{pid}/editors/5',T['creator']))
show('editors after',api('GET',f'/projects/{pid}/editors',T['creator']))
show('GET study as nogrants',api('GET',f'/projects/{pid}',T['nogrants']))
json.dump({'throwawayStudyId':pid},open(os.path.join(SP,'a2ids.json'),'w'))
# non-editor who SEES the study (received persona sees study 3 via clinic A): 403 expected
import json as _j
S=_j.load(open(CONTEXT,encoding='utf-8-sig'))["projects"]["speech"]["id"]
show('received (sees study) GET editors',api('GET',f'/projects/{S}/editors',T['received']))
show('received (sees study) add editor',api('POST',f'/projects/{S}/editors',T['received'],{'email':'e2e-nogrants-1026@test.com'}))
show('received (sees study) remove editor 1',api('DELETE',f'/projects/{S}/editors/1',T['received']))
show('editors of study (as editor persona)',api('GET',f'/projects/{S}/editors',T['editor']))
# re-add editor persona to throw-away study (for the UI steps)
show('re-add editor persona',api('POST',f'/projects/{pid}/editors',T['creator'],{'email':'e2e-editor-1026@test.com'}))
