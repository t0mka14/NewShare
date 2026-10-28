# Pauses or resumes the signal source (wmplayer) by suspending/resuming its process (no GUI needed).
# Usage: .\player.ps1 -Action Pause|Resume [-Name wmplayer]
param([Parameter(Mandatory)][ValidateSet('Pause','Resume')][string]$Action, [string]$Name = 'wmplayer')
Add-Type -Namespace Nt -Name P -MemberDefinition @'
[System.Runtime.InteropServices.DllImport("ntdll.dll")] public static extern int NtSuspendProcess(System.IntPtr h);
[System.Runtime.InteropServices.DllImport("ntdll.dll")] public static extern int NtResumeProcess(System.IntPtr h);
'@
foreach ($p in Get-Process $Name -ErrorAction Stop) {
  if ($Action -eq 'Pause') { $r = [Nt.P]::NtSuspendProcess($p.Handle) } else { $r = [Nt.P]::NtResumeProcess($p.Handle) }
  "$Action $($p.Name) pid $($p.Id): status $r"
}
