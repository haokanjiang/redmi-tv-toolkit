[CmdletBinding(DefaultParameterSetName="Interactive")]
param(
    [string]$Device=$env:TV_HOST,
    [string]$AdbPath,
    [Parameter(ParameterSetName="List")][switch]$List,
    [Parameter(ParameterSetName="Status")][switch]$Status,
    [Parameter(ParameterSetName="Restore")][switch]$Restore,
    [Parameter(ParameterSetName="Number",Mandatory=$true)][ValidateRange(1,2147483647)][int]$Select,
    [Parameter(ParameterSetName="Component",Mandatory=$true)][string]$TargetComponent,
    [Parameter(ParameterSetName="Number")][Parameter(ParameterSetName="Component")][string]$FallbackComponent,
    [Parameter(ParameterSetName="Help")][switch]$Help
)
$ErrorActionPreference="Stop"
. (Join-Path $PSScriptRoot "common.ps1")
if ($Help) {
    Write-Host "manage-home.ps1 -Device HOST:PORT [-List|-Status|-Restore|-Select N|-TargetComponent PACKAGE/ACTIVITY]"
    Write-Host "HOME entries are desktops; LEANBACK-only entries are TV apps, labelled separately."
    return
}
if (-not $Device) { throw "Pass -Device or set TV_HOST" }
$adb=Resolve-ToolkitAdb $AdbPath
Connect-ToolkitDevice $adb $Device
$info=Get-ToolkitDeviceInfo $adb $Device
Assert-ToolkitCompatibility $info
if ($Status) {
    $state = Invoke-ToolkitControl $adb $Device HOME_STATUS
    [ordered]@{Device=$info; SystemHome=Get-ToolkitHome $adb $Device; Toolkit=$state} | ConvertTo-Json -Depth 6
    return
}
if ($Restore) {
    $ack = Invoke-ToolkitControl $adb $Device HOME_RESTORE
    $state = Invoke-ToolkitControl $adb $Device HOME_STATUS
    if ($state.enabled -or $state.recovery) { throw "Restore read-back failed" }
    Write-Host "Verified: Home Override and boot recovery disabled."
    return
}
$candidates=@(Get-ToolkitLaunchCandidates $adb $Device)
if ($List -or $PSCmdlet.ParameterSetName -eq "Interactive") {
    for ($i=0; $i -lt $candidates.Count; $i++) {
        $entry=$candidates[$i]
        $label=if($entry.IsHome){"HOME desktop"}else{"LEANBACK TV entry (not a HOME desktop)"}
        Write-Host "[$($i+1)] $($entry.Component) [$label]"
    }
    if ($List) { return }
    $number=[int](Read-Host "Select an entry number (0 cancels)")
    if ($number -eq 0) { return }
} else { $number=$Select }
if (-not $TargetComponent) {
    if ($number -lt 1 -or $number -gt $candidates.Count) { throw "Invalid selection" }
    $TargetComponent=$candidates[$number-1].Component
}
$options=@{Adb=$adb;Device=$Device;TargetComponent=$TargetComponent}
if ($PSBoundParameters.ContainsKey("FallbackComponent")) {$options.FallbackComponent=$FallbackComponent}
Set-ToolkitTarget @options | Out-Null
