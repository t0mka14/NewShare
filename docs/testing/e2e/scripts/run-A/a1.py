from ui import *
M=tok('master'); A=json.load(open('auth.json'))['admin']['token']
print("sites visible to admin:", [(x['id'],x['name']) for x in api('GET','/sites',A)[1]])
print("projects visible to admin:", [(x['id'],x['name'],x['can_edit']) for x in api('GET','/projects',A)[1]])
body={"name":"E2E 1026 received probe","language_id":[1],"tasks":[{"type":"voice","category":"phonation","recording":{"mode":"basicStop"},"phoneme":"a","repeat":1,"minDuration":5,"maxDuration":5,"recordVideo":False,"canRepeat":True,"canSkip":False,"showIndicator":True}],"project_id":1,"editingMode":False}
c,r=api('POST','/protocols',M,body); print("probe create",c,r)
pid=r.get('protocol_id') or r.get('id')
res={}
res['POST /protocols (study 1)']=api('POST','/protocols',A,dict(body,name="E2E 1026 x"))
res['PATCH /projects/1']=api('PATCH','/projects/1',A,{"description":"hack"})
res[f'POST /protocols/{pid}/archive']=api('POST',f'/protocols/{pid}/archive',A,{})
res['POST /projects/1/archive']=api('POST','/projects/1/archive',A,{"archived":True})
res['POST /projects/1/editors']=api('POST','/projects/1/editors',A,{"email":"master@test.com"})
res['PUT /projects/1/sites/1']=api('PUT','/projects/1/sites/1',A,{})
res['DELETE /projects/1/sites/1']=api('DELETE','/projects/1/sites/1',A)
res['GET /projects/3 (not visible)']=api('GET','/projects/3',A)
res['GET /library']=api('GET','/library',A)
res['GET /users']=api('GET','/users',A)
res['GET /uploads/nonexistent']=api('GET','/uploads/nonexistent',A)
res['POST /protocols (study 3 hidden)']=api('POST','/protocols',A,dict(body,project_id=3,name="E2E 1026 y"))
res['GET /projects/1/editors']=api('GET','/projects/1/editors',A)
for k,v in res.items(): print(k, v[0], str(v[1])[:100])
json.dump({k:[v[0],v[1]] for k,v in res.items()},open('a1res.json','w'))
print("probe pid",pid)
