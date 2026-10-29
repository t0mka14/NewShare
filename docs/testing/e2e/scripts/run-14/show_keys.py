"""F-009: print instruction keys of every protocol/task in the site-config JSON (first 60 chars). Reads cfgA.json from E2E_STATE_DIR."""
import json, os
c=json.load(open(os.path.join(os.environ["E2E_STATE_DIR"],"cfgA.json"),encoding="utf-8"))
S=c["strings"]
print("languages",c["languages"],"protocols",len(c["protocols"]))
for p in c["protocols"]:
    print("PROTOCOL",{k:(v if not isinstance(v,(list,dict)) else type(v).__name__) for k,v in p.items()})
    for t in p.get("tasks",[]):
        ks=[k for k in t if "nstruction" in k]
        print(" task",t.get("id"),t.get("type"),{k:t[k] for k in t if not isinstance(t[k],(list,dict))})
        for k in ks: print("   ",k,t[k])
print("strings type",type(S).__name__, list(S)[:5] if isinstance(S,dict) else "")
print("--- READING strings")
for lang in S:
    for k in ("p1_t3_instr1","p1_t3_instr2","p1_t3_instr3"):
        v=S[lang].get(k); print(lang,k,repr(v[:60]) if isinstance(v,str) else v, "len",len(v) if v else None)
