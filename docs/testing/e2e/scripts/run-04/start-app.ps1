# Start the app with hot reload (plan 3.4). Usage: .\start-app.ps1 -Log <path to hotrun.log>
param([Parameter(Mandatory)][string]$Log)
Remove-Item Env:NoDefaultCurrentDirectoryInExePath -ErrorAction SilentlyContinue
$env:JAVA_HOME = 'C:\Users\koubato2\.jdks\ms-25.0.4.1'
Set-Location (Resolve-Path "$PSScriptRoot\..\..\..\..\..")
.\gradlew.bat :app:hotRun --mainClass=org.example.app.MainKt --auto *> $Log
