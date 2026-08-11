[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Device,
    [string]$AdbPath,
    [string]$BackupPath,
    [switch]$KeepHelper,
    [switch]$RemoveProjectivy,
    [switch]$RestoreAdbState
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "common.ps1")

$Adb = Resolve-ToolkitAdb -AdbPath $AdbPath
Connect-ToolkitDevice -Adb $Adb -Device $Device

if (-not $BackupPath) {
    $BackupPath = Get-ChildItem -LiteralPath (Get-ToolkitBackupRoot) `
        -Filter "blueplanet-*.json" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $BackupPath -or -not (Test-Path -LiteralPath $BackupPath)) {
    throw "No backup found. Pass -BackupPath to a setup-created JSON backup."
}
$BackupPath = (Resolve-Path -LiteralPath $BackupPath).Path
$backup = Get-Content -Raw -LiteralPath $BackupPath | ConvertFrom-Json
if ($backup.SchemaVersion -ne 1) {
    throw "Unsupported backup schema: $($backup.SchemaVersion)"
}

Write-Host "Restoring backup: $BackupPath"

if (Test-ToolkitPackage -Adb $Adb -Device $Device -Package $script:ToolkitHelperPackage) {
    $startupValue = if ($null -eq $backup.StartupApp) { "" } else { [string]$backup.StartupApp }
    Invoke-ToolkitAdb -Adb $Adb -Device $Device -Arguments @(
        "shell", "am", "start", "-n", $script:ToolkitHelperControl,
        "--es", "action", "restore-startup", "--es", "value", $startupValue) `
        -AllowFailure | Out-Null
    Start-Sleep -Seconds 1
}

Set-ToolkitSetting -Adb $Adb -Device $Device -Namespace secure `
    -Name "enabled_accessibility_services" -Value $backup.AccessibilityServices
Set-ToolkitSetting -Adb $Adb -Device $Device -Namespace secure `
    -Name "accessibility_enabled" -Value $backup.AccessibilityEnabled

if ($backup.Home -and $backup.Home -match "/") {
    Invoke-ToolkitAdb -Adb $Adb -Device $Device -Arguments @(
        "shell", "cmd", "package", "set-home-activity", [string]$backup.Home) `
        -AllowFailure | Out-Null
}

if ($null -ne $backup.PeriodicEnabled) {
    $periodicValue = if ([bool]$backup.PeriodicEnabled) { "true" } else { "false" }
    Invoke-ToolkitAdb -Adb $Adb -Device $Device `
        -Arguments @("shell", "cmd", "periodic", "enable", $periodicValue) `
        -AllowFailure | Out-Null
}

if (-not $KeepHelper -and
    (Test-ToolkitPackage -Adb $Adb -Device $Device -Package $script:ToolkitHelperPackage)) {
    Invoke-ToolkitAdb -Adb $Adb -Device $Device -Arguments @(
        "shell", "dpm", "remove-active-admin", $script:ToolkitHelperAdmin) `
        -AllowFailure | Out-Null
    Invoke-ToolkitAdb -Adb $Adb -Device $Device -Arguments @(
        "uninstall", $script:ToolkitHelperPackage) -AllowFailure | Out-Null
}

if ($RemoveProjectivy -and
    (Test-ToolkitPackage -Adb $Adb -Device $Device -Package $script:ToolkitProjectivyPackage)) {
    Invoke-ToolkitAdb -Adb $Adb -Device $Device -Arguments @(
        "uninstall", $script:ToolkitProjectivyPackage) -AllowFailure | Out-Null
}

if ($RestoreAdbState) {
    Write-Warning "Restoring adb_enabled last; the device may disconnect immediately."
    Set-ToolkitSetting -Adb $Adb -Device $Device -Namespace global `
        -Name "adb_enabled" -Value $backup.AdbEnabled
}

Write-Host "Rollback completed. Projectivy was preserved unless -RemoveProjectivy was used."

