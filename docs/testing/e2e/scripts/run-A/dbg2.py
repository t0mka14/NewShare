from playwright.sync_api import sync_playwright
import json
from common import *
with sync_playwright() as pw:
    b=pw.chromium.launch(headless=False); ctx=b.new_context(viewport={"width":1440,"height":900}); p=ctx.new_page()
    p.on("response", lambda r: print("RES", r.url[-60:], r.status) if "/api/" in r.url else None)
    p.goto(BASE+"/#/login"); p.wait_for_timeout(2000)
    p.screenshot(path="login-1440.png", full_page=True)
    p.fill('input[type=email],input[name=email]','master@test.com'); p.fill('input[type=password]','1234'); p.keyboard.press('Enter'); p.wait_for_timeout(3000)
    print(p.url, p.evaluate("Object.keys(localStorage)"))
    p.reload(); p.wait_for_timeout(3000)
    print("after reload", p.url, p.evaluate("Object.keys(localStorage)"))
    b.close()
