"""Check 10 F-099: Recordings search placeholder fully visible at 1440x900 for the one-clinic (received) persona; also master for comparison, CS/DE."""
from ui import *
JS="""()=>{const i=document.querySelector('input[type=search],input[placeholder*=earch],input[placeholder*=ledat],input[placeholder*=uch]'); if(!i) return null;
const cs=getComputedStyle(i); const c=document.createElement('canvas').getContext('2d'); c.font=cs.font;
const tw=c.measureText(i.placeholder).width; const cw=i.clientWidth-parseFloat(cs.paddingLeft)-parseFloat(cs.paddingRight);
return {ph:i.placeholder,textW:tw,contentW:cw,clientW:i.clientWidth,scrollW:i.scrollWidth,fits:tw<=cw,font:cs.font}}"""
for who in ("received","master"):
  for lang in ("en","cs","de"):
    s=Sess(who,w=1440,h=900,lang=lang); p=s.page; s.go("/recordings",1500)
    r=p.evaluate(JS); print(who,lang,r); s.shot(f"10-search-placeholder-{who}-{lang}",full=False); print(" errors",s.errors[:3]); s.close()
