# Run 02 scripts

- `front.ps1`: brings the SHARE window to the front (the terminal covers it after a start, and `take_screenshot` captures screen pixels).
- `redact.ps1 -Path <png> -Rects "x,y,w,h",...`: blacks out rectangles (token field, installation ID) in a screenshot, in place.
- `computers_page.py [clinicKey=A] [shotName]`: Playwright (venv python, `PYTHONPATH` not needed): opens `#/clinics/<id>/computers` as master and
  screenshots it with installation IDs replaced in the DOM. Env: `E2E_STATE_DIR` (auth.json), `E2E_RUN_DIR`, `E2E_CONTEXT`; clinic id comes from the context file.
