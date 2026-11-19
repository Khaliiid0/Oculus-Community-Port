param([ValidatePattern('^[a-z0-9-]+$')][string]$RunName = 'boot1')
$ErrorActionPreference = 'Stop'
$taskVersionRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskRoot = Join-Path $taskVersionRoot 'installed-audit'
$taskInstance = Join-Path $taskRoot 'instance'
$taskJar = Join-Path $taskInstance 'mods/oculus-forge-26.3-0.4.0-beta.4-checkpoint.2-private-audit.jar'
if ((Get-FileHash -LiteralPath $taskJar).Hash -ne '2A7155B63F692090295634AA81F3261CCAED02F2BA2882159CD2FA49EFE485A7') { throw 'Wrong packaged candidate' }
if (@(Get-ChildItem -LiteralPath (Join-Path $taskInstance 'mods') -File -Filter '*.jar').Count -ne 1) { throw 'Unexpected additional mods' }
$taskArguments = Get-Content -LiteralPath (Join-Path $taskRoot 'production-client.args') -Raw
if ($taskArguments -match 'forge_userdev|MOD_CLASSES|sourceSets|oculus.experimental|mixin.config') { throw 'Not a clean production launch' }
foreach ($taskName in @('MOD_CLASSES', 'MOD_RESOURCES', 'MCP_MAPPINGS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH')) {
    if ([Environment]::GetEnvironmentVariable($taskName, 'Process')) { throw "Unexpected inherited launch override: $taskName" }
}
$taskLog = Join-Path $taskRoot "$RunName-console.log"
if (Test-Path -LiteralPath $taskLog) { throw 'Run log already exists; use a new name' }
$taskWork = [IO.Path]::GetFullPath((Join-Path $taskVersionRoot '../../..'))
$taskJava = Join-Path $taskWork 'gradle-home/jdks/eclipse_adoptium-25-amd64-windows.2/bin/java.exe'
Push-Location -LiteralPath $taskInstance
try {
    & $taskJava ('@' + (Join-Path $taskRoot 'production-client.args')) *> $taskLog
    $taskExit = $LASTEXITCODE
    [IO.File]::WriteAllText((Join-Path $taskRoot "$RunName-exit.txt"), [string]$taskExit)
    Copy-Item -LiteralPath (Join-Path $taskInstance 'logs/latest.log') -Destination (Join-Path $taskRoot "$RunName-game.log")
    if ($taskExit -ne 0) { throw "Production client exited $taskExit; see $taskLog" }
} finally { Pop-Location }
