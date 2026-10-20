[CmdletBinding()]
param([string]$Device=$env:TV_HOST,[string]$AdbPath)
$ErrorActionPreference="Stop";. (Join-Path $PSScriptRoot "common.ps1");$adb=Resolve-ToolkitAdb $AdbPath;Connect-ToolkitDevice $adb $Device;$info=Get-ToolkitDeviceInfo $adb $Device;Assert-ToolkitCompatibility $info;Write-Host "Profile check: $($info.Model)/$($info.Device)";Write-Host "System Home: $(Get-ToolkitHome $adb $Device)";Write-Host "Candidates: $(@(Get-ToolkitLaunchCandidates $adb $Device).Count)";Write-Host "Safety checks: system Home and packages were left unchanged."

