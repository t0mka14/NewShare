"""Check 6 F-097 and 7 F-096 in the UI."""
import re
from ui import *
for who,lang in (("editor","en"),("master","en"),("editor","cs"),("editor","de")):
    s=Sess(who,lang=lang); p=s.page
    s.go("/studies"); s.shot(f"6-{who}-{lang}-studies"); print(who,lang,"STUDIES:", [l for l in p.inner_text('body').split("\n") if re.search(r"\d",l) and len(l)<70][:20])
    s.go("/studies/3"); p.wait_for_timeout(500); s.shot(f"6-{who}-{lang}-study3"); b=p.inner_text('body')
    print(who,lang,"STUDY3:", [l for l in b.split("\n") if re.search(r"(?i)clin|klinik|Shared|Sdílen|Geteilt|\(\d",l)][:10])
    s.go("/studies/3/clinics"); s.shot(f"6-{who}-{lang}-study3-clinics"); print(who,lang,"TABS:", p.locator('[role=tab]').all_inner_texts())
    s.close()
for who in ("editor","master"):
    c,ep=api('GET','/projects',tok(who)); print(who,"API /projects:", [(x['id'],x.get('site_count'),x.get('visible_site_count'),'count_sites' in x) for x in ep])
