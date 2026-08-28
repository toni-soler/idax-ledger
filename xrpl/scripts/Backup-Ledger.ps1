param(
  [Parameter(Mandatory=$true)][string]$BackupRoot,
  [string]$PgHost='localhost',[int]$PgPort=5432,[string]$PgDatabase='idaxdb',[string]$PgUser='idax_backend',
  [switch]$LeaveStopped
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtime=(Resolve-Path $(if($env:IDAX_LEDGER_RUNTIME_PATH){$env:IDAX_LEDGER_RUNTIME_PATH}else{Join-Path $repo '.runtime'})).Path
$target=[IO.Path]::GetFullPath($BackupRoot)
if($target -eq $repo -or $repo.StartsWith($target,[StringComparison]::OrdinalIgnoreCase)){throw 'BackupRoot must be outside the XRPL repository'}
if(-not $env:IDAX_LEDGER_BACKUP_PASSPHRASE){throw 'Set IDAX_LEDGER_BACKUP_PASSPHRASE; it is read only from the process environment'}
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss';$backup=Join-Path $target "idax-ledger-$stamp";New-Item -ItemType Directory -Path $backup -Force|Out-Null
$wasRunning=@(docker compose -f (Join-Path $repo 'docker-compose.yml') ps --status running -q).Count -gt 0
try{
  if($wasRunning){& (Join-Path $PSScriptRoot 'Stop-PrivateNetwork.ps1')}
  $pg=Join-Path $backup 'postgres-idax-ledger.dump'; & pg_dump -h $PgHost -p $PgPort -U $PgUser -d $PgDatabase -Fc -n idax_ledger -f $pg; if($LASTEXITCODE){throw 'pg_dump failed'}
  $public=Join-Path $backup 'xrpl-public.tar.gz'
  Push-Location $runtime; try{& tar -czf $public validator-01/data validator-02/data validator-03/data validator-01/log validator-02/log validator-03/log persistence-test-result.json; if($LASTEXITCODE){throw 'public XRPL archive failed'}}finally{Pop-Location}
  $secretPlain=Join-Path $backup '.secrets.tar';$secretEncrypted=Join-Path $backup 'xrpl-secrets.tar.enc'
  Push-Location $runtime;try{& tar -cf $secretPlain validator-01/config validator-01/secrets validator-02/config validator-02/secrets validator-03/config validator-03/secrets anchoring; if($LASTEXITCODE){throw 'secret archive failed'}}finally{Pop-Location}
  & openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000 -in $secretPlain -out $secretEncrypted -pass env:IDAX_LEDGER_BACKUP_PASSPHRASE; if($LASTEXITCODE){throw 'secret encryption failed'};Remove-Item -LiteralPath $secretPlain -Force
  $manifest=[ordered]@{format='idax-ledger-backup-v1';createdAt=(Get-Date).ToUniversalTime().ToString('o');networkId=2181844733;postgres='postgres-idax-ledger.dump';xrplPublic='xrpl-public.tar.gz';xrplSecrets='xrpl-secrets.tar.enc';validators=3;consistentOfflineXrpl=$true}
  $manifest|ConvertTo-Json|Set-Content -Encoding utf8 (Join-Path $backup 'manifest.json')
  Get-ChildItem $backup -File|ForEach-Object{"$((Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower())  $($_.Name)"}|Set-Content -Encoding ascii (Join-Path $backup 'SHA256SUMS')
  Write-Output $backup
}finally{if($wasRunning -and -not $LeaveStopped){& (Join-Path $PSScriptRoot 'Start-PrivateNetwork.ps1')}}
