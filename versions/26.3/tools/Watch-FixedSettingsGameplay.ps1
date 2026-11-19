param([Parameter(Mandatory)][int]$ClientProcessId)
$ErrorActionPreference = 'Stop'
$taskVersion = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskInstance = Join-Path $taskVersion 'installed-audit/gameplay-checkpoint3'
$taskEvidence = Join-Path $taskVersion 'test-evidence/2026-11-17-corrected-gameplay'
$taskProcessInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$ClientProcessId"
if ($taskProcessInfo.Name -ne 'java.exe' -or $taskProcessInfo.CommandLine -notlike '*gameplay-checkpoint3*production-client.args*') { throw 'Not the expected test client' }
$taskIdentity = (Get-Process -Id $ClientProcessId).StartTime.ToUniversalTime()
New-Item -ItemType Directory -Path $taskEvidence -Force | Out-Null
$taskCsv = Join-Path $taskEvidence 'process-monitor.csv'
if (Test-Path -LiteralPath $taskCsv) { throw 'Preserve existing monitor evidence' }
$taskLastCounts = ''
for ($taskIndex = 0; $taskIndex -lt 240; $taskIndex++) {
    $taskProcess = Get-Process -Id $ClientProcessId -ErrorAction SilentlyContinue
    if (-not $taskProcess) { Write-Output 'Client exited; monitor stopped.'; break }
    if ($taskProcess.StartTime.ToUniversalTime() -ne $taskIdentity) { throw 'Process ID reused' }
    $taskLog = Get-Content -LiteralPath (Join-Path $taskInstance 'logs/latest.log') -Raw
    $taskPauseCount = [regex]::Matches($taskLog, 'Saving and pausing game').Count
    $taskFaultCount = [regex]::Matches($taskLog, 'HDR scene capture failed|GL_INVALID|shader pass failed at runtime|Could not activate shader pack|Shaders paused:').Count
    $taskRow = [pscustomobject]@{
        SampleUtc=[DateTime]::UtcNow.ToString('o'); ProcessId=$ClientProcessId
        ProcessAgeSeconds=[math]::Round(([DateTime]::UtcNow-$taskIdentity).TotalSeconds,1)
        CpuSeconds=[math]::Round($taskProcess.TotalProcessorTime.TotalSeconds,2)
        WorkingSetBytes=$taskProcess.WorkingSet64; PrivateBytes=$taskProcess.PrivateMemorySize64
        HandleCount=$taskProcess.HandleCount; PauseLogCount=$taskPauseCount
        RenderingFaultCount=$taskFaultCount
        SavedOptionsSHA256=(Get-FileHash -LiteralPath (Join-Path $taskInstance 'options.txt')).Hash
        LogBytes=(Get-Item -LiteralPath (Join-Path $taskInstance 'logs/latest.log')).Length
    }
    $taskRow | Export-Csv -LiteralPath $taskCsv -NoTypeInformation -Append
    $taskCounts = "$taskPauseCount/$taskFaultCount/$($taskRow.SavedOptionsSHA256)"
    if ($taskCounts -ne $taskLastCounts) {
        Write-Output "$($taskRow.SampleUtc): pauses=$taskPauseCount renderingFaults=$taskFaultCount options=$($taskRow.SavedOptionsSHA256)"
        $taskLastCounts = $taskCounts
    }
    Start-Sleep -Seconds 30
}
# Process lifetime/CPU and saved options cannot establish active gameplay or FPS.
