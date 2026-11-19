param(
    [Parameter(Mandatory=$true)][string]$Binary,
    [Parameter(Mandatory=$true)][string]$Sources,
    [string]$VersionRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
)
$ErrorActionPreference='Stop'
$taskVersion=(Resolve-Path -LiteralPath $VersionRoot).Path
$taskRepo=[IO.Path]::GetFullPath((Join-Path $taskVersion '../..'))
$taskExpected=@{
    'LICENSE-oculus-forge.txt'=Join-Path $taskRepo 'LICENSE'
    'LICENSE-GPL-3.0.txt'=Join-Path $taskVersion 'licenses/GPL-3.0.txt'
    'NOTICE'=Join-Path $taskVersion 'NOTICE'
    'LICENSE_NOTES.md'=Join-Path $taskVersion 'LICENSE_NOTES.md'
    'META-INF/licenses/JCPP-LICENSE.txt'=Join-Path $taskRepo 'third_party/JCPP-LICENSE.txt'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach($taskArchive in @($Binary,$Sources)){
    $taskZip=[IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $taskArchive).Path)
    try{
        if(@($taskZip.Entries.FullName|Select-Object -Unique).Count -ne $taskZip.Entries.Count){throw 'Duplicate archive entries'}
        # GPL first, so the immutable old checkpoint is a precise missing-text
        # negative control rather than merely a different-version failure.
        foreach($taskName in @('LICENSE-GPL-3.0.txt','LICENSE-oculus-forge.txt','NOTICE','LICENSE_NOTES.md','META-INF/licenses/JCPP-LICENSE.txt')){
            $taskEntry=$taskZip.GetEntry($taskName)
            if($null -eq $taskEntry){throw "Missing notice: $taskName"}
            $taskStream=$taskEntry.Open()
            try{$taskHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($taskStream))}finally{$taskStream.Dispose()}
            if($taskHash -cne (Get-FileHash -LiteralPath $taskExpected[$taskName]).Hash){throw "Notice bytes differ: $taskName"}
        }
        $taskReader=[IO.StreamReader]::new($taskZip.GetEntry('META-INF/MANIFEST.MF').Open())
        try{$taskManifest=$taskReader.ReadToEnd()}finally{$taskReader.Dispose()}
        if($taskManifest -notmatch 'Oculus-Publication-Status: PRIVATE-UNQUALIFIED' -or $taskManifest -notmatch 'Implementation-Version: 0\.4\.0-beta\.4-checkpoint\.4-private-audit'){throw 'Wrong private artifact identity'}
    }finally{$taskZip.Dispose()}
}
Write-Output 'PASS: binary/sources carry exact LGPL, accompanying GPL, module NOTICE/license notes and JCPP license; checkpoint 4 remains PRIVATE-UNQUALIFIED.'
