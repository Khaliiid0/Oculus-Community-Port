param(
    [ValidatePattern('^[a-z0-9-]+$')][string]$SnapshotId = 'beta4-20261119',
    [switch]$PlanOnly,
    [switch]$PolicyOnly
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
function Assert-SourceArchivePath {
    param([Parameter(Mandatory)][string]$Path)
    if ([IO.Path]::IsPathRooted($Path) -or $Path -match '(^|/)\.\.(/|$)|[\x00-\x1F:]|//|\\' -or $Path.StartsWith('/')) { throw 'Unsafe source path' }
    if ($Path -match '(^|/)(\.git|\.gradle|build|build-private-candidate|installed-audit|run(?:-[^/]+)?|logs|saves|screenshots|test-evidence|release|candidates)/') { throw 'Generated/private source input' }
    if ($Path -match '(?i)\.(class|zip|log|key|pem|pfx|p12|env)$' -or ($Path -match '(?i)\.jar$' -and $Path -cne 'gradle/wrapper/gradle-wrapper.jar')) { throw 'Unexpected binary/private input' }
}
if ($PolicyOnly) { return }
$taskVersion = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskRepo = [IO.Path]::GetFullPath((Join-Path $taskVersion '../..'))
$taskBoundary = $taskRepo + [IO.Path]::DirectorySeparatorChar
$taskFixed = @('.gitattributes','.gitignore','gradlew','gradlew.bat','LICENSE','LICENSE_NOTES.md','NOTICE','README.md','ARCHITECTURE.md',
    'versions/26.3/.gitignore','versions/26.3/build.gradle','versions/26.3/settings.gradle','versions/26.3/gradle.properties',
    'versions/26.3/SOURCE_BUILD.md','versions/26.3/LIMITED_BETA_PLAN.md','versions/26.3/LIMITED_BETA_VISUAL_MATRIX.md',
    'versions/26.3/NOTICE','versions/26.3/LICENSE_NOTES.md','versions/26.3/README.md',
    'versions/26.3/SHADER_OPTIONS.md','versions/26.3/PUBLIC_BETA_ACCEPTANCE.md','versions/26.3/release-acceptance.json')
$taskPrefixes = @('src/','test-packs/','tools/','gradle/wrapper/','third_party/','versions/26.3/src/',
    'versions/26.3/runtime-fixtures/','versions/26.3/tools/','versions/26.3/licenses/')
$taskListed = @(& git -c core.quotePath=false -C $taskRepo ls-files --cached --others --exclude-standard)
if ($LASTEXITCODE -ne 0) { throw 'git source inventory failed' }
$taskFiles = [Collections.Generic.List[object]]::new()
$taskTotal = 0L
foreach ($taskRelative in @($taskListed | Sort-Object -Unique)) {
    $taskNormalized = $taskRelative.Replace('\','/')
    $taskAllowed = $taskNormalized -cin $taskFixed
    foreach ($taskPrefix in $taskPrefixes) { if ($taskNormalized.StartsWith($taskPrefix,[StringComparison]::Ordinal)) { $taskAllowed = $true } }
    if (-not $taskAllowed) { continue }
    Assert-SourceArchivePath -Path $taskNormalized
    $taskPath = [IO.Path]::GetFullPath((Join-Path $taskRepo $taskRelative))
    if (-not $taskPath.StartsWith($taskBoundary,[StringComparison]::OrdinalIgnoreCase)) { throw 'Source escaped repository' }
    $taskItem = Get-Item -LiteralPath $taskPath
    if ($taskItem.PSIsContainer -or ($taskItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $taskItem.Length -gt 10MB) { throw 'Unexpected source file type/size' }
    $taskParent = $taskItem.Directory
    while ($taskParent.FullName -ne $taskRepo) {
        if (-not $taskParent.FullName.StartsWith($taskBoundary,[StringComparison]::OrdinalIgnoreCase) -or ($taskParent.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Source parent escaped repository or is a link' }
        $taskParent = $taskParent.Parent
    }
    $taskTotal += $taskItem.Length
    if ($taskTotal -gt 64MB) { throw 'Source archive exceeds size budget' }
    $taskFiles.Add([pscustomobject]@{Path=$taskNormalized; FullPath=$taskPath; Bytes=$taskItem.Length; SHA256=(Get-FileHash -LiteralPath $taskPath).Hash})
}
if ($taskFiles.Count -lt 500 -or $taskFiles.Count -gt 2000) { throw "Unexpected source count: $($taskFiles.Count)" }
foreach ($taskRequired in $taskFixed + @('gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties','third_party/JCPP-LICENSE.txt','versions/26.3/licenses/GPL-3.0.txt')) {
    if ($taskRequired -cnotin $taskFiles.Path) { throw "Missing build/source input: $taskRequired" }
}
if ($PlanOnly) { [pscustomobject]@{Status='PLAN-ONLY'; Files=$taskFiles.Count; Bytes=$taskTotal}; return }
$taskVersionRows=@(Get-Content -LiteralPath (Join-Path $taskVersion 'gradle.properties')|Where-Object{$_ -cmatch '^mod_version='})
if($taskVersionRows.Count -ne 1){throw 'Missing/duplicate module version'}
$taskModVersion=$taskVersionRows[0].Substring(12)
if($taskModVersion -cnotmatch '^[0-9A-Za-z][0-9A-Za-z.-]+$'){throw 'Unsafe module version'}
$taskPrivate = $taskModVersion -match '-checkpoint\.'
if (-not $taskPrivate -and $taskModVersion -cne '0.4.0-beta.4') { throw 'Unreviewed public source version' }
$taskOutputRoot = if ($taskPrivate) { 'build-private-candidate' } else { 'build' }
$taskOutput = Join-Path $taskVersion "$taskOutputRoot/corresponding-source/$SnapshotId"
if (Test-Path -LiteralPath $taskOutput) { throw 'Refuse to overwrite an existing snapshot directory' }
$taskPrefix = if ($taskPrivate) { 'Oculus-Community-Port-26.3-private-source/' } else { 'Oculus-Community-Port-26.3-source/' }
$taskArchiveName = if ($taskPrivate) { "oculus-26.3-$taskModVersion-private-source.zip" } else { "oculus-26.3-$taskModVersion-source.zip" }
New-Item -ItemType Directory -Path $taskOutput | Out-Null
$taskArchivePath = Join-Path $taskOutput $taskArchiveName
$taskManifestPath = Join-Path $taskOutput 'source-inventory.csv'
$taskFiles | Select-Object Path,Bytes,SHA256 | Export-Csv -LiteralPath $taskManifestPath -NoTypeInformation
Add-Type -AssemblyName System.IO.Compression
$taskStream = [IO.File]::Open($taskArchivePath,[IO.FileMode]::CreateNew,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try {
    $taskZip = [IO.Compression.ZipArchive]::new($taskStream,[IO.Compression.ZipArchiveMode]::Create,$true)
    try {
        foreach ($taskFile in $taskFiles) {
            $taskEntry = $taskZip.CreateEntry($taskPrefix+$taskFile.Path,[IO.Compression.CompressionLevel]::Optimal)
            $taskEntry.LastWriteTime = [DateTimeOffset]::new(2000,1,1,0,0,0,[TimeSpan]::Zero)
            $taskInput = [IO.File]::OpenRead($taskFile.FullPath); $taskDestination = $taskEntry.Open()
            try { $taskInput.CopyTo($taskDestination) } finally { $taskDestination.Dispose(); $taskInput.Dispose() }
        }
    } finally { $taskZip.Dispose() }
} finally { $taskStream.Dispose() }
# Any incomplete archive is preserved for diagnosis, never silently retried or
# deleted. Completion requires a successful read-back of every exact entry.
$taskZip = [IO.Compression.ZipFile]::OpenRead($taskArchivePath)
try {
    if ($taskZip.Entries.Count -ne $taskFiles.Count) { throw 'ZIP entry count mismatch' }
    $taskNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($taskEntry in $taskZip.Entries) { if (-not $taskNames.Add($taskEntry.FullName)) { throw 'Duplicate ZIP path' } }
    foreach ($taskFile in $taskFiles) {
        $taskEntry = $taskZip.GetEntry($taskPrefix+$taskFile.Path)
        if (-not $taskEntry -or $taskEntry.Length -ne $taskFile.Bytes) { throw 'Missing/truncated ZIP input' }
        $taskInput = $taskEntry.Open(); $taskSha = [Security.Cryptography.SHA256]::Create()
        try { $taskHash = [Convert]::ToHexString($taskSha.ComputeHash($taskInput)) } finally { $taskSha.Dispose(); $taskInput.Dispose() }
        if ($taskHash -ne $taskFile.SHA256) { throw 'ZIP differs from source inventory' }
    }
} finally { $taskZip.Dispose() }
[pscustomobject]@{Path=$taskArchiveName; SHA256=(Get-FileHash -LiteralPath $taskArchivePath).Hash} | Export-Csv -LiteralPath (Join-Path $taskOutput 'archive-sha256.csv') -NoTypeInformation
[pscustomobject]@{Status='SOURCE-CONTENTS-VERIFIED'; Archive=$taskArchivePath; Files=$taskFiles.Count; Bytes=$taskTotal; SHA256=(Get-FileHash -LiteralPath $taskArchivePath).Hash}
