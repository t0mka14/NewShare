"""Run 18 step E: count needles in this run's log lines (app.log lines from START onward, plus hotrun logs and main.chr.log). Needles are never printed, only labels, counts and redacted excerpts.
Usage: python leak_scan.py <START 'YYYY-MM-DD HH:MM:SS'> <run dir> ; env E2E_STATE_DIR (site_tokens.json), E2E_BACKUP (backup settings.json)"""
import json, os, re, sys, glob, gzip
REPO=os.path.abspath(os.path.join(os.path.dirname(__file__),*[".."]*5))
start, run = sys.argv[1], sys.argv[2]
needles={"patient code E2E18-FAKE":"E2E18-FAKE","Paris token":"paris000paris000paris000paris000","backslash C:\\Users":"C:" + chr(92) + "Users","forward C:/Users":"C:/Users"}
needles["noproto token"]=json.load(open(os.path.join(os.environ["E2E_STATE_DIR"],"site_tokens.json")))["noproto"]
b=json.load(open(os.path.join(os.environ["E2E_BACKUP"],"settings.json"),encoding="utf-8-sig"))
needles["original token"]=b["siteToken"]; needles["original installationId"]=b["installationId"]
for f in glob.glob(os.path.join(REPO,"app","data","sessions","2026-11-06_E2E18*")):
    sid=f.rsplit("_",1)[1]; needles["session id "+sid[:8]]=sid; needles["folder "+sid[:8]]=os.path.basename(f)
for m in re.finditer(r"installation[Ii][Dd]\W+([0-9a-f-]{36})", open(os.path.join(REPO,"app","data","logs","app.log"),encoding="utf-8",errors="replace").read()):
    needles["installationId in log "+m.group(1)[:8]]=m.group(1)
files={}
lines=[l for l in open(os.path.join(REPO,"app","data","logs","app.log"),encoding="utf-8",errors="replace") if re.match(r"\d{4}-\d\d-\d\d \d\d:\d\d:\d\d",l) is None or l[:19]>=start]
files["app.log (run lines)"]=lines
for p in glob.glob(os.path.join(run,"logs","hotrun-*.log"))+[os.path.join(REPO,"app","build","run","main","main.chr.log")]:
    files[os.path.basename(p)]=open(p,encoding="utf-8",errors="replace").read().splitlines(True)
tot=0
for name,ls in files.items():
    print("==",name,len(ls),"lines")
    for lab,n in needles.items():
        c=sum(l.count(n) for l in ls); tot+=c
        print("  %-40s %d"%(lab,c))
        if c:
            for l in [l for l in ls if n in l][:3]: print("     >",l.replace(n,"<"+lab+">").rstrip()[:240])
print("TOTAL hits",tot)
