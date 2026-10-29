"""F-139: open saved protocol (id 3) -> Add/Edit info page, type, Cancel; same for instructions. Never saves. Snapshot via API before/after."""
import json, re
from ui import *
t=tok("master")
def snap():
    c,r=api("GET","/protocols/3",t)
    return {k:r.get(k) for k in("updated_at","info_text","instructions_text","global_contents","updated_by","version")}
before=snap(); json.dump(before,open(os.path.join(SP,"snap14b-before.json"),"w"))
print("BEFORE keys/updated_at:", before["updated_at"], "| info len", len(str(before["info_text"])), "| instr", str(before["instructions_text"])[:50])
s=Sess("master"); p=s.page
s.go("/studies/3/protocols"); p.reload(); p.wait_for_timeout(1500)
p.get_by_text("E2E 1026 Speech protocol").first.click(); p.wait_for_timeout(2500); print(p.url)
s.shot("14b-2-1-editor-saved")
save=lambda: p.locator('[data-testid=protocol-save]').is_disabled()
pages=lambda: p.inner_text('[data-testid=protocol-settings]').split("Pages")[-1].replace("\n"," | ")
print("save disabled:", save(), "| pages:", pages())
print("testids:", p.locator('[data-testid]').evaluate_all("e=>e.map(x=>x.dataset.testid).filter(x=>/page|instr|info|modal|dirty/i.test(x))"))
print("edit/remove testids:", p.locator('[data-testid]').evaluate_all("e=>e.map(x=>x.dataset.testid).filter(x=>/edit|remove/i.test(x))"))
for name,tid in (("info",'edit-info-page'),("instr",'add-instructions-page')):
    loc=p.locator(f'[data-testid={tid}]') if name!="info" else p.locator('[data-testid=protocol-settings]').get_by_role("button",name="Edit",exact=True)
    print(name,"button text:",loc.inner_text())
    loc.click(); p.wait_for_timeout(800); s.shot(f"14b-2-{name}-1-open")
    ed=p.locator('[contenteditable=true]').last
    ed.click(); p.keyboard.type("run14b test"); p.wait_for_timeout(500)
    s.shot(f"14b-2-{name}-2-typed")
    print(name,"modal testids:", p.locator('[data-testid]').evaluate_all("e=>e.map(x=>x.dataset.testid).filter(x=>/modal/i.test(x))"))
    p.locator('[data-testid=modal-cancel]').click(); p.wait_for_timeout(800)
    s.shot(f"14b-2-{name}-3-after-cancel")
    print(name,"after cancel -> save disabled:", save(), "| pages:", pages(), "| dirty note visible:", p.locator("[data-testid=protocol-dirty-note]").is_visible())
# leave
p.goto(BASE+"/#/studies/3/protocols"); p.wait_for_timeout(1500)
print("url after leave:", p.url, "| confirm dialog?", p.get_by_role("button",name=re.compile("Discard|Leave",re.I)).count())
s.shot("14b-2-9-after-leave")
s.close()
after=snap(); print("UNCHANGED:", after==before, "| updated_at:", after["updated_at"])
