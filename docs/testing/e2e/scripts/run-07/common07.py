"""Shared helpers for run 07 (quarantine and computer assignment). Reuses run-06/common06.py (redaction, REPO, context, hashing).
Env: E2E_STATE_DIR, PYTHONPATH=scripts/common, E2E_RUN_DIR (default reports/2026-10-26-run-07-quarantine)."""
import sys, os
os.environ.setdefault("E2E_RUN_DIR", "")
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "run-06"))
_rd = os.environ.get("E2E_RUN_DIR") or None
if not _rd:
    os.environ["E2E_RUN_DIR"] = os.path.abspath(os.path.join(HERE, "..", "..", "reports", "2026-10-26-run-07-quarantine"))
from common06 import *   # api, tok, BASE, context(), redact(), sha256_file, REPO, LOGS, download, MASK_JS
RUN07 = os.environ["E2E_RUN_DIR"]

def session07(patient=None):
    """The run-07 session of the context file (patient code from arg/env E2E_PATIENT, default: the last entry with run == '07')."""
    patient = patient or os.environ.get("E2E_PATIENT")
    ss = [s for s in context()["sessions"] if s.get("run") == "07" and (not patient or s["patient"] == patient)]
    return ss[-1]

def own_installation_id():
    """Installation ID of the CURRENT app/data (path via E2E_SETTINGS); never printed."""
    p = os.environ.get("E2E_SETTINGS", os.path.join(REPO, "app", "data", "config", "settings.json"))
    return json.load(open(p, encoding="utf-8-sig"))["installationId"]

def red(text):
    """redact() plus the current installation ID in short form."""
    t = redact(text)
    try:
        iid = own_installation_id(); t = t.replace(iid, "<installationId>").replace(iid[:8], "<installationId>")
    except Exception: pass
    return t
