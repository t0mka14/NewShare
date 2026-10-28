from ui import *
s=Sess("master"); p=s.page
s.go("/library/7"); p.click('[data-testid=library-add]'); p.wait_for_timeout(700)
print("name:", p.input_value('[data-testid=library-add-name]'), "study:", p.input_value('[data-testid=library-add-study]'))
p.click('[data-testid=library-add-submit]'); p.wait_for_timeout(1200); s.shot("library-add-name-taken"); print(p.inner_text('[data-testid=library-add-modal]')[-250:])
p.select_option('[data-testid=library-add-study]', "4"); p.wait_for_timeout(300)
print("name:", p.input_value('[data-testid=library-add-name]'))
p.fill('[data-testid=library-add-name]','E2E 1026 Speech copy')
p.get_by_label("Open in the editor after adding").check()
p.click('[data-testid=library-add-submit]'); p.wait_for_timeout(2500); print(p.url); s.shot("library-copy-opened-in-editor")
print(p.inner_text('.pe-header, body')[:300])
s.close()
