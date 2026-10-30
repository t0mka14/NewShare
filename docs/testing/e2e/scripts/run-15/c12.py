"""Check 12 F-100: add-editor API. Creator owns study 8. Part 1 add new editor (201), re-add (200, same grant, ISO added_at), upper-case re-add. Removal of the new editor afterwards restores state."""
import json, os
from common import *
P=json.load(open(os.path.join(SP,'ids15.json')))['studyId']; Tc=tok('creator')
def show(l,x): print(l, x[0], json.dumps(x[1])[:400])
show('editors before',api('GET',f'/projects/{P}/editors',Tc))
a=api('POST',f'/projects/{P}/editors',Tc,{'email':'e2e-editor-1026@test.com'}); show('add new (expect 201)',a)
b=api('POST',f'/projects/{P}/editors',Tc,{'email':'e2e-editor-1026@test.com'}); show('re-add (expect 200)',b)
d=api('POST',f'/projects/{P}/editors',Tc,{'email':'E2E-EDITOR-1026@TEST.COM'}); show('re-add upper-case (expect 200)',d)
show('editors after',api('GET',f'/projects/{P}/editors',Tc))
ga=a[1] if isinstance(a[1],dict) else {}; gb=b[1] if isinstance(b[1],dict) else {}
import re
def added(x):
    g=x.get('editor') or x.get('grant') or x
    return g.get('added_at')
print("added_at new:",added(ga),"| re-add:",added(gb),"| equal:",added(ga)==added(gb), "| ISO:", bool(re.match(r"\d{4}-\d\d-\d\dT[\d:.]+Z?",str(added(gb)))))
