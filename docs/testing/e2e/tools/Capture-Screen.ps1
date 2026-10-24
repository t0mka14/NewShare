<#
.SYNOPSIS
  Saves a PNG of the whole desktop, or of one window, for an E2E finding.

.DESCRIPTION
  Fallback for when the compose-hot-reload MCP `take_screenshot` is unavailable, and the only
  way to capture the web browser and the native Windows dialogs. Must run in the same desktop
  session as the app (the RDP session), which is where Claude Code's shells run.

.EXAMPLE
  .\Capture-Screen.ps1 -Path reports\2026-10-23-run-01\screenshots\F01-zero-level.png
  .\Capture-Screen.ps1 -Path out.png -WindowTitle "Speech examination"
#>
param(
    [Parameter(Mandatory)] [string] $Path,
    # Substring of the window title; omit for the whole virtual desktop.
    [string] $WindowTitle
)

Add-Type -AssemblyName System.Drawing, System.Windows.Forms
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class E2eWin {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
}
'@
[E2eWin]::SetProcessDPIAware() | Out-Null

if ($WindowTitle) {
    $proc = Get-Process | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like "*$WindowTitle*" } |
        Select-Object -First 1
    if (-not $proc) { throw "No window whose title contains '$WindowTitle'" }
    [E2eWin]::SetForegroundWindow($proc.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 300
    $r = New-Object E2eWin+RECT
    [E2eWin]::GetWindowRect($proc.MainWindowHandle, [ref]$r) | Out-Null
    $bounds = [System.Drawing.Rectangle]::FromLTRB($r.Left, $r.Top, $r.Right, $r.Bottom)
} else {
    $bounds = [System.Windows.Forms.SystemInformation]::VirtualScreen
}

$dir = Split-Path -Parent $Path
if ($dir) { New-Item -ItemType Directory -Force $dir | Out-Null }
$bmp = New-Object System.Drawing.Bitmap $bounds.Width, $bounds.Height
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($bounds.Location, [System.Drawing.Point]::Empty, $bounds.Size)
$bmp.Save((Resolve-Path -LiteralPath $dir).Path + '\' + (Split-Path -Leaf $Path), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
"saved $Path ($($bounds.Width)x$($bounds.Height))"
