param([string]$VersionRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')))
$ErrorActionPreference='Stop'
$taskVersion=(Resolve-Path -LiteralPath $VersionRoot).Path
$taskPrior=Join-Path $taskVersion 'test-evidence/2026-11-18-deferred-input'
$taskManifest=@(Import-Csv -LiteralPath (Join-Path $taskPrior 'SHA256SUMS.csv')|Where-Object Path -ceq 'numeric-readback.csv')
if($taskManifest.Count -ne 1 -or (Get-FileHash -LiteralPath (Join-Path $taskPrior 'numeric-readback.csv')).Hash -cne $taskManifest[0].SHA256){throw 'Archived numeric input changed'}
$taskRows=@(Import-Csv -LiteralPath (Join-Path $taskPrior 'numeric-readback.csv'))
$taskData=@{}
foreach($taskRow in $taskRows){$taskData[$taskRow.Name]=[BitConverter]::UInt32BitsToSingle([Convert]::ToUInt32($taskRow.Bits,16))}
if($taskData.far -ne 128 -or [Math]::Abs($taskData.cameraY-66.62) -gt 0.001){throw 'Unexpected recorded control'}
$taskPitch=15*[Math]::PI/180
# Minecraft yaw=0 faces +Z; positive pitch looks down. Inverse view rotation:
# screen right=-X, screen up=(0,cos(pitch),sin(pitch)).
$taskX=-[double]$taskData.viewX
$taskY=[Math]::Cos($taskPitch)*$taskData.viewY+[Math]::Sin($taskPitch)*$taskData.viewZ
$taskZ=[Math]::Sin($taskPitch)*$taskData.viewY-[Math]::Cos($taskPitch)*$taskData.viewZ
$taskMetric=[Math]::Max([Math]::Sqrt($taskX*$taskX+$taskZ*$taskZ),[Math]::Abs($taskY))
foreach($taskFraction in @(0,0.5,0.75)){
    $taskDistance=(1-$taskFraction)*$taskMetric
    [pscustomobject]@{Fraction=$taskFraction;PlayerX=2120.5+$taskFraction*$taskX;PlayerY=65+$taskFraction*$taskY;PlayerZ=522.5+$taskFraction*$taskZ;PredictedFogMetric=$taskDistance;PredictedBorderFog=1-[Math]::Exp(-3*[Math]::Pow($taskDistance/$taskData.far,16))}
}
