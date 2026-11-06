"""Run 18: create protocol 'E2E18 filming' in Test Study 001 (Paris's study) through POST /api/protocols, copying task params from protocol 29 (Test1)
and setting recordVideo=true, basicStop. Tasks: phonation, reading (short text), monologue (long text). Needs tokens.json from run-17/login.py (master token)."""
import json, copy, sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "common"))
from common import api, tok
t=tok('master')
c,src=api('GET','/protocols/29',t)
by={x['params'].get('category'):x for x in src['tasks']}
tasks=[]
for i,cat in enumerate(['phonation','reading','monologue'],1):
    x=copy.deepcopy(by[cat]); p=x['params']
    p['recordVideo']=True; p['recording']={'mode':'basicStop'}; p['repeat']=1; p['canRepeat']=True; p['showIndicator']=True
    p.pop('duration',None)
    tasks.append({'task_id':x['task_id'],'task_order':i,'params':p,'contents':[]})
body={'name':'E2E18 filming','language_id':1,'description':'run 18 filming + layout','version':1,'created_by':1,'updated_by':1,'project_id':1,
 'tasks':tasks,'randomization':{},'use_audio_guide':False,
 'required_identifiers':[{'name':'patient_code','placeholder':'HC001','regex':'[A-Za-z0-9_-]+','required':True,'label':'','help':'','catalogue':True}],
 'recordings_file_name':src['recordings_file_name'],'editingMode':'new'}
c,r=api('POST','/protocols',t,body); print(c,r)
