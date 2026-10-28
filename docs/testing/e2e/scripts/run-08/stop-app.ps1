# Stops the app (plan 3.4). Usage: .\stop-app.ps1 [-LauncherPid <powershell running start-app.ps1>]
param([int]$LauncherPid = 0)
$root = Resolve-Path "$PSScriptRoot\..\..\..\..\.."
$pf = Join-Path $root 'app\build\run\main\main.pid'
if ($LauncherPid) { taskkill /PID $LauncherPid /T /F 2>&1 | Out-Null }
if (Test-Path $pf) {
  $line = (Get-Content $pf | Where-Object { $_ -like 'pid=*' } | Select-Object -First 1)
  if ($line) { $id = [int]($line -replace 'pid=',''); Stop-Process -Id $id -Force -ErrorAction SilentlyContinue; Start-Sleep 2
    "app pid $id alive: $([bool](Get-Process -Id $id -ErrorAction SilentlyContinue))" }
  Remove-Item $pf -Force
}
"stopped"
