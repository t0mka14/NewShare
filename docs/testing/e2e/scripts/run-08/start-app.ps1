# Starts start-app.ps1 of run 04 as a minimized background PowerShell and records its PID.
# Usage: .\start-app.ps1 -Log <hotrun.log> [-PidFile <file for the launcher pid>]
param([Parameter(Mandatory)][string]$Log, [string]$PidFile)
$s = Join-Path $PSScriptRoot '..\run-04\start-app.ps1'
$p = Start-Process powershell -ArgumentList '-NoProfile','-File',(Resolve-Path $s),'-Log',$Log -WindowStyle Minimized -PassThru
if ($PidFile) { $p.Id | Out-File $PidFile -Encoding ascii }
"launcher pid $($p.Id)"
