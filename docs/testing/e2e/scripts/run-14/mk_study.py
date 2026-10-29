"""Create the throw-away study 'E2E 1026 run14 study' as master; store its id in state/ids14.json."""
import json, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "common"))
from common import *
c,r=api('POST','/projects',tok('master'),{'name':'E2E 1026 run14 study','description':''})
print(c,r)
if c in (200,201):
    pid=r.get('id') or r.get('project',{}).get('id'); json.dump({'studyId':pid},open(os.path.join(SP,'ids14.json'),'w'))
