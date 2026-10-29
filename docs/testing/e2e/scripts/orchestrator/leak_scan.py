import json,glob,os,re
root=r'C:\Users\koubato2\IdeaProjects\NewShare'
sec=json.load(open(os.path.join(root,'docs/testing/e2e/reports/2026-10-26-secrets.json'),encoding='utf-8'))
needles=set()
def walk(x):
    if isinstance(x,dict): [walk(v) for v in x.values()]
    elif isinstance(x,list): [walk(v) for v in x]
    elif isinstance(x,str) and len(x)>=20 and re.fullmatch(r'[A-Za-z0-9_-]+',x): needles.add(x)
walk(sec)
for d in ['app/data','app/data.e2e-1026-run06']:
    for p in glob.glob(os.path.join(root,d,'**','*.json'),recursive=True):
        try: s=open(p,encoding='utf-8').read()
        except: continue
        for m in re.findall(r'"installationId"\s*:\s*"([^"]+)"',s): needles.add(m)
print('needles',len(needles))
hits=0;n=0
for p in glob.glob(os.path.join(root,'docs/testing/e2e/**/*'),recursive=True):
    if os.path.isdir(p) or p.endswith('secrets.json') or p.endswith('.png'): continue
    b=open(p,'rb').read(); n+=1
    for enc in ('utf-8','utf-16-le'):
        for x in needles:
            if x.encode(enc) in b: hits+=1; print('HIT',p,enc)
print('files',n,'hits',hits)
c=open(os.path.join(root,'docs/testing/e2e/reports/2026-10-26-context.json'),'rb').read(3); print('BOM' if c==b'\xef\xbb\xbf' else 'no BOM')
