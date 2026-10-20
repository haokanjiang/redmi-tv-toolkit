[CmdletBinding()]
param([string]$Device=$env:TV_HOST,[string]$AdbPath,[string]$TargetComponent,[string]$FallbackComponent)
$ErrorActionPreference="Stop";. (Join-Path $PSScriptRoot "common.ps1");if(!$Device){throw "Pass -Device"};$adb=Resolve-ToolkitAdb $AdbPath;Connect-ToolkitDevice $adb $Device;$info=Get-ToolkitDeviceInfo $adb $Device;Assert-ToolkitCompatibility $info
$apk=Join-Path $PSScriptRoot "..\app\dist\redmi-tv-toolkit.apk";if(!(Test-Path $apk)){& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "..\app\build.ps1");if($LASTEXITCODE){throw "build failed"}}
Invoke-ToolkitAdb $adb $Device @("install","-r",$apk)|Out-Null;Invoke-ToolkitAdb $adb $Device @("shell","pm","grant",$script:ToolkitHelperPackage,"android.permission.WRITE_SECURE_SETTINGS") -AllowFailure|Out-Null
Write-Host "Toolkit installed. Use manage-home.ps1 to choose a launcher; no system Home was changed."
if($TargetComponent){& (Join-Path $PSScriptRoot "manage-home.ps1") -Device $Device -Select 0}
