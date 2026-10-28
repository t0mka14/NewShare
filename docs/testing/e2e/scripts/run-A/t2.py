from ui import *
s=Sess("master"); p=s.page
old=["/admin","/admin/uploads","/admin/uploads/abc","/admin/uploads/devices","/admin/uploads/downloads","/admin/projects/3","/admin/projects/3/protocols","/admin/projects/3/protocols/1","/admin/sites/4","/admin/project-management","/admin/site-management","/admin/admin-management","/participant/test","/admin/uploads?site=4&project=3","/admin/uploads?project=3"]
for r in old:
    p.goto(BASE+"/#"+r); p.wait_for_timeout(1500); print("REDIR",r,"->",p.url.split('#')[1])
p.goto(BASE+"/#/login"); p.wait_for_timeout(1500); print("login while authed ->", p.url.split('#')[1], p.evaluate("Object.keys(localStorage)"))
s.go("/studies/3/protocols/1"); p.click('[data-testid=protocol-preview]'); p.wait_for_timeout(3000); print(p.url); s.shot("protocol-preview"); print(p.inner_text('body')[:500])
s.close()
