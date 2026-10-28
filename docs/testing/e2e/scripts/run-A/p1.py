from ui import *
s=Sess("master"); p=s.page
for r in ["/recordings","/studies","/clinics","/library","/users"]:
    s.go(r); s.shot("pre-"+r.strip("/")); print(r, s.primaries(), p.locator('[data-testid^=nav-]').evaluate_all('e=>e.map(x=>x.dataset.testid)'))
# invite user modal
s.go("/users"); p.click('[data-testid=new-user]'); p.wait_for_timeout(500); s.shot("users-new-modal")
print(p.inner_text('[data-testid=invite-user-modal]'))
input("x") if False else None
s.close()
