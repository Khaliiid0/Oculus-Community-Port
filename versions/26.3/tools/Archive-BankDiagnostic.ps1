$ErrorActionPreference = 'Stop'
$taskVersion = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskInstance = Join-Path $taskVersion 'installed-audit/bank-stage-20261118'
$taskEvidence = Join-Path $taskVersion 'test-evidence/2026-11-18-bank-stage'
if ((Get-Content -LiteralPath (Join-Path $taskInstance 'bank-capture-exit.txt') -Raw).Trim() -ne '0') { throw 'Diagnostic client has not exited normally' }
$taskProbe = Join-Path $taskInstance 'shaderpacks/oculus-comp-bank-depth-probe'
$taskBootstrap = Join-Path $taskInstance 'saves/Oculus263 Gameplay 20261117/datapacks/oculus-bank-stage'
$taskArchive = Join-Path $taskInstance 'disabled-diagnostic-inputs'
if (Test-Path -LiteralPath $taskArchive) { throw 'Preserve existing diagnostic archive' }
foreach ($taskTarget in @($taskProbe, $taskBootstrap)) {
    $taskResolved = (Resolve-Path -LiteralPath $taskTarget).Path
    if (-not $taskResolved.StartsWith($taskInstance + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Target escaped isolated instance' }
    $taskItem = Get-Item -LiteralPath $taskResolved -Force
    if (-not $taskItem.PSIsContainer) { throw 'Expected directory' }
    $taskItems = @($taskItem) + @(Get-ChildItem -LiteralPath $taskResolved -Force -Recurse)
    if (@($taskItems | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) { throw 'Refuse linked diagnostic input' }
}
if (@(Get-ChildItem -LiteralPath $taskBootstrap -File -Recurse -Force).Count -ne 4) { throw 'Unexpected bootstrap file count' }
$taskZipPath = Join-Path $taskInstance 'shaderpacks/ComplementaryReimagined_r5.7.1.zip'
if ((Get-FileHash -LiteralPath $taskZipPath).Hash -ne '24A20634A7832D422D3CD5023BE16829F26840E25C69404DD418306EA79F63F0') { throw 'Original ZIP changed' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskZip = [IO.Compression.ZipFile]::OpenRead($taskZipPath)
try {
    $taskEntries = @($taskZip.Entries | Where-Object { -not $_.FullName.EndsWith('/') })
    if (@(Get-ChildItem -LiteralPath $taskProbe -File -Recurse -Force).Count -ne $taskEntries.Count + 1) { throw 'Unexpected derived-pack file count' }
    $taskRows = foreach ($taskEntry in $taskEntries) {
        $taskOriginal = $taskEntry.FullName
        $taskRelative = if ($taskOriginal -ceq 'shaders/world0/final.fsh') { 'shaders/world0/final.original-source.txt' } else { $taskOriginal }
        $taskFile = [IO.Path]::GetFullPath((Join-Path $taskProbe $taskRelative))
        if (-not $taskFile.StartsWith($taskProbe + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'ZIP path escaped derived folder' }
        $taskStream = $taskEntry.Open()
        try { $taskHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($taskStream)) } finally { $taskStream.Dispose() }
        if ((Get-FileHash -LiteralPath $taskFile).Hash -ne $taskHash) { throw "Original extracted shader input changed: $taskOriginal" }
        [pscustomobject]@{ OriginalEntry=$taskOriginal; RetainedAs=$taskRelative; SHA256=$taskHash; OriginalMatch=$true }
    }
} finally { $taskZip.Dispose() }
if ((Get-FileHash -LiteralPath (Join-Path $taskProbe 'shaders/world0/final.fsh')).Hash -ne (Get-FileHash -LiteralPath (Join-Path $taskEvidence 'border-distance-predicate-final.fsh')).Hash) { throw 'Final probe source not archived exactly' }
$taskRows | Export-Csv -LiteralPath (Join-Path $taskEvidence 'original-entry-comparison.csv') -NoTypeInformation
foreach ($taskFile in @('bank-capture-game.log','bank-capture-console.log','bank-capture-exit.txt','options.txt','production-client.args')) {
    Copy-Item -LiteralPath (Join-Path $taskInstance $taskFile) -Destination (Join-Path $taskEvidence $taskFile)
}
foreach ($taskFile in @('oculus.properties','oculus-renderer.properties')) {
    Copy-Item -LiteralPath (Join-Path $taskInstance "config/$taskFile") -Destination (Join-Path $taskEvidence "run-$taskFile")
}
$taskStageRoot = Join-Path $taskInstance 'oculus-diagnostics'
$taskStages = @(Get-ChildItem -LiteralPath $taskStageRoot -File -Recurse -Force)
if ($taskStages.Count -ne 19 -or @($taskStages | Where-Object { $_.Extension -cne '.png' }).Count) { throw 'Unexpected bounded stage captures' }
if (@(@(Get-Item -LiteralPath $taskStageRoot -Force) + @(Get-ChildItem -LiteralPath $taskStageRoot -Recurse -Force) | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) { throw 'Linked stage input' }
Copy-Item -LiteralPath $taskStageRoot -Destination (Join-Path $taskEvidence 'stage-previews') -Recurse
Copy-Item -LiteralPath $taskBootstrap -Destination (Join-Path $taskEvidence 'authored-bootstrap') -Recurse
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Start-LightingShoreAudit.ps1') -Destination $taskEvidence
Copy-Item -LiteralPath $PSCommandPath -Destination $taskEvidence
# Both exact source directories were resolved, enumerated, type/count/link checked
# above. Move only these isolated diagnostic inputs; preserve all contents.
New-Item -ItemType Directory -Path $taskArchive | Out-Null
Move-Item -LiteralPath $taskProbe -Destination (Join-Path $taskArchive 'oculus-comp-bank-depth-probe')
Move-Item -LiteralPath $taskBootstrap -Destination (Join-Path $taskArchive 'oculus-bank-stage')
Write-Output "PASS: $($taskRows.Count) original ZIP entries unchanged; 19 stage previews retained; two diagnostic directories archived reversibly."
