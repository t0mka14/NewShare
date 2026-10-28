from ui import *
routes=["/recordings","/studies","/studies/3/protocols","/studies/3/clinics","/studies/3/editors","/studies/3/settings","/clinics","/clinics/4/studies","/clinics/4/computers","/clinics/4/settings","/library","/library/15","/users","/users/3","/studies/3/protocols/1","/studies/3/protocols/2"]
s=Sess("master",1024,768); p=s.page
for r in routes:
    s.go(r,1800); ov=p.evaluate("()=>[document.documentElement.scrollWidth,document.documentElement.clientWidth]")
    # elements overflowing viewport
    wide=p.evaluate("()=>[...document.querySelectorAll('main *')].filter(e=>e.getBoundingClientRect().right>window.innerWidth+1&&e.offsetParent).slice(0,3).map(e=>e.tagName+'.'+e.className)")
    s.shot("r1024"+r.replace("/","-")); print(r,ov,wide)
s.close()
