#!/bin/sh
# Run 05 precondition check: grab one frame from the OBS Virtual Camera and measure the cable level.
# Usage: sh check-env.sh <out.png>   then LOOK at the png (placeholder = OBS logo + crossed-out camera = camera not started).
ffmpeg -hide_banner -y -f dshow -i video="OBS Virtual Camera" -frames:v 1 -update 1 "$1" 2>&1 | tail -2
ffmpeg -hide_banner -f dshow -i "audio=CABLE Output (VB-Audio Virtual Cable)" -t 4 -af volumedetect -f null - 2>&1 | grep -E "mean_volume|max_volume"
tasklist | grep -iE "obs|wmplayer"
