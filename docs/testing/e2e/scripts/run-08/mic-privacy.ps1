# Read or set the microphone privacy switch (plan 3.1). Usage: .\mic-privacy.ps1 [-Set Allow|Deny]
param([ValidateSet('Allow','Deny')][string]$Set)
$k = 'HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\CapabilityAccessManager\ConsentStore\microphone'
if ($Set) { Set-ItemProperty $k -Name Value -Value $Set }
"microphone privacy = $((Get-ItemProperty $k).Value)"
