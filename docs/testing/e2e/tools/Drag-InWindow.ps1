<#
  Real OS mouse drag inside a window, in that window's CLIENT-area pixel coordinates
  (the same coordinates as the compose-hot-reload semantic tree bounds, at 100 % scaling).
  Used for the editor boundary handles, which only react to pointer drags.

  Example: drag from the centre of a handle at (412, 300) to x = 470:
    .\Drag-InWindow.ps1 -FromX 412 -FromY 300 -ToX 470
  -InfoOnly prints the window's client origin and size and does nothing else.
#>
param(
    [int]$FromX, [int]$FromY, [int]$ToX, [int]$ToY = -1,
    [string]$WindowTitle = 'SHARE',
    [int]$Steps = 20, [int]$StepDelayMs = 15,
    [switch]$InfoOnly
)
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class DragNative {
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X; public int Y; }
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L; public int T; public int R; public int B; }
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint d, UIntPtr e);
    public const uint DOWN = 0x0002, UP = 0x0004;
}
'@
$proc = Get-Process | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like "$WindowTitle*" } | Select-Object -First 1
if (-not $proc) { throw "No window whose title starts with '$WindowTitle'" }
$h = $proc.MainWindowHandle
$origin = New-Object DragNative+POINT
[void][DragNative]::ClientToScreen($h, [ref]$origin)
$rect = New-Object DragNative+RECT
[void][DragNative]::GetClientRect($h, [ref]$rect)
"window '$($proc.MainWindowTitle)' pid $($proc.Id): client origin ($($origin.X),$($origin.Y)) size $($rect.R)x$($rect.B)"
if ($InfoOnly) { return }
if ($ToY -lt 0) { $ToY = $FromY }

[void][DragNative]::ShowWindow($h, 9)
[void][DragNative]::SetForegroundWindow($h)
Start-Sleep -Milliseconds 300
$sx = $origin.X + $FromX; $sy = $origin.Y + $FromY
$ex = $origin.X + $ToX;   $ey = $origin.Y + $ToY
[void][DragNative]::SetCursorPos($sx, $sy); Start-Sleep -Milliseconds 150
[DragNative]::mouse_event([DragNative]::DOWN, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 100
for ($i = 1; $i -le $Steps; $i++) {
    $x = [int]($sx + ($ex - $sx) * $i / $Steps); $y = [int]($sy + ($ey - $sy) * $i / $Steps)
    [void][DragNative]::SetCursorPos($x, $y); Start-Sleep -Milliseconds $StepDelayMs
}
Start-Sleep -Milliseconds 100
[DragNative]::mouse_event([DragNative]::UP, 0, 0, 0, [UIntPtr]::Zero)
"dragged screen ($sx,$sy) -> ($ex,$ey)"
