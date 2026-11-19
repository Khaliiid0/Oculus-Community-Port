param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$Case,
    [Parameter(Mandatory)][string]$Settings,
    [Parameter(Mandatory)][string]$Resources,
    [Parameter(Mandatory)][string]$View
)
$ErrorActionPreference = 'Stop'
$taskVersionRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskInstance = Join-Path $taskVersionRoot 'installed-audit/visual-matrix'
$taskEvidence = Join-Path $taskVersionRoot 'test-evidence/2026-11-17-installed-visual-matrix'
New-Item -ItemType Directory -Path (Join-Path $taskEvidence 'screenshots') -Force | Out-Null
$taskCsv = Join-Path $taskEvidence 'observations.csv'
$taskExisting = if (Test-Path -LiteralPath $taskCsv) { @(Import-Csv -LiteralPath $taskCsv) } else { @() }
if (@($taskExisting | Where-Object Case -EQ $Case).Count) { throw 'Case already recorded; preserve it' }
$taskSaved = @(Select-String -LiteralPath (Join-Path $taskInstance 'logs/latest.log') -Pattern 'Saved screenshot as ([0-9_.-]+\.png)') | Select-Object -Last 1
if (-not $taskSaved) { throw 'No native F2 capture was logged' }
$taskName = $taskSaved.Matches[0].Groups[1].Value
if (@($taskExisting | Where-Object Screenshot -EQ $taskName).Count) { throw 'This screenshot already belongs to a case' }
$taskSource = Join-Path $taskInstance "screenshots/$taskName"
$taskBytes = [IO.File]::ReadAllBytes($taskSource)
if ($taskBytes.Length -lt 24 -or [Convert]::ToHexString($taskBytes[0..7]) -ne '89504E470D0A1A0A') { throw 'Not a PNG screenshot' }
$taskWidth = [uint32]$taskBytes[16]*16777216 + [uint32]$taskBytes[17]*65536 + [uint32]$taskBytes[18]*256 + $taskBytes[19]
$taskHeight = [uint32]$taskBytes[20]*16777216 + [uint32]$taskBytes[21]*65536 + [uint32]$taskBytes[22]*256 + $taskBytes[23]
$taskSelection = Get-Content -LiteralPath (Join-Path $taskInstance 'config/oculus.properties')
$taskPackLine = @($taskSelection | Where-Object { $_ -match '^shaderpack=' })
$taskEnabledLine = @($taskSelection | Where-Object { $_ -match '^shaders.enabled=' })
if ($taskPackLine.Count -ne 1 -or $taskEnabledLine.Count -ne 1) { throw 'Unexpected shader selection file' }
$taskPack = $taskPackLine[0].Substring(11)
$taskSidecar = Join-Path $taskInstance "shaderpacks/$taskPack.txt"
Copy-Item -LiteralPath $taskSource -Destination (Join-Path $taskEvidence "screenshots/$taskName")
$taskRow = [pscustomobject]@{
    Case=$Case; Settings=$Settings; Resources=$Resources; View=$View
    Screenshot=$taskName; SHA256=(Get-FileHash -LiteralPath $taskSource).Hash
    Width=$taskWidth; Height=$taskHeight; CapturedUtc=(Get-Item -LiteralPath $taskSource).LastWriteTimeUtc.ToString('o')
    RequestedPack=$taskPack; ShadersEnabled=$taskEnabledLine[0].Substring(16)
    SidecarSHA256=$(if(Test-Path -LiteralPath $taskSidecar){(Get-FileHash -LiteralPath $taskSidecar).Hash}else{'NONE'})
}
$taskRow | Export-Csv -LiteralPath $taskCsv -NoTypeInformation -Append
Write-Output "Recorded $Case : $taskName ($taskWidth x $taskHeight), selected=$taskPack"
