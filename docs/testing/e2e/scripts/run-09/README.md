# Run 09 scripts (window sizes and layout)

- `tree_check.py <tree dir> <screen> [--sizes W1,W2,...] [--baseline W4]`: checks C1 (control inside the client area, or reachable in a dump taken after scrolling), C2 (control shrunk to under half the baseline height, buttons also width; or under 6 px) and C3 (partial overlap of two controls) on semantic-tree dumps named `<screen>_<size>.json` (plus `<screen>_<size>_s<n>.json` for dumps after a scroll). `python -I` is not needed; plain python 3. Output: pass/FAIL per size and one line per problem.
  Limits: it only knows controls present in the dump; a W2 phonation dump without a scrolled copy reports the "Play example" button as unreachable although the instruction box scrolls (judge those by hand); handle height scales with the window and shows up as a false "squashed" in the editor.
- `redact_tree.py <file>...`: blanks `editableText` and token/ID-looking strings in a tree dump in place (Settings dumps).
- `move-window.ps1 [-X 0] [-Y 0]`: moves the SHARE window (needed for W6, because the window opens at 48,48 and would run off the screen).
- `minimize-restore.ps1 [-Seconds 5]`: ShowWindow minimise, wait, restore (stands in for the title-bar click, resize check 4).

How the run was driven: `resize_window`, then `get_semantic_tree` (the MCP tool returns the tree to the agent only; the dumps in `reports/.../logs/trees/` were written by hand from that output, trimmed to the controls), `scripts/run-02/front.ps1` before every `take_screenshot`. `scripts/run-02/redact.ps1` takes ONE rectangle per call in this setup ("-Rects a,b" only blacked out the first).
Sizes: W1 640x480, W2 800x600, W3 1024x768, W4 1280x800, W5 1366x768, W6 = work area 1512x876 (check with `[System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea`). The client area is 16 px narrower and 39 px lower than the outer size.
