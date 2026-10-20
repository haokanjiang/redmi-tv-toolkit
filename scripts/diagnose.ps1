[CmdletBinding()]
param([string]$Device=$env:TV_HOST,[string]$AdbPath)
$ErrorActionPreference="Stop"
. (Join-Path $PSScriptRoot "common.ps1")
if (-not $Device) { throw "Pass -Device or set TV_HOST" }
$adb=Resolve-ToolkitAdb $AdbPath
Connect-ToolkitDevice $adb $Device
$info=Get-ToolkitDeviceInfo $adb $Device
Assert-ToolkitCompatibility $info
[ordered]@{
    Device=$info
    Adb=Get-ToolkitAdbDiagnostic $adb $Device
    SystemHome=Get-ToolkitHome $adb $Device
    AccessibilityServices=Get-ToolkitSetting $adb $Device secure enabled_accessibility_services
    AccessibilityEnabled=Get-ToolkitSetting $adb $Device secure accessibility_enabled
    Toolkit=Invoke-ToolkitControl $adb $Device HOME_STATUS
} | ConvertTo-Json -Depth 8
