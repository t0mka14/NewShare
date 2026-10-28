from common import *
M=tok('master'); A=json.load(open('auth.json'))['admin']['token']
b=json.load(open('probe_body.json'))
c,r=api('POST','/protocols',M,b); print(c,r); pid=r.get('protocol_id')
print('admin archive probe', api('POST',f'/protocols/{pid}/archive',A,{}))
print('admin GET protocol', api('GET',f'/protocols/{pid}',A)[0])
print('admin POST protocol edit', api('POST','/protocols',A,dict(b,editingMode=True,protocol_group_id=None))[0:2])
json.dump({'probe':pid},open('probe.json','w'))
