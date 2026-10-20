[CmdletBinding()]
param([string]$Device=$env:TV_HOST, [string]$AdbPath, [string]$TargetComponent,
    [string]$FallbackComponent, [string]$ApkPath)
$ErrorActionPreference="Stop"
. (Join-Path $PSScriptRoot "common.ps1")
if (-not $Device) { throw "Pass -Device" }
$adb=Resolve-ToolkitAdb $AdbPath
Connect-ToolkitDevice $adb $Device
$info=Get-ToolkitDeviceInfo $adb $Device
Assert-ToolkitCompatibility $info
if ($FallbackComponent -and -not $TargetComponent) { throw "FallbackComponent requires TargetComponent" }
if ($TargetComponent) {
    $canonical=ConvertTo-ToolkitComponent $TargetComponent
    if (@(Get-ToolkitLaunchCandidates $adb $Device | Where-Object {$_.Component -ceq $canonical}).Count -ne 1) {
        throw "Target is not an installed HOME/LEANBACK component"
    }
}
if (-not $ApkPath) {
    $name=if($info.Model -eq "MiTV-MFTR0"){"redmi-tv-toolkit.apk"}else{"redmi-tv-toolkit-blueplanet.apk"}
    $ApkPath=Join-Path $PSScriptRoot "..\app\dist\$name"
}
Install-ToolkitHelper $adb $Device $ApkPath
Write-Host "Helper installed; WRITE_SECURE_SETTINGS verified."
if ($TargetComponent) {
    $options=@{Adb=$adb;Device=$Device;TargetComponent=$TargetComponent}
    if ($PSBoundParameters.ContainsKey("FallbackComponent")) {$options.FallbackComponent=$FallbackComponent}
    Set-ToolkitTarget @options | Out-Null
}
