"""Check 14 i18n spot check (CS, DE): Clinics, clinic computers (Config), study (visible counts), protocol editor (duration warning, custom-field hint), Last changed."""
import re, json
from ui import *
from redact import redact
P=json.load(open(os.path.join(SP,'ids14.json')))['studyId']
RAW=re.compile(r"\b[a-z][A-Za-z0-9]*(?:\.[a-z][A-Za-z0-9]*){1,}\b")
def scan(p,tag):
    t=p.inner_text('body'); raw=[x for x in RAW.findall(t) if not re.match(r"^(e2e|www|\d)",x) and not x.endswith(('.wav','.json','.png'))]
    print(tag,"| undefined:", "undefined" in t, "| raw-key-like:", raw[:6])
for lang in ("cs","de"):
    for who,path,tag in (("master","/clinics","clinics"),("master","/clinics/4/computers","computers"),("editor","/studies/3","study3-editor"),("editor","/studies","studies-editor"),("master","/studies/3/clinics","study3-master"),("master",f"/studies/{P}/protocols","protocols"),("master","/recordings","recordings")):
        s=Sess(who,lang=lang); p=s.page; s.go(path); redact(p); scan(p,f"{lang} {tag}"); s.shot(f"14-{lang}-{tag}"); s.close()
    s=Sess("master",lang=lang); p=s.page; s.go(f"/studies/{P}/protocols/28"); p.wait_for_timeout(800)
    p.click('[data-testid=task-edit-0]'); p.wait_for_timeout(600); mm=p.locator('.modal-form'); inp=mm.locator('.form-group input[type=number]'); inp.nth(1).fill('70'); inp.nth(2).fill('60'); p.wait_for_timeout(400)
    print(lang,"warning:", p.locator('[role=alert]').all_inner_texts(), "| save enabled:", p.locator('[data-testid=modal-save]').is_enabled()); s.shot(f"14-{lang}-duration-warning"); scan(p,f"{lang} task modal")
    p.click('[data-testid=modal-cancel]'); p.wait_for_timeout(300)
    p.click('[data-testid=edit-identifiers]'); p.wait_for_timeout(400); m=p.locator('.modal-form, [role=dialog], .ui-modal').last
    m.locator('button').filter(has_text=re.compile("vlastní|benutzer|custom",re.I)).first.click(); p.wait_for_timeout(300); print(lang,"custom-field hint:", [l for l in m.inner_text().split("\n") if re.search(r"(?i)jazyc|sprache|language",l)]); s.shot(f"14-{lang}-F076-hint")
    p.keyboard.press("Escape"); s.close()
print("done")
