param([switch]$Prepare, [switch]$PrepareOnly, [switch]$CaptureStages,
    [ValidatePattern('^[a-z0-9-]+$')][string]$RunName='lighting',
    [ValidatePattern('^[a-z0-9-]+$')][string]$InstanceName)
$ErrorActionPreference='Stop'
$taskVersion=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskAudit=Join-Path $taskVersion 'installed-audit'
$taskOriginal=Join-Path $taskAudit 'instance'
if($PrepareOnly -and -not $Prepare){throw 'PrepareOnly requires Prepare'}
$taskInstance=Join-Path $taskAudit $(if($InstanceName){$InstanceName}elseif($CaptureStages){'bank-stage-20261118'}else{'lighting-shore-20261118'})
$taskJar='oculus-forge-26.3-0.4.0-beta.4-checkpoint.3-private-audit.jar'
$taskJarHash='8BB635A9FECA705114FE7B47C8FC16A88580EFD1C78733B5860049CED0A1F8DE'
foreach($taskName in @('MOD_CLASSES','MOD_RESOURCES','MCP_MAPPINGS','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')) {
    if([Environment]::GetEnvironmentVariable($taskName,'Process')){throw "Unexpected launch override: $taskName"}
}
$taskLibraries=@(Import-Csv -LiteralPath (Join-Path $taskAudit 'production-classpath.csv'))
if($taskLibraries.Count -ne 102){throw 'Unexpected production classpath'}
foreach($taskRow in $taskLibraries){
    $taskPath=[IO.Path]::GetFullPath((Join-Path $taskOriginal $taskRow.Path))
    if(-not $taskPath.StartsWith($taskOriginal+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or (Get-FileHash -LiteralPath $taskPath).Hash -ne $taskRow.SHA256){throw 'Runtime library changed/escaped'}
}
if($Prepare){
    if(Test-Path -LiteralPath $taskInstance){throw 'Preserve existing lighting instance'}
    $taskSourceInstance=Join-Path $taskAudit $(if($CaptureStages){'lighting-shore-20261118'}else{'gameplay-checkpoint3'})
    $taskPriorExit=if($CaptureStages){'lighting-exit.txt'}else{'gameplay-exit.txt'}
    if((Get-Content -LiteralPath (Join-Path $taskSourceInstance $taskPriorExit) -Raw).Trim() -ne '0'){throw 'Source world did not exit normally'}
    $taskCandidate=Join-Path $taskVersion "build-private-candidate/libs/$taskJar"
    if((Get-FileHash -LiteralPath $taskCandidate).Hash -ne $taskJarHash){throw 'Wrong candidate'}
    $taskWorld=Join-Path $taskSourceInstance 'saves/Oculus263 Gameplay 20261117'
    $taskFixture=Join-Path $taskVersion 'runtime-fixtures/lighting-shore'
    foreach($taskSource in @($taskWorld,$taskFixture)){
        $taskItems=@(Get-Item -LiteralPath $taskSource)+@(Get-ChildItem -LiteralPath $taskSource -Recurse -Force)
        if($taskItems.Count -gt 10000 -or @($taskItems|Where-Object{$_.Attributes -band [IO.FileAttributes]::ReparsePoint}).Count){throw 'Unexpected world/fixture input'}
    }
    foreach($taskName in @('mods','config','shaderpacks','saves','natives')){New-Item -ItemType Directory -Path (Join-Path $taskInstance $taskName) -Force|Out-Null}
    Copy-Item -LiteralPath $taskCandidate -Destination (Join-Path $taskInstance 'mods')
    Copy-Item -LiteralPath $taskWorld -Destination (Join-Path $taskInstance 'saves/Oculus263 Gameplay 20261117') -Recurse
    $taskDataPack=Join-Path $taskInstance 'saves/Oculus263 Gameplay 20261117/datapacks/oculus-lighting-audit'
    if(-not $CaptureStages){
        if(Test-Path -LiteralPath $taskDataPack){throw 'Unexpected existing lighting datapack'}
        Copy-Item -LiteralPath $taskFixture -Destination $taskDataPack -Recurse
    }else{
        $taskStageFixture=Join-Path $taskVersion 'runtime-fixtures/bank-stage'
        $taskStageItems=@(Get-Item -LiteralPath $taskStageFixture)+@(Get-ChildItem -LiteralPath $taskStageFixture -Recurse -Force)
        if(@($taskStageItems|Where-Object{$_.Attributes -band [IO.FileAttributes]::ReparsePoint}).Count -or @($taskStageItems|Where-Object{-not $_.PSIsContainer}).Count -ne 4){throw 'Unexpected diagnostic bootstrap'}
        $taskStageDestination=Join-Path $taskInstance 'saves/Oculus263 Gameplay 20261117/datapacks/oculus-bank-stage'
        if(Test-Path -LiteralPath $taskStageDestination){throw 'Preserve earlier diagnostic bootstrap'}
        Copy-Item -LiteralPath $taskStageFixture -Destination $taskStageDestination -Recurse
    }
    $taskSourceOptions=if($CaptureStages){'options.txt'}else{'gameplay-final-options.txt'}
    Copy-Item -LiteralPath (Join-Path $taskSourceInstance $taskSourceOptions) -Destination (Join-Path $taskInstance 'options.txt')
    foreach($taskPack in @('BSL_v10.1.1.zip','ComplementaryReimagined_r5.7.1.zip')){Copy-Item -LiteralPath (Join-Path $taskOriginal "shaderpacks/$taskPack") -Destination (Join-Path $taskInstance 'shaderpacks')}
    $taskInitialPack=if($CaptureStages){'ComplementaryReimagined_r5.7.1.zip'}else{'BSL_v10.1.1.zip'}
    [IO.File]::WriteAllText((Join-Path $taskInstance 'config/oculus.properties'),"shaderpack=$taskInitialPack`nshaders.enabled=true`n")
    [IO.File]::WriteAllText((Join-Path $taskInstance 'config/oculus-renderer.properties'),"worldRenderer.enabled=true`n")
    $taskArgs=@(Get-Content -LiteralPath (Join-Path $taskAudit 'production-client.args'));$taskCount=0
    for($taskIndex=0;$taskIndex -lt $taskArgs.Count;$taskIndex++){
        if($taskArgs[$taskIndex] -eq '"--gameDir"'){
            if($taskArgs[$taskIndex+1] -ne ('"'+$taskOriginal.Replace('\','/')+'"')){throw 'Unexpected original gameDir'}
            $taskArgs[$taskIndex+1]='"'+$taskInstance.Replace('\','/')+'"';$taskCount++
        }
        $taskArgs[$taskIndex]=$taskArgs[$taskIndex].Replace($taskOriginal.Replace('\','/')+'/natives/',$taskInstance.Replace('\','/')+'/natives/')
    }
    if($taskCount -ne 1){throw 'Missing exact directory substitution'}
    if($CaptureStages){$taskArgs=@('"-Doculus.diagnostic.captureWorldStages=true"','"-Doculus.diagnostic.capturePostStages=true"','"-Doculus.diagnostic.captureRoot=shaders/world0"')+$taskArgs}
    [IO.File]::WriteAllLines((Join-Path $taskInstance 'production-client.args'),$taskArgs,[Text.UTF8Encoding]::new($false))
}
if((Get-FileHash -LiteralPath (Join-Path $taskInstance "mods/$taskJar")).Hash -ne $taskJarHash -or @(Get-ChildItem -LiteralPath (Join-Path $taskInstance 'mods') -File -Filter '*.jar').Count -ne 1){throw 'Unexpected mods'}
$taskPacks=@{'BSL_v10.1.1.zip'='131CA6B56B100E60577FFDB0DD51BFAB2EE6E334564EEEE324E5422026891668';'ComplementaryReimagined_r5.7.1.zip'='24A20634A7832D422D3CD5023BE16829F26840E25C69404DD418306EA79F63F0'}
foreach($taskName in $taskPacks.Keys){if((Get-FileHash -LiteralPath (Join-Path $taskInstance "shaderpacks/$taskName")).Hash -ne $taskPacks[$taskName]){throw 'Shader ZIP changed'}}
if(@(Get-ChildItem -LiteralPath (Join-Path $taskInstance 'shaderpacks') -File).Count -ne 2){throw 'Unexpected pack/sidecar'}
$taskOptions=Get-Content -LiteralPath (Join-Path $taskInstance 'options.txt')
foreach($taskSetting in @('improvedTransparency:false','renderDistance:8','simulationDistance:6','mipmapLevels:2','resourcePacks:[]')){if($taskSetting -cnotin $taskOptions){throw "Unexpected setting: $taskSetting"}}
$taskArgumentsPath=Join-Path $taskInstance 'production-client.args'
$taskArguments=Get-Content -LiteralPath $taskArgumentsPath -Raw
if($taskArguments -notmatch '"forge_client"' -or $taskArguments -match 'sourceSets|forge_userdev|MOD_CLASSES|oculus.experimental|mixin.config'){throw 'Not clean production launch'}
if($PrepareOnly){Write-Output "Prepared isolated instance: $taskInstance";return}
$taskConsole=Join-Path $taskInstance "$RunName-console.log"
if(Test-Path -LiteralPath $taskConsole){throw 'Preserve old run log'}
$taskWork=[IO.Path]::GetFullPath((Join-Path $taskVersion '../../..'))
$taskJava=Join-Path $taskWork 'gradle-home/jdks/eclipse_adoptium-25-amd64-windows.2/bin/java.exe'
Push-Location -LiteralPath $taskInstance
try{
    & $taskJava ('@'+$taskArgumentsPath) *> $taskConsole
    $taskExit=$LASTEXITCODE
    [IO.File]::WriteAllText((Join-Path $taskInstance "$RunName-exit.txt"),[string]$taskExit)
    Copy-Item -LiteralPath (Join-Path $taskInstance 'logs/latest.log') -Destination (Join-Path $taskInstance "$RunName-game.log")
    if($taskExit -ne 0){throw "Lighting audit exited $taskExit"}
}finally{Pop-Location}
