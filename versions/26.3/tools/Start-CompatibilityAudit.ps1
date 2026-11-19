param(
    [ValidatePattern('^[a-z0-9-]+$')][string]$RunName = 'boot1',
    [switch]$Prepare,
    [ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$CandidateSha256
)
$ErrorActionPreference = 'Stop'
$taskVersionRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskAudit = Join-Path $taskVersionRoot 'installed-audit'
$taskOriginal = Join-Path $taskAudit 'instance'
$taskInstance = Join-Path $taskAudit 'compatibility-pause'
$taskJarName = 'oculus-forge-26.3-0.4.0-beta.4-checkpoint.3-private-audit.jar'
$taskArgumentsPath = Join-Path $taskInstance 'production-client.args'
foreach ($taskName in @('MOD_CLASSES', 'MOD_RESOURCES', 'MCP_MAPPINGS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH')) {
    if ([Environment]::GetEnvironmentVariable($taskName, 'Process')) { throw "Unexpected inherited launch override: $taskName" }
}
if ($Prepare) {
    if (Test-Path -LiteralPath $taskInstance) { throw 'Preserve the existing compatibility instance; do not overwrite it' }
    if (-not $CandidateSha256) { throw 'Pin the new candidate hash explicitly' }
    $taskCandidate = Join-Path $taskVersionRoot "build-private-candidate/libs/$taskJarName"
    if ((Get-FileHash -LiteralPath $taskCandidate).Hash -ne $CandidateSha256) { throw 'Candidate hash mismatch' }
    $taskClasspath = @(Import-Csv -LiteralPath (Join-Path $taskAudit 'production-classpath.csv'))
    if ($taskClasspath.Count -ne 102) { throw 'Unexpected production classpath' }
    foreach ($taskRow in $taskClasspath) {
        $taskFile = [IO.Path]::GetFullPath((Join-Path $taskOriginal $taskRow.Path))
        if (-not $taskFile.StartsWith($taskOriginal + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Classpath escaped original instance' }
        if ((Get-FileHash -LiteralPath $taskFile).Hash -ne $taskRow.SHA256) { throw 'Production runtime library changed' }
    }
    $taskWorld = Join-Path $taskOriginal 'saves/Oculus263 Gameplay 20261117'
    $taskItems = @(Get-Item -LiteralPath $taskWorld) + @(Get-ChildItem -LiteralPath $taskWorld -Recurse -Force)
    if ($taskItems.Count -gt 10000 -or @($taskItems | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) { throw 'Unexpected gameplay-save contents' }
    foreach ($taskName in @('mods', 'config', 'shaderpacks', 'saves', 'natives')) {
        New-Item -ItemType Directory -Path (Join-Path $taskInstance $taskName) -Force | Out-Null
    }
    Copy-Item -LiteralPath $taskCandidate -Destination (Join-Path $taskInstance 'mods')
    Copy-Item -LiteralPath $taskWorld -Destination (Join-Path $taskInstance 'saves/Oculus263 Gameplay 20261117') -Recurse
    Copy-Item -LiteralPath (Join-Path $taskOriginal 'options.txt') -Destination (Join-Path $taskInstance 'options.txt')
    foreach ($taskName in @('oculus.properties', 'oculus-renderer.properties')) {
        Copy-Item -LiteralPath (Join-Path $taskOriginal "config/$taskName") -Destination (Join-Path $taskInstance 'config')
    }
    $taskPack = Join-Path $taskOriginal 'shaderpacks/BSL_v10.1.1.zip'
    if ((Get-FileHash -LiteralPath $taskPack).Hash -ne '131CA6B56B100E60577FFDB0DD51BFAB2EE6E334564EEEE324E5422026891668') { throw 'Original BSL ZIP changed' }
    Copy-Item -LiteralPath $taskPack -Destination (Join-Path $taskInstance 'shaderpacks')
    $taskArguments = @(Get-Content -LiteralPath (Join-Path $taskAudit 'production-client.args'))
    $taskDirectoryCount = 0
    for ($taskIndex = 0; $taskIndex -lt $taskArguments.Count; $taskIndex++) {
        if ($taskArguments[$taskIndex] -eq '"--gameDir"') {
            if ($taskArguments[$taskIndex + 1] -ne ('"' + $taskOriginal.Replace('\', '/') + '"')) { throw 'Unexpected original game directory' }
            $taskArguments[$taskIndex + 1] = '"' + $taskInstance.Replace('\', '/') + '"'
            $taskDirectoryCount++
        }
        $taskArguments[$taskIndex] = $taskArguments[$taskIndex].Replace($taskOriginal.Replace('\', '/') + '/natives/', $taskInstance.Replace('\', '/') + '/natives/')
    }
    if ($taskDirectoryCount -ne 1) { throw 'Missing exact game-directory override' }
    [IO.File]::WriteAllLines($taskArgumentsPath, $taskArguments, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $taskInstance 'candidate-sha256.txt'), $CandidateSha256.ToUpperInvariant())
}
$taskPinnedHash = (Get-Content -LiteralPath (Join-Path $taskInstance 'candidate-sha256.txt') -Raw).Trim()
if ($taskPinnedHash -notmatch '^[A-F0-9]{64}$' -or (Get-FileHash -LiteralPath (Join-Path $taskInstance "mods/$taskJarName")).Hash -ne $taskPinnedHash) { throw 'Wrong installed candidate' }
if (@(Get-ChildItem -LiteralPath (Join-Path $taskInstance 'mods') -File -Filter '*.jar').Count -ne 1) { throw 'Unexpected extra mods' }
$taskArgumentsText = Get-Content -LiteralPath $taskArgumentsPath -Raw
if ($taskArgumentsText -notmatch '"forge_client"' -or $taskArgumentsText -match 'forge_userdev|MOD_CLASSES|sourceSets|oculus.experimental|mixin.config') { throw 'Not a clean production client launch' }
$taskConsole = Join-Path $taskInstance "$RunName-console.log"
if (Test-Path -LiteralPath $taskConsole) { throw 'Run log already exists; choose a new name' }
$taskWork = [IO.Path]::GetFullPath((Join-Path $taskVersionRoot '../../..'))
$taskJava = Join-Path $taskWork 'gradle-home/jdks/eclipse_adoptium-25-amd64-windows.2/bin/java.exe'
Push-Location -LiteralPath $taskInstance
try {
    & $taskJava ('@' + $taskArgumentsPath) *> $taskConsole
    $taskExit = $LASTEXITCODE
    [IO.File]::WriteAllText((Join-Path $taskInstance "$RunName-exit.txt"), [string]$taskExit)
    Copy-Item -LiteralPath (Join-Path $taskInstance 'logs/latest.log') -Destination (Join-Path $taskInstance "$RunName-game.log")
    if ($taskExit -ne 0) { throw "Compatibility audit client exited $taskExit" }
} finally { Pop-Location }
