import json, urllib.request, urllib.error, os
BASE=os.environ.get("E2E_BASE","http://192.168.122.183:10001")
# SP: folder holding auth.json / tokens.json (login tokens, never committed). E2E_STATE_DIR or cwd.
SP=os.environ.get("E2E_STATE_DIR", os.getcwd())
REPORTS=os.environ.get("E2E_REPORTS", os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),"..","..","reports")))
CONTEXT=os.environ.get("E2E_CONTEXT", os.path.join(REPORTS,"2026-10-26-context.json"))
SECRETS=os.environ.get("E2E_SECRETS", os.path.join(REPORTS,"2026-10-26-secrets.json"))
def api(method, path, token=None, body=None):
    req=urllib.request.Request(BASE+"/api"+path, method=method, data=(json.dumps(body).encode() if body is not None else None))
    req.add_header("Content-Type","application/json")
    if token: req.add_header("Authorization","Bearer "+token)
    try:
        r=urllib.request.urlopen(req, timeout=30); raw=r.read(); code=r.status
    except urllib.error.HTTPError as e:
        raw=e.read(); code=e.code
    try: return code, json.loads(raw)
    except Exception: return code, raw.decode(errors="replace")
def tok(name):
    return json.load(open(os.path.join(SP,"tokens.json")))[name]
