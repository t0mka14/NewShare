from ui import *
M=tok('master')
print('archive probe', api('POST','/protocols/18/archive',M,{}))
# API validation checks
print('clinic 65', api('POST','/sites',M,{'name':'x'*65,'country':'X'}))
print('clinic empty', api('POST','/sites',M,{'name':'  ','country':'X'}))
print('library name taken', api('GET','/library',M)[1][0]['id'])
lid=api('GET','/library',M)[1][0]['id']
print('copy name_taken', api('POST',f'/library/{lid}/copy',M,{'project_id':3,'name':'E2E 1026 Speech protocol'}))
print('editors unknown', api('POST','/projects/3/editors',M,{'email':'nobody-1026@test.com'}))
print('remove creator', api('DELETE','/projects/3/editors/1',M))
print('GET site-config bad token', api('GET','/site-config/doesnotexist0000'))
s=Sess("master"); p=s.page
s.go("/studies/3/editors"); s.shot("study3-editors-before")
p.click('[data-testid=study-add-editor]'); p.fill('[data-testid=add-editor-email]','nobody-1026@test.com'); p.click('[data-testid=add-editor-submit]'); p.wait_for_timeout(1000); s.shot("study3-editor-unknown-email"); print(p.inner_text('[data-testid=add-editor-modal]')[-200:])
p.fill('[data-testid=add-editor-email]','e2e-editor-1026@test.com'); p.click('[data-testid=add-editor-submit]'); p.wait_for_timeout(1500); s.shot("study3-editors-added"); print(p.inner_text('[data-testid=study-editors]'))
print(p.locator('[data-testid^=editor-remove-]').evaluate_all('e=>e.map(x=>x.dataset.testid)'))
p.click('[data-testid=editor-remove-3]'); p.wait_for_timeout(700); s.shot("study3-editor-remove-confirm"); print(p.locator('.ui-modal,[role=dialog]').last.inner_text())
p.get_by_role("button",name=re.compile("^Remove")).last.click(); p.wait_for_timeout(1200); print(p.inner_text('[data-testid=study-editors]'))
# runs at from clinic side
s.go("/clinics/5/studies"); s.shot("clinic5-studies"); print(p.inner_text('[data-testid=clinic-receives]')[:600])
s.close()
