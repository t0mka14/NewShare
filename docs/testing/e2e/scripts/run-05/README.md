# run-05 scripts
- check-env.sh <out.png>: precondition check (OBS camera frame + CABLE level + processes). View the png: OBS logo with a crossed-out camera = virtual camera not started.
- Camera-loss step: find the app's ffmpeg with Get-CimInstance Win32_Process -Filter "Name='ffmpeg.exe'" (ParentProcessId = pid in app/build/run/main/main.pid) and Stop-Process -Id <pid> -Force.
- Reused: ../run-04/start-app.ps1, ../run-02/front.ps1, ../run-03/inspect_zip.py (python -I inspect_zip.py <zip> <new empty dir>).
