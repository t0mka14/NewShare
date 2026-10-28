"""One login per persona (rate limited). Writes auth.json + tokens.json into E2E_STATE_DIR.
Passwords come from env E2E_PASSWORD (shared test password); emails are the personas in the context file."""
import json, os
from common import *
pw=os.environ["E2E_PASSWORD"]
ctx=json.load(open(CONTEXT,encoding='utf-8-sig'))["personas"]
who={"master":"master@test.com"}
for k in ("editor","received","nogrants","creator"): who[k]=ctx[k]["email"]
auth={}; toks={}
for k,e in who.items():
    c,r=api('POST','/auth/admin/login',body={'email':e,'password':pw})
    print(k,e,c, (r.get('user') or r) if c!=200 else {x:r['user'].get(x) for x in r['user'] if x!='password'})
    if c==200: auth[k]={'user':r['user'],'token':r['token']}; toks[k]=r['token']
json.dump(auth,open(os.path.join(SP,'auth.json'),'w')); json.dump(toks,open(os.path.join(SP,'tokens.json'),'w'))
