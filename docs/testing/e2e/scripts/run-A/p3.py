from ui import *
ids={}
s=Sess("master"); p=s.page
s.go("/studies")
s.shot("studies-list-seed")
p.click('[data-testid=studies-new]'); p.wait_for_timeout(400)
p.click('[data-testid=new-study-submit]'); p.wait_for_timeout(400); s.shot("study-new-validation")
print(p.inner_text('[data-testid=new-study-modal]'))
for nm,desc in [("E2E 1026 Speech study","E2E run A: speech protocol, EN+CS"),("E2E 1026 Video study","E2E run A: video protocol, EN+CS")]:
    s.go("/studies"); p.click('[data-testid=studies-new]'); p.wait_for_timeout(400)
    p.get_by_label("Name").first.fill(nm)
    p.get_by_label("Description").fill(desc)
    s.shot("study-new-modal-filled") if "Speech" in nm else None
    p.click('[data-testid=new-study-submit]'); p.wait_for_timeout(1500)
    print(nm, p.url); ids[nm]=p.url
    s.shot("study-created-"+nm.split()[2].lower())
# dup name
s.go("/studies"); p.click('[data-testid=studies-new]'); p.get_by_label("Name").first.fill("E2E 1026 Speech study"); p.click('[data-testid=new-study-submit]'); p.wait_for_timeout(1000); s.shot("study-new-dupname"); print("dup:", p.inner_text('[data-testid=new-study-modal]'))
print(ids, s.errors)
s.close()
