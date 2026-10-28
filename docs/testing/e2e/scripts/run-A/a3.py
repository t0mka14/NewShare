from ui import *
s=Sess("admin"); p=s.page
for r in ["/recordings","/studies","/studies/1/protocols","/studies/1/clinics","/studies/1/editors","/studies/1/settings","/studies/1/protocols/18","/clinics","/clinics/1/computers","/clinics/1/settings","/library","/users"]:
    s.go(r,1800); name="admin"+r.replace("/","-"); s.shot(name)
    print(r,"->",p.url.split('#')[1],"| nav",p.locator('[data-testid^=nav-]').evaluate_all('e=>e.map(x=>x.dataset.testid)'),"| prim",s.primaries(),
      "| lock",p.locator('[data-testid=study-received-badge]').count(),"banner",p.locator('[data-testid=study-received-banner],[data-testid=protocol-readonly-banner]').count(),
      "| save/new/archive", p.locator('[data-testid=protocol-save],[data-testid=study-new-protocol],[data-testid=study-settings-save],[data-testid=study-add-editor]').count())
p.goto(BASE+"/#/recordings"); p.wait_for_timeout(1500)
print("clinic filter present:", p.locator('text=Clinic').count(), p.locator('[data-testid*=clinic]').evaluate_all('e=>e.map(x=>x.dataset.testid)'))
print("user role line:", p.inner_text('[data-testid=user-role]'), "| clinics nav label:", p.inner_text('[data-testid=nav-clinics]'))
s.go("/studies/1/protocols"); print(p.inner_text('[data-testid=study-page]')[:700])
s.go("/studies/1/protocols/18"); print(p.inner_text('body')[:500])
print(s.errors)
s.close()
