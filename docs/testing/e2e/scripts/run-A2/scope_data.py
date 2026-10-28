"""§10.8 with data (rerun after the desktop upload creates recordings). For every recording the master sees: list / open / zip / clip /attempts
as each persona. Expectation: a persona sees (200) exactly the recordings of clinics it is assigned to; everything else is 404 (never 403/200).
editor = editor of the study, NOT assigned to the clinic of the recording (clinic A); received = assigned to clinic A."""
import json
from common import *
C=json.load(open(CONTEXT,encoding='utf-8-sig')); A=C["sites"]["A"]["name"]
T={k:tok(k) for k in ("master","editor","received","nogrants","creator")}
c,r=api('GET','/uploads?pageSize=100',T['master']); rows=r['rows']; print("master sees",r['total'])
for k in ("editor","received","nogrants","creator"):
    c,lr=api('GET','/uploads?pageSize=100',T[k]); ids={x['sessionId'] for x in lr['rows']}; print(k,"lists",len(ids))
    for x in rows:
        sid=x['sessionId']; site=x.get('siteName') or x.get('site')
        res={p:api('GET',f'/uploads/{sid}{p}',T[k])[0] for p in ('','/attempts','/zip','/activity')}
        res['bulk']=api('POST','/uploads/download',T[k],{'ids':[sid]})[0]
        print(f"  {k:9} {sid} clinic={site} inList={sid in ids} {res}")
