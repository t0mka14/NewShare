"""Part C step 8: find the uploaded session on the server (master), print status, download the ZIP via ticket, read examination.json, check questionnaireAnswers.
Usage: c8_server_check.py <sessionId> <out_dir>.  Writes <out_dir>/examination.redacted.json (installationId replaced) and the unzip lives in a temp dir."""
import sys, os, json, zipfile, tempfile, urllib.request
from common import *
sid, out = sys.argv[1], sys.argv[2]
t = tok('master')
c, r = api('GET', '/uploads?q=' + sid[:8], t)
rows = r['rows']
print("list", c, "total", r['total'], [(x['id'][:8], x['status']) for x in rows])
row = [x for x in rows if x['id'] == sid][0]
print({k: v for k, v in row.items() if 'install' not in k.lower() and 'device' not in k.lower()})
c, tk = api('POST', '/uploads/ticket', t); ticket = tk['ticket']
data = urllib.request.urlopen(f"{BASE}/api/uploads/{sid}/zip?ticket={ticket}", timeout=60).read()
print("zip bytes", len(data), data[:2])
tmp = tempfile.mkdtemp(prefix="run17zip-")
open(os.path.join(tmp, "s.zip"), "wb").write(data)
z = zipfile.ZipFile(os.path.join(tmp, "s.zip"))
names = z.namelist(); print("entries:", [n for n in names if not n.startswith('clips/')][:30], "total", len(names))
ex = json.loads(z.read([n for n in names if n.endswith('examination.json')][0]).decode('utf-8'))
iid = ex.get('installationId'); ex['installationId'] = '<installationId>'
raw = json.dumps(ex, ensure_ascii=False, indent=2)
assert iid not in raw
open(os.path.join(out, 'examination.redacted.json'), 'w', encoding='utf-8').write(raw)
for i, tk_ in enumerate(ex['tasks']):
    print("task", i, "keys of questionnaireAnswers:", json.dumps(tk_.get('questionnaireAnswers'), ensure_ascii=False))
