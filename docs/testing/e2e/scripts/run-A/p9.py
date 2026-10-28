from ui import *
s=Sess("master"); p=s.page
s.go("/studies/3/protocols/1"); s.shot("speech-editor-en-saved")
print(p.locator('[data-testid=protocol-languages]').inner_html()[:800])
print(p.inner_text('body')[:1200])
s.go("/studies/3/protocols"); s.shot("study3-protocols-list")
print(p.inner_text('[data-testid=study-protocols]'))
s.close()
