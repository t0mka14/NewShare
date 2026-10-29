import re,os
def slug(h):
    h=h.strip().lower()
    h=re.sub(r'[^\w\- ]','',h)  # drop punctuation (GitHub keeps - and _)
    return h.replace(' ','-')
t=open('findings.md',encoding='utf-8').read()
cache={}
bad=[]
for fid,path,anc in re.findall(r'^\| \[(F-\d+)\]\(([^)#]+)#([^)]+)\)',t,re.M):
    if path not in cache:
        if not os.path.exists(path): bad.append((fid,'NOFILE',path)); continue
        s=open(path,encoding='utf-8').read()
        a=set(slug(h) for h in re.findall(r'^#+ (.*)$',s,re.M))|set(re.findall(r'id="([^"]+)"',s))
        cache[path]=a
    if anc not in cache[path]: bad.append((fid,anc[:60]))
print(len(bad)); [print(b) for b in bad]
