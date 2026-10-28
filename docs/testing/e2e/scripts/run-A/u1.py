from ui import *
M=tok('master')
s=Sess("master"); p=s.page
s.go("/users"); s.shot("users-list-final"); print(p.inner_text('[data-testid=users-table]'))
s.go("/users/6"); s.shot("user6-page"); print(p.inner_text('[data-testid=user-page]')[:900])
p.click('[data-testid=user-flag-studies]'); p.wait_for_timeout(1200); s.shot("user6-flag-on")
print(api('GET','/users/6',M)[1])
s.go("/users/4")
p.click('[data-testid=user-clinic-4]'); p.wait_for_timeout(1200); s.shot("user4-clinic-assigned"); print(api('GET','/users/4',M)[1]['sites'])
s.close()
print(api('PUT','/users/3/sites',M,{'site_ids':[5]}))
print(api('POST','/projects/3/editors',M,{'email':'e2e-editor-1026@test.com'}))
print([ (u['email'],u['sites'],u['can_create_projects']) for u in api('GET','/users',M)[1]])
