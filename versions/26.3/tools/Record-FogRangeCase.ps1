param([Parameter(Mandatory=$true)][ValidateSet('baseline-a','halfway','closer','baseline-b')][string]$Case)
$ErrorActionPreference='Stop'
$taskVersion=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskInstance=Join-Path $taskVersion 'installed-audit/fog-range-20261118'
$taskRoot=Join-Path $taskVersion 'test-evidence/2026-11-18-fog-range'
if(Test-Path -LiteralPath (Join-Path $taskRoot 'SHA256SUMS.csv')){throw 'Preserve frozen evidence'}
$taskCsv=Join-Path $taskRoot 'observations.csv'
$taskRows=if(Test-Path -LiteralPath $taskCsv){@(Import-Csv -LiteralPath $taskCsv)}else{@()}
if($Case -in $taskRows.Case){throw 'Preserve earlier case'}
$taskSaved=Select-String -LiteralPath (Join-Path $taskInstance 'logs/latest.log') -Pattern 'Saved screenshot as ([0-9_.-]+\.png)'|Select-Object -Last 1
if(-not $taskSaved){throw 'No logged F2 screenshot'}
$taskName=$taskSaved.Matches[0].Groups[1].Value
if($taskName -in $taskRows.Screenshot){throw 'Image already assigned'}
$taskPng=Join-Path $taskInstance "screenshots/$taskName"
$taskBytes=[IO.File]::ReadAllBytes($taskPng)
if($taskBytes.Length -lt 24 -or [Convert]::ToHexString($taskBytes[0..7]) -cne '89504E470D0A1A0A'){throw 'Not a PNG'}
$taskWidth=[uint32]$taskBytes[16]*16777216+[uint32]$taskBytes[17]*65536+[uint32]$taskBytes[18]*256+$taskBytes[19]
$taskHeight=[uint32]$taskBytes[20]*16777216+[uint32]$taskBytes[21]*65536+[uint32]$taskBytes[22]*256+$taskBytes[23]
if($taskWidth -ne 960 -or $taskHeight -ne 540){throw 'Unexpected native dimensions'}
$taskConfig=Get-Content -LiteralPath (Join-Path $taskInstance 'config/oculus.properties')
if('shaderpack=ComplementaryReimagined_r5.7.1.zip' -cnotin $taskConfig -or 'shaders.enabled=true' -cnotin $taskConfig){throw 'Not original Complementary enabled'}
if(@(Get-ChildItem -LiteralPath (Join-Path $taskInstance 'shaderpacks') -Force).Count -ne 2){throw 'Unexpected derived pack/sidecar'}
if((Get-FileHash -LiteralPath (Join-Path $taskInstance 'options.txt')).Hash -cne 'B9C4461E99FD2B525D5F8552B927C4354C7B0BC8F57ED789D0DF8698343D65A5'){throw 'Saved settings changed'}
New-Item -ItemType Directory -Path (Join-Path $taskRoot 'screenshots') -Force|Out-Null
$taskDestination=Join-Path $taskRoot "screenshots/$taskName"
if(Test-Path -LiteralPath $taskDestination){throw 'Preserve original image'}
Copy-Item -LiteralPath $taskPng -Destination $taskDestination
[pscustomobject]@{Case=$Case;Screenshot=$taskName;SHA256=(Get-FileHash -LiteralPath $taskPng).Hash;Width=$taskWidth;Height=$taskHeight;CapturedUtc=(Get-Item -LiteralPath $taskPng).LastWriteTimeUtc.ToString('o');Pack='ComplementaryReimagined_r5.7.1.zip';OptionsSHA256=(Get-FileHash -LiteralPath (Join-Path $taskInstance 'options.txt')).Hash}|Export-Csv -LiteralPath $taskCsv -Append -NoTypeInformation
Write-Output "Recorded $Case -> $taskName; pose/visual acceptance requires log/image review."
