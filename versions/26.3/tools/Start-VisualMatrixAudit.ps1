param(
    [ValidatePattern('^[a-z0-9-]+$')][string]$RunName = 'defaults',
    [switch]$Prepare
)
$ErrorActionPreference = 'Stop'
$taskVersionRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskAudit = Join-Path $taskVersionRoot 'installed-audit'
$taskOriginal = Join-Path $taskAudit 'instance'
$taskInstance = Join-Path $taskAudit 'visual-matrix'
$taskJarName = 'oculus-forge-26.3-0.4.0-beta.4-checkpoint.3-private-audit.jar'
$taskHash = '8BB635A9FECA705114FE7B47C8FC16A88580EFD1C78733B5860049CED0A1F8DE'
foreach ($taskName in @('MOD_CLASSES', 'MOD_RESOURCES', 'MCP_MAPPINGS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH')) {
    if ([Environment]::GetEnvironmentVariable($taskName, 'Process')) { throw "Unexpected inherited launch override: $taskName" }
}
if ($Prepare) {
    if (Test-Path -LiteralPath $taskInstance) { throw 'Preserve the existing visual instance; inspect before reuse' }
    $taskCandidate = Join-Path $taskVersionRoot "build-private-candidate/libs/$taskJarName"
    if ((Get-FileHash -LiteralPath $taskCandidate).Hash -ne $taskHash) { throw 'Wrong qualified candidate' }
    $taskClasspath = @(Import-Csv -LiteralPath (Join-Path $taskAudit 'production-classpath.csv'))
    if ($taskClasspath.Count -ne 102) { throw 'Unexpected classpath' }
    foreach ($taskRow in $taskClasspath) {
        $taskPath = [IO.Path]::GetFullPath((Join-Path $taskOriginal $taskRow.Path))
        if (-not $taskPath.StartsWith($taskOriginal + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Classpath escaped original instance' }
        if ((Get-FileHash -LiteralPath $taskPath).Hash -ne $taskRow.SHA256) { throw 'Runtime library changed' }
    }
    $taskSources = @(
        (Join-Path $taskVersionRoot 'run-runtime-audit/saves/Oculus263RuntimeAudit'),
        (Join-Path $taskVersionRoot 'run-runtime-audit/resourcepacks/oculus-normal-audit-20261110')
    )
    foreach ($taskSource in $taskSources) {
        $taskItems = @(Get-Item -LiteralPath $taskSource) + @(Get-ChildItem -LiteralPath $taskSource -Recurse -Force)
        if ($taskItems.Count -gt 10000 -or @($taskItems | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) { throw 'Unexpected audit save/resource inputs' }
    }
    foreach ($taskName in @('mods', 'config', 'shaderpacks', 'saves', 'resourcepacks', 'natives')) {
        New-Item -ItemType Directory -Path (Join-Path $taskInstance $taskName) -Force | Out-Null
    }
    Copy-Item -LiteralPath $taskCandidate -Destination (Join-Path $taskInstance 'mods')
    Copy-Item -LiteralPath $taskSources[0] -Destination (Join-Path $taskInstance 'saves/Oculus263RuntimeAudit') -Recurse
    Copy-Item -LiteralPath $taskSources[1] -Destination (Join-Path $taskInstance 'resourcepacks/oculus-normal-audit-20261110') -Recurse
    Copy-Item -LiteralPath (Join-Path $taskAudit 'compatibility-pause/options.txt') -Destination (Join-Path $taskInstance 'options.txt')
    foreach ($taskPack in @('BSL_v10.1.1.zip', 'ComplementaryReimagined_r5.7.1.zip')) {
        Copy-Item -LiteralPath (Join-Path $taskOriginal "shaderpacks/$taskPack") -Destination (Join-Path $taskInstance 'shaderpacks')
    }
    # Generated audit inputs only: shader-off reference, separate startup opt-in.
    [IO.File]::WriteAllText((Join-Path $taskInstance 'config/oculus.properties'), "shaderpack=BSL_v10.1.1.zip`nshaders.enabled=false`n")
    [IO.File]::WriteAllText((Join-Path $taskInstance 'config/oculus-renderer.properties'), "worldRenderer.enabled=true`n")
    $taskArguments = @(Get-Content -LiteralPath (Join-Path $taskAudit 'production-client.args'))
    $taskCount = 0
    for ($taskIndex = 0; $taskIndex -lt $taskArguments.Count; $taskIndex++) {
        if ($taskArguments[$taskIndex] -eq '"--gameDir"') {
            if ($taskArguments[$taskIndex + 1] -ne ('"' + $taskOriginal.Replace('\', '/') + '"')) { throw 'Unexpected original game directory' }
            $taskArguments[$taskIndex + 1] = '"' + $taskInstance.Replace('\', '/') + '"'; $taskCount++
        }
        $taskArguments[$taskIndex] = $taskArguments[$taskIndex].Replace($taskOriginal.Replace('\', '/') + '/natives/', $taskInstance.Replace('\', '/') + '/natives/')
    }
    if ($taskCount -ne 1) { throw 'Missing exact game-directory substitution' }
    [IO.File]::WriteAllLines((Join-Path $taskInstance 'production-client.args'), $taskArguments, [Text.UTF8Encoding]::new($false))
}
if ((Get-FileHash -LiteralPath (Join-Path $taskInstance "mods/$taskJarName")).Hash -ne $taskHash -or
    @(Get-ChildItem -LiteralPath (Join-Path $taskInstance 'mods') -File -Filter '*.jar').Count -ne 1) { throw 'Unexpected mod JARs' }
$taskExpectedPacks = @{
    'BSL_v10.1.1.zip' = '131CA6B56B100E60577FFDB0DD51BFAB2EE6E334564EEEE324E5422026891668'
    'ComplementaryReimagined_r5.7.1.zip' = '24A20634A7832D422D3CD5023BE16829F26840E25C69404DD418306EA79F63F0'
}
foreach ($taskName in $taskExpectedPacks.Keys) {
    if ((Get-FileHash -LiteralPath (Join-Path $taskInstance "shaderpacks/$taskName")).Hash -ne $taskExpectedPacks[$taskName]) { throw 'Shader ZIP changed' }
}
$taskArgumentsPath = Join-Path $taskInstance 'production-client.args'
$taskArgumentsText = Get-Content -LiteralPath $taskArgumentsPath -Raw
if ($taskArgumentsText -notmatch '"forge_client"' -or $taskArgumentsText -match 'sourceSets|forge_userdev|MOD_CLASSES|oculus.experimental|mixin.config') { throw 'Not a clean production launch' }
$taskConsole = Join-Path $taskInstance "$RunName-console.log"
if (Test-Path -LiteralPath $taskConsole) { throw 'Preserve the old run log; choose a new run name' }
$taskWork = [IO.Path]::GetFullPath((Join-Path $taskVersionRoot '../../..'))
$taskJava = Join-Path $taskWork 'gradle-home/jdks/eclipse_adoptium-25-amd64-windows.2/bin/java.exe'
Push-Location -LiteralPath $taskInstance
try {
    & $taskJava ('@' + $taskArgumentsPath) *> $taskConsole
    $taskExit = $LASTEXITCODE
    [IO.File]::WriteAllText((Join-Path $taskInstance "$RunName-exit.txt"), [string]$taskExit)
    Copy-Item -LiteralPath (Join-Path $taskInstance 'logs/latest.log') -Destination (Join-Path $taskInstance "$RunName-game.log")
    if ($taskExit -ne 0) { throw "Visual audit exited $taskExit" }
} finally { Pop-Location }
