# run-03 scripts
- `endpoint-volume.ps1`: lists Windows audio endpoints with volume/mute; `-Set 1.0 -Name "CABLE Input"` sets one (`-Flow 1` = capture).
- `inspect_zip.py`: read-only check of a processed session ZIP: `python -I inspect_zip.py <zip> <new empty extract dir>`.
Reused: `../run-02/front.ps1` (bring SHARE to front before take_screenshot).
