param(
    [ValidatePattern('^[a-z0-9-]+$')][string]$SnapshotId='checkpoint4-20261118-source-a',
    [ValidatePattern('^[a-z0-9-]+$')][string]$RunId='source-rebuild-checkpoint4-20261118'
)
$ErrorActionPreference='Stop'
$taskVersion=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskRepo=[IO.Path]::GetFullPath((Join-Path $taskVersion '../..'))
$taskWork=[IO.Path]::GetFullPath((Join-Path $taskRepo '..'))
$taskEvidence=Join-Path $taskVersion 'test-evidence/2026-11-18-notice-packaging'
$taskSnapshot=Join-Path $taskVersion "build-private-candidate/corresponding-source/$SnapshotId"
$taskInventory=@(Import-Csv -LiteralPath (Join-Path $taskSnapshot 'source-inventory.csv'))
$taskArchiveRows=@(Import-Csv -LiteralPath (Join-Path $taskSnapshot 'archive-sha256.csv'))
if($taskArchiveRows.Count -ne 1 -or $taskArchiveRows[0].Path -cne 'oculus-26.3-0.4.0-beta.4-checkpoint.4-private-source.zip' -or $taskInventory.Count -lt 1000 -or $taskInventory.Count -gt 2000){throw 'Unexpected source snapshot'}
$taskArchive=Join-Path $taskSnapshot $taskArchiveRows[0].Path
if((Get-FileHash -LiteralPath $taskArchive).Hash -cne $taskArchiveRows[0].SHA256){throw 'Source ZIP changed'}
$taskDestination=Join-Path $taskVersion "build-private-candidate/$RunId"
if(Test-Path -LiteralPath $taskDestination){throw 'Preserve existing extracted build'}
$taskPrefix='Oculus-Community-Port-26.3-private-source/'
Add-Type -AssemblyName System.IO.Compression.FileSystem
. (Join-Path $PSScriptRoot 'Prepare-CorrespondingSource.ps1') -PolicyOnly
$taskZip=[IO.Compression.ZipFile]::OpenRead($taskArchive)
try{
    if($taskZip.Entries.Count -ne $taskInventory.Count -or @($taskZip.Entries.FullName|Select-Object -Unique).Count -ne $taskInventory.Count){throw 'Duplicate/missing ZIP entries'}
    foreach($taskRow in $taskInventory){
        Assert-SourceArchivePath -Path $taskRow.Path
        $taskEntry=$taskZip.GetEntry($taskPrefix+$taskRow.Path)
        if($null -eq $taskEntry -or $taskEntry.Length -ne [long]$taskRow.Bytes -or (($taskEntry.ExternalAttributes -shr 16) -band 0xF000) -eq 0xA000){throw 'Unexpected source ZIP entry'}
        $taskOutput=[IO.Path]::GetFullPath((Join-Path $taskDestination $taskEntry.FullName))
        if(-not $taskOutput.StartsWith($taskDestination+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'ZIP target escaped new directory'}
        $taskStream=$taskEntry.Open()
        try{$taskHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($taskStream))}finally{$taskStream.Dispose()}
        if($taskHash -cne $taskRow.SHA256){throw 'Source ZIP differs from inventory'}
    }
}finally{$taskZip.Dispose()}
[IO.Compression.ZipFile]::ExtractToDirectory($taskArchive,$taskDestination)
$taskCleanRoot=Join-Path $taskDestination $taskPrefix.TrimEnd('/')
$taskFiles=@(Get-ChildItem -LiteralPath $taskCleanRoot -Recurse -Force -File)
if($taskFiles.Count -ne $taskInventory.Count -or @(Get-ChildItem -LiteralPath $taskCleanRoot -Force -Recurse|Where-Object{$_.Attributes -band [IO.FileAttributes]::ReparsePoint}).Count){throw 'Not a clean extracted source tree'}
foreach($taskRow in $taskInventory){if((Get-FileHash -LiteralPath (Join-Path $taskCleanRoot $taskRow.Path)).Hash -cne $taskRow.SHA256){throw 'Extracted input differs'}}
$env:JAVA_HOME=Join-Path $taskWork 'gradle-home/jdks/eclipse_adoptium-25-amd64-windows.2'
$env:GRADLE_USER_HOME=Join-Path $taskWork 'gradle-home'
Push-Location -LiteralPath $taskCleanRoot
try{
    foreach($taskMode in @('default','extended-hdr')){
        $taskLog=Join-Path $taskEvidence "clean-$taskMode.log"
        if(Test-Path -LiteralPath $taskLog){throw 'Preserve clean-build log'}
        $taskArgs=@('-p','versions/26.3','--offline','--no-daemon','--no-build-cache','--no-configuration-cache','--rerun-tasks','--max-workers=2','--warning-mode=fail','--console=plain')
        if($taskMode -eq 'default'){$taskArgs+=@('test','portApiAudit','verifyFaultAuditIsolation')}else{$taskArgs+=@('preparePrivateBetaCandidate','-PoculusPrivateCandidate=true','-PoculusExtendedTerrain=true','-PoculusHdrWorldScene=true')}
        & ./gradlew.bat @taskArgs *> $taskLog
        if($LASTEXITCODE -ne 0){throw "Clean $taskMode build failed; retain logs"}
        $taskBuild=if($taskMode -eq 'default'){'build'}else{'build-private-candidate'}
        foreach($taskSuite in @('test','portApiAudit')){
            $taskTarget=Join-Path $taskEvidence "clean-$taskMode-$taskSuite"
            if(Test-Path -LiteralPath $taskTarget){throw 'Preserve clean test snapshot'}
            Copy-Item -LiteralPath (Join-Path $taskCleanRoot "versions/26.3/$taskBuild/test-results/$taskSuite") -Destination $taskTarget -Recurse
            $taskCount=0;$taskBad=0
            foreach($taskXml in Get-ChildItem -LiteralPath $taskTarget -File -Filter 'TEST-*.xml'){
                $taskResult=[xml](Get-Content -LiteralPath $taskXml.FullName -Raw)
                $taskCount += [int]$taskResult.testsuite.tests
                $taskBad += [int]$taskResult.testsuite.failures+[int]$taskResult.testsuite.errors+[int]$taskResult.testsuite.skipped
            }
            if($taskBad -or $taskCount -ne $(if($taskSuite -eq 'test'){1057}else{82})){throw 'Unexpected fresh clean test result'}
        }
        Write-Output "PASS: clean $taskMode, 1139 fresh tests; log retained."
    }
    $taskHold=Join-Path $taskEvidence 'clean-publication-hold.log'
    if(Test-Path -LiteralPath $taskHold){throw 'Preserve publication check'}
    & ./gradlew.bat -p versions/26.3 verifyPortReadiness --offline --no-daemon --no-configuration-cache --warning-mode=fail --console=plain *> $taskHold
    if($LASTEXITCODE -eq 0 -or -not (Select-String -LiteralPath $taskHold -SimpleMatch '26.3 publication/default-launch hold remains' -Quiet)){throw 'Publication guard unexpectedly passed/failed for another reason'}
}finally{Pop-Location}
foreach($taskName in @('oculus-forge-26.3-0.4.0-beta.4-checkpoint.4-private-audit.jar','oculus-forge-26.3-0.4.0-beta.4-checkpoint.4-private-audit-sources.jar')){
    if((Get-FileHash -LiteralPath (Join-Path $taskCleanRoot "versions/26.3/build-private-candidate/libs/$taskName")).Hash -cne (Get-FileHash -LiteralPath (Join-Path $taskVersion "build-private-candidate/libs/$taskName")).Hash){throw 'Clean artifact differs; do not replace original'}
    Write-Output "PASS: byte-identical clean rebuild $taskName"
}
foreach($taskRow in $taskInventory){if((Get-FileHash -LiteralPath (Join-Path $taskCleanRoot $taskRow.Path)).Hash -cne $taskRow.SHA256){throw 'Build changed an original source input'}}
Write-Output 'PASS: source inputs intact; ordinary publication guard still rejects.'
