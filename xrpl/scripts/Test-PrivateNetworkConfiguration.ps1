param([string]$RuntimePath)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if(-not $RuntimePath){$RuntimePath=if($env:IDAX_LEDGER_RUNTIME_PATH){$env:IDAX_LEDGER_RUNTIME_PATH}else{Join-Path $repo '.runtime'}}
$runtime=[IO.Path]::GetFullPath($RuntimePath);$names='validator-01','validator-02','validator-03';$identities=@()
foreach($name in $names){
  $keyFile=Join-Path $runtime "$name/secrets/validator-keys.json";$unlFile=Join-Path $runtime "$name/config/validators.txt"
  if(-not(Test-Path $keyFile) -or -not(Test-Path $unlFile)){throw "$name configuration is incomplete"}
  $identity=(Get-Content $keyFile -Raw|ConvertFrom-Json).public_key
  if($identity -notmatch '^nH[1-9A-HJ-NP-Za-km-z]{45,60}$'){throw "$name has an invalid validator identity"};$identities+=$identity
  $unl=@(Get-Content $unlFile|Where-Object{$_ -and -not $_.StartsWith('[')})
  if($unl.Count -ne 3 -or @($unl|Sort-Object -Unique).Count -ne 3){throw "$name UNL must contain exactly three unique validators"}
  if(@($unl|Where-Object{$_ -notmatch '^nH[1-9A-HJ-NP-Za-km-z]{45,60}$'}).Count){throw "$name UNL contains an invalid validator token"}
}
if(@($identities|Sort-Object -Unique).Count -ne 3){throw 'Duplicate validator identity detected; startup is refused'}
foreach($name in $names){$unl=@(Get-Content (Join-Path $runtime "$name/config/validators.txt")|Where-Object{$_ -and -not $_.StartsWith('[')});if(Compare-Object ($identities|Sort-Object) ($unl|Sort-Object)){throw "$name UNL does not match the configured validator identities"}}
Write-Output 'Validator identities and UNLs are valid and mutually consistent.'
