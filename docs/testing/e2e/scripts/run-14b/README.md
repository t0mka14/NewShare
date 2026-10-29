# Run 14b scripts (live re-check, web commit 69f956e, 2026-10-29)
Run with `. ./env.sh` then `PYTHONPATH="$PYTHONPATH;<scripts>\common" "$PY" cX.py` (helpers from `../common/`). Nothing is saved; `c2.py` compares API snapshots of protocol 3 before and after.
| Script | Check |
|---|---|
| `c1.py` | F-137: new protocol -> Edit fields -> Add custom field -> Cancel |
| `c1b.py` | F-137: toggle "required" on Participant code, Esc, reopen |
| `c2.py` | F-139: info page (Edit) and Add Instructions, type, Cancel; API unchanged check |
