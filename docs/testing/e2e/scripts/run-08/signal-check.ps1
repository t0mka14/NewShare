# Measures the signal on the virtual cable (plan 3.1.3). Usage: .\signal-check.ps1 [-Device "CABLE Output (VB-Audio Virtual Cable)"] [-Seconds 4]
param([string]$Device = 'CABLE Output (VB-Audio Virtual Cable)', [int]$Seconds = 4)
ffmpeg -hide_banner -f dshow -i "audio=$Device" -t $Seconds -af volumedetect -f null - 2>&1 | Select-String 'mean_volume','max_volume'
