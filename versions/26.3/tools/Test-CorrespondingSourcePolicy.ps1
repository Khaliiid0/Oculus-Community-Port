$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Prepare-CorrespondingSource.ps1') -PolicyOnly
$taskGood = @(
    'src/main/java/dev/oculusforge/RuntimeState.java',
    'versions/26.3/src/test/java/dev/oculusforge/render/world/RuntimeMixinContractTest.java',
    'versions/26.3/runtime-fixtures/water-scene/pack.mcmeta',
    'gradle/wrapper/gradle-wrapper.jar',
    'versions/26.3/tools/Prepare-CorrespondingSource.ps1',
    'versions/26.3/licenses/GPL-3.0.txt',
    'versions/26.3/NOTICE',
    'versions/26.3/LICENSE_NOTES.md'
)
$taskBad = @(
    '../source.java','src/../private.java','/src/file.java','C:/private/file.java',
    'src//file.java','src\file.java',"src/a`nb.java",'src/file.java:stream',
    'src/.git/config','src/.gradle/cache.bin','src/build/outputs.txt',
    'versions/26.3/installed-audit/options.txt','versions/26.3/run-runtime-audit/options.txt',
    'src/logs/latest.txt','src/saves/level.dat','src/screenshots/frame.png',
    'src/test-evidence/private.txt','src/release/file.txt','src/candidates/file.txt',
    'src/private.env','src/private.KEY','src/certificate.pem','src/compiler.class',
    'src/third-party.zip','src/helper.jar'
)
foreach ($taskPath in $taskGood) { Assert-SourceArchivePath -Path $taskPath }
foreach ($taskPath in $taskBad) {
    $taskRejected = $false
    try { Assert-SourceArchivePath -Path $taskPath } catch { $taskRejected = $true }
    if (-not $taskRejected) { throw "Policy accepted invalid path: $taskPath" }
}
Write-Output "PASS: $($taskGood.Count) allowed paths and $($taskBad.Count) rejected paths; includes RuntimeMixinContractTest regression. This is a path-policy test, not a secret-content or full filesystem security audit."
