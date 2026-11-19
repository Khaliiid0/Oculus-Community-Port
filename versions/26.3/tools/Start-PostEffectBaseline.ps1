$ErrorActionPreference = 'Stop'
$taskVersionRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskAudit = Join-Path $taskVersionRoot 'installed-audit'
$taskSource = Join-Path $taskAudit 'instance'
$taskBaseline = Join-Path $taskAudit 'baseline-forge'
if (Test-Path -LiteralPath $taskBaseline) { throw 'Baseline directory already exists; preserve it and inspect before reuse' }
foreach ($taskName in @('MOD_CLASSES', 'MOD_RESOURCES', 'MCP_MAPPINGS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH')) {
    if ([Environment]::GetEnvironmentVariable($taskName, 'Process')) { throw "Unexpected inherited launch override: $taskName" }
}
$taskClasspath = @(Import-Csv -LiteralPath (Join-Path $taskAudit 'production-classpath.csv'))
if ($taskClasspath.Count -ne 102) { throw 'Unexpected qualified production classpath' }
foreach ($taskRow in $taskClasspath) {
    $taskFile = [IO.Path]::GetFullPath((Join-Path $taskSource $taskRow.Path))
    if (-not $taskFile.StartsWith($taskSource + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Classpath escaped source instance' }
    if ((Get-FileHash -LiteralPath $taskFile).Hash -ne $taskRow.SHA256) { throw 'Production library changed' }
}
$taskWorld = Join-Path $taskSource 'saves/Oculus263RuntimeAudit'
$taskItems = @(Get-Item -LiteralPath $taskWorld) + @(Get-ChildItem -LiteralPath $taskWorld -Recurse -Force)
if ($taskItems.Count -gt 10000 -or @($taskItems | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) { throw 'Unexpected disposable save contents' }
foreach ($taskName in @('mods', 'saves', 'natives')) {
    New-Item -ItemType Directory -Path (Join-Path $taskBaseline $taskName) -Force | Out-Null
}
Copy-Item -LiteralPath $taskWorld -Destination (Join-Path $taskBaseline 'saves/Oculus263RuntimeAudit') -Recurse
Copy-Item -LiteralPath (Join-Path $taskSource 'options.txt') -Destination (Join-Path $taskBaseline 'options.txt')
$taskArguments = @(Get-Content -LiteralPath (Join-Path $taskAudit 'production-client.args'))
$taskOriginalDirectory = '"' + $taskSource.Replace('\', '/') + '"'
$taskNewDirectory = '"' + $taskBaseline.Replace('\', '/') + '"'
$taskDirectoryCount = 0
for ($taskIndex = 0; $taskIndex -lt $taskArguments.Count; $taskIndex++) {
    if ($taskArguments[$taskIndex] -eq '"--gameDir"') {
        if ($taskArguments[$taskIndex + 1] -ne $taskOriginalDirectory) { throw 'Unexpected game directory argument' }
        $taskArguments[$taskIndex + 1] = $taskNewDirectory
        $taskDirectoryCount++
    }
    $taskArguments[$taskIndex] = $taskArguments[$taskIndex].Replace($taskSource.Replace('\', '/') + '/natives/', $taskBaseline.Replace('\', '/') + '/natives/')
}
if ($taskDirectoryCount -ne 1 -or ($taskArguments -join "`n") -match 'forge_userdev|MOD_CLASSES|sourceSets|oculus.experimental|mixin.config') { throw 'Not a clean production baseline' }
$taskArgumentFile = Join-Path $taskAudit 'baseline-forge.args'
if (Test-Path -LiteralPath $taskArgumentFile) { throw 'Baseline argument file already exists' }
[IO.File]::WriteAllLines($taskArgumentFile, $taskArguments, [Text.UTF8Encoding]::new($false))
if (@(Get-ChildItem -LiteralPath (Join-Path $taskBaseline 'mods') -Recurse -File).Count) { throw 'Baseline mods folder is not empty' }
$taskWork = [IO.Path]::GetFullPath((Join-Path $taskVersionRoot '../../..'))
$taskJava = Join-Path $taskWork 'gradle-home/jdks/eclipse_adoptium-25-amd64-windows.2/bin/java.exe'
Push-Location -LiteralPath $taskBaseline
try {
    & $taskJava ('@' + $taskArgumentFile) *> (Join-Path $taskAudit 'baseline-forge-console.log')
    $taskExit = $LASTEXITCODE
    [IO.File]::WriteAllText((Join-Path $taskAudit 'baseline-forge-exit.txt'), [string]$taskExit)
    Copy-Item -LiteralPath (Join-Path $taskBaseline 'logs/latest.log') -Destination (Join-Path $taskAudit 'baseline-forge-game.log')
    if ($taskExit -ne 0) { throw "Baseline client exited $taskExit" }
} finally { Pop-Location }
