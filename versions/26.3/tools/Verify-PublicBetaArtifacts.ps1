param(
    [string]$VersionRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')),
    [string]$ArtifactDirectory
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$taskVersion=(Resolve-Path -LiteralPath $VersionRoot).Path
$taskRepo=[IO.Path]::GetFullPath((Join-Path $taskVersion '../..'))
if(-not $ArtifactDirectory){$ArtifactDirectory=Join-Path $taskVersion 'build/libs'}
$taskExpected=@{
    'LICENSE-oculus-forge.txt'=Join-Path $taskRepo 'LICENSE'
    'LICENSE-GPL-3.0.txt'=Join-Path $taskVersion 'licenses/GPL-3.0.txt'
    'NOTICE'=Join-Path $taskVersion 'NOTICE'
    'LICENSE_NOTES.md'=Join-Path $taskVersion 'LICENSE_NOTES.md'
    'META-INF/licenses/JCPP-LICENSE.txt'=Join-Path $taskRepo 'third_party/JCPP-LICENSE.txt'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach($taskSuffix in @('','-sources')){
    $taskPath=Join-Path $ArtifactDirectory "oculus-forge-26.3-0.4.0-beta.4$taskSuffix.jar"
    $taskZip=[IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $taskPath).Path)
    try{
        $taskNames=@($taskZip.Entries.FullName)
        if(@($taskNames|Select-Object -Unique).Count -ne $taskNames.Count){throw 'Duplicate archive path'}
        foreach($taskName in $taskExpected.Keys){
            $taskEntry=$taskZip.GetEntry($taskName)
            if($null -eq $taskEntry){throw "Missing notice: $taskName"}
            $taskStream=$taskEntry.Open()
            try{$taskHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($taskStream))}finally{$taskStream.Dispose()}
            if($taskHash -cne (Get-FileHash -LiteralPath $taskExpected[$taskName]).Hash){throw "Notice differs: $taskName"}
        }
        $taskReader=[IO.StreamReader]::new($taskZip.GetEntry('META-INF/MANIFEST.MF').Open())
        try{$taskManifest=$taskReader.ReadToEnd().Replace("`r`n ",'')}finally{$taskReader.Dispose()}
        if($taskManifest -notmatch '(?m)^Implementation-Version: 0\.4\.0-beta\.4\r?$' -or
            $taskManifest -match 'Oculus-Publication-Status|PRIVATE-UNQUALIFIED|checkpoint|private-audit'){throw 'Not final public beta metadata'}
        if($taskSuffix -eq ''){
            $taskReader=[IO.StreamReader]::new($taskZip.GetEntry('META-INF/mods.toml').Open())
            try{$taskMetadata=$taskReader.ReadToEnd()}finally{$taskReader.Dispose()}
            foreach($taskRequired in @('version="0.4.0-beta.4"','versionRange="[26.3]"','versionRange="[66.1.2,67)"','license="LGPL-3.0-only"','World rendering is OFF by default.')){
                if(-not $taskMetadata.Contains($taskRequired)){throw "Missing metadata: $taskRequired"}
            }
            if($taskMetadata -match 'Development checkpoint|PRIVATE-UNQUALIFIED|private-audit'){throw 'Private mod metadata'}
            if(@($taskNames|Where-Object{$_ -match '^(test-packs|runtime-fixtures)/|^dev/oculusforge/(testing|audit)/|oculus-fault-audit|\.(glsl|vsh|fsh)$|/shaders\.properties$'}).Count){throw 'Runtime artifact contains developer shader/fault fixtures'}
            foreach($taskRequired in @('oculus.mixins.json','pack.mcmeta','oculus-logo.png','dev/oculusforge/bundle/jcpp/Preprocessor.class')){
                if($taskRequired -cnotin $taskNames){throw "Missing production input: $taskRequired"}
            }
        }
    }finally{$taskZip.Dispose()}
    [pscustomobject]@{Status='PUBLIC-ARTIFACT-CONTENTS-PASS'; File=[IO.Path]::GetFileName($taskPath); SHA256=(Get-FileHash -LiteralPath $taskPath).Hash}
}
