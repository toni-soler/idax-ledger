param(
    [uri]$RpcUrl = "http://localhost:5006",
    [uint32]$NetworkId = 2181844733,
    [long]$FundingDrops = 1000000000,
    [string]$RuntimePath
)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if (-not $RuntimePath) {$RuntimePath=if($env:IDAX_LEDGER_RUNTIME_PATH){$env:IDAX_LEDGER_RUNTIME_PATH}else{Join-Path $root '.runtime'}}
$runtime=[IO.Path]::GetFullPath($RuntimePath)
$secretDirectory = Join-Path $runtime "anchoring/secrets"
$seedPath = Join-Path $secretDirectory "anchor.seed"
$addressPath = Join-Path $runtime "anchoring/address.txt"

function Invoke-Xrpl([string]$method, [hashtable]$parameters) {
    $body = @{ method = $method; params = @($parameters) } | ConvertTo-Json -Depth 12 -Compress
    $response = Invoke-RestMethod -Uri $RpcUrl -Method Post -ContentType "application/json" -Body $body
    if ($response.result.error) { throw "$method failed: $($response.result.error_message)" }
    return $response.result
}

New-Item -ItemType Directory -Force -Path $secretDirectory | Out-Null
if (Test-Path -LiteralPath $seedPath) {
    $seed = (Get-Content -Raw -LiteralPath $seedPath).Trim()
    $wallet = Invoke-Xrpl "wallet_propose" @{ seed = $seed }
} else {
    $wallet = Invoke-Xrpl "wallet_propose" @{}
    $seed = [string]$wallet.master_seed
    Set-Content -LiteralPath $seedPath -Value $seed -Encoding ascii -NoNewline
}
$address = [string]$wallet.account_id
Set-Content -LiteralPath $addressPath -Value $address -Encoding ascii

try {
    $existing = Invoke-Xrpl "account_info" @{ account = $address; ledger_index = "validated" }
    if ($existing.account_data) {
        Write-Output "IDAX Ledger Anchoring Account: $address"
        Write-Output "Already funded; secret remains under the selected runtime anchoring/secrets directory"
        exit 0
    }
} catch { }

$rootSeed = $env:IDAX_LEDGER_FUNDING_SEED
if(-not $rootSeed){throw 'Set IDAX_LEDGER_FUNDING_SEED in the process environment to fund a new account'}
$rootWallet=Invoke-Xrpl "wallet_propose" @{seed=$rootSeed}
$rootAddress=[string]$rootWallet.account_id
$rootInfo = Invoke-Xrpl "account_info" @{ account = $rootAddress; ledger_index = "validated" }
$server = Invoke-Xrpl "server_info" @{}
$lastLedger = [uint32]$server.info.validated_ledger.seq + 20
$transaction = @{
    TransactionType = "Payment"
    Account = $rootAddress
    Destination = $address
    Amount = "$FundingDrops"
    Fee = "10"
    Sequence = [uint32]$rootInfo.account_data.Sequence
    LastLedgerSequence = $lastLedger
    NetworkID = $NetworkId
    Flags = [uint32]2147483648
}
$signed = Invoke-Xrpl "sign" @{ secret = $rootSeed; tx_json = $transaction; offline = $true }
$submitted = Invoke-Xrpl "submit" @{ tx_blob = [string]$signed.tx_blob }
if ($submitted.engine_result -ne "tesSUCCESS") { throw "Funding submit failed: $($submitted.engine_result)" }
$hash = [string]$submitted.tx_json.hash
$deadline = (Get-Date).AddSeconds(90)
do {
    Start-Sleep -Seconds 2
    try { $validated = Invoke-Xrpl "tx" @{ transaction = $hash } } catch { $validated = $null }
} while ((-not $validated.validated) -and (Get-Date) -lt $deadline)
if (-not $validated.validated -or $validated.meta.TransactionResult -ne "tesSUCCESS") {
    throw "Funding transaction was not validated successfully"
}
Write-Output "IDAX Ledger Anchoring Account: $address"
Write-Output "Funded with $FundingDrops private-network drops; secret remains under the selected runtime anchoring/secrets directory"
