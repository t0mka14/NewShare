# Mouse wheel in the SHARE window at CLIENT coords. Usage: .\wheel.ps1 -X 400 -Y 300 -Clicks -5   (negative = scroll down; one click = 120 delta)
param([int]$X, [int]$Y, [int]$Clicks = -3)
Add-Type @'
using System; using System.Runtime.InteropServices;
public static class Wh {
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X; public int Y; }
  [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, int d, UIntPtr e);
}
'@
$p = Get-Process | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like 'SHARE*' } | Select-Object -First 1
$o = New-Object Wh+POINT; [void][Wh]::ClientToScreen($p.MainWindowHandle, [ref]$o)
[void][Wh]::SetForegroundWindow($p.MainWindowHandle); Start-Sleep -Milliseconds 200
[void][Wh]::SetCursorPos($o.X + $X, $o.Y + $Y); Start-Sleep -Milliseconds 150
for ($i = 0; $i -lt [Math]::Abs($Clicks); $i++) { [Wh]::mouse_event(0x0800, 0, 0, [Math]::Sign($Clicks) * 120, [UIntPtr]::Zero); Start-Sleep -Milliseconds 60 }
"wheel $Clicks at client ($X,$Y)"
