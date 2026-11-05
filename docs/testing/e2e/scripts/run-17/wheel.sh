#!/bin/sh
# Usage: wheel.sh IMGX IMGY CLICKS (image coords like click.sh)
R="$(pwd -W)/docs/testing/e2e"
powershell -NoProfile -File "$R/scripts/run-17/wheel.ps1" -X $(( $1 - 8 )) -Y $(( $2 - 31 )) -Clicks $3
