param(
 [Parameter(Mandatory=$true)][string]$BackupPath,[string]$RuntimePath,
 [string]$PgHost='localhost',[int]$PgPort=5432,[string]$PgDatabase='idaxdb',[string]$PgUser='idax_backend',
 [switch]$ConfirmRestore
)
$ErrorActionPreference='Stop';if(-not $ConfirmRestore){throw 'Restore is destructive for the target schema/runtime; pass -ConfirmRestore'}
if(-not $env:IDAX_LEDGER_BACKUP_PASSPHRASE){throw 'Set IDAX_LEDGER_BACKUP_PASSPHRASE'}
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path;$backup=(Resolve-Path $BackupPath).Path
if(-not $RuntimePath){$RuntimePath=Join-Path $repo '.runtime'};$runtime=[IO.Path]::GetFullPath($RuntimePath)
foreach($name in 'manifest.json','postgres-idax-ledger.dump','xrpl-public.tar.gz','xrpl-secrets.tar.enc','SHA256SUMS'){if(-not(Test-Path (Join-Path $backup $name))){throw "Missing backup artifact: $name"}}
$manifest=Get-Content (Join-Path $backup 'manifest.json') -Raw|ConvertFrom-Json
if($manifest.format -ne 'idax-ledger-backup-v1' -or [uint64]$manifest.networkId -ne 2181844733){throw 'Unsupported backup format or XRPL NetworkID'}
foreach($line in Get-Content (Join-Path $backup 'SHA256SUMS')){
  if($line -notmatch '^([0-9a-f]{64})\s{2}(.+)$'){throw "Malformed checksum entry: $line"}
  $file=Join-Path $backup $Matches[2];if(-not(Test-Path $file)){throw "Checksum target is missing: $($Matches[2])"}
  $actual=(Get-FileHash $file -Algorithm SHA256).Hash.ToLower();if($actual -ne $Matches[1]){throw "Checksum mismatch: $($Matches[2])"}
}
& (Join-Path $PSScriptRoot 'Stop-PrivateNetwork.ps1')
if(Test-Path $runtime){$saved="$runtime.pre-restore-$(Get-Date -Format yyyyMMddHHmmss)";Move-Item -LiteralPath $runtime -Destination $saved}
New-Item -ItemType Directory -Path $runtime -Force|Out-Null
& tar -xzf (Join-Path $backup 'xrpl-public.tar.gz') -C $runtime;if($LASTEXITCODE){throw 'public XRPL restore failed'}
$plain=Join-Path $backup '.restore-secrets.tar';try{& openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in (Join-Path $backup 'xrpl-secrets.tar.enc') -out $plain -pass env:IDAX_LEDGER_BACKUP_PASSPHRASE;if($LASTEXITCODE){throw 'secret decryption failed'};& tar -xf $plain -C $runtime;if($LASTEXITCODE){throw 'secret restore failed'}}finally{if(Test-Path $plain){Remove-Item -LiteralPath $plain -Force}}
& pg_restore -h $PgHost -p $PgPort -U $PgUser -d $PgDatabase --clean --if-exists --no-owner (Join-Path $backup 'postgres-idax-ledger.dump');if($LASTEXITCODE){throw 'pg_restore failed'}
Write-Output "Restored to $runtime. Start validators and verify NetworkID before starting writers."
