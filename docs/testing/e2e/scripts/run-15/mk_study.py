"""Create the throw-away study 'E2E 1030 run15 study' as the creator persona; ids in state15/ids15.json."""
import json, os
from common import *
c,r=api('POST','/projects',tok('creator'),{'name':'E2E 1030 run15 study','description':'run 15 throw-away'})
print(c,r)
if c in (200,201):
    pid=r.get('id') or r.get('project',{}).get('id'); json.dump({'studyId':pid},open(os.path.join(SP,'ids15.json'),'w'))
