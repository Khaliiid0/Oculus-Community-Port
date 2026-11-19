param([Parameter(Mandatory=$true)][string]$Png)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$taskBitmap=[Drawing.Bitmap]::new((Resolve-Path -LiteralPath $Png).Path)
try {
    if($taskBitmap.Width -ne 960 -or $taskBitmap.Height -ne 540){throw 'Unexpected native capture dimensions'}
    $taskValues=@()
    for($taskRow=0;$taskRow -lt 8;$taskRow++){
        $taskSamples=for($taskBit=0;$taskBit -lt 32;$taskBit++){
            $taskPixel=$taskBitmap.GetPixel(22+$taskBit*4,539-(72+$taskRow*6))
            ($taskPixel.R+$taskPixel.G+$taskPixel.B)/3.0
        }
        $taskMin=($taskSamples|Measure-Object -Minimum).Minimum
        $taskMax=($taskSamples|Measure-Object -Maximum).Maximum
        if($taskMax-$taskMin -lt 50){throw "Insufficient binary contrast in row $taskRow"}
        [uint32]$taskBits=0
        for($taskBit=0;$taskBit -lt 32;$taskBit++){
            if($taskSamples[$taskBit] -gt ($taskMin+$taskMax)/2){$taskBits=$taskBits -bor ([uint32]1 -shl $taskBit)}
        }
        $taskValues+= [pscustomobject]@{Name=@('far','cameraY','cloudHeight','liveDepth','opaqueDepth','viewX','viewY','viewZ')[$taskRow];Bits=$taskBits.ToString('X8');Value=[BitConverter]::UInt32BitsToSingle($taskBits);Min=$taskMin;Max=$taskMax}
    }
    if($taskValues[0].Value -ne 128 -or [Math]::Abs($taskValues[1].Value-66.62) -gt 0.001){throw 'Known far/camera controls fail; reject bit readback'}
    if(@($taskValues|Where-Object{-not [float]::IsFinite($_.Value)}).Count){throw 'Non-finite probe result'}
    $taskValues
}finally{$taskBitmap.Dispose()}
