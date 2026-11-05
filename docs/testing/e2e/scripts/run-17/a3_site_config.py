"""Part A step 3: GET /api/site-config/<Paris token>, save raw JSON (no token in name/content), check protocol + showIf. Usage: a3_site_config.py <out.json>
Paris token is the one published in the test plan."""
import json, sys, os
from common import *
TOKEN="paris000paris000paris000paris000"
out=sys.argv[1]
c,r=api('GET','/site-config/'+TOKEN)
raw=json.dumps(r,ensure_ascii=False,indent=2)
assert TOKEN not in raw
open(out,'w',encoding='utf-8').write(raw)
print("status",c,"configVersion",r['configVersion'],"protocols",[p['name'] for p in r['protocols']])
pr=[p for p in r['protocols'] if p['name']=="E2E run 17 questionnaire rules"]
if not pr: sys.exit("protocol not served")
t=[t for t in pr[0]['tasks'] if t['type']=='QUESTIONNAIRE'][0]
strings=r['strings'].get('en',{})
keys={q['questionKey']:i+1 for i,q in enumerate(t['questions'])}
opts={}
for q in t['questions']:
    for k in q.get('questionOptions',[]): opts[k]=strings.get(k,k)
for i,q in enumerate(t['questions']):
    s=q.get('showIf','<absent>')
    if isinstance(s,dict): s="Q%d in %s"%(keys[s['questionKey']],[opts.get(k,k) for k in s['optionKeys']])
    print("Q%d"%(i+1),q['questionType'],'showIf' in q, s, "|",strings.get(q['questionTextKey']), "|", q.get('questionRegex'))
print(json.dumps(t['questions'][4],indent=1)); print(json.dumps(t['questions'][0]))
