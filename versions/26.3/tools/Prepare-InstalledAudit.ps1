$ErrorActionPreference = 'Stop'
$taskVersionRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskRoot = Join-Path $taskVersionRoot 'installed-audit'
$taskInstance = Join-Path $taskRoot 'instance'
$taskWork = [IO.Path]::GetFullPath((Join-Path $taskVersionRoot '../../..'))
$taskGradle = Join-Path $taskWork 'gradle-home'
$taskVanilla = Get-Content -LiteralPath (Join-Path $taskInstance 'versions/26.3/26.3.json') -Raw | ConvertFrom-Json
$taskForge = Get-Content -LiteralPath (Join-Path $taskInstance 'versions/26.3-forge-66.1.2/26.3-forge-66.1.2.json') -Raw | ConvertFrom-Json
if ($taskVanilla.id -ne '26.3' -or $taskForge.id -ne '26.3-forge-66.1.2' -or $taskForge.mainClass -ne 'net.minecraftforge.bootstrap.ForgeBootstrap') { throw 'Unexpected installed metadata' }
if (($taskForge.arguments.game -join ' ') -ne '--launchTarget forge_client') { throw 'Not the production client target' }

function Test-AuditLibraryRules($taskRules) {
    if (-not $taskRules) { return $true }
    $taskAllowed = $false
    foreach ($taskRule in $taskRules) {
        $taskMatches = $true
        if ($taskRule.features) { $taskMatches = $false }
        if ($taskRule.os.name -and $taskRule.os.name -ne 'windows') { $taskMatches = $false }
        if ($taskRule.os.arch -and $taskRule.os.arch -notin @('amd64', 'x86_64')) { $taskMatches = $false }
        if ($taskRule.os.version -and [Environment]::OSVersion.Version.ToString() -notmatch $taskRule.os.version) { $taskMatches = $false }
        if ($taskMatches) { $taskAllowed = $taskRule.action -eq 'allow' }
    }
    return $taskAllowed
}
function Get-AuditArtifact($taskArtifact, $taskDestination) {
    if (-not $taskArtifact.sha1 -or $taskArtifact.sha1 -notmatch '^[a-f0-9]{40}$') { throw 'Missing trusted artifact hash' }
    if (Test-Path -LiteralPath $taskDestination) {
        if ((Get-FileHash -LiteralPath $taskDestination -Algorithm SHA1).Hash -ne $taskArtifact.sha1) { throw "Existing artifact hash mismatch: $taskDestination" }
        return
    }
    New-Item -ItemType Directory -Path (Split-Path -Parent $taskDestination) -Force | Out-Null
    $taskCandidates = @(
        (Join-Path $taskWork "gradle-home/caches/minecraftforge/forgegradle/mavenizer/caches/maven/forge/$($taskArtifact.path)"),
        (Join-Path $env:APPDATA ".minecraft/libraries/$($taskArtifact.path)")
    )
    $taskParts = $taskArtifact.path -split '/'
    if ($taskParts.Length -ge 4) {
        $taskGroup = $taskParts[0..($taskParts.Length - 4)] -join '.'
        $taskName = $taskParts[-3]; $taskVersion = $taskParts[-2]
        $taskCandidates += Join-Path $taskGradle "caches/modules-2/files-2.1/$taskGroup/$taskName/$taskVersion/$($taskArtifact.sha1)/$($taskParts[-1])"
    }
    foreach ($taskCandidate in $taskCandidates) {
        if ((Test-Path -LiteralPath $taskCandidate -PathType Leaf) -and (Get-FileHash -LiteralPath $taskCandidate -Algorithm SHA1).Hash -eq $taskArtifact.sha1) {
            Copy-Item -LiteralPath $taskCandidate -Destination $taskDestination
            return
        }
    }
    $taskUri = [uri]$taskArtifact.url
    if ($taskUri.Scheme -ne 'https' -or $taskUri.Host -notin @('libraries.minecraft.net', 'maven.minecraftforge.net', 'piston-data.mojang.com')) { throw 'Unexpected download host' }
    Invoke-WebRequest -Uri $taskUri -OutFile $taskDestination
    if ((Get-FileHash -LiteralPath $taskDestination -Algorithm SHA1).Hash -ne $taskArtifact.sha1) { throw 'Downloaded artifact hash mismatch; stop without executing it' }
}

