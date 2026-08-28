param([int]$TimeoutSeconds = 300)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$runtime = if ($env:IDAX_LEDGER_RUNTIME_PATH) { [IO.Path]::GetFullPath($env:IDAX_LEDGER_RUNTIME_PATH) } else { Join-Path $root ".runtime" }
& (Join-Path $PSScriptRoot 'Test-PrivateNetworkConfiguration.ps1') -RuntimePath $runtime

foreach ($number in 1..3) {
    $node = "validator-{0:d2}" -f $number
    foreach ($relative in @("config/xrpld.cfg", "config/validators.txt", "secrets/validator-keys.json")) {
        $required = Join-Path (Join-Path $runtime $node) $relative
        if (-not (Test-Path -LiteralPath $required)) {
            throw "Missing $required. Run Initialize-PrivateNetwork.ps1 explicitly before start."
        }
    }
}

docker compose --project-directory $root up -d
if ($LASTEXITCODE -ne 0) { throw "Could not start the XRPL private network" }
& (Join-Path $PSScriptRoot "Test-PrivateNetwork.ps1") -TimeoutSeconds $TimeoutSeconds
