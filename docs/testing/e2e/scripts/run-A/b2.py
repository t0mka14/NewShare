from pe import *
s=Sess("master"); p=s.page
new_protocol(s,4,"E2E 1026 Video protocol","en")
add_task(s,"phonation",**{"Min Duration (s)":5,"Max Duration (s)":5,"Repetitions":1,"Also record video":True})
add_task(s,"monologue",**{"Required Duration (s)":15,"Repetitions":2,"Also record video":True,"Allow repeat":True})
add_task(s,"syllableRepeating",**{"Required Duration (s)":5,"Repetitions":1,"Also record video":False})
p.locator("label.protocol-lang-pill").filter(has_text="cs").click(); p.wait_for_timeout(300)
s.shot("video-editor-before-save")
print(p.locator('[data-testid=protocol-tasks]').inner_text())
p.click('[data-testid=protocol-save]'); p.wait_for_timeout(3500)
print(p.url); s.shot("video-editor-saved"); print(s.errors)
s.close()
