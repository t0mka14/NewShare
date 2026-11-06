"""Run 18: PATCH site 8 ('E2E18 no protocol') config_json {"defaultLanguage":"cs"} so that a protocol-less config serves languages ['cs'] and the app can show the Czech no-protocol line."""
import sys, os, json
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "common"))
from common import api, tok
c,r=api('PATCH','/sites/8',tok('master'),{'config_json':{'defaultLanguage':'cs'}}); print(c,r)
