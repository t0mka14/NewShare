"""Check 9 F-082: New clinic modal, 60 then 64 then 65 chars; counter and limit message. Cancel, nothing created. Plus clinic settings name field (Clinic A, nothing saved)."""
from ui import *
s=Sess("master"); p=s.page
s.go("/clinics"); print("buttons:", p.get_by_role("button").all_inner_texts())
p.get_by_role("button",name=re.compile("New clinic|Add clinic|Create clinic",re.I)).first.click(); p.wait_for_timeout(600)
m=p.locator('[role=dialog],.ui-modal').last
inp=m.locator('input[type=text],input:not([type])').first
print("input attrs:", inp.evaluate("e=>({max:e.maxLength,ph:e.placeholder,tid:e.dataset.testid})"))
for n in (0,60,63,64,70):
    inp.fill(""); inp.type("a"*n) if n<=40 else inp.fill("a"*n); p.wait_for_timeout(300)
    print(n,"-> value len",len(inp.input_value()),"| modal text:", m.inner_text().replace("\n"," | ")[:300])
    if n in (60,64,70): s.shot(f"9-new-clinic-{n}")
m.get_by_role("button",name=re.compile("Cancel")).click(); p.wait_for_timeout(400); print("modal closed:", p.locator('[role=dialog],.ui-modal').count()==0)
# clinic settings
s.go("/clinics/4/settings"); p.wait_for_timeout(600)
ins=p.locator('main input').evaluate_all("e=>e.map(x=>({tid:x.dataset.testid,max:x.maxLength,v:x.value.length}))"); print("settings inputs:",ins)
nm=p.locator('main input[type=text],main input:not([type])').first; orig=nm.input_value()
nm.fill("b"*64); p.wait_for_timeout(300); print("settings 64:", p.inner_text('main').replace("\n"," | ")[:600]); s.shot("9-clinic-settings-64")
nm.fill(orig); p.reload(); p.wait_for_timeout(800)
print("reloaded name unchanged:", p.locator('main input').first.input_value()==orig, "errors", s.errors[:3])
c,r=api('GET','/sites',tok('master')); print("clinics:", [x['name'][:30] for x in r])
s.close()
