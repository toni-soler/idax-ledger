param([int]$TimeoutSeconds = 300, [int]$ShutdownTimeoutSeconds = 60)
$ErrorActionPreference = "Stop"
& (Join-Path $PSScriptRoot "Stop-PrivateNetwork.ps1") -TimeoutSeconds $ShutdownTimeoutSeconds
& (Join-Path $PSScriptRoot "Start-PrivateNetwork.ps1") -TimeoutSeconds $TimeoutSeconds
