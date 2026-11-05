"""One master login (rate limited) -> auth.json + tokens.json in E2E_STATE_DIR. Password from env E2E_PASSWORD."""
import json, os
from common import *
c,r=api('POST','/auth/admin/login',body={'email':'master@test.com','password':os.environ['E2E_PASSWORD']})
print(c, list(r.keys()) if isinstance(r,dict) else r)
if c==200:
    json.dump({'master':{'user':r['user'],'token':r['token']}},open(os.path.join(SP,'auth.json'),'w'))
    json.dump({'master':r['token']},open(os.path.join(SP,'tokens.json'),'w'))
