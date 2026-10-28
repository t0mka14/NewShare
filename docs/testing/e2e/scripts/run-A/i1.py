from ui import *
import re
routes=["/recordings","/studies","/studies/3/protocols","/studies/3/clinics","/studies/3/editors","/studies/3/settings","/clinics","/clinics/4/studies","/clinics/4/computers","/clinics/4/settings","/library","/library/15","/users","/users/3","/nothing-here","/studies/3/protocols/1"]
rawre=re.compile(r"\b[a-z][A-Za-z0-9]*(?:\.[a-z][A-Za-z0-9_]*)+\b")
for lang in ["cs","de"]:
    s=Sess("master",lang=lang); p=s.page
    for r in routes:
        s.go(r,1800); txt=p.inner_text('body')
        raws=[m for m in rawre.findall(txt) if '@' not in m and not m.endswith(('.com','.org'))]
        bad=[w for w in ("undefined","null","NaN","[object") if w in txt]
        s.shot(f"i18n-{lang}"+r.replace("/","-"))
        print(lang,r,"RAW:",raws[:6],"BAD:",bad)
    # modals
    s.go("/studies/3/protocols/1"); 
    p.click('[data-testid=task-edit-4]'); p.wait_for_timeout(500); s.shot(f"i18n-{lang}-questionnaire-modal"); print(lang,"Q modal:",p.locator('.questionnaire-modal-form').inner_text()[:600].replace("\n"," | "))
    p.click('[data-testid=modal-cancel]')
    p.click('[data-testid=edit-identifiers]'); p.wait_for_timeout(400); s.shot(f"i18n-{lang}-identifiers"); print(lang,"ident:",p.locator('.modal-settings').inner_text()[:500].replace("\n"," | ")); p.click('[data-testid=modal-cancel]')
    p.click('[data-testid=task-edit-0]'); p.wait_for_timeout(400); s.shot(f"i18n-{lang}-task-modal"); print(lang,"task:",p.locator('.modal-form').inner_text()[:400].replace("\n"," | ")); 
    s.close()