# Child metadata overrides parent versions, preserving native classifier variants.
$taskLibraries = [ordered]@{}
foreach ($taskLibrary in @($taskVanilla.libraries) + @($taskForge.libraries)) {
    if (-not (Test-AuditLibraryRules $taskLibrary.rules)) { continue }
    if ($taskLibrary.name -match ':natives-windows-(arm64|x86)$') { continue }
    $taskParts = $taskLibrary.name -split ':'
    $taskKey = $taskParts[0] + ':' + $taskParts[1] + ':' + $(if ($taskParts.Length -gt 3) { $taskParts[3] } else { '' })
    $taskLibraries[$taskKey] = $taskLibrary
}
$taskClasspath = @()
foreach ($taskLibrary in $taskLibraries.Values) {
    $taskArtifact = $taskLibrary.downloads.artifact
    if (-not $taskArtifact) { throw "Unsupported legacy library shape: $($taskLibrary.name)" }
    $taskDestination = [IO.Path]::GetFullPath((Join-Path $taskInstance "libraries/$($taskArtifact.path)"))
    if (-not $taskDestination.StartsWith((Join-Path $taskInstance 'libraries') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Library path escaped installation' }
    Get-AuditArtifact $taskArtifact $taskDestination
    $taskClasspath += $taskDestination
}
$taskClient = Join-Path $taskInstance 'versions/26.3/26.3.jar'
if ((Get-FileHash -LiteralPath $taskClient -Algorithm SHA1).Hash -ne $taskVanilla.downloads.client.sha1) { throw 'Vanilla client changed' }
$taskClasspath += $taskClient
if (@($taskClasspath | Where-Object { $_ -match '(?i)sourceSets|source-only|community-github[/\\]build|test-classes|userdev|slime-launcher' }).Count) { throw 'Development classpath contamination' }

$taskAssets = Join-Path $env:APPDATA '.minecraft/assets'
if (-not (Test-Path -LiteralPath (Join-Path $taskAssets "indexes/$($taskVanilla.assetIndex.id).json"))) { throw 'Existing game assets unavailable' }
$taskNatives = Join-Path $taskInstance 'natives'
New-Item -ItemType Directory -Path $taskNatives -Force | Out-Null
$taskLogConfig = Join-Path $taskRoot 'log4j2-audit.xml'
if (-not (Test-Path -LiteralPath $taskLogConfig -PathType Leaf)) { throw 'Audit logging configuration missing' }
$taskLogUri = [uri]::new($taskLogConfig).AbsoluteUri
$taskArguments = @(
    '-Xms512M', '-Xmx2G', '--enable-native-access=ALL-UNNAMED', '--add-exports', 'java.base/jdk.internal.misc=ALL-UNNAMED',
    '-XX:StackShadowPages=32', '-XX:+UseCompactObjectHeaders', '-Djava.net.preferIPv6Addresses=system',
    "-Djava.library.path=$taskNatives/java", "-Djna.tmpdir=$taskNatives/jna", "-Dorg.lwjgl.system.SharedLibraryExtractPath=$taskNatives/lwjgl",
    "-Dio.netty.native.workdir=$taskNatives/netty", '-Dminecraft.launcher.brand=OculusIsolatedAudit', '-Dminecraft.launcher.version=1',
    "-Dlog4j.configurationFile=$taskLogUri", '-cp', ($taskClasspath -join ';'), $taskForge.mainClass,
    '--launchTarget', 'forge_client', '--username', 'Dev', '--uuid', '380df991f603344ca090369bad2a924a', '--accessToken', '0',
    '--version', $taskForge.id, '--versionType', 'release', '--gameDir', $taskInstance,
    '--assetsDir', $taskAssets, '--assetIndex', $taskVanilla.assetIndex.id, '--width', '960', '--height', '540'
)
# Offline, disposable single-player test identity only. Never reads account tokens.
$taskQuoted = foreach ($taskArgument in $taskArguments) { '"' + $taskArgument.Replace('\', '/').Replace('"', '\"') + '"' }
[IO.File]::WriteAllLines((Join-Path $taskRoot 'production-client.args'), $taskQuoted, [Text.UTF8Encoding]::new($false))
$taskClasspath | ForEach-Object { [pscustomobject]@{Path=[IO.Path]::GetRelativePath($taskInstance, $_); SHA256=(Get-FileHash -LiteralPath $_).Hash} } |
    Export-Csv -LiteralPath (Join-Path $taskRoot 'production-classpath.csv') -NoTypeInformation
Write-Output "Prepared $($taskClasspath.Count) checksum-verified production classpath entries. No source roots, renderer overrides or account credentials."
