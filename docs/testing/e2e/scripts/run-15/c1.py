"""Check 1 F-090: Connect a computer panel shows an absolute URL. Master on Clinic A (id 4) Computers tab."""
from ui import *
from redact import redact
for lang in ("en","cs","de"):
    s=Sess("master",lang=lang); p=s.page
    s.go("/clinics/4/computers"); p.wait_for_timeout(800)
    btn=p.get_by_role("button",name=re.compile("Connect a computer|Připojit|verbinden",re.I))
    print(lang,"connect buttons:",btn.count(), p.get_by_role("button").all_inner_texts()[:12])
    if btn.count(): btn.first.click(); p.wait_for_timeout(600)
    t=p.inner_text('body'); print(lang,"URLs in text:", re.findall(r"(?:https?://|\./)[^\s|]*api[^\s]*",t))
    print(lang,"code/mono:", p.locator('code,pre,input[readonly]').evaluate_all("e=>e.map(x=>x.value||x.textContent)")[:6])
    redact(p); s.shot(f"1-connect-computer-{lang}"); print(lang,"errors",s.errors); s.close()
