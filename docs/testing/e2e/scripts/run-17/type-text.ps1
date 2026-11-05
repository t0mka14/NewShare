# Types text into the focused SHARE window with SendInput (KEYEVENTF_UNICODE, one key-down/up per char). Usage: .\type-text.ps1 -Text "E2E17-001" [-Mode unicode|scan]
param([Parameter(Mandatory)][string]$Text, [string]$Mode = 'unicode')
Add-Type @'
using System; using System.Runtime.InteropServices;
public static class Ti {
  [StructLayout(LayoutKind.Sequential)] public struct KEYBDINPUT { public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }
  [StructLayout(LayoutKind.Explicit)] public struct INPUT { [FieldOffset(0)] public uint type; [FieldOffset(8)] public KEYBDINPUT ki; [FieldOffset(32)] public long pad; }
  [DllImport("user32.dll", SetLastError=true)] public static extern uint SendInput(uint n, INPUT[] i, int size);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint c, uint t);
  [DllImport("user32.dll")] public static extern short VkKeyScan(char c);
  public static uint Send(char c, bool unicode) {
    INPUT[] a = new INPUT[2];
    for (int k = 0; k < 2; k++) { a[k].type = 1; a[k].ki.dwFlags = (unicode ? 4u : 8u) | (k == 1 ? 2u : 0u);
      a[k].ki.wScan = unicode ? (ushort)c : (ushort)MapVirtualKey((uint)(VkKeyScan(c) & 0xff), 0); }
    return SendInput(2, a, Marshal.SizeOf(typeof(INPUT)));
  }
}
'@
$p = Get-Process | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like 'SHARE*' } | Select-Object -First 1
[Ti]::SetForegroundWindow($p.MainWindowHandle) | Out-Null; Start-Sleep -Milliseconds 300
$n = 0; foreach ($c in $Text.ToCharArray()) { $n += [Ti]::Send($c, ($Mode -eq 'unicode')); Start-Sleep -Milliseconds 40 }
"sent $n input events for $($Text.Length) chars"
