"""Run 07 step 6: download the run-07 recording's ZIP (admin API) into <dir> (an empty folder outside the repo) and compare its SHA-256 with the
local ZIP (found through the context file's zip path, or app/data/sessions) and the hash the app declared. Usage: python zip_compare.py <dir>. Writes logs/zip-compare.txt."""
import sys
from common07 import *
dest = sys.argv[1]; os.makedirs(dest, exist_ok=True)
s = session07(); sid = s["sessionId"]
local = os.path.join(REPO, s["zip"]) if s.get("zip") else local_zip(s)
p = os.path.join(dest, os.path.basename(local))
code, h = download("/uploads/%s/zip" % sid, tok("master"), p)
a, b = sha256_file(local), sha256_file(p) if code == 200 else None
out = ["HTTP %s" % code, "size local %d, downloaded %s" % (os.path.getsize(local), os.path.getsize(p) if code == 200 else "-"),
       "local SHA-256      " + a, "downloaded SHA-256 " + str(b), "context SHA-256    " + s.get("zipSha256", "-"),
       "MATCH" if a == b else "MISMATCH"]
print("\n".join(out)); open(os.path.join(LOGS, "zip-compare.txt"), "w").write("\n".join(out))
