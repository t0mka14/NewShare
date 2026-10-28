# run-04 scripts
- start-app.ps1 -Log <hotrun.log>: starts the app with hot reload (plan 3.4); run via Start-Process powershell -File ... -WindowStyle Minimized. Stop: taskkill /PID <that powershell> /T /F, then Stop-Process the pid in app/build/run/main/main.pid and delete that file.
- Reused unchanged: ../run-02/front.ps1, ../run-03/inspect_zip.py, ../../tools/Drag-InWindow.ps1.
- Editor drag used: Drag-InWindow.ps1 -FromX 766 -FromY 200 -ToX 700 (stop handle at tree x 754 + 12, grabbed at its top end).
- The run-03 session was compared with a find -type f / sha256sum listing before and after (logs/run03-session-*.txt).
