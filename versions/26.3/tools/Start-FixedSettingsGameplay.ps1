param(
    [ValidatePattern('^[a-z0-9-]+$')][string]$RunName = 'gameplay',
    [switch]$Prepare
)
$ErrorActionPreference = 'Stop'
$taskVersion = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskAudit = Join-Path $taskVersion 'installed-audit'
$taskOriginal = Join-Path $taskAudit 'instance'
$taskInstance = Join-Path $taskAudit 'gameplay-checkpoint3'
$taskJarName = 'oculus-forge-26.3-0.4.0-beta.4-checkpoint.3-private-audit.jar'
$taskHash = '8BB635A9FECA705114FE7B47C8FC16A88580EFD1C78733B5860049CED0A1F8DE'
$taskPackHash = '131CA6B56B100E60577FFDB0DD51BFAB2EE6E334564EEEE324E5422026891668'
foreach ($taskName in @('MOD_CLASSES','MOD_RESOURCES','MCP_MAPPINGS','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')) {
    if ([Environment]::GetEnvironmentVariable($taskName, 'Process')) { throw "Unexpected inherited launch override: $taskName" }
}
$taskLibraries = @(Import-Csv -LiteralPath (Join-Path $taskAudit 'production-classpath.csv'))
if ($taskLibraries.Count -ne 102) { throw 'Unexpected production library count' }
foreach ($taskLibrary in $taskLibraries) {
    $taskPath = [IO.Path]::GetFullPath((Join-Path $taskOriginal $taskLibrary.Path))
    if (-not $taskPath.StartsWith($taskOriginal + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Library escaped instance' }
    if ((Get-FileHash -LiteralPath $taskPath).Hash -ne $taskLibrary.SHA256) { throw 'Production library changed' }
}
if ($Prepare) {
    if (Test-Path -LiteralPath $taskInstance) { throw 'Preserve the existing gameplay instance' }
    $taskCandidate = Join-Path $taskVersion "build-private-candidate/libs/$taskJarName"
    if ((Get-FileHash -LiteralPath $taskCandidate).Hash -ne $taskHash) { throw 'Wrong candidate' }
    $taskWorld = Join-Path $taskOriginal 'saves/Oculus263 Gameplay 20261117'
    $taskItems = @(Get-Item -LiteralPath $taskWorld) + @(Get-ChildItem -LiteralPath $taskWorld -Recurse -Force)
    if ($taskItems.Count -gt 10000 -or @($taskItems | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) { throw 'Unexpected disposable world inputs' }
    foreach ($taskName in @('mods','config','shaderpacks','saves','natives')) { New-Item -ItemType Directory -Path (Join-Path $taskInstance $taskName) -Force | Out-Null }
    Copy-Item -LiteralPath $taskCandidate -Destination (Join-Path $taskInstance 'mods')
    Copy-Item -LiteralPath $taskWorld -Destination (Join-Path $taskInstance 'saves/Oculus263 Gameplay 20261117') -Recurse
    Copy-Item -LiteralPath (Join-Path $taskAudit 'compatibility-pause/options.txt') -Destination (Join-Path $taskInstance 'options.txt')
    Copy-Item -LiteralPath (Join-Path $taskOriginal 'shaderpacks/BSL_v10.1.1.zip') -Destination (Join-Path $taskInstance 'shaderpacks')
    [IO.File]::WriteAllText((Join-Path $taskInstance 'config/oculus.properties'), "shaderpack=BSL_v10.1.1.zip`nshaders.enabled=true`n")
    [IO.File]::WriteAllText((Join-Path $taskInstance 'config/oculus-renderer.properties'), "worldRenderer.enabled=true`n")
    $taskArguments = @(Get-Content -LiteralPath (Join-Path $taskAudit 'production-client.args'))
    $taskCount = 0
    for ($taskIndex = 0; $taskIndex -lt $taskArguments.Count; $taskIndex++) {
        if ($taskArguments[$taskIndex] -eq '"--gameDir"') {
            if ($taskArguments[$taskIndex + 1] -ne ('"' + $taskOriginal.Replace('\','/') + '"')) { throw 'Unexpected original game directory' }
            $taskArguments[$taskIndex + 1] = '"' + $taskInstance.Replace('\','/') + '"'; $taskCount++
        }
        $taskArguments[$taskIndex] = $taskArguments[$taskIndex].Replace($taskOriginal.Replace('\','/') + '/natives/', $taskInstance.Replace('\','/') + '/natives/')
    }
    if ($taskCount -ne 1) { throw 'Missing exact game-directory substitution' }
    [IO.File]::WriteAllLines((Join-Path $taskInstance 'production-client.args'), $taskArguments, [Text.UTF8Encoding]::new($false))
}
if ((Get-FileHash -LiteralPath (Join-Path $taskInstance "mods/$taskJarName")).Hash -ne $taskHash -or @(Get-ChildItem -LiteralPath (Join-Path $taskInstance 'mods') -File -Filter '*.jar').Count -ne 1) { throw 'Unexpected installed mods' }
if ((Get-FileHash -LiteralPath (Join-Path $taskInstance 'shaderpacks/BSL_v10.1.1.zip')).Hash -ne $taskPackHash -or @(Get-ChildItem -LiteralPath (Join-Path $taskInstance 'shaderpacks') -File).Count -ne 1) { throw 'Unexpected pack or sidecar' }
$taskOptions = Get-Content -LiteralPath (Join-Path $taskInstance 'options.txt')
foreach ($taskOption in @('improvedTransparency:false','renderDistance:8','simulationDistance:6','mipmapLevels:2','resourcePacks:[]')) {
    if ($taskOption -cnotin $taskOptions) { throw "Unexpected fixed setting: $taskOption" }
}
$taskArgumentsPath = Join-Path $taskInstance 'production-client.args'
$taskArgumentsText = Get-Content -LiteralPath $taskArgumentsPath -Raw
if ($taskArgumentsText -notmatch '"forge_client"' -or $taskArgumentsText -match 'sourceSets|forge_userdev|MOD_CLASSES|oculus.experimental|mixin.config') { throw 'Contaminated launch arguments' }
$taskConsole = Join-Path $taskInstance "$RunName-console.log"
if (Test-Path -LiteralPath $taskConsole) { throw 'Preserve previous run log' }
Copy-Item -LiteralPath (Join-Path $taskInstance 'options.txt') -Destination (Join-Path $taskInstance "$RunName-start-options.txt")
$taskWork = [IO.Path]::GetFullPath((Join-Path $taskVersion '../../..'))
$taskJava = Join-Path $taskWork 'gradle-home/jdks/eclipse_adoptium-25-amd64-windows.2/bin/java.exe'
Push-Location -LiteralPath $taskInstance
try {
    & $taskJava ('@' + $taskArgumentsPath) *> $taskConsole
    $taskExit = $LASTEXITCODE
    [IO.File]::WriteAllText((Join-Path $taskInstance "$RunName-exit.txt"), [string]$taskExit)
    Copy-Item -LiteralPath (Join-Path $taskInstance 'logs/latest.log') -Destination (Join-Path $taskInstance "$RunName-game.log")
    Copy-Item -LiteralPath (Join-Path $taskInstance 'options.txt') -Destination (Join-Path $taskInstance "$RunName-final-options.txt")
    if ($taskExit -ne 0) { throw "Gameplay client exited $taskExit" }
} finally { Pop-Location }
