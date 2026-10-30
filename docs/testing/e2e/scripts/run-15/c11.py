"""Check 11 F-078: signed-in user opens #/login -> lands on Recordings, stays signed in; then Sign out -> cleared login page. Master and received."""
from ui import *
def tokpresent(p): return p.evaluate("()=>({tok:!!localStorage.getItem('adminToken'),user:!!localStorage.getItem('adminUser')})")
for who in ("master","received"):
    s=Sess(who); p=s.page
    print(who,"start",p.url.split('#')[-1],tokpresent(p))
    p.goto(BASE+"/#/login"); p.wait_for_timeout(1500); print(who,"after #/login:",p.url.split('#')[-1],tokpresent(p)); s.shot(f"11-login-redirect-{who}")
    p.goto(BASE+"/#/login"); p.reload(); p.wait_for_timeout(1500); print(who,"after reload on #/login:",p.url.split('#')[-1],tokpresent(p))
    btn=p.get_by_role("button",name=re.compile("Sign out|Log out",re.I)); print("signout btns:",btn.count(), p.locator('[data-testid*=logout],[data-testid*=signout],[aria-label*=Sign],[title*=Sign]').count())
    (btn.first if btn.count() else p.locator('[data-testid*=logout],[data-testid*=signout],[aria-label*=Sign],[title*=Sign]').first).click(); p.wait_for_timeout(1200)
    print(who,"after sign out:",p.url.split('#')[-1],tokpresent(p)); 
    print(" login form:", p.locator('input[type=password]').count(), "email value:", repr(p.locator('input[type=email],input[name=email]').first.input_value() if p.locator('input[type=email],input[name=email]').count() else None), "pw value:", repr(p.locator('input[type=password]').first.input_value() if p.locator('input[type=password]').count() else None))
    s.shot(f"11-after-signout-{who}"); print(" errors",s.errors[:3]); s.close()
