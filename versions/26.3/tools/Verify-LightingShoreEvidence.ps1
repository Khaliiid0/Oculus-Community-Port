param([Parameter(Mandatory=$true)][string]$EvidenceRoot)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path -LiteralPath $EvidenceRoot).Path
$taskRepo = [IO.Path]::GetFullPath((Join-Path $taskRoot '../../../..'))
$taskKind = (Get-Content -LiteralPath (Join-Path $taskRoot 'audit-kind.txt') -Raw).Trim()
if ($taskKind -notin @('lighting','bank')) { throw 'Unknown audit kind' }
$taskRows = @(Import-Csv -LiteralPath (Join-Path $taskRoot 'SHA256SUMS.csv'))
if ($taskRows.Count -lt 25 -or @($taskRows.Path | Select-Object -Unique).Count -ne $taskRows.Count) { throw 'Incomplete/duplicate evidence manifest' }
$taskFiles = @(Get-ChildItem -LiteralPath $taskRoot -File -Recurse -Force | Where-Object Name -ne 'SHA256SUMS.csv')
if ($taskFiles.Count -ne $taskRows.Count) { throw 'Unexpected/missing evidence files' }
foreach ($taskRow in $taskRows) {
    $taskPath = [IO.Path]::GetFullPath((Join-Path $taskRoot $taskRow.Path))
    if (-not $taskPath.StartsWith($taskRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Evidence escaped root' }
    if ((Get-FileHash -LiteralPath $taskPath).Hash -ne $taskRow.SHA256) { throw "Evidence changed: $($taskRow.Path)" }
}
$taskInstanceName = if ($taskKind -eq 'bank') { 'bank-stage-20261118' } else { 'lighting-shore-20261118' }
$taskInstance = Join-Path $taskRepo "versions/26.3/installed-audit/$taskInstanceName"
$taskJar = Join-Path $taskInstance 'mods/oculus-forge-26.3-0.4.0-beta.4-checkpoint.3-private-audit.jar'
if ((Get-FileHash -LiteralPath $taskJar).Hash -ne '8BB635A9FECA705114FE7B47C8FC16A88580EFD1C78733B5860049CED0A1F8DE' -or @(Get-ChildItem -LiteralPath (Join-Path $taskInstance 'mods') -File -Filter '*.jar').Count -ne 1) { throw 'Installed mod context changed' }
foreach ($taskPack in @(
    @('BSL_v10.1.1.zip','131CA6B56B100E60577FFDB0DD51BFAB2EE6E334564EEEE324E5422026891668'),
    @('ComplementaryReimagined_r5.7.1.zip','24A20634A7832D422D3CD5023BE16829F26840E25C69404DD418306EA79F63F0')
)) { if ((Get-FileHash -LiteralPath (Join-Path $taskInstance "shaderpacks/$($taskPack[0])")).Hash -ne $taskPack[1]) { throw 'Original pack ZIP changed' } }
$taskRun = if ($taskKind -eq 'bank') { 'bank-capture' } else { 'final-lighting' }
if ((Get-Content -LiteralPath (Join-Path $taskRoot "$taskRun-exit.txt") -Raw).Trim() -ne '0') { throw 'Nonzero client exit' }
$taskLogPath = Join-Path $taskRoot "$taskRun-game.log"
$taskLog = Get-Content -LiteralPath $taskLogPath -Raw
foreach ($taskMarker in @('Oculus renderer startup: extended=true, hdr=true, JVM overrides=false, issue=', 'Oculus world renderer submitted PACK-NATIVE LAYERED terrain', 'Oculus world renderer submitted the PACK-NATIVE terrain shadow map', 'Executed 1 deferred stage(s) BEFORE translucent terrain', 'scene=R11F_G11F_B10F', 'Oculus shutdown: ownedTextures=0 ownedBytes=0 uniformRings=0 worldTextures=0 worldBytes=0 centerDepthTextures=0 centerDepthBytes=0 centerDepthRings=0', 'advance_time is now set to true')) {
    if (-not $taskLog.Contains($taskMarker)) { throw "Missing runtime marker: $taskMarker" }
}
if ($taskLog -match 'HDR scene capture failed|GL_INVALID|shader pass failed at runtime|Shaders paused:') { throw 'Runtime rendering failure/suspension' }
$taskErrors = @(Select-String -LiteralPath $taskLogPath -Pattern '/ERROR\]' | Where-Object { $_.Line -notmatch '\[com\.mojang\.(realmsclient\.|authlib\.minecraft\.client\.MinecraftClient\])' })
if ($taskErrors.Count) { throw 'Unexpected non-offline-profile error' }
$taskOptionsFile = if ($taskKind -eq 'bank') { 'options.txt' } else { 'final-options.txt' }
$taskOptions = Get-Content -LiteralPath (Join-Path $taskRoot $taskOptionsFile)
foreach ($taskSetting in @('improvedTransparency:false','renderDistance:8','simulationDistance:6','mipmapLevels:2','resourcePacks:[]')) { if ($taskSetting -cnotin $taskOptions) { throw "Unexpected recorded setting: $taskSetting" } }
$taskPngs = @(Get-ChildItem -LiteralPath $taskRoot -File -Filter '*.png' -Recurse)
$taskExpected = if ($taskKind -eq 'bank') { 22 } else { 12 }
if ($taskPngs.Count -ne $taskExpected) { throw 'Wrong native capture count' }
foreach ($taskPng in $taskPngs) {
    $taskBytes = [IO.File]::ReadAllBytes($taskPng.FullName)
    if ([Convert]::ToHexString($taskBytes[0..7]) -ne '89504E470D0A1A0A') { throw 'Invalid PNG signature' }
    $taskWidth = [System.Buffers.Binary.BinaryPrimitives]::ReadInt32BigEndian([byte[]]$taskBytes[16..19])
    $taskHeight = [System.Buffers.Binary.BinaryPrimitives]::ReadInt32BigEndian([byte[]]$taskBytes[20..23])
    if ($taskWidth -ne 960 -or $taskHeight -ne 540) { throw 'Unexpected native image dimensions' }
}
if ($taskKind -eq 'lighting') {
    $taskCases = @(Import-Csv -LiteralPath (Join-Path $taskRoot 'observations.csv'))
    if ($taskCases.Count -ne 12 -or @($taskCases.Case | Select-Object -Unique).Count -ne 12 -or @($taskCases.Screenshot | Select-Object -Unique).Count -ne 12) { throw 'Incomplete/duplicate lighting cases' }
    foreach ($taskCase in $taskCases) { if ((Get-FileHash -LiteralPath (Join-Path $taskRoot "screenshots/$($taskCase.Screenshot)")).Hash -ne $taskCase.SHA256) { throw 'Case/image mismatch' } }
    foreach ($taskPackName in @("Activated shader pack 'BSL_v10.1.1'", "Activated shader pack 'ComplementaryReimagined_r5.7.1'")) { if (-not $taskLog.Contains($taskPackName)) { throw 'Missing original-pack activation' } }
} else {
    $taskEntries = @(Import-Csv -LiteralPath (Join-Path $taskRoot 'original-entry-comparison.csv'))
    if ($taskEntries.Count -lt 100 -or @($taskEntries | Where-Object OriginalMatch -cne 'True').Count) { throw 'Original-pack comparison failed' }
    if (Test-Path -LiteralPath (Join-Path $taskInstance 'shaderpacks/oculus-comp-bank-depth-probe')) { throw 'Probe pack still active' }
    if (Test-Path -LiteralPath (Join-Path $taskInstance 'saves/Oculus263 Gameplay 20261117/datapacks/oculus-bank-stage')) { throw 'Automatic diagnostic bootstrap still active' }
}
Write-Output "PASS: $taskKind; $($taskRows.Count) evidence hashes; $taskExpected native PNGs; unchanged private checkpoint 3/original pack ZIPs; exit 0 and tracked shutdown counters zero. Visual discrepancy remains OPEN; hashes are not an image-correctness oracle."
