from ui import *
s=Sess("master"); p=s.page
s.go("/studies/3/protocols/1"); p.click('[data-testid=protocol-preview]'); p.wait_for_timeout(3000); print(p.url); s.shot("protocol-preview"); print(p.inner_text('body')[:600]); print(s.primaries())
print(p.locator('[data-testid=app-sidebar]').count())
s.close()
