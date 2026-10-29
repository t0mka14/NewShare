"""Check 8 F-087 with redacted screenshot; CS/DE column headers."""
from ui import *
from redact import redact
for who,lang in (("master","en"),("master","cs"),("master","de")):
    s=Sess(who,lang=lang); p=s.page
    s.go("/clinics/4/computers"); redact(p); s.shot(f"8-clinic4-computers-{lang}")
    print(lang,"headers:", p.locator('th').all_inner_texts()); s.close()
