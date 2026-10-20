[CmdletBinding()]
param([string]$Device=$env:TV_HOST,[string]$AdbPath)
$ErrorActionPreference="Stop";. (Join-Path $PSScriptRoot "common.ps1");$adb=Resolve-ToolkitAdb $AdbPath;Connect-ToolkitDevice $adb $Device;$info=Get-ToolkitDeviceInfo $adb $Device;Assert-ToolkitCompatibility $info
[ordered]@{Device=$info;SystemHome=Get-ToolkitHome $adb $Device;AccessibilityServices=Get-ToolkitSetting $adb $Device secure enabled_accessibility_services;AccessibilityEnabled=Get-ToolkitSetting $adb $Device secure accessibility_enabled;Candidates=@(Get-ToolkitLaunchCandidates $adb $Device | Where-Object { $_.IsHome })}|ConvertTo-Json -Depth 8

