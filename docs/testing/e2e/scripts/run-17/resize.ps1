# Resize (outer window size, as in the 1000x820 default) and move the SHARE window. Usage: .\resize.ps1 -W 960 -H 720 [-X 0 -Y 0]
param([int]$W, [int]$H, [int]$X = 0, [int]$Y = 0)
Add-Type @'
using System; using System.Runtime.InteropServices;
public static class Rs {
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int w, int hh, uint f);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
}
'@
$p = Get-Process | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like 'SHARE*' } | Select-Object -First 1
[void][Rs]::ShowWindow($p.MainWindowHandle, 9); [void][Rs]::SetForegroundWindow($p.MainWindowHandle)
[void][Rs]::SetWindowPos($p.MainWindowHandle, [IntPtr]::Zero, $X, $Y, $W, $H, 0x0040)
"resized to ${W}x${H} at ($X,$Y)"
