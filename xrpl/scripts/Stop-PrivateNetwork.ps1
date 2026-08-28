param([int]$TimeoutSeconds = 60)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

docker compose --project-directory $root stop --timeout $TimeoutSeconds
if ($LASTEXITCODE -ne 0) { throw "Could not stop the XRPL private network gracefully" }
