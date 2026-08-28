param([int]$TimeoutSeconds = 240)
$ErrorActionPreference = "Stop"
$containers = @("idax-ledger-validator-01", "idax-ledger-validator-02", "idax-ledger-validator-03")
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    $ready = $true
    $states = @()
    foreach ($container in $containers) {
        $raw = docker exec $container xrpld server_info 2>$null
        if ($LASTEXITCODE -ne 0) { $ready = $false; break }
        $json = ($raw -join "`n") | ConvertFrom-Json
        $info = $json.result.info
        $states += [pscustomobject]@{ container=$container; state=$info.server_state; peers=[int]$info.peers; ledger=[long]$info.validated_ledger.seq; completeLedgers=$info.complete_ledgers; validator=$info.pubkey_validator }
        if ($info.server_state -ne "proposing" -or [int]$info.peers -lt 2 -or -not $info.validated_ledger.seq -or $info.complete_ledgers -eq "empty") { $ready = $false }
    }
    if (-not $ready) { Start-Sleep -Seconds 5 }
} while (-not $ready -and (Get-Date) -lt $deadline)
if (-not $ready) { throw "XRPL validators did not reach healthy consensus before timeout" }
if (($states.validator | Sort-Object -Unique).Count -ne 3) { throw "Validator identities are not unique" }
$firstLedger = $states[0].ledger
Start-Sleep -Seconds 8
$next = ((docker exec $containers[0] xrpld server_info) -join "`n" | ConvertFrom-Json).result.info.validated_ledger.seq
if ([long]$next -le [long]$firstLedger) { throw "Validated ledger did not progress" }
$states | Format-Table -AutoSize
Write-Output "Consensus healthy; validated ledger progressed from $firstLedger to $next."
