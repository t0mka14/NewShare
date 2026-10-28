"""§10.16 with two users. Author = creator persona (can_create_projects), second user = editor persona (editor of the throw-away study).
Needs a2ids.json (throwaway study id from api_editors.py). Copies go to the throw-away study (no clinic -> never in a config)."""
import json,os
from common import *
T={k:tok(k) for k in ("master","editor","received","nogrants","creator")}
P=json.load(open(os.path.join(SP,'a2ids.json')))['throwawayStudyId']
def show(l,r): print(f"{l}: {r[0]} {str(r[1])[:230]}"); return r
NAME=os.environ.get('E2E_LIB_NAME','E2E 1026 A2 library protocol')
def task(rep): return {"task_id":1,"task_order":1,"params":{"type":"voice","category":"phonation","recording":{"mode":"basicStop"},"phoneme":"a","repeat":rep,"minDuration":5,"maxDuration":5,"recordVideo":False,"canRepeat":True,"canSkip":False,"showIndicator":True}}
body={"name":NAME,"language_id":[1],"version":1,"info_text":"","instructions_text":"","instructions_pdf_url":None,"tasks":[task(1)],"project_id":P,"editingMode":False,
 "required_identifiers":[{"name":"patient_code","placeholder":"HC001","regex":"[A-Za-z0-9_-]+","required":True,"label":"","help":"","catalogue":True}],"recordings_file_name":"${field.patient_code}_${taskIndex}_${task.subtype}_Rep${repetition}","description":"A2 library probe"}
c,r=show('creator creates protocol in own study',api('POST','/protocols',T['creator'],body)); src=r.get('protocol_id') or r.get('id')
c,g=api('GET',f'/protocols/{src}',T['creator']); grp=g['protocol_group_id']; print('src',src,'group',grp)
show('received GET /library',api('GET','/library',T['received'])); show('nogrants GET /library',api('GET','/library',T['nogrants']))
show('nogrants POST share',api('POST',f'/protocols/{src}/share',T['nogrants'],{}))
show('received POST /library/15/copy',api('POST','/library/15/copy',T['received'],{'project_id':3,'name':'x'}))
c,r=show('creator shares',api('POST',f'/protocols/{src}/share',T['creator'],{})); 
lib=api('GET','/library?mine=1',T['creator'])[1]; print('creator mine',[(x['id'],x['name'],x.get('version'),x.get('used_in')) for x in lib])
eid=[x['id'] for x in lib if x['name']==NAME][0]
show('editor GET /library (sees creator entry)',api('GET','/library',T['editor']))
c,r=show('editor adds copy to throw-away study',api('POST',f'/library/{eid}/copy',T['editor'],{'project_id':P,'name':'E2E 1026 A2 library copy'})); cp=r['protocol_id']
show('editor adds copy again (name_taken)',api('POST',f'/library/{eid}/copy',T['editor'],{'project_id':P,'name':'E2E 1026 A2 library copy'}))
show('editor adds copy to study 4 (no edit rights)',api('POST',f'/library/{eid}/copy',T['editor'],{'project_id':4,'name':'zzz'}))
show('creator edits entry? (editor withdraws, not author)',api('POST',f'/library/{eid}/withdraw',T['editor'],{}))
# editor edits the copy: new version
c,cg=api('GET',f'/protocols/{cp}',T['editor']); print('copy',cp,'v',cg['version'],'group',cg['protocol_group_id'])
cb=dict(body,name=cg['name'],protocol_group_id=cg['protocol_group_id'],editingMode=True,description='EDITED BY SECOND USER'); cb['tasks']=[task(3)]
show('editor saves edited copy (new version)',api('POST','/protocols',T['editor'],cb))
c,le=api('GET',f'/library/{eid}',T['editor']); print('library entry after copy edit: v',le['version'],'desc',le['description'],'repeat',le['tasks'][0]['params'].get('repeat'))
# creator updates the entry: edit the source protocol, share again
sb=dict(body,name=NAME,protocol_group_id=grp,editingMode=True,description='UPDATED BY AUTHOR'); sb['tasks']=[task(4)]
show('creator saves source v2',api('POST','/protocols',T['creator'],sb))
show('creator shares again (update entry)',api('POST',f'/protocols/{src}/share',T['creator'],{}))
c,le=api('GET','/library',T['creator']); print('library list',[(x['id'],x['name'],x.get('version'),x.get('used_in')) for x in le])
c,cg2=api('GET',f'/protocols/{cp}',T['editor']); print('copy (id %d) still v%s'%(cp,cg2['version']), 'copied_from',cg2.get('copied_from_id'))
c,lst=api('GET',f'/projects/{P}/protocols',T['editor']); print('study protocols',[(x.get('name'),x.get('version')) for x in lst] if isinstance(lst,list) else lst)
cur=[x['id'] for x in api('GET','/library',T['creator'])[1] if x['name']==NAME][0]
show('library GET /library/:id v',api('GET',f'/library/{cur}',T['creator']))
show('master (not author) sees entry',api('GET',f'/library/{cur}',T['master']))
# never in config: library ids absent from every site-config
sec=json.load(open(SECRETS,encoding='utf-8-sig')); 
for k,v in sec.items():
    c,r=api('GET','/site-config/'+v); print('site-config',k,c,[p.get('name') for p in r.get('protocols',[])])
show('editor withdraw (not author)',api('POST',f'/library/{cur}/withdraw',T['editor'],{}))
show('creator withdraws',api('POST',f'/library/{cur}/withdraw',T['creator'],{}))
print('library after withdraw (creator)',[(x['id'],x['name']) for x in api('GET','/library',T['creator'])[1]])
c,lst=api('GET',f'/projects/{P}/protocols',T['editor']); print('copies remain',[(x.get('name'),x.get('version')) for x in lst])
json.dump({'src':src,'copy':cp,'entry':cur},open(os.path.join(SP,'a2lib.json'),'w'))
