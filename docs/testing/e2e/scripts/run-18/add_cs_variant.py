"""Run 18: save a new version of protocol 36 ('E2E18 filming', group 16) with language_id [1,2] so a Czech variant exists and Paris serves languages en+cs.
Needed because the app's language switch only offers languages present in the config (Paris served only 'en')."""
import sys, os, copy
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "common"))
from common import api, tok
t=tok('master')
c,p=api('GET','/protocols/36',t)
body={k:p[k] for k in ['name','description','randomization','required_identifiers','recordings_file_name','project_id','protocol_group_id']}
body.update(language_id=[1,2],editingMode=True,created_by=1,updated_by=1,use_audio_guide=False,
  tasks=[{'task_id':x['task_id'],'task_order':x['task_order'],'params':x['params'],'contents':[]} for x in p['tasks']])
c,r=api('POST','/protocols',t,body); print(c,r)
