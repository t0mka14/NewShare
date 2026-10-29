"""Checks 1, 2 (UI), 10, 11 (F-068) on protocol 28 in the throw-away study."""
import json, datetime
from ui import *
P=json.load(open(os.path.join(SP,'ids14.json')))['studyId']; PID=28
c,pr=api('GET',f'/projects/{P}/protocols',tok('master')); print("API",c,[(x['id'],x.get('updated_at'),x.get('created_at'),x.get('description')) for x in pr])
print("now UTC", datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='seconds'), "local", datetime.datetime.now().isoformat(timespec='seconds'))
s=Sess("master"); p=s.page
s.go(f"/studies/{P}/protocols"); s.shot("1-2-study-protocol-list"); print("LIST:", p.locator('[data-testid=study-protocols-table]').inner_text().replace("\n"," | "))
s.go(f"/studies/3/protocols"); s.shot("2-1-speech-protocol-list"); print("LIST3:", p.locator('[data-testid=study-protocols-table]').inner_text().replace("\n"," | "))
# editor "Last changed"
s.go(f"/studies/{P}/protocols/{PID}"); s.shot("1-3-editor-last-changed"); body=p.inner_text('body'); import re
print("Last changed in editor:", [l for l in body.split("\n") if re.search("hang|ago|Last",l)][:5])
# library
s.go("/library"); s.shot("1-4-library"); print("LIB:", p.inner_text('body')[:600].replace("\n"," | "))
# F-079
for path in (f"/studies/{P}/protocols/{PID}/preview", f"/studies/{P}/protocols/new/preview"):
    pg=s.ctx.new_page(); pg.goto(BASE+"/#"+path); pg.wait_for_timeout(2500); name=path.replace("/","_")
    pg.screenshot(path=os.path.join(SHOTS,"10-preview"+name+"-direct.png"),full_page=True); print("direct",path,"->",pg.url.split('#')[-1], pg.inner_text('body')[:200].replace("\n"," | "))
    pg.reload(); pg.wait_for_timeout(2500); pg.screenshot(path=os.path.join(SHOTS,"10-preview"+name+"-reload.png"),full_page=True); print("reload ->",pg.url.split('#')[-1], pg.inner_text('body')[:150].replace("\n"," | ")); pg.close()
s.close()
