from ui import *
s=Sess("master"); p=s.page
s.go("/clinics"); s.shot("clinics-list-seed")
p.click('[data-testid=new-clinic]'); p.wait_for_timeout(400)
p.click('[data-testid=new-clinic-submit]'); p.wait_for_timeout(400); s.shot("clinic-new-validation"); print("empty:", p.inner_text('[data-testid=new-clinic-modal]'))
p.fill('[data-testid=clinic-name-input]','Fakultní nemocnice Brno'); p.wait_for_timeout(300); s.shot("clinic-new-filename-hint"); print("hint:", p.inner_text('[data-testid=clinic-filename-hint]'))
for nm in ["E2E 1026 Clinic A","E2E 1026 Clinic B"]:
    s.go("/clinics"); p.click('[data-testid=new-clinic]'); p.fill('[data-testid=clinic-name-input]',nm)
    p.get_by_label("Country").fill("Czechia")
    p.wait_for_timeout(300); s.shot("clinic-new-modal-filled") if nm.endswith("A") else None
    p.click('[data-testid=new-clinic-submit]'); p.wait_for_timeout(1800); print(nm, p.url)
    s.shot("clinic-created-"+nm[-1])
print(s.errors)
s.close()
