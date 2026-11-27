#!/bin/sh
# Usage: shot.sh <name>  -- brings Recita to front and saves screenshots/<name>.png of the Recita window
R="$(pwd -W)/docs/testing/e2e"
powershell -NoProfile -File "$R/scripts/run-02/front.ps1" >/dev/null
sleep 0.5
powershell -NoProfile -File "$R/tools/Capture-Screen.ps1" -Path "$R/reports/2026-11-05-run-17-questionnaire-rules/screenshots/$1.png" -WindowTitle Recita | tail -1
