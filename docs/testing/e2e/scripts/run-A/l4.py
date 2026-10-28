from ui import *
s=Sess("master"); p=s.page
s.go("/studies/4/protocols/9")
print("save disabled at load:", p.locator('[data-testid=protocol-save]').is_disabled(), "dirty note:", p.locator('[data-testid=protocol-dirty-note]').count())
# edit phonation rep -> 3
p.click('[data-testid=task-edit-0]'); p.wait_for_timeout(400)
g=p.locator('.modal-form .form-group').filter(has=p.locator('label',has_text=re.compile('^Repetitions$'))).locator('input'); g.fill("3")
p.click('[data-testid=modal-save]'); p.wait_for_timeout(300)
print("save disabled after edit:", p.locator('[data-testid=protocol-save]').is_disabled(), p.locator('[data-testid=protocol-dirty-note]').inner_text() if p.locator('[data-testid=protocol-dirty-note]').count() else "")
s.shot("library-copy-edited-dirty")
p.click('[data-testid=protocol-save]'); p.wait_for_timeout(600); p.get_by_role("button",name=re.compile("Update Everyone",re.I)).click(); p.wait_for_timeout(2500); print(p.url); s.shot("library-copy-saved")
s.go("/studies/4/protocols"); print(p.inner_text('[data-testid=study-protocols]')); s.shot("study4-protocols-with-copy")
s.close()
from common import *
t=tok('master')
print(json.dumps(api('GET','/library',t)[1],ensure_ascii=False)[:900])
c,r=api('GET','/library/7',t); print(c, [ (x['params'].get('category'),x['params'].get('repeat')) for x in r.get('tasks',[])], r.get('version'), r.get('used_in'))
