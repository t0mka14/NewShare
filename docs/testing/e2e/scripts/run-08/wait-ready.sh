#!/bin/sh
# Waits (max ~2 min) for app/build/run/main/main.pid and "Waiting for changes"; prints the app pid. Run from the repo root.
for i in $(seq 1 40); do
  [ -f app/build/run/main/main.pid ] && tail -1 app/build/run/main/main.chr.log | grep -q Waiting && break
  sleep 3
done
grep '^pid=' app/build/run/main/main.pid
