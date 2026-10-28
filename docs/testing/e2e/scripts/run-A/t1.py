from ui import *
s=Sess("master"); p=s.page
routes=["/recordings","/recordings/nonexistent-id","/studies","/studies/3","/studies/3/protocols","/studies/3/clinics","/studies/3/recordings","/studies/3/editors","/studies/3/settings","/studies/3/protocols/1","/studies/3/protocols/1/preview","/clinics","/clinics/4","/clinics/4/studies","/clinics/4/computers","/clinics/4/recordings","/clinics/4/settings","/library","/library/15","/library/15/edit","/users","/users/3","/nothing-here","/login"]
out=[]
for r in routes:
    s.go(r,1800)
    nav=p.locator('[data-testid^=nav-]').evaluate_all('e=>e.map(x=>x.dataset.testid)')
    col=p.evaluate("()=>{const e=document.querySelector('[data-testid=app-shell]');return e?e.dataset.collapsed:null}")
    pr=s.primaries()
    name="tour"+r.replace("/","-")
    s.shot(name)
    out.append((r,p.url.split('#')[1],nav,col,pr))
    print(out[-1])
print(s.errors)
# redirects
old=["/admin","/admin/uploads","/admin/uploads/abc","/admin/uploads/devices","/admin/uploads/downloads","/admin/projects/3","/admin/projects/3/protocols","/admin/projects/3/protocols/1","/admin/sites/4","/admin/project-management","/admin/site-management","/admin/admin-management","/participant/test","/admin/uploads?site=4&project=3"]
for r in old:
    s.go(r,1200); print("REDIR",r,"->",p.url.split('#')[1])
s.close()
