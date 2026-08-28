param([string]$Image = "xrpllabsofficial/xrpld:3.3.0", [uint32]$NetworkId = 2181844733)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$runtime = Join-Path $root ".runtime"
New-Item -ItemType Directory -Force -Path $runtime | Out-Null

$publicKeys = @()
for ($number = 1; $number -le 3; $number++) {
    $node = "validator-{0:d2}" -f $number
    $nodeRoot = Join-Path $runtime $node
    $secrets = Join-Path $nodeRoot "secrets"
    foreach ($directory in @($secrets, (Join-Path $nodeRoot "config"), (Join-Path $nodeRoot "data"), (Join-Path $nodeRoot "log"))) {
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
    }
    $keyFile = Join-Path $secrets "validator-keys.json"
    if (-not (Test-Path -LiteralPath $keyFile)) {
        docker run --rm --platform linux/amd64 -v "${secrets}:/keys" --entrypoint validator-keys $Image create_keys --keyfile /keys/validator-keys.json
        if ($LASTEXITCODE -ne 0) { throw "Could not generate keys for $node" }
    }
    $publicKeys += (Get-Content -Raw -LiteralPath $keyFile | ConvertFrom-Json).public_key
}

$validators = "[validators]`n" + ($publicKeys -join "`n") + "`n"
$template = Get-Content -Raw -LiteralPath (Join-Path $root "config/xrpld.cfg.template")
for ($number = 1; $number -le 3; $number++) {
    $node = "validator-{0:d2}" -f $number
    $nodeRoot = Join-Path $runtime $node
    $secrets = Join-Path $nodeRoot "secrets"
    $tokenOutput = docker run --rm --platform linux/amd64 -v "${secrets}:/keys" --entrypoint validator-keys $Image create_token --keyfile /keys/validator-keys.json
    if ($LASTEXITCODE -ne 0) { throw "Could not create token for $node" }
    $allTokenLines = @($tokenOutput)
    $headerIndex = [Array]::IndexOf($allTokenLines, "[validator_token]")
    if ($headerIndex -lt 0) { throw "validator-keys output did not contain a token for $node" }
    $tokenLines = @()
    for ($line = $headerIndex + 1; $line -lt $allTokenLines.Count -and $allTokenLines[$line].Trim(); $line++) {
        $tokenLines += $allTokenLines[$line].Trim()
    }
    $config = $template.Replace("__NETWORK_ID__", "$NetworkId").Replace("__VALIDATOR_TOKEN__", ($tokenLines -join "`n"))
    $configPath = Join-Path $nodeRoot "config/xrpld.cfg"
    Set-Content -LiteralPath $configPath -Value $config -Encoding utf8NoBOM
    Set-Content -LiteralPath (Join-Path $nodeRoot "config/validators.txt") -Value $validators -Encoding utf8NoBOM
}
Write-Output "Initialized XRPL network $NetworkId with three distinct persistent validator identities."
