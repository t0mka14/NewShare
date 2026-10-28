# Run 07 step 7: types a clinic token (read from the secrets file, never printed) into the focused field of the SHARE window via the clipboard (Ctrl+A, Ctrl+V with keybd_event), then clears the clipboard.
# Usage: .\paste_token.ps1 -Key E2E-1026-Clinic-B [-Secrets <path>]   (focus the field first, e.g. with MCP click on settings.siteTokenField)
param([Parameter(Mandatory)][string]$Key, [string]$Secrets = (Join-Path $PSScriptRoot '..\..\reports\2026-10-26-secrets.json'))
$t = (Get-Content -Raw -Encoding UTF8 $Secrets | ConvertFrom-Json).$Key
if (-not $t) { Write-Error "no secret $Key"; exit 1 }
& (Join-Path $PSScriptRoot '..\run-02\front.ps1') | Out-Null
Add-Type @'
using System; using System.Runtime.InteropServices;
public static class Kb { [DllImport("user32.dll")] public static extern void keybd_event(byte k, byte s, uint f, UIntPtr e);
 public static void Combo(byte key) { keybd_event(0x11,0,0,UIntPtr.Zero); keybd_event(key,0,0,UIntPtr.Zero); keybd_event(key,0,2,UIntPtr.Zero); keybd_event(0x11,0,2,UIntPtr.Zero); } }
'@
Set-Clipboard -Value $t
Start-Sleep -Milliseconds 400
[Kb]::Combo(0x41); Start-Sleep -Milliseconds 300   # Ctrl+A
[Kb]::Combo(0x56); Start-Sleep -Milliseconds 600   # Ctrl+V
Set-Clipboard -Value ' '
"pasted token for $Key ($($t.Length) chars)"
