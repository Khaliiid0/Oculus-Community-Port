param(
    [ValidatePattern('^[0-9]+\.[0-9]+\.[0-9]+-(?:alpha|beta|preview|rc)\.[0-9]+$')]
    [string]$Version = '0.4.0-beta.2'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskRepo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path

function Read-EntryText($Entry) {
    if ($null -eq $Entry) { throw 'Required archive entry is missing' }
    $reader = [System.IO.StreamReader]::new($Entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Get-EntryHash($Entry) {
    $stream = $Entry.Open()
    $digest = [System.Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($digest.ComputeHash($stream)).Replace('-', '') }
    finally { $digest.Dispose(); $stream.Dispose() }
}

foreach ($target in @(
    @{Directory=$taskRepo; Minecraft='26.2'; Major=69},
    @{Directory=(Join-Path $taskRepo 'versions/1.21.11'); Minecraft='1.21.11'; Major=65}
)) {
    $candidate = Join-Path $target.Directory "candidates/$Version"
    $name = "oculus-forge-$($target.Minecraft)-$Version"
    foreach ($suffix in @('.jar', '-sources.jar')) {
        $file = Join-Path $candidate ($name + $suffix)
        $line = (Get-Content -LiteralPath ($file + '.sha256') -Raw).Trim()
        if ($line -notmatch '^([a-fA-F0-9]{64})  (.+)$') { throw "Invalid checksum: $file" }
        $expected = $Matches[1]
        if ($Matches[2] -cne [System.IO.Path]::GetFileName($file)) { throw "Wrong checksum filename: $file" }
        if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $expected) { throw "Checksum mismatch: $file" }
    }

    $binary = [System.IO.Compression.ZipFile]::OpenRead((Join-Path $candidate ($name + '.jar')))
    $sources = [System.IO.Compression.ZipFile]::OpenRead((Join-Path $candidate ($name + '-sources.jar')))
    try {
        foreach ($archive in @($binary, $sources)) {
            $bad = @($archive.Entries | Where-Object {
                $_.FullName -match '^(dev/oculusforge/testing/|buffer-routing/|buffer-roundtrip/|noise-preview/|custom-texture-roundtrip/|unsupported-probe/|compile-error-probe/|test-packs/|org/anarres/cpp/)'
            })
            if ($bad.Count -ne 0) { throw "Developer fixture/class or unrelocated dependency in $name" }
            $duplicates = @($archive.Entries | Group-Object FullName | Where-Object Count -gt 1)
            if ($duplicates.Count -ne 0) { throw "Duplicate archive entries in $name" }
        }
        $manifest = Read-EntryText ($binary.GetEntry('META-INF/MANIFEST.MF'))
        if ($manifest -notmatch ('(?m)^Implementation-Version: ' + [regex]::Escape($Version) + '\r?$')) {
            throw "Wrong manifest version: $name"
        }
        foreach ($required in @('META-INF/mods.toml', 'pack.mcmeta', 'assets/oculus/lang/en_us.json',
            'oculus-logo.png', 'LICENSE-oculus-forge.txt', 'LICENSE_NOTES.md', 'NOTICE',
            'META-INF/licenses/JCPP-LICENSE.txt')) {
            if ($null -eq $binary.GetEntry($required)) { throw "Missing $required in $name" }
        }
        $entry = $binary.GetEntry('dev/oculusforge/OculusForge.class')
        if ($null -eq $entry) { throw "Missing mod entrypoint: $name" }
        $stream = $entry.Open()
        try {
            $header = New-Object byte[] 8
            if ($stream.Read($header, 0, 8) -ne 8 -or ($header[6] * 256 + $header[7]) -ne $target.Major) {
                throw "Wrong Java bytecode target: $name"
            }
        } finally { $stream.Dispose() }
        $javaRoot = (Resolve-Path -LiteralPath (Join-Path $target.Directory 'src/main/java')).Path
        $javaFiles = @(Get-ChildItem -LiteralPath $javaRoot -Recurse -File -Filter '*.java')
        foreach ($java in $javaFiles) {
            $relative = $java.FullName.Substring($javaRoot.Length + 1).Replace('\', '/')
            $sourceEntry = $sources.GetEntry($relative)
            if ($null -eq $sourceEntry -or (Get-EntryHash $sourceEntry) -ne (Get-FileHash -LiteralPath $java.FullName -Algorithm SHA256).Hash) {
                throw "Missing/mismatched corresponding source: $relative"
            }
        }
        [pscustomobject]@{
            Minecraft=$target.Minecraft; Version=$Version; JavaClassMajor=$target.Major
            MatchedJavaSources=$javaFiles.Count; BinarySHA256=(Get-FileHash -LiteralPath (Join-Path $candidate ($name + '.jar')) -Algorithm SHA256).Hash
        }
    } finally { $binary.Dispose(); $sources.Dispose() }
}
