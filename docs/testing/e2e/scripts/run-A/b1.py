from pe import *
s=Sess("master"); p=s.page
new_protocol(s,3,"E2E 1026 Speech protocol","en")
# identifiers: custom fields
p.click('[data-testid=edit-identifiers]'); p.wait_for_timeout(300)
for nm,lab,help_,reg,req in [("visit_code","Visit code","e.g. V01",r"V[0-9]{2}",True),("year_of_birth_opt","Year of birth (optional)","four digits",r"(19|20)[0-9]{2}",False)]:
    p.get_by_role("button",name="Add custom field").click(); p.wait_for_timeout(150)
    row=p.locator('.identifier-custom-row').last
    row.locator('input[type=text]').nth(0).fill(nm); row.locator('input[type=text]').nth(1).fill(lab); row.locator('input[type=text]').nth(2).fill(help_)
    row.locator('input[type=text]').nth(4).fill(reg)
    c=row.locator('input[type=checkbox]')
    if c.is_checked()!=req: c.click()
s.shot("speech-identifiers-filled")
p.click('[data-testid=modal-save]'); p.wait_for_timeout(300)
# info page
p.click('[data-testid=add-info-page]'); p.wait_for_timeout(600)
p.locator('.ql-editor').first.click(); p.keyboard.type("Welcome. This E2E 1026 speech protocol records phonation, PATAKA, reading, a monologue and a short questionnaire.")
s.shot("speech-info-modal"); p.click('[data-testid=modal-save]'); p.wait_for_timeout(300)
# tasks
add_task(s,"phonation",**{"Min Duration (s)":5,"Max Duration (s)":5,"Repetitions":2})
add_task(s,"syllableRepeating",**{"Required Duration (s)":5,"Repetitions":1})
add_task(s,"reading",**{"Repetitions":1})
add_task(s,"monologue",**{"Required Duration (s)":20,"Allow skip":True})
add_questionnaire(s,"E2E 1026 questionnaire","Short questionnaire for run A",[
  {"text":"Describe how you feel today","type":"open","optional":False},
  {"text":"Any other comment","type":"open","optional":True},
  {"text":"Do you smoke","type":"single","options":["Yes","No"],"optional":False},
  {"text":"Which symptoms apply","type":"multiple","options":["Hoarseness","Cough","None"],"optional":False}])
s.shot("speech-questionnaire-filled")
p.click('[data-testid=modal-save]'); p.wait_for_timeout(400)
s.shot("speech-editor-before-save")

p.locator("label.protocol-lang-pill").filter(has_text="cs").click(); p.wait_for_timeout(300); s.shot("speech-editor-cs-chip-added")
posts=[]
p.on("request", lambda r: posts.append((r.method,r.url,r.post_data)) if r.method=="POST" and "/protocols" in r.url else None)
p.click('[data-testid=protocol-save]'); p.wait_for_timeout(3500)
print(p.url); s.shot("speech-editor-saved")
json.dump(posts,open("speech_post.json","w"))
print(p.locator('[data-testid=protocol-languages]').inner_text() if p.locator('[data-testid=protocol-languages]').count() else "nolangs")
print(s.errors)
s.close()
