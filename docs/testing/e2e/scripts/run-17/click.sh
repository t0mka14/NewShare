#!/bin/sh
# Usage: click.sh X Y  -- real OS click at IMAGE coordinates of a Capture-Screen window screenshot (client = image - (8,31) for the 1000x820 window; override OFFX/OFFY)
R="$(pwd -W)/docs/testing/e2e"
X=$(( $1 - ${OFFX:-8} )); Y=$(( $2 - ${OFFY:-31} ))
powershell -NoProfile -File "$R/tools/Drag-InWindow.ps1" -FromX $X -FromY $Y -ToX $X -ToY $Y -Steps 1 -StepDelayMs 5 | tail -1
