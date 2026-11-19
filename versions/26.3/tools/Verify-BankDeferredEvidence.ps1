param([Parameter(Mandatory=$true)][string]$EvidenceRoot)
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path -LiteralPath $EvidenceRoot).Path
$taskRepo=[IO.Path]::GetFullPath((Join-Path $taskRoot '../../../..'))
$taskRows=@(Import-Csv -LiteralPath (Join-Path $taskRoot 'SHA256SUMS.csv'))
$taskFiles=@(Get-ChildItem -LiteralPath $taskRoot -Recurse -Force -File|Where-Object Name -ne 'SHA256SUMS.csv')
if($taskRows.Count -lt 40 -or $taskFiles.Count -ne $taskRows.Count -or @($taskRows.Path|Select-Object -Unique).Count -ne $taskRows.Count){throw 'Incomplete/duplicate evidence'}
foreach($taskRow in $taskRows){
    $taskPath=[IO.Path]::GetFullPath((Join-Path $taskRoot $taskRow.Path))
    if(-not $taskPath.StartsWith($taskRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or (Get-FileHash -LiteralPath $taskPath).Hash -cne $taskRow.SHA256){throw "Changed/escaped evidence: $($taskRow.Path)"}
}
$taskInstance=Join-Path $taskRepo 'versions/26.3/installed-audit/bank-deferred-20261118'
foreach($taskIdentity in @(
    @('mods/oculus-forge-26.3-0.4.0-beta.4-checkpoint.3-private-audit.jar','8BB635A9FECA705114FE7B47C8FC16A88580EFD1C78733B5860049CED0A1F8DE'),
    @('shaderpacks/BSL_v10.1.1.zip','131CA6B56B100E60577FFDB0DD51BFAB2EE6E334564EEEE324E5422026891668'),
    @('shaderpacks/ComplementaryReimagined_r5.7.1.zip','24A20634A7832D422D3CD5023BE16829F26840E25C69404DD418306EA79F63F0')
)){if((Get-FileHash -LiteralPath (Join-Path $taskInstance $taskIdentity[0])).Hash -cne $taskIdentity[1]){throw 'Installed artifact/original ZIP changed'}}
if((Get-Content -LiteralPath (Join-Path $taskRoot 'deferred-input-exit.txt') -Raw).Trim() -ne '0'){throw 'Nonzero runtime exit'}
$taskLog=Get-Content -LiteralPath (Join-Path $taskRoot 'deferred-input-game.log') -Raw
foreach($taskMarker in @('Oculus renderer startup: extended=true, hdr=true, JVM overrides=false, issue=',"Activated shader pack 'oculus-comp-deferred-input-probe'",'PACK-NATIVE LAYERED terrain','PACK-NATIVE terrain shadow map','Executed 1 deferred stage(s) BEFORE translucent terrain','advance_time is now set to true','Oculus shutdown: ownedTextures=0 ownedBytes=0 uniformRings=0 worldTextures=0 worldBytes=0 centerDepthTextures=0 centerDepthBytes=0 centerDepthRings=0')){if(-not $taskLog.Contains($taskMarker)){throw "Missing marker: $taskMarker"}}
if($taskLog -match 'HDR scene capture failed|GL_INVALID|shader pass failed at runtime|Shaders paused:|Failed to (apply|load) shader pack'){throw 'Unexpected renderer failure/suspension'}
$taskErrors=@($taskLog -split "`n"|Where-Object{$_ -match '/ERROR\]' -and $_ -notmatch '\[com\.mojang\.realmsclient\.' -and $_ -notmatch '\[oshi\.driver\.windows\.registry\.HkeyPerformanceDataUtil\]: Unable to locate English counter names'})
if($taskErrors.Count){throw 'Unexpected non-environment ERROR'}
foreach($taskSetting in @('improvedTransparency:false','renderDistance:8','simulationDistance:6','mipmapLevels:2','resourcePacks:[]')){if($taskSetting -cnotin (Get-Content -LiteralPath (Join-Path $taskRoot 'options.txt'))){throw 'Recorded options changed'}}
$taskReadback=@(& (Join-Path $taskRoot 'Read-BankProbeBits.ps1') -Png (Join-Path $taskRoot 'numeric-readback.png'))
$taskRecorded=@(Import-Csv -LiteralPath (Join-Path $taskRoot 'numeric-readback.csv'))
if($taskReadback.Count -ne 8 -or $taskRecorded.Count -ne 8){throw 'Incomplete numeric readback'}
for($taskIndex=0;$taskIndex -lt 8;$taskIndex++){if($taskReadback[$taskIndex].Name -cne $taskRecorded[$taskIndex].Name -or $taskReadback[$taskIndex].Bits -cne $taskRecorded[$taskIndex].Bits){throw 'Numeric panel no longer matches recorded values'}}
$taskRejected=$false
try{& (Join-Path $taskRoot 'Read-BankProbeBits.ps1') -Png (Join-Path $taskRoot 'cloud-overlay.png')|Out-Null}catch{if($_.Exception.Message -notmatch 'Insufficient binary contrast|Known far/camera controls fail'){throw};$taskRejected=$true}
if(-not $taskRejected){throw 'Decoder accepted a non-panel capture'}
$taskEntries=@(Import-Csv -LiteralPath (Join-Path $taskRoot 'original-input-comparison.csv'))
if($taskEntries.Count -ne 404 -or @($taskEntries|Where-Object ExpectedContent -cne 'True').Count){throw 'Unexpected derived shader inputs'}
foreach($taskActive in @('shaderpacks/oculus-comp-deferred-input-probe','saves/Oculus263 Gameplay 20261117/datapacks/oculus-bank-stage')){if(Test-Path -LiteralPath (Join-Path $taskInstance $taskActive)){throw 'Diagnostic input remains active'}}
foreach($taskPair in @(@('oculus.properties','shaders.enabled=false'),@('oculus-renderer.properties','worldRenderer.enabled=false'))){
    if($taskPair[1] -cnotin (Get-Content -LiteralPath (Join-Path $taskInstance "config/$($taskPair[0])")) -or (Get-FileHash -LiteralPath (Join-Path $taskInstance "config/$($taskPair[0])")).Hash -cne (Get-FileHash -LiteralPath (Join-Path $taskRoot "restored-$($taskPair[0])")).Hash){throw 'Isolated preferences not restored'}
}
Add-Type -AssemblyName System.Drawing
$taskPngs=@(Get-ChildItem -LiteralPath $taskRoot -Recurse -File -Filter '*.png')
if($taskPngs.Count -ne 24){throw 'Expected 19 stage previews and five native F2 captures'}
foreach($taskPng in $taskPngs){$taskBitmap=[Drawing.Bitmap]::new($taskPng.FullName);try{if($taskBitmap.Width -ne 960 -or $taskBitmap.Height -ne 540){throw 'Unexpected image dimensions'}}finally{$taskBitmap.Dispose()}}
Write-Output "PASS: $($taskRows.Count) hashes; 24 original PNGs; exact numeric controls/eight values; wrong-image decoder rejection; 404 shader entries accounted for; unchanged private binary and original ZIPs; exit 0/eight zero counters; diagnostic inputs withdrawn. No visual fix or public readiness inferred."
