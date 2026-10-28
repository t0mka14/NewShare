"""§10.4 as the received persona on study 3 (seen through clinic A): API writes must be 403; protocol row menu; direct editor link."""
import json
from ui import *
C=json.load(open(CONTEXT,encoding='utf-8-sig')); S=C["projects"]["speech"]["id"]; PID=C["protocols"]["speech"]["idsCurrent"]["en"]; A=C["sites"]["A"]["id"]
R=tok('received'); N=tok('nogrants')
body={'name':'E2E A2 must-fail','language_id':[1],'description':'','version':1,'info_text':'','instructions_text':'','instructions_pdf_url':None,'editingMode':False,'tasks':[{'task_id':1,'task_order':1,'params':{'type':'voice','category':'phonation','recording':{'mode':'basicStop'},'phoneme':'a','repeat':1,'minDuration':5,'maxDuration':5,'recordVideo':False,'canRepeat':True,'canSkip':False,'showIndicator':True}}],'required_identifiers':[{'name':'patient_code','placeholder':'HC001','regex':'[A-Za-z0-9_-]+','required':True,'label':'','help':'','catalogue':True}],'recordings_file_name':'${field.patient_code}_${taskIndex}'}
for l,(m,pth,b) in {"POST /protocols (study S)":("POST","/protocols",dict(body,project_id=S,name="E2E A2 must-fail")),
  f"PATCH /projects/{S}":("PATCH",f"/projects/{S}",{"description":"x"}),f"POST /protocols/{PID}/archive":("POST",f"/protocols/{PID}/archive",{}),
  f"POST /projects/{S}/archive":("POST",f"/projects/{S}/archive",{"archived":True}),f"PUT /projects/{S}/sites/{A}":("PUT",f"/projects/{S}/sites/{A}",{}),
  f"POST /protocols/{PID}/share":("POST",f"/protocols/{PID}/share",{}),f"PATCH /sites/{A}":("PATCH",f"/sites/{A}",{"description":"x"}),
  f"PATCH /sites/{A} access_token":("PATCH",f"/sites/{A}",{"access_token":"x"*32}),"POST /sites":("POST","/sites",{"name":"E2E A2 must-fail","country":"X"}),"POST /projects":("POST","/projects",{"name":"E2E A2 must-fail"}),"POST /users":("POST","/users",{"email":"x@y.z"}),f"GET /sites/{A} token field":("GET",f"/sites/{A}",None)}.items():
    c,r=api(m,pth,R,b); print(f"received {l}: {c} {str(r)[:110]}" if 'token field' not in l else f"received {l}: {c} access_token present={isinstance(r,dict) and 'access_token' in r}")
print("nogrants POST /projects:", api("POST","/projects",N,{"name":"E2E A2 must-fail"})[0])
s=Sess("received"); p=s.page; s.go(f"/studies/{S}/protocols"); p.locator('[data-testid=study-protocols-table] tbody tr').first.get_by_role("button").last.click(); p.wait_for_timeout(500); s.shot("received-protocol-row-menu"); print("row menu:", p.locator('[role=menuitem]').all_inner_texts())
p.keyboard.press("Escape"); s.go(f"/studies/{S}/protocols/{PID}"); s.shot("received-protocol-editor-direct"); print("direct editor ->", p.url.split('#')[-1], p.inner_text('body')[:200].replace(chr(10)," | ")); s.close()
