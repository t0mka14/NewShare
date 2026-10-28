from ui import *
s=Sess("master"); p=s.page
for st,cl in [(3,[4,5]),(4,[4])]:
    s.go(f"/studies/{st}/clinics")
    for c in cl:
        p.click(f'[data-testid=study-runs-at-switch-{c}]'); p.wait_for_timeout(1000)
    s.shot(f"study{st}-clinics-after")
# turn off confirmation: toggle B on study 4 on then off
s.go("/studies/4/clinics"); p.click('[data-testid=study-runs-at-switch-5]'); p.wait_for_timeout(1000)
p.click('[data-testid=study-runs-at-switch-5]'); p.wait_for_timeout(700); s.shot("study4-runsat-off-confirm"); print(p.locator('[role=dialog],.ui-modal').first.inner_text())
print(p.locator('button').all_inner_texts())
s.close()
