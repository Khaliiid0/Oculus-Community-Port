param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Case,
    [Parameter(Mandatory)][string]$View,
    [Parameter(Mandatory)][string]$State
)
$ErrorActionPreference='Stop'
$taskVersion=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskInstance=Join-Path $taskVersion 'installed-audit/lighting-shore-20261118'
$taskEvidence=Join-Path $taskVersion 'test-evidence/2026-11-18-lighting-shore'
New-Item -ItemType Directory -Path (Join-Path $taskEvidence 'screenshots') -Force|Out-Null
$taskCsv=Join-Path $taskEvidence 'observations.csv'
$taskRows=if(Test-Path -LiteralPath $taskCsv){@(Import-Csv -LiteralPath $taskCsv)}else{@()}
if($Case -in $taskRows.Case){throw 'Preserve existing case'}
$taskSaved=@(Select-String -LiteralPath (Join-Path $taskInstance 'logs/latest.log') -Pattern 'Saved screenshot as ([0-9_.-]+\.png)')|Select-Object -Last 1
if(-not $taskSaved){throw 'No native F2 image'}
$taskName=$taskSaved.Matches[0].Groups[1].Value
if($taskName -in $taskRows.Screenshot){throw 'Image already assigned'}
$taskFile=Join-Path $taskInstance "screenshots/$taskName"
$taskBytes=[IO.File]::ReadAllBytes($taskFile)
if($taskBytes.Length -lt 24 -or [Convert]::ToHexString($taskBytes[0..7]) -ne '89504E470D0A1A0A'){throw 'Not a native PNG'}
$taskWidth=[uint32]$taskBytes[16]*16777216+[uint32]$taskBytes[17]*65536+[uint32]$taskBytes[18]*256+$taskBytes[19]
$taskHeight=[uint32]$taskBytes[20]*16777216+[uint32]$taskBytes[21]*65536+[uint32]$taskBytes[22]*256+$taskBytes[23]
$taskConfig=Get-Content -LiteralPath (Join-Path $taskInstance 'config/oculus.properties')
$taskPack=@($taskConfig|Where-Object{$_ -match '^shaderpack='})
$taskEnabled=@($taskConfig|Where-Object{$_ -match '^shaders.enabled='})
if($taskPack.Count -ne 1 -or $taskEnabled.Count -ne 1){throw 'Unexpected configuration'}
if(@(Get-ChildItem -LiteralPath (Join-Path $taskInstance 'shaderpacks') -File -Filter '*.txt').Count){throw 'Unexpected pack overrides'}
$taskDestination=Join-Path $taskEvidence "screenshots/$taskName"
if(Test-Path -LiteralPath $taskDestination){throw 'Preserve existing PNG'}
Copy-Item -LiteralPath $taskFile -Destination $taskDestination
[pscustomobject]@{
    Case=$Case;View=$View;State=$State;Screenshot=$taskName;SHA256=(Get-FileHash -LiteralPath $taskFile).Hash
    Width=$taskWidth;Height=$taskHeight;CapturedUtc=(Get-Item -LiteralPath $taskFile).LastWriteTimeUtc.ToString('o')
    RequestedPack=$taskPack[0].Substring(11);ShadersEnabled=$taskEnabled[0].Substring(16)
    SavedOptionsSHA256=(Get-FileHash -LiteralPath (Join-Path $taskInstance 'options.txt')).Hash
}|Export-Csv -LiteralPath $taskCsv -Append -NoTypeInformation
Write-Output "Recorded $Case -> $taskName ($taskWidth x $taskHeight); labels require separate visual/log review."
