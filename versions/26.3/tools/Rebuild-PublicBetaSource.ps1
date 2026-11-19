param(
    [ValidatePattern('^[a-z0-9-]+$')][string]$SnapshotId='beta4-20261119-source-a',
    [ValidatePattern('^[a-z0-9-]+$')][string]$RunId='public-source-rebuild-20261119'
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$taskVersion=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskWork=[IO.Path]::GetFullPath((Join-Path $taskVersion '../../..'))
$taskEvidence=Join-Path $taskVersion 'test-evidence/2026-11-19-public-beta'
$taskSnapshot=Join-Path $taskVersion "build/corresponding-source/$SnapshotId"
$taskInventory=@(Import-Csv -LiteralPath (Join-Path $taskSnapshot 'source-inventory.csv'))
$taskArchives=@(Import-Csv -LiteralPath (Join-Path $taskSnapshot 'archive-sha256.csv'))
if($taskArchives.Count -ne 1 -or $taskArchives[0].Path -cne 'oculus-26.3-0.4.0-beta.4-source.zip' -or
    $taskInventory.Count -lt 1000 -or $taskInventory.Count -gt 2000){throw 'Unexpected public source snapshot'}
$taskArchive=Join-Path $taskSnapshot $taskArchives[0].Path
if((Get-FileHash -LiteralPath $taskArchive).Hash -cne $taskArchives[0].SHA256){throw 'Source archive hash differs'}
$taskDestination=Join-Path $taskVersion "build/$RunId"
if(Test-Path -LiteralPath $taskDestination){throw 'Preserve existing extracted source build'}
$taskPrefix='Oculus-Community-Port-26.3-source/'
Add-Type -AssemblyName System.IO.Compression.FileSystem
. (Join-Path $PSScriptRoot 'Prepare-CorrespondingSource.ps1') -PolicyOnly
$taskZip=[IO.Compression.ZipFile]::OpenRead($taskArchive)
try{
    if($taskZip.Entries.Count -ne $taskInventory.Count -or @($taskZip.Entries.FullName|Select-Object -Unique).Count -ne $taskInventory.Count){throw 'Duplicate/missing archive paths'}
    foreach($taskRow in $taskInventory){
        Assert-SourceArchivePath -Path $taskRow.Path
        $taskEntry=$taskZip.GetEntry($taskPrefix+$taskRow.Path)
        if($null -eq $taskEntry -or $taskEntry.Length -ne [long]$taskRow.Bytes -or (($taskEntry.ExternalAttributes -shr 16) -band 0xF000) -eq 0xA000){throw 'Unexpected archive entry'}
        $taskTarget=[IO.Path]::GetFullPath((Join-Path $taskDestination $taskEntry.FullName))
        if(-not $taskTarget.StartsWith($taskDestination+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Archive escaped destination'}
        $taskStream=$taskEntry.Open()
        try{$taskHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($taskStream))}finally{$taskStream.Dispose()}
        if($taskHash -cne $taskRow.SHA256){throw 'Archive differs from source inventory'}
    }
}finally{$taskZip.Dispose()}
[IO.Compression.ZipFile]::ExtractToDirectory($taskArchive,$taskDestination)
$taskCleanRoot=Join-Path $taskDestination $taskPrefix.TrimEnd('/')
if(@(Get-ChildItem -LiteralPath $taskCleanRoot -Recurse -Force -File).Count -ne $taskInventory.Count -or
    @(Get-ChildItem -LiteralPath $taskCleanRoot -Recurse -Force|Where-Object{$_.Attributes -band [IO.FileAttributes]::ReparsePoint}).Count){throw 'Not a clean extracted tree'}
$env:JAVA_HOME=Join-Path $taskWork 'gradle-home/jdks/eclipse_adoptium-25-amd64-windows.2'
$env:GRADLE_USER_HOME=Join-Path $taskWork 'gradle-home'
if(-not(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))){throw 'Existing Java 25 toolchain unavailable'}
Push-Location -LiteralPath $taskCleanRoot
try{
    foreach($taskMode in @('default','extended-hdr')){
        $taskLog=Join-Path $taskEvidence "clean-public-$taskMode.log"
        if(Test-Path -LiteralPath $taskLog){throw 'Preserve existing clean build log'}
        $taskArgs=@('-p','versions/26.3','build','sourcesJar','portApiAudit','verifyFaultAuditIsolation','--offline','--no-daemon','--no-build-cache','--no-configuration-cache','--rerun-tasks','--max-workers=2','--warning-mode=fail','--console=plain')
        if($taskMode -eq 'extended-hdr'){$taskArgs+=@('-PoculusExtendedTerrain=true','-PoculusHdrWorldScene=true')}
        & ./gradlew.bat @taskArgs *> $taskLog
        if($LASTEXITCODE -ne 0){throw "Clean public $taskMode build failed; retain log"}
        foreach($taskSuite in @('test','portApiAudit')){
            $taskTarget=Join-Path $taskEvidence "clean-public-$taskMode-$taskSuite"
            if(Test-Path -LiteralPath $taskTarget){throw 'Preserve existing clean JUnit snapshot'}
            Copy-Item -LiteralPath (Join-Path $taskCleanRoot "versions/26.3/build/test-results/$taskSuite") -Destination $taskTarget -Recurse
            $taskTests=0;$taskBad=0
            foreach($taskFile in Get-ChildItem -LiteralPath $taskTarget -File -Filter 'TEST-*.xml'){
                $taskXml=[xml](Get-Content -LiteralPath $taskFile.FullName -Raw)
                $taskTests += [int]$taskXml.testsuite.tests
                $taskBad += [int]$taskXml.testsuite.failures+[int]$taskXml.testsuite.errors+[int]$taskXml.testsuite.skipped
            }
            if($taskBad -or $taskTests -ne $(if($taskSuite -eq 'test'){1057}else{82})){throw 'Unexpected clean test result'}
        }
        & (Join-Path $taskCleanRoot 'versions/26.3/tools/Verify-PublicBetaArtifacts.ps1') -VersionRoot (Join-Path $taskCleanRoot 'versions/26.3')
        foreach($taskName in @('oculus-forge-26.3-0.4.0-beta.4.jar','oculus-forge-26.3-0.4.0-beta.4-sources.jar')){
            if((Get-FileHash -LiteralPath (Join-Path $taskCleanRoot "versions/26.3/build/libs/$taskName")).Hash -cne
                (Get-FileHash -LiteralPath (Join-Path $taskVersion "build/libs/$taskName")).Hash){throw 'Clean public artifact is not byte-identical'}
        }
        Write-Output "PASS: clean public $taskMode; 1139 fresh tests, exact binary and sources reproduction."
    }
}finally{Pop-Location}
foreach($taskRow in $taskInventory){if((Get-FileHash -LiteralPath (Join-Path $taskCleanRoot $taskRow.Path)).Hash -cne $taskRow.SHA256){throw 'Build changed an original source input'}}
Write-Output 'PASS: all extracted source inputs remain unchanged.'
