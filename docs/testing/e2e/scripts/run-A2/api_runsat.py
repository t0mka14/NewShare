"""§10.6 runs-at through the API, §10.8 recordings scope probes. Restores the context state at the end.
Ids from the context file: study = projects.speech.id, clinics A/B = sites.A.id / sites.B.id."""
import json
from common import *
C=json.load(open(CONTEXT,encoding='utf-8-sig')); S=C["projects"]["speech"]["id"]; V=C["projects"]["video"]["id"]; A=C["sites"]["A"]["id"]; B=C["sites"]["B"]["id"]
sec=json.load(open(SECRETS,encoding='utf-8-sig')); TB=[v for k,v in sec.items() if k.endswith("Clinic-B")][0]; TA=[v for k,v in sec.items() if k.endswith("Clinic-A")][0]
T={k:tok(k) for k in ("master","editor","received","nogrants","creator")}
def cfgnames(token):
    c,r=api('GET','/site-config/'+token); 
    return c,[p.get('name') or p.get('protocolName') for p in (r.get('protocols') or [])] if isinstance(r,dict) else r
def show(label,res): print(f"{label}: {res[0]} {str(res[1])[:150]}")
print("config B before",cfgnames(TB)); print("config A before",cfgnames(TA))
# editor sees B, not A
show("editor GET /sites/%d"%B, api('GET',f'/sites/{B}',T['editor']))
show("editor GET /sites/%d (not visible)"%A, api('GET',f'/sites/{A}',T['editor']))
# study side: remove then add at B as editor
show("editor DELETE /projects/%d/sites/%d (study side, visible clinic)"%(S,B), api('DELETE',f'/projects/{S}/sites/{B}',T['editor']))
print("config B after remove",cfgnames(TB))
show("editor PUT /projects/%d/sites/%d"%(S,B), api('PUT',f'/projects/{S}/sites/{B}',T['editor'],{}))
print("config B after add",cfgnames(TB))
# non-editor 403 : received persona sees study S via clinic A
show("received PUT /projects/%d/sites/%d (non-editor)"%(S,A), api('PUT',f'/projects/{S}/sites/{A}',T['received'],{}))
show("received DELETE /projects/%d/sites/%d (non-editor)"%(S,A), api('DELETE',f'/projects/{S}/sites/{A}',T['received']))
# editor, clinic not visible: 404
show("editor DELETE /projects/%d/sites/%d (clinic A not visible)"%(S,A), api('DELETE',f'/projects/{S}/sites/{A}',T['editor']))
show("editor PUT /projects/%d/sites/%d (clinic A not visible)"%(S,A), api('PUT',f'/projects/{S}/sites/{A}',T['editor'],{}))
print("config A after 404 attempts (must be unchanged)",cfgnames(TA))
# editor, study not editable and not visible
show("editor PUT /projects/%d/sites/%d (study 4 not visible)"%(V,B), api('PUT',f'/projects/{V}/sites/{B}',T['editor'],{}))
show("nogrants PUT /projects/%d/sites/%d"%(S,B), api('PUT',f'/projects/{S}/sites/{B}',T['nogrants'],{}))
print("--- 10.8 recordings scope")
for k in ("editor","received","nogrants","creator"):
    show(k+" GET /uploads", api('GET','/uploads',T[k]))
    show(k+" GET /uploads/summary", api('GET','/uploads/summary',T[k]))
    show(k+" GET /uploads/NOSUCH-ID", api('GET','/uploads/NOSUCH-ID',T[k]))
    show(k+" GET /uploads/NOSUCH-ID/download", api('GET','/uploads/NOSUCH-ID/download',T[k]))
    show(k+" GET /devices", api('GET','/devices',T[k]))
