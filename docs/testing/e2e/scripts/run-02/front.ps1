# Bring the SHARE window to the front (the terminal usually covers it after a start).
# Usage: .\front.ps1 [-WindowTitle SHARE]
param([string]$WindowTitle = 'SHARE')
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class Fr {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
  [DllImport("user32.dll")] public static extern void keybd_event(byte k, byte s, uint f, UIntPtr e);
}
'@
$p = Get-Process | Where-Object { $_.MainWindowTitle -like "$WindowTitle*" -and $_.MainWindowHandle -ne 0 } | Select-Object -First 1
if (-not $p) { Write-Error 'window not found'; exit 1 }
[Fr]::keybd_event(0x12, 0, 0, [UIntPtr]::Zero); [Fr]::keybd_event(0x12, 0, 2, [UIntPtr]::Zero)
[Fr]::ShowWindow($p.MainWindowHandle, 9) | Out-Null
[Fr]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
"fronted $($p.MainWindowTitle)"
