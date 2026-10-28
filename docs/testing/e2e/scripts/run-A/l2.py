from ui import *
s=Sess("master"); p=s.page
s.go("/studies/3/protocols")
p.locator('[data-testid=study-protocols-table] tbody tr').first.locator('button').last.click(); p.wait_for_timeout(300)
p.get_by_role("menuitem",name="Share to library").click(); p.wait_for_timeout(1500); s.shot("library-share-toast")
print(p.inner_text('body')[-400:])
p.locator('[data-testid=study-protocols-table] tbody tr').first.locator('button').last.click(); p.wait_for_timeout(300); print(p.locator('[role=menu]').inner_text()); s.shot("protocol-row-menu-after-share")
p.keyboard.press("Escape")
s.go("/library"); s.shot("library-list"); print(p.inner_text('[data-testid=library-page]')); print(s.primaries())
p.locator('[data-testid=library-table] tbody tr').first.click(); p.wait_for_timeout(1200); print(p.url); s.shot("library-entry"); print(p.inner_text('[data-testid=library-entry-page]')[:1500]); print(s.primaries())
s.close()
