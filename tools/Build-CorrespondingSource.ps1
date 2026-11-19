[CmdletBinding()]
param(
    [string]$Version = '0.4.0-beta.2',
    [string]$GitExecutable = 'git'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+-(?:alpha|beta|preview|rc)\.[0-9]+$') {
    throw "Unsafe or unsupported version: $Version"
}

$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$candidateDirectory = [IO.Path]::GetFullPath((Join-Path $root "candidates/$Version"))
$expectedBoundary = $candidateDirectory.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if (-not $expectedBoundary.StartsWith($root.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw "Candidate directory escaped the repository: $candidateDirectory"
}
[IO.Directory]::CreateDirectory($candidateDirectory) | Out-Null

$archiveName = "oculus-community-port-$Version-corresponding-source.zip"
$archivePath = [IO.Path]::GetFullPath((Join-Path $candidateDirectory $archiveName))
$sidecarPath = "$archivePath.sha256"
if (Test-Path -LiteralPath $archivePath -PathType Leaf) {
    throw "Refusing to overwrite existing source archive: $archivePath"
}
if (Test-Path -LiteralPath $sidecarPath -PathType Leaf) {
    throw "Refusing to overwrite existing checksum: $sidecarPath"
}

$safeDirectory = $root.Replace('\', '/')
$listed = & $GitExecutable -c "safe.directory=$safeDirectory" -c 'core.quotePath=false' -C $root `
    ls-files --cached --others --exclude-standard
if ($LASTEXITCODE -ne 0) {
    throw "git ls-files failed with exit code $LASTEXITCODE"
}

$paths = @($listed | Where-Object { $_ -ne '' } | Sort-Object -Unique)
if ($paths.Count -lt 100 -or $paths.Count -gt 1000) {
    throw "Unexpected corresponding-source file count: $($paths.Count)"
}

$forbiddenPrefixes = @('.git/', '.gradle/', 'build/', 'candidates/', 'release/', 'run/',
    'test-evidence/', 'versions/1.21.11/.gradle/', 'versions/1.21.11/build/',
    'versions/1.21.11/candidates/', 'versions/1.21.11/release/', 'versions/1.21.11/run/')
$files = New-Object 'System.Collections.Generic.List[object]'
$totalBytes = 0L
foreach ($relative in $paths) {
    $normalized = $relative.Replace('\', '/')
    $hasControlCharacter = $false
    foreach ($character in $normalized.ToCharArray()) {
        if ([char]::IsControl($character)) {
            $hasControlCharacter = $true
            break
        }
    }
    if ([IO.Path]::IsPathRooted($relative) -or $normalized.StartsWith('/') -or
        $normalized -match '(^|/)\.\.(/|$)' -or $normalized.IndexOf([char]0) -ge 0 -or
        $hasControlCharacter) {
        throw "Unsafe source path: $relative"
    }
    foreach ($prefix in $forbiddenPrefixes) {
        if ($normalized.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Forbidden generated/private path in source set: $relative"
        }
    }

    $fullPath = [IO.Path]::GetFullPath((Join-Path $root $relative))
    if (-not ($fullPath.StartsWith($root.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase))) {
        throw "Source path escaped the repository: $relative"
    }
    $item = Get-Item -LiteralPath $fullPath
    if ($item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "Source entry is not a regular non-link file: $relative"
    }
    if ($item.Length -gt 10MB) {
        throw "Source entry exceeds 10 MiB: $relative"
    }
    if ([long]$item.Length -gt ([long]::MaxValue - $totalBytes)) {
        throw 'Corresponding-source size overflow'
    }
    $totalBytes += [long]$item.Length
    if ($totalBytes -gt 64MB) {
        throw 'Corresponding source exceeds the 64 MiB safety budget'
    }
    $files.Add([pscustomobject]@{ Relative = $normalized; FullPath = $fullPath; Length = $item.Length })
}

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$fixedTimestamp = [DateTimeOffset]::new(2000, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
$prefix = "Oculus-Community-Port-$Version/"
$fileStream = [IO.File]::Open($archivePath, [IO.FileMode]::CreateNew, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
try {
    $archive = [IO.Compression.ZipArchive]::new($fileStream, [IO.Compression.ZipArchiveMode]::Create, $true)
    try {
        foreach ($file in $files) {
            $entry = $archive.CreateEntry($prefix + $file.Relative, [IO.Compression.CompressionLevel]::Optimal)
            $entry.LastWriteTime = $fixedTimestamp
            $source = [IO.File]::OpenRead($file.FullPath)
            $destination = $entry.Open()
            try {
                $source.CopyTo($destination)
            } finally {
                $destination.Dispose()
                $source.Dispose()
            }
        }
    } finally {
        $archive.Dispose()
    }
} catch {
    $fileStream.Dispose()
    [IO.File]::Delete($archivePath)
    throw
} finally {
    $fileStream.Dispose()
}

$verificationStream = [IO.File]::OpenRead($archivePath)
$verificationFailed = $false
try {
    $archive = [IO.Compression.ZipArchive]::new($verificationStream, [IO.Compression.ZipArchiveMode]::Read, $false)
    try {
        if ($archive.Entries.Count -ne $files.Count) {
            throw "Source archive entry count mismatch: $($archive.Entries.Count) != $($files.Count)"
        }
        $entries = @{}
        foreach ($entry in $archive.Entries) {
            if ($entries.ContainsKey($entry.FullName)) {
                throw "Duplicate source archive entry: $($entry.FullName)"
            }
            $entries[$entry.FullName] = $entry
        }
        foreach ($file in $files) {
            $entryName = $prefix + $file.Relative
            if (-not $entries.ContainsKey($entryName)) {
                throw "Missing source archive entry: $entryName"
            }
            $sourceHash = (Get-FileHash -LiteralPath $file.FullPath -Algorithm SHA256).Hash
            $entryStream = $entries[$entryName].Open()
            try {
                $hasher = [Security.Cryptography.SHA256]::Create()
                try {
                    $entryHash = [Convert]::ToHexString($hasher.ComputeHash($entryStream))
                } finally {
                    $hasher.Dispose()
                }
            } finally {
                $entryStream.Dispose()
            }
            if ($sourceHash -ne $entryHash) {
                throw "Source archive content mismatch: $entryName"
            }
        }
    } finally {
        $archive.Dispose()
    }
} catch {
    $verificationFailed = $true
    throw
} finally {
    $verificationStream.Dispose()
    if ($verificationFailed) {
        [IO.File]::Delete($archivePath)
    }
}

$archiveHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText($sidecarPath, "$archiveHash  $archiveName`n", [Text.UTF8Encoding]::new($false))

[pscustomobject]@{
    Archive = $archivePath
    Files = $files.Count
    UncompressedBytes = $totalBytes
    Sha256 = $archiveHash
}
