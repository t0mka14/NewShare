# Minimizes the SHARE window, waits, restores it (ShowWindow SW_MINIMIZE / SW_RESTORE). Usage: minimize-restore.ps1 [-Seconds 5] [-WindowTitle SHARE]
param([int]$Seconds = 5, [string]$WindowTitle = 'SHARE')
Add-Type @'
using System; using System.Runtime.InteropServices;
public static class Mn { [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
 [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h); [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h); }
'@
$p = Get-Process | Where-Object { $_.MainWindowTitle -like "$WindowTitle*" -and $_.MainWindowHandle -ne 0 } | Select-Object -First 1
if (-not $p) { Write-Error 'window not found'; exit 1 }
$h = $p.MainWindowHandle
[Mn]::ShowWindow($h, 6) | Out-Null
"minimized: $([Mn]::IsIconic($h))"
Start-Sleep $Seconds
[Mn]::ShowWindow($h, 9) | Out-Null; [Mn]::SetForegroundWindow($h) | Out-Null
Start-Sleep 1
"restored: iconic=$([Mn]::IsIconic($h))"
