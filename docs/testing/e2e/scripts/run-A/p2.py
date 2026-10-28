from ui import *
s=Sess("master"); p=s.page
s.go("/users")
# validation: submit empty
p.click('[data-testid=new-user]'); p.click('[data-testid=invite-user-submit]'); p.wait_for_timeout(400); s.shot("users-invite-validation")
print("val:", p.inner_text('[data-testid=invite-user-modal]'))
p.fill('[data-testid=invite-user-name]','x'); p.fill('[data-testid=invite-user-email]','admin@test.com'); p.click('[data-testid=invite-user-submit]'); p.wait_for_timeout(800); s.shot("users-invite-email-taken")
print("dup:", p.inner_text('[data-testid=invite-user-modal]'))
p.keyboard.press("Escape"); p.wait_for_timeout(300)
for role in ["editor","received","nogrants","creator"]:
    if not p.locator('[data-testid=invite-user-modal]').count():
        p.click('[data-testid=new-user]')
    p.fill('[data-testid=invite-user-name]',f'E2E 1026 {role.capitalize()}'); p.fill('[data-testid=invite-user-email]',f'e2e-{role}-1026@test.com')
    p.click('[data-testid=invite-user-submit]'); p.wait_for_timeout(1500)
    print(role, p.url)
    s.go("/users")
s.shot("users-list-after")
print(s.errors)
s.close()
