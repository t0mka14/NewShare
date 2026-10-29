import re, collections
t=open('findings.md',encoding='utf-8').read()
rows=re.findall(r'^\| \[(F-\d+)\]\(([^)#]+)[^)]*\) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|$',t,re.M)
print(len(rows))
openr=[r for r in rows if r[6].strip()=='open']
c=collections.Counter((r[3].strip(),r[2].strip()) for r in openr)
areas=sorted({a for a,_ in c}); sev=['blocker','major','minor','cosmetic','question']
for a in areas: print(a,[c[(a,s)] for s in sev],sum(c[(a,s)] for s in sev))
print('total',[sum(c[(a,s)] for a in areas) for s in sev],len(openr))
new=[r for r in rows if int(r[0][2:])>=67]
print('new', collections.Counter(r[2].strip() for r in new), len(new))
print('notopen:',[(r[0],r[6].strip()) for r in rows if r[6].strip()!='open'])
print('open major', [(r[0],r[3]) for r in openr if r[2].strip()=='major'])
