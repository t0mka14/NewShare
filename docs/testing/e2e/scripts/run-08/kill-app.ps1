# Kills the app JVM abruptly (simulated crash) and prints the UTC time of the kill. The launcher keeps running.
# Usage: .\kill-app.ps1
$root = Resolve-Path "$PSScriptRoot\..\..\..\..\.."
$pf = Join-Path $root 'app\build\run\main\main.pid'
$id = [int]((Get-Content $pf | Where-Object { $_ -like 'pid=*' } | Select-Object -First 1) -replace 'pid=','')
$t = [DateTime]::UtcNow
Stop-Process -Id $id -Force
"killed pid $id at $($t.ToString('HH:mm:ss.fff'))Z"
Remove-Item $pf -Force -ErrorAction SilentlyContinue
