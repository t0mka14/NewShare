# Black out rectangles in a PNG, in place. Rects as "x,y,w,h" strings (client pixels of the screenshot).
# Usage: .\redact.ps1 -Path shot.png -Rects "32,518,920,56","32,709,371,24"
param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string[]]$Rects)
Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Image]::FromFile((Resolve-Path $Path))
$bmp = New-Object System.Drawing.Bitmap $src
$src.Dispose()
$g = [System.Drawing.Graphics]::FromImage($bmp)
foreach ($r in $Rects) {
  $n = $r.Split(',') | ForEach-Object { [int]$_ }
  $g.FillRectangle([System.Drawing.Brushes]::Black, $n[0], $n[1], $n[2], $n[3])
}
$g.Dispose()
$full = (Resolve-Path $Path).Path
$bmp.Save($full + '.tmp', [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Move-Item -Force ($full + '.tmp') $full
"redacted $($Rects.Count) rect(s) in $Path"
