param([switch]$VerifyOnly)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$version='0.4.0-beta.3'
$binaryName="oculus-forge-26.2-$version.jar"
$sourcesName="oculus-forge-26.2-$version-sources.jar"
$expectedBinary='1CFAB3DC019768E2672B92CBE0E4B6E06388A6085C9B55ACDABFE21DCD5C7397'
$expectedSources='028553B31E9EBFF222D4707374C43B84100142914ECBE1ACB3FE4E813A4D39FA'
$bundleRoot=Join-Path $repoRoot "candidates/$version"
$notesRoot=Join-Path $repoRoot "release-notes/$version"

function Get-CheckedFile([string]$relative) {
    $path=[IO.Path]::GetFullPath((Join-Path $repoRoot $relative))
    if(-not $path.StartsWith($repoRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {throw "Outside repository: $relative"}
    $item=Get-Item -LiteralPath $path -Force
    if($item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {throw "Not a regular file: $relative"}
    $parent=$item.Directory
    while($parent.FullName -ne $repoRoot) {
        if($parent.Attributes -band [IO.FileAttributes]::ReparsePoint) {throw "Linked source parent: $relative"}
        $parent=$parent.Parent
        if($null -eq $parent) {throw "Invalid source parent: $relative"}
    }
    return $item.FullName
}
function Assert-Hash([string]$path,[string]$expected) {
    if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $expected) {throw "SHA-256 mismatch: $path"}
}
function Stage-File([string]$source,[string]$relative) {
    $destination=[IO.Path]::GetFullPath((Join-Path $bundleRoot $relative))
    if(-not $destination.StartsWith($bundleRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {throw 'Unsafe destination'}
    if(Test-Path -LiteralPath $destination) {
        Assert-Hash $destination (Get-FileHash -LiteralPath $source).Hash
    } else {
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination
    }
}
function Make-SourceArchive([string[]]$paths,[string]$output) {
    if(Test-Path -LiteralPath $output) {throw "Refusing to replace archive: $output"}
    $stream=[IO.File]::Open($output,[IO.FileMode]::CreateNew)
    $archive=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach($relative in ($paths|Sort-Object -Unique)) {
            $path=Get-CheckedFile $relative
            $entry=$archive.CreateEntry($relative.Replace('\','/'),[IO.Compression.CompressionLevel]::Optimal)
            $entry.LastWriteTime=[DateTimeOffset]::new(2000,1,1,0,0,0,[TimeSpan]::Zero)
            $entry.ExternalAttributes=0
            $input=[IO.File]::OpenRead($path);$outputStream=$entry.Open()
            try {$input.CopyTo($outputStream)} finally {$outputStream.Dispose();$input.Dispose()}
        }
    } finally {$archive.Dispose();$stream.Dispose()}
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
if($VerifyOnly) {
    $manifest=Join-Path $bundleRoot 'SHA256SUMS.txt'
    foreach($line in [IO.File]::ReadAllLines($manifest)) {
        if($line -notmatch '^([A-Fa-f0-9]{64})  (.+)$') {throw 'Invalid checksum manifest'}
        $path=[IO.Path]::GetFullPath((Join-Path $bundleRoot $Matches[2]))
        if(-not $path.StartsWith($bundleRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {throw 'Unsafe checksum path'}
        Assert-Hash $path $Matches[1]
    }
    Assert-Hash (Join-Path $bundleRoot "UPLOAD/$binaryName") $expectedBinary
    Assert-Hash (Join-Path $bundleRoot "SOURCE/$sourcesName") $expectedSources
    Write-Output 'Verified every bundle checksum and the pinned binary/source JARs.'
    return
}

$binary=Get-CheckedFile "build/libs/$binaryName"
$sources=Get-CheckedFile "build/libs/$sourcesName"
Assert-Hash $binary $expectedBinary;Assert-Hash $sources $expectedSources
if(Test-Path -LiteralPath $bundleRoot) {throw 'Milestone bundle already exists; use -VerifyOnly, never replace frozen files'}

# A source ZIP must include the exact main Java files contained in the sources JAR.
$sourceJar=[IO.Compression.ZipFile]::OpenRead($sources)
try {
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $repoRoot 'src/main/java') -Recurse -File) {
        $relative=[IO.Path]::GetRelativePath((Join-Path $repoRoot 'src/main/java'),$file.FullName).Replace('\','/')
        $entry=$sourceJar.GetEntry($relative)
        if($null -eq $entry) {throw "Sources JAR missing $relative"}
        $entryStream=$entry.Open();$sha=[Security.Cryptography.SHA256]::Create()
        try {$hash=[Convert]::ToHexString($sha.ComputeHash($entryStream))} finally {$sha.Dispose();$entryStream.Dispose()}
        Assert-Hash $file.FullName $hash
    }
} finally {$sourceJar.Dispose()}

$gallery=Get-Content -LiteralPath (Get-CheckedFile "release-notes/$version/gallery.json") -Raw | ConvertFrom-Json
foreach($shot in $gallery.images) {
    if($shot.file -notmatch '^[a-z0-9-]+\.png$') {throw 'Unsafe gallery filename'}
    $original=Get-CheckedFile $shot.source
    Assert-Hash $original $shot.sha256
}

# Whitelist source/build/legal/test inputs. Never traverse run profiles, caches or accounts.
$sourcePaths=[Collections.Generic.List[string]]::new()
foreach($name in 'build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','.gitattributes','.gitignore','LICENSE','LICENSE_NOTES.md','NOTICE') {$sourcePaths.Add($name)}
foreach($dir in 'src','gradle/wrapper','third_party','test-packs',"release-notes/$version") {
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $repoRoot $dir) -Recurse -File) {
        $sourcePaths.Add([IO.Path]::GetRelativePath($repoRoot,$file.FullName))
    }
}
foreach($file in Get-ChildItem -LiteralPath $repoRoot -Filter '*.md' -File) {$sourcePaths.Add($file.Name)}
foreach($name in 'build.gradle','settings.gradle','README.md') {$sourcePaths.Add("tools/artifact-smoke/$name")}
foreach($file in Get-ChildItem -LiteralPath (Join-Path $repoRoot 'tools') -Filter '*.ps1' -File) {$sourcePaths.Add("tools/$($file.Name)")}
foreach($path in $sourcePaths) {
    $null=Get-CheckedFile $path
    if($path -match '(?i)(^|[/\\])(?:run(?:-[^/\\]+)?|\.gradle|\.git|logs|saves|downloads|versions|test-evidence|candidates)([/\\]|$)' -or $path -match '(?i)\.(jar|zip|log|toml)$') {
        # TOML resources (mods.toml) are required; all other denied inputs are excluded.
        if($path.Replace('\','/') -notin @('src/main/resources/META-INF/mods.toml','gradle/wrapper/gradle-wrapper.jar')) {throw "Unexpected source input: $path"}
    }
}
$checkDir=Join-Path $repoRoot 'build/milestone-source-reprocheck'
if(Test-Path -LiteralPath $checkDir) {throw 'Reprocheck directory already exists; inspect it before reusing'}
$kitPath=Join-Path $repoRoot "candidates/oculus-community-port-26.2-$version-upload-kit.zip"
if(Test-Path -LiteralPath $kitPath) {throw 'Upload kit archive already exists; refusing to replace it'}
foreach($required in 'README.md','DESCRIPTION.md','GALLERY.html','QUALIFICATION.md','UPLOAD-CHECKLIST.md') {
    $null=Get-CheckedFile "release-notes/$version/$required"
}

# Stage only after preflight; no destructive cleanup or replacement of a frozen kit.
[IO.Directory]::CreateDirectory($bundleRoot) | Out-Null
Stage-File $binary "UPLOAD/$binaryName"
Stage-File (Get-CheckedFile "build/libs/$binaryName.sha256") "UPLOAD/$binaryName.sha256"
Stage-File $sources "SOURCE/$sourcesName"
Stage-File (Get-CheckedFile "build/libs/$sourcesName.sha256") "SOURCE/$sourcesName.sha256"
Stage-File (Get-CheckedFile "release-notes/$version/README.md") 'README.md'
foreach($note in Get-ChildItem -LiteralPath $notesRoot -File) {
    if($note.Name -notin @('README.md','GALLERY.html')) {Stage-File $note.FullName "DOCS/$($note.Name)"}
}
Stage-File (Get-CheckedFile "release-notes/$version/DESCRIPTION.md") 'DOCS/CURSEFORGE-DESCRIPTION.md'
Stage-File (Get-CheckedFile "release-notes/$version/DESCRIPTION.md") 'DOCS/MODRINTH-DESCRIPTION.md'
foreach($shot in $gallery.images) {Stage-File (Get-CheckedFile $shot.source) "GALLERY/$($shot.file)"}
Stage-File (Get-CheckedFile "release-notes/$version/GALLERY.html") 'GALLERY/index.html'

$archiveName="oculus-community-port-26.2-$version-corresponding-source.zip"
$archivePath=Join-Path $bundleRoot "SOURCE/$archiveName"
Make-SourceArchive $sourcePaths.ToArray() $archivePath
[IO.Directory]::CreateDirectory($checkDir) | Out-Null
$second=Join-Path $checkDir $archiveName
Make-SourceArchive $sourcePaths.ToArray() $second
Assert-Hash $second (Get-FileHash -LiteralPath $archivePath).Hash

$manifestLines=@(Get-ChildItem -LiteralPath $bundleRoot -File -Recurse | Sort-Object FullName | ForEach-Object {
    '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant(),[IO.Path]::GetRelativePath($bundleRoot,$_.FullName).Replace('\','/')
})
[IO.File]::WriteAllLines((Join-Path $bundleRoot 'SHA256SUMS.txt'),$manifestLines,[Text.UTF8Encoding]::new($false))
[IO.Compression.ZipFile]::CreateFromDirectory($bundleRoot,$kitPath,[IO.Compression.CompressionLevel]::Optimal,$false)
$kitHash=(Get-FileHash -LiteralPath $kitPath).Hash.ToLowerInvariant()
[IO.File]::WriteAllText($kitPath+'.sha256',"$kitHash  $([IO.Path]::GetFileName($kitPath))`n",[Text.UTF8Encoding]::new($false))
Write-Output "Prepared $bundleRoot"
Write-Output "Upload kit: $kitPath"
Write-Output 'Corresponding-source ZIP is byte-identical across two independent archive writes.'
Write-Output 'PUBLICATION HOLD: read DOCS/QUALIFICATION.md and DOCS/UPLOAD-CHECKLIST.md.'
