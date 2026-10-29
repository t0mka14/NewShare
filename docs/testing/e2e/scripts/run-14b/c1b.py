"""F-137 part 2: toggle required on Participant code, Esc, reopen -> original state. Never saves."""
from ui import *
s=Sess("master"); p=s.page
s.go("/studies/3/protocols"); p.reload(); p.wait_for_timeout(1500)
p.click('[data-testid=study-new-protocol]'); p.wait_for_timeout(500)
p.fill('[data-testid=new-protocol-name]','run14b throwaway')
p.click('[data-testid=new-protocol-create]'); p.wait_for_timeout(2500)
def req(): return p.get_by_text("Required",exact=True).first.locator("xpath=preceding-sibling::input|..//input[@type=checkbox]").first
def state(): return p.evaluate("()=>[...document.querySelectorAll('input[type=checkbox]')].filter(e=>e.offsetParent).map(e=>e.checked)")
chip=lambda: p.inner_text('[data-testid=protocol-settings]').split("Patient fields asked before recording")[1].split("Edit fields")[0].replace("\n"," | ")
p.click('[data-testid=edit-identifiers]'); p.wait_for_timeout(600)
print("open1", state()); s.shot("14b-1-5-required-original")
p.get_by_text("Required",exact=True).first.click(); p.wait_for_timeout(500)
print("toggled", state(), "| chip:", chip()); s.shot("14b-1-6-required-toggled")
p.keyboard.press("Escape"); p.wait_for_timeout(600)
print("after esc: chip:", chip()); s.shot("14b-1-7-after-esc")
p.click('[data-testid=edit-identifiers]'); p.wait_for_timeout(600)
print("reopen", state()); s.shot("14b-1-8-reopened")
p.keyboard.press("Escape"); p.wait_for_timeout(400)
print("final chip:", chip(), "| save disabled:", p.locator('[data-testid=protocol-save]').is_disabled())
s.close()
