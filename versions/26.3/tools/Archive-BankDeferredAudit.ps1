$ErrorActionPreference='Stop'
$taskVersion=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskInstance=Join-Path $taskVersion 'installed-audit/bank-deferred-20261118'
$taskEvidence=Join-Path $taskVersion 'test-evidence/2026-11-18-deferred-input'
if((Get-Content -LiteralPath (Join-Path $taskInstance 'deferred-input-exit.txt') -Raw).Trim() -ne '0'){throw 'Client has not exited normally'}
$taskProbe=Join-Path $taskInstance 'shaderpacks/oculus-comp-deferred-input-probe'
$taskBootstrap=Join-Path $taskInstance 'saves/Oculus263 Gameplay 20261117/datapacks/oculus-bank-stage'
$taskArchive=Join-Path $taskInstance 'disabled-diagnostic-inputs'
if(Test-Path -LiteralPath $taskArchive){throw 'Preserve earlier archive'}
foreach($taskTarget in @($taskProbe,$taskBootstrap)){
    $taskPath=(Resolve-Path -LiteralPath $taskTarget).Path
    if(-not $taskPath.StartsWith($taskInstance+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Target escaped isolated instance'}
    $taskItem=Get-Item -LiteralPath $taskPath -Force
    if(-not $taskItem.PSIsContainer){throw 'Expected directory'}
    if(@(@($taskItem)+@(Get-ChildItem -LiteralPath $taskPath -Recurse -Force)|Where-Object{$_.Attributes -band [IO.FileAttributes]::ReparsePoint}).Count){throw 'Refuse linked diagnostic input'}
}
if(@(Get-ChildItem -LiteralPath $taskBootstrap -File -Recurse -Force).Count -ne 4){throw 'Unexpected bootstrap'}
if(@(Get-ChildItem -LiteralPath $taskProbe -File -Recurse -Force).Count -ne 407){throw 'Unexpected derived-pack count'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskZipPath=Join-Path $taskInstance 'shaderpacks/ComplementaryReimagined_r5.7.1.zip'
if((Get-FileHash -LiteralPath $taskZipPath).Hash -ne '24A20634A7832D422D3CD5023BE16829F26840E25C69404DD418306EA79F63F0'){throw 'Original ZIP changed'}
$taskZip=[IO.Compression.ZipFile]::OpenRead($taskZipPath)
try{
    $taskEntries=@($taskZip.Entries|Where-Object{-not $_.FullName.EndsWith('/')})
    if($taskEntries.Count -ne 404){throw 'Unexpected original entries'}
    $taskRows=foreach($taskEntry in $taskEntries){
        $taskRelative=$taskEntry.FullName
        $taskPath=[IO.Path]::GetFullPath((Join-Path $taskProbe $taskRelative))
        if(-not $taskPath.StartsWith($taskProbe+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'ZIP path escaped'}
        $taskStream=$taskEntry.Open()
        try{$taskHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($taskStream))}finally{$taskStream.Dispose()}
        $taskActual=(Get-FileHash -LiteralPath $taskPath).Hash
        $taskByteMatch=$taskHash -ceq $taskActual
        $taskKind='Identical'
        if($taskRelative -cin @('shaders/program/deferred1.glsl','shaders/program/composite1.glsl')){
            $taskReader=[IO.StreamReader]::new($taskEntry.Open())
            try{$taskOriginal=$taskReader.ReadToEnd().Replace("`r`n","`n")}finally{$taskReader.Dispose()}
            $taskCurrent=(Get-Content -LiteralPath $taskPath -Raw).Replace("`r`n","`n")
            if($taskRelative -ceq 'shaders/program/deferred1.glsl'){
                $taskCurrent=$taskCurrent.Replace("#undef BORDER_FOG // LOCAL-ONLY diagnostic bypass, NOT a release fix.`n",'')
                $taskKind='DiagnosticBorderFogUndefOnly'
            }else{$taskKind='OriginalContentLineEndingsOnly'}
            if($taskCurrent -cne $taskOriginal){throw "Unaccounted shader edit: $taskRelative"}
        }elseif(-not $taskByteMatch){throw "Original input changed unexpectedly: $taskRelative"}
        [pscustomobject]@{Path=$taskRelative;OriginalSHA256=$taskHash;CurrentSHA256=$taskActual;ByteIdentical=$taskByteMatch;ExpectedContent=$true;Classification=$taskKind}
    }
    $taskWrapper=$taskZip.GetEntry('shaders/world0/deferred1.fsh').Open()
    try{$taskHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($taskWrapper))}finally{$taskWrapper.Dispose()}
    if((Get-FileHash -LiteralPath (Join-Path $taskProbe 'shaders/world0/deferred1.original-source.txt')).Hash -cne $taskHash){throw 'Original wrapper backup changed'}
}finally{$taskZip.Dispose()}
foreach($taskPair in @(@('cloud-overlay.glsl','oculus-bank-probe.glsl'),@('composite-overlay.glsl','oculus-composite-probe.glsl'))){
    if((Get-FileHash -LiteralPath (Join-Path $taskVersion "runtime-fixtures/bank-deferred-input/$($taskPair[0])")).Hash -cne (Get-FileHash -LiteralPath (Join-Path $taskProbe "shaders/$($taskPair[1])")).Hash){throw 'Unexpected authored include'}
}
$taskRows|Export-Csv -LiteralPath (Join-Path $taskEvidence 'original-input-comparison.csv') -NoTypeInformation
foreach($taskName in @('deferred-input-game.log','deferred-input-console.log','deferred-input-exit.txt','options.txt','production-client.args')){Copy-Item -LiteralPath (Join-Path $taskInstance $taskName) -Destination $taskEvidence}
foreach($taskName in @('oculus.properties','oculus-renderer.properties')){Copy-Item -LiteralPath (Join-Path $taskInstance "config/$taskName") -Destination (Join-Path $taskEvidence "run-$taskName")}
foreach($taskName in @('Start-LightingShoreAudit.ps1','Read-BankProbeBits.ps1','Archive-BankDeferredAudit.ps1')){Copy-Item -LiteralPath (Join-Path $PSScriptRoot $taskName) -Destination $taskEvidence}
Copy-Item -LiteralPath $taskBootstrap -Destination (Join-Path $taskEvidence 'authored-bootstrap') -Recurse
# The two exact source directories above were resolved, enumerated and checked
# for expected type/count/contents and links. Preserve them by reversible moves.
New-Item -ItemType Directory -Path $taskArchive | Out-Null
Move-Item -LiteralPath $taskProbe -Destination (Join-Path $taskArchive 'oculus-comp-deferred-input-probe')
Move-Item -LiteralPath $taskBootstrap -Destination (Join-Path $taskArchive 'oculus-bank-stage')
Write-Output 'PASS: 404 original entries accounted for; local diagnostic delta only; two exact directories archived reversibly.'
