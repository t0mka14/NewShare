"""§10.8 probes with no data: every recording sub-endpoint with an unknown id, per persona (all must be 404, never 403/500)."""
from common import *
for k in ("editor","received","nogrants","creator"):
    t=tok(k)
    for m,p,b in (("GET","/uploads/NOSUCH/zip",None),("GET","/uploads/NOSUCH/clips/a.wav",None),("GET","/uploads/NOSUCH/attempts",None),("GET","/uploads/NOSUCH/activity",None),("GET","/uploads/NOSUCH/comments",None),("POST","/uploads/NOSUCH/reveal",{}),("POST","/uploads/download",{"ids":["NOSUCH"]}),("GET","/uploads/export.csv",None)):
        c,r=api(m,p,t,b); print(k,m,p,c,str(r)[:80].replace("\n"," "))
