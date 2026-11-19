param([Parameter(Mandatory=$true)][int]$MinecraftProcessId)
$ErrorActionPreference = 'Stop'
$taskProcess = Get-Process -Id $MinecraftProcessId
if ($taskProcess.ProcessName -notin @('java', 'javaw') -or $taskProcess.MainWindowTitle -notmatch '^Minecraft' -or $taskProcess.MainWindowHandle -eq 0) {
    throw 'Refusing to send test hotkeys to a non-Minecraft process'
}
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class OculusTestKeyboard {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr window);
    [DllImport("user32.dll")] public static extern void keybd_event(byte key, byte scan, uint flags, UIntPtr extra);
}
'@
if (-not [OculusTestKeyboard]::SetForegroundWindow($taskProcess.MainWindowHandle)) {
    throw 'Could not focus the Minecraft test window'
}
Start-Sleep -Milliseconds 150
try {
    [OculusTestKeyboard]::keybd_event(0x72, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 100
    [OculusTestKeyboard]::keybd_event(0x54, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 100
} finally {
    [OculusTestKeyboard]::keybd_event(0x54, 0, 2, [UIntPtr]::Zero)
    [OculusTestKeyboard]::keybd_event(0x72, 0, 2, [UIntPtr]::Zero)
}
"Sent F3+T to Minecraft PID $MinecraftProcessId"
