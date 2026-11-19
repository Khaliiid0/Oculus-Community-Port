param(
    [Parameter(Mandatory)][ValidatePattern('^[A-F0-9]{64}$')][string]$BinarySHA256,
    [ValidatePattern('^[a-z0-9-]+$')][string]$InstanceName='public-beta4-20261119',
    [switch]$PrepareOnly
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$taskVersion=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskAudit=Join-Path $taskVersion 'installed-audit'
$taskOriginal=Join-Path $taskAudit 'instance'
$taskPrevious=Join-Path $taskAudit 'notice-smoke-checkpoint4-20261118'
if((Get-Content -LiteralPath (Join-Path $taskPrevious 'notice-smoke-exit.txt') -Raw).Trim() -cne '0'){throw 'Previous world did not close normally'}
$taskInstance=Join-Path $taskAudit $InstanceName
$taskName='oculus-forge-26.3-0.4.0-beta.4.jar'
$taskBinary=Join-Path $taskVersion "build/libs/$taskName"
foreach($taskOverride in @('MOD_CLASSES','MOD_RESOURCES','MCP_MAPPINGS','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')){
    if([Environment]::GetEnvironmentVariable($taskOverride,'Process')){throw "Unexpected launch override: $taskOverride"}
}
if((Get-FileHash -LiteralPath $taskBinary).Hash -cne $BinarySHA256){throw 'Public binary hash mismatch'}
$taskLibraries=@(Import-Csv -LiteralPath (Join-Path $taskAudit 'production-classpath.csv'))
if($taskLibraries.Count -ne 102){throw 'Unexpected production classpath'}
foreach($taskRow in $taskLibraries){
    $taskPath=[IO.Path]::GetFullPath((Join-Path $taskOriginal $taskRow.Path))
    if(-not $taskPath.StartsWith($taskOriginal+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
        (Get-FileHash -LiteralPath $taskPath).Hash -cne $taskRow.SHA256){throw 'Production dependency differs/escaped'}
}
if(Test-Path -LiteralPath $taskInstance){throw 'Preserve existing public smoke instance'}
$taskWorld=Join-Path $taskPrevious 'saves/Oculus263 Gameplay 20261117'
$taskWorldItems=@(Get-Item -LiteralPath $taskWorld)+@(Get-ChildItem -LiteralPath $taskWorld -Recurse -Force)
if($taskWorldItems.Count -gt 10000 -or @($taskWorldItems|Where-Object{$_.Attributes -band [IO.FileAttributes]::ReparsePoint}).Count){throw 'Unexpected source world/link'}
$taskDataPacks=@(Get-ChildItem -LiteralPath (Join-Path $taskWorld 'datapacks') -Force)
if($taskDataPacks.Count -ne 1 -or $taskDataPacks[0].Name -cne 'oculus-lighting-audit'){throw 'Unexpected world datapack'}
# Retain only the authored seven-file manual time/weather controls. They have
# no load/tick tags, no shaders and cannot run automatically at world entry.
$taskManualFiles=@(Get-ChildItem -LiteralPath $taskDataPacks[0].FullName -Recurse -File)
if($taskManualFiles.Count -ne 7){throw 'Unexpected manual lighting control count'}
foreach($taskFile in $taskManualFiles){
    $taskRelative=[IO.Path]::GetRelativePath($taskDataPacks[0].FullName,$taskFile.FullName)
    $taskFixture=Join-Path $taskVersion "runtime-fixtures/lighting-shore/$taskRelative"
    if((Get-FileHash -LiteralPath $taskFile.FullName).Hash -cne (Get-FileHash -LiteralPath $taskFixture).Hash){throw 'Manual lighting fixture differs'}
}
foreach($taskDirectory in @('mods','config','shaderpacks','saves','natives')){New-Item -ItemType Directory -Path (Join-Path $taskInstance $taskDirectory) -Force|Out-Null}
Copy-Item -LiteralPath $taskBinary -Destination (Join-Path $taskInstance 'mods')
Copy-Item -LiteralPath $taskWorld -Destination (Join-Path $taskInstance 'saves/Oculus263 Gameplay 20261117') -Recurse
Copy-Item -LiteralPath (Join-Path $taskPrevious 'options.txt') -Destination (Join-Path $taskInstance 'options.txt')
$taskOptions=Get-Content -LiteralPath (Join-Path $taskInstance 'options.txt')
foreach($taskSetting in @('improvedTransparency:false','renderDistance:8','simulationDistance:6','mipmapLevels:2','resourcePacks:[]')){if($taskSetting -cnotin $taskOptions){throw "Unexpected setting: $taskSetting"}}
$taskPacks=@{'BSL_v10.1.1.zip'='131CA6B56B100E60577FFDB0DD51BFAB2EE6E334564EEEE324E5422026891668';'ComplementaryReimagined_r5.7.1.zip'='24A20634A7832D422D3CD5023BE16829F26840E25C69404DD418306EA79F63F0'}
foreach($taskPack in $taskPacks.Keys){
    $taskPackPath=Join-Path $taskOriginal "shaderpacks/$taskPack"
    if((Get-FileHash -LiteralPath $taskPackPath).Hash -cne $taskPacks[$taskPack]){throw 'Original shader pack differs'}
    Copy-Item -LiteralPath $taskPackPath -Destination (Join-Path $taskInstance 'shaderpacks')
}
[IO.File]::WriteAllText((Join-Path $taskInstance 'config/oculus.properties'),"shaderpack=ComplementaryReimagined_r5.7.1.zip`nshaders.enabled=true`n")
[IO.File]::WriteAllText((Join-Path $taskInstance 'config/oculus-renderer.properties'),"worldRenderer.enabled=true`n")
$taskArgs=@(Get-Content -LiteralPath (Join-Path $taskAudit 'production-client.args'));$taskSubstitutions=0
for($taskIndex=0;$taskIndex -lt $taskArgs.Count;$taskIndex++){
    if($taskArgs[$taskIndex] -ceq '"--gameDir"'){
        if($taskArgs[$taskIndex+1] -cne ('"'+$taskOriginal.Replace('\','/')+'"')){throw 'Unexpected original game directory'}
        $taskArgs[$taskIndex+1]='"'+$taskInstance.Replace('\','/')+'"';$taskSubstitutions++
    }
    $taskArgs[$taskIndex]=$taskArgs[$taskIndex].Replace($taskOriginal.Replace('\','/')+'/natives/',$taskInstance.Replace('\','/')+'/natives/')
}
if($taskSubstitutions -ne 1 -or ($taskArgs -join "`n") -notmatch '"forge_client"' -or
    ($taskArgs -join "`n") -match 'sourceSets|forge_userdev|MOD_CLASSES|oculus\.experimental|oculus\.diagnostic|mixin\.config'){throw 'Not clean production launch arguments'}
$taskArgumentsPath=Join-Path $taskInstance 'production-client.args'
[IO.File]::WriteAllLines($taskArgumentsPath,$taskArgs,[Text.UTF8Encoding]::new($false))
if(@(Get-ChildItem -LiteralPath (Join-Path $taskInstance 'mods') -File).Count -ne 1 -or
    (Get-FileHash -LiteralPath (Join-Path $taskInstance "mods/$taskName")).Hash -cne $BinarySHA256){throw 'Wrong sole installed mod'}
if($PrepareOnly){Write-Output "Prepared clean public-beta instance: $taskInstance";return}
$taskWork=[IO.Path]::GetFullPath((Join-Path $taskVersion '../../..'))
$taskJava=Join-Path $taskWork 'gradle-home/jdks/eclipse_adoptium-25-amd64-windows.2/bin/java.exe'
Push-Location -LiteralPath $taskInstance
try{
    & $taskJava ('@'+$taskArgumentsPath) *> (Join-Path $taskInstance 'public-smoke-console.log')
    $taskExit=$LASTEXITCODE
    [IO.File]::WriteAllText((Join-Path $taskInstance 'public-smoke-exit.txt'),[string]$taskExit)
    Copy-Item -LiteralPath (Join-Path $taskInstance 'logs/latest.log') -Destination (Join-Path $taskInstance 'public-smoke-game.log')
    if($taskExit -ne 0){throw "Public smoke exited $taskExit"}
}finally{Pop-Location}
