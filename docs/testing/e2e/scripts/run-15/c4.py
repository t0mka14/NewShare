"""Checks 4 (F-084), 5 (F-098), 6 (F-101): not-available pages, empty Clinics page, hidden clinic URL."""
from ui import *
def txt(p): return p.inner_text('main') if p.locator('main').count() else p.inner_text('body')
for lang in ("en","cs","de"):
    for who,route,tag in (("nogrants","/library","library"),("creator","/library","library"),("editor","/users","users"),("nogrants","/users","users"),("nogrants","/clinics","clinics"),("creator","/clinics","clinics"),("nogrants","/clinics/4","hiddenclinic"),("nogrants","/studies/3","hiddenstudy"),("nogrants","/clinics/99999","unknownclinic")):
        if lang!="en" and tag in("hiddenstudy","unknownclinic"): continue
        s=Sess(who,lang=lang); p=s.page; s.go(route,1500)
        t=txt(p).replace("\n"," | ")
        print(f"[{lang}] {who} {route} -> {p.url.split('#')[-1]} :: {t[:420]}")
        s.shot(f"{tag}-{who}-{lang}".replace("--","-"))
        if s.errors: print("   errors:",s.errors[:3])
        s.close()
