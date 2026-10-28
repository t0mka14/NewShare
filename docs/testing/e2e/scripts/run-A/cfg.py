from common import *
REPD=REPORTS
sec=json.load(open(REPD+r'\2026-10-26-secrets.json'))
L=REPD+r'\2026-10-26-run-A-new-web\logs'
res={}
for k,n in (('E2E-1026-Clinic-A','A'),('E2E-1026-Clinic-B','B')):
    c,r=api('GET','/site-config/'+sec[k]); res[n]=r
    print(n,c,[(p['name'],p['version'],p['project']) for p in r['protocols']], r['languages'])
    assert sec[k] not in json.dumps(r)
    json.dump(r,open(L+'\site-config-'+n+'.json','w',encoding='utf-8'),ensure_ascii=False,indent=1)
r=res['A']
miss=[]
for p in r['protocols']:
    for t in p['tasks']:
        ks=[t['titleKey']]+t.get('instructionKeys',[])+[q.get('questionTextKey') for q in t.get('questions',[])]
        for l in ('en','cs'):
            for k in ks:
                if not r['strings'][l].get(k): miss.append((l,k))
print('missing',miss)
print([ (t['subtype'] if 'subtype' in t else 'Q', t.get('recordVideo'),t['nrepetition'],t['length']) for p in r['protocols'] for t in p['tasks']])
