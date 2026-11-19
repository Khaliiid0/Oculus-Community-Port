param([Parameter(Mandatory=$true)][string]$EvidenceRoot)
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path -LiteralPath $EvidenceRoot).Path
$taskRepo=[IO.Path]::GetFullPath((Join-Path $taskRoot '../../../..'))
$taskVersion=Join-Path $taskRepo 'versions/26.3'
$taskInstance=Join-Path $taskVersion 'installed-audit/fog-range-20261118'
$taskRows=@(Import-Csv -LiteralPath (Join-Path $taskRoot 'SHA256SUMS.csv'))
if($taskRows.Count -lt 45 -or @(Get-ChildItem -LiteralPath $taskRoot -File -Recurse|Where-Object Name -ne 'SHA256SUMS.csv').Count -ne $taskRows.Count -or @($taskRows.Path|Select-Object -Unique).Count -ne $taskRows.Count){throw 'Incomplete/duplicate evidence'}
foreach($taskRow in $taskRows){$taskPath=[IO.Path]::GetFullPath((Join-Path $taskRoot $taskRow.Path));if(-not $taskPath.StartsWith($taskRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or (Get-FileHash -LiteralPath $taskPath).Hash -cne $taskRow.SHA256){throw 'Changed/escaped evidence'}}
foreach($taskPair in @(@('mods/oculus-forge-26.3-0.4.0-beta.4-checkpoint.3-private-audit.jar','8BB635A9FECA705114FE7B47C8FC16A88580EFD1C78733B5860049CED0A1F8DE'),@('shaderpacks/ComplementaryReimagined_r5.7.1.zip','24A20634A7832D422D3CD5023BE16829F26840E25C69404DD418306EA79F63F0'),@('shaderpacks/BSL_v10.1.1.zip','131CA6B56B100E60577FFDB0DD51BFAB2EE6E334564EEEE324E5422026891668'))){if((Get-FileHash -LiteralPath (Join-Path $taskInstance $taskPair[0])).Hash -cne $taskPair[1]){throw 'Original artifact changed'}}
if(@(Get-ChildItem -LiteralPath (Join-Path $taskInstance 'shaderpacks') -Force).Count -ne 2){throw 'Derived pack/sidecar present'}
if((Get-Content -LiteralPath (Join-Path $taskRoot 'fog-range-exit.txt') -Raw).Trim() -ne '0'){throw 'Nonzero client exit'}
$taskLog=Get-Content -LiteralPath (Join-Path $taskRoot 'fog-range-game.log') -Raw
foreach($taskMarker in @("Activated shader pack 'ComplementaryReimagined_r5.7.1'",'Oculus renderer startup: extended=true, hdr=true, JVM overrides=false, issue=','PACK-NATIVE LAYERED terrain','PACK-NATIVE terrain shadow map','OCULUS_FOG_HALFWAY','OCULUS_FOG_CLOSER','OCULUS_FOG_TIME_RESTORED','Oculus shutdown: ownedTextures=0 ownedBytes=0 uniformRings=0 worldTextures=0 worldBytes=0 centerDepthTextures=0 centerDepthBytes=0 centerDepthRings=0')){if(-not $taskLog.Contains($taskMarker)){throw "Missing $taskMarker"}}
foreach($taskPose in @('[2120.5d, 65.0d, 522.5d]','[2079.741075d, 68.936811d, 565.011071d]','[2059.361613d, 70.905217d, 586.266607d]')){if(-not $taskLog.Contains("OCULUS_FOG_POSE $taskPose ROT [0.0f, 15.0f] FLY 1b")){throw 'Pose/flying verification missing'}}
if($taskLog -match 'HDR scene capture failed|GL_INVALID|shader pass failed at runtime|Shaders paused:'){throw 'Renderer failure/suspension'}
foreach($taskFile in @('start-options.txt','options.txt')){if((Get-FileHash -LiteralPath (Join-Path $taskRoot $taskFile)).Hash -cne 'B9C4461E99FD2B525D5F8552B927C4354C7B0BC8F57ED789D0DF8698343D65A5'){throw 'Saved options changed'}}
$taskCases=@(Import-Csv -LiteralPath (Join-Path $taskRoot 'observations.csv'))
if($taskCases.Count -ne 4 -or @($taskCases.Case|Select-Object -Unique).Count -ne 4){throw 'Incomplete cases'}
foreach($taskName in @('baseline-a','halfway','closer','baseline-b')){if($taskName -cnotin $taskCases.Case){throw 'Missing distance case'}}
foreach($taskCase in $taskCases){if((Get-FileHash -LiteralPath (Join-Path $taskRoot "screenshots/$($taskCase.Screenshot)")).Hash -cne $taskCase.SHA256){throw 'Capture/case mismatch'}}
$taskPrediction=@(& (Join-Path $taskRoot 'Get-FogRangePrediction.ps1') -VersionRoot $taskVersion)
if($taskPrediction.Count -ne 3 -or $taskPrediction[0].PredictedBorderFog -lt 0.54 -or $taskPrediction[0].PredictedBorderFog -gt 0.56 -or $taskPrediction[1].PredictedBorderFog -gt 0.00002 -or $taskPrediction[2].PredictedBorderFog -gt 0.000000001){throw 'Fog calculation changed'}
foreach($taskPair in @(@('oculus.properties','shaders.enabled=false'),@('oculus-renderer.properties','worldRenderer.enabled=false'))){if($taskPair[1] -cnotin (Get-Content -LiteralPath (Join-Path $taskInstance "config/$($taskPair[0])"))){throw 'Isolated preference not disabled'}}
foreach($taskName in @('a-oculus-fog','oculus-bank-stage')){if(Test-Path -LiteralPath (Join-Path $taskInstance "saves/Oculus263 Gameplay 20261117/datapacks/$taskName")){throw 'Diagnostic datapack still active'}}
if(-not (Select-String -LiteralPath (Join-Path $taskRoot 'authored-controls/data/a_oculus_fog/function/d_restore.mcfunction') -SimpleMatch 'gamerule minecraft:advance_time true' -Quiet)){throw 'Incorrect restoration control'}
Add-Type -AssemblyName System.Drawing
$taskPngs=@(Get-ChildItem -LiteralPath $taskRoot -File -Filter '*.png' -Recurse)
if($taskPngs.Count -ne 23){throw 'Expected 19 bounded stage previews and four F2 scene captures'}
foreach($taskPng in $taskPngs){$taskBitmap=[Drawing.Bitmap]::new($taskPng.FullName);try{if($taskBitmap.Width -ne 960 -or $taskBitmap.Height -ne 540){throw 'Image dimensions changed'}}finally{$taskBitmap.Dispose()}}
Write-Output "PASS: $($taskRows.Count) hashes; four unmodified-pack poses/23 original PNGs; ray prediction; identical options; original artifact identity; clean zero-counter exit and withdrawn controls. Visual interpretation is manual, not full parity/FPS/publication approval."
