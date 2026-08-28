param(
    [int]$TimeoutSeconds = 300,
    [int]$LongStopSeconds = 30,
    [uint32]$ExpectedNetworkId = 2181844733,
    [switch]$ResetNetwork
)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$containers = @("idax-ledger-validator-01", "idax-ledger-validator-02", "idax-ledger-validator-03")

function Invoke-ServerInfo([string]$container) {
    $raw = docker exec $container xrpld server_info 2>$null
    if ($LASTEXITCODE -ne 0) { throw "server_info failed for $container" }
    return (($raw -join "`n") | ConvertFrom-Json).result.info
}

function Wait-Network([string]$stage, [long]$minimumLedger = 0) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $states = @()
        $ready = $true
        foreach ($container in $containers) {
            try {
                $info = Invoke-ServerInfo $container
                $states += [pscustomobject]@{
                    container = $container
                    state = $info.server_state
                    peers = [int]$info.peers
                    ledger = [long]$info.validated_ledger.seq
                    hash = [string]$info.validated_ledger.hash
                    completeLedgers = [string]$info.complete_ledgers
                    validator = [string]$info.pubkey_validator
                    networkId = [long]$info.network_id
                }
                if ($info.server_state -ne "proposing" -or [int]$info.peers -lt 2 -or
                        -not $info.validated_ledger.seq -or $info.complete_ledgers -eq "empty" -or
                        [long]$info.validated_ledger.seq -le $minimumLedger) {
                    $ready = $false
                }
            } catch { $ready = $false; break }
        }
        if (-not $ready) { Start-Sleep -Seconds 5 }
    } while (-not $ready -and (Get-Date) -lt $deadline)
    if (-not $ready) { throw "$stage did not reach proposing consensus with non-empty complete_ledgers before timeout" }
    return @($states)
}

function Get-Ledger([long]$index, [string]$container = "idax-ledger-validator-01") {
    $raw = docker exec $container xrpld ledger $index 2>$null
    if ($LASTEXITCODE -ne 0) { throw "ledger $index query failed for $container" }
    $response = ($raw -join "`n") | ConvertFrom-Json
    if ($response.result.status -ne "success" -or -not $response.result.ledger_hash) {
        throw "Historical ledger $index is unavailable: $($response.result.error_message)"
    }
    return [pscustomobject]@{ index=[long]$response.result.ledger_index; hash=[string]$response.result.ledger_hash }
}

function Assert-Stage([string]$stage, $states, $baselineIdentities, [long]$networkId, $historical, [long]$minimumLedger) {
    $identities = @($states.validator | Sort-Object -Unique)
    if (($identities -join ',') -ne ($baselineIdentities -join ',')) { throw "$stage changed validator identities" }
    if (@($states | Where-Object networkId -ne $networkId).Count -gt 0) { throw "$stage changed network_id" }
    if (@($states | Where-Object ledger -le $minimumLedger).Count -gt 0) { throw "$stage did not progress beyond ledger $minimumLedger" }
    foreach ($state in $states) {
        if (-not (Test-LedgerRange $state.completeLedgers $state.ledger)) {
            throw "$stage complete_ledgers '$($state.completeLedgers)' excludes its validated ledger $($state.ledger)"
        }
    }
    foreach ($container in $containers) {
        $actual = Get-Ledger $historical.index $container
        if ($actual.hash -ne $historical.hash) { throw "$stage changed ledger $($historical.index) hash on $container" }
    }
}

function Test-LedgerRange([string]$ranges, [long]$index) {
    foreach ($part in $ranges.Split(',')) {
        $bounds = $part.Split('-')
        $low = [long]$bounds[0]
        $high = if ($bounds.Count -eq 1) { $low } else { [long]$bounds[1] }
        if ($index -ge $low -and $index -le $high) { return $true }
    }
    return $false
}

if ($ResetNetwork) {
    & (Join-Path $PSScriptRoot "Reset-PrivateNetwork.ps1") -ConfirmNetworkDataLoss
    & (Join-Path $PSScriptRoot "Initialize-PrivateNetwork.ps1")
}

& (Join-Path $PSScriptRoot "Start-PrivateNetwork.ps1") -TimeoutSeconds $TimeoutSeconds
$initial = Wait-Network "initial start"
$baselineIdentities = @($initial.validator | Sort-Object -Unique)
if ($baselineIdentities.Count -ne 3) { throw "Expected three unique validator identities" }
$networkId = [long]$initial[0].networkId
if ($networkId -ne $ExpectedNetworkId) { throw "Expected network_id $ExpectedNetworkId, got $networkId" }

$initialLedger = [long]$initial[0].ledger
Start-Sleep -Seconds 12
$progressed = Wait-Network "initial progression"
if ([long]$progressed[0].ledger -le $initialLedger) { throw "Initial ledger did not progress" }
$historicalIndex = [long]$progressed[0].ledger - 2
$historical = Get-Ledger $historicalIndex

$observations = [ordered]@{ initial=$progressed; historical=$historical }
foreach ($restart in 1..2) {
    & (Join-Path $PSScriptRoot "Stop-PrivateNetwork.ps1")
    if ($restart -eq 1 -and $LongStopSeconds -gt 0) { Start-Sleep -Seconds $LongStopSeconds }
    docker compose --project-directory $root start
    if ($LASTEXITCODE -ne 0) { throw "Restart #$restart failed to start containers" }
    $states = Wait-Network "restart #$restart" ([long]$progressed[0].ledger)
    Assert-Stage "restart #$restart" $states $baselineIdentities $networkId $historical ([long]$progressed[0].ledger)
    $observations["restart$restart"] = $states
    $progressed = $states
}

$containerIdsBefore = @($containers | ForEach-Object { docker inspect --format '{{.Id}}' $_ })
docker compose --project-directory $root up -d --force-recreate
if ($LASTEXITCODE -ne 0) { throw "Container recreation failed" }
$recreated = Wait-Network "container recreation" ([long]$progressed[0].ledger)
$containerIdsAfter = @($containers | ForEach-Object { docker inspect --format '{{.Id}}' $_ })
if (($containerIdsBefore -join ',') -eq ($containerIdsAfter -join ',')) { throw "Containers were not recreated" }
Assert-Stage "container recreation" $recreated $baselineIdentities $networkId $historical ([long]$progressed[0].ledger)
$observations.recreated = $recreated

$resultPath = Join-Path $root ".runtime/persistence-test-result.json"
$observations | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $resultPath -Encoding utf8NoBOM
Write-Output "XRPL persistence PASS: ledger $($historical.index) retained hash $($historical.hash) across two restarts and container recreation."
Write-Output "Evidence: $resultPath"
