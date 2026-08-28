param([switch]$ConfirmNetworkDataLoss)
$ErrorActionPreference = "Stop"
if (-not $ConfirmNetworkDataLoss) {
    throw "Reset destroys validator identities, NodeDB, SQLite history, logs and generated config. Re-run with -ConfirmNetworkDataLoss."
}

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$runtime = Join-Path $root ".runtime"
$resolvedRoot = [System.IO.Path]::GetFullPath($root)
$resolvedRuntime = [System.IO.Path]::GetFullPath($runtime)
if (-not $resolvedRuntime.StartsWith($resolvedRoot + [System.IO.Path]::DirectorySeparatorChar) -or
        [System.IO.Path]::GetFileName($resolvedRuntime) -ne ".runtime") {
    throw "Refusing unsafe reset target: $resolvedRuntime"
}

docker compose --project-directory $root down --remove-orphans
if ($LASTEXITCODE -ne 0) { throw "Could not stop containers before reset" }
if (Test-Path -LiteralPath $resolvedRuntime) {
    Remove-Item -LiteralPath $resolvedRuntime -Recurse -Force
}
Write-Output "Destroyed local XRPL network data at $resolvedRuntime. Run Initialize-PrivateNetwork.ps1 to create a new network."
