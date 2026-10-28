# Moves the SHARE window's top-left corner (outer) to X,Y without resizing. Usage: move-window.ps1 [-X 0] [-Y 0] [-WindowTitle SHARE]
param([int]$X = 0, [int]$Y = 0, [string]$WindowTitle = 'SHARE')
Add-Type @'
using System; using System.Runtime.InteropServices;
public static class Mv { [StructLayout(LayoutKind.Sequential)] public struct R { public int L,T,Rt,B; }
 [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out R r);
 [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h, int x, int y, int w, int hh, bool rep); }
'@
$p = Get-Process | Where-Object { $_.MainWindowTitle -like "$WindowTitle*" -and $_.MainWindowHandle -ne 0 } | Select-Object -First 1
if (-not $p) { Write-Error 'window not found'; exit 1 }
$r = New-Object Mv+R; [Mv]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null
[Mv]::MoveWindow($p.MainWindowHandle, $X, $Y, $r.Rt - $r.L, $r.B - $r.T, $true) | Out-Null
"moved to $X,$Y size $($r.Rt - $r.L)x$($r.B - $r.T)"
