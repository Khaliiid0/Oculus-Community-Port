param(
    [int]$GameProcessId = 28200,
    [datetime]$StopAtUtc = [datetime]'2026-11-17T18:58:28Z',
    [ValidatePattern('^[a-z0-9-]+\.csv$')][string]$OutputFileName = 'gameplay-process-samples.csv'
)
$ErrorActionPreference = 'Stop'
$taskVersionRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskEvidence = Join-Path $taskVersionRoot 'test-evidence/2026-11-17-baseline-and-gameplay'
$taskCsv = Join-Path $taskEvidence $OutputFileName
if (Test-Path -LiteralPath $taskCsv) { throw 'Preserve existing monitor output' }
$taskLog = Join-Path $taskVersionRoot 'installed-audit/instance/logs/latest.log'
$taskInitial = Get-CimInstance Win32_Process -Filter "ProcessId=$GameProcessId"
if (-not $taskInitial -or $taskInitial.CommandLine -notlike '*installed-audit*production-client.args*') { throw 'Not the selected audit client' }
$taskSample = 0
while ([datetime]::UtcNow -lt $StopAtUtc.ToUniversalTime()) {
    $taskProcess = Get-Process -Id $GameProcessId -ErrorAction SilentlyContinue
    if (-not $taskProcess) { Write-Output 'Selected client process exited; inspect its exit code/log.'; break }
    $taskText = Get-Content -LiteralPath $taskLog -Raw
    $taskRow = [pscustomobject]@{
        Utc = [datetime]::UtcNow.ToString('o')
        ProcessId = $taskProcess.Id
        CpuSeconds = $taskProcess.CPU
        WorkingSetBytes = $taskProcess.WorkingSet64
        PrivateBytes = $taskProcess.PrivateMemorySize64
        LogBytes = (Get-Item -LiteralPath $taskLog).Length
        ErrorRecords = ([regex]::Matches($taskText, '/ERROR\]')).Count
        GlInvalidRecords = ([regex]::Matches($taskText, 'GL_INVALID')).Count
    }
    $taskRow | Export-Csv -LiteralPath $taskCsv -NoTypeInformation -Append
    if ($taskSample % 2 -eq 0) {
        Write-Output "$($taskRow.Utc): client alive; WS=$($taskRow.WorkingSetBytes); private=$($taskRow.PrivateBytes); error records=$($taskRow.ErrorRecords); GL_INVALID=$($taskRow.GlInvalidRecords)"
    }
    $taskSample++
    Start-Sleep -Seconds 30
}
Write-Output 'Monitor finished. These samples are not FPS measurements or proof of active play; use user feedback and final logs.'
