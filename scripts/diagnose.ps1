[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Device,
    [string]$AdbPath,
    [string]$OutputFile
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "common.ps1")

$Adb = Resolve-ToolkitAdb -AdbPath $AdbPath
Connect-ToolkitDevice -Adb $Adb -Device $Device
$info = Get-ToolkitDeviceInfo -Adb $Adb -Device $Device
$services = Get-ToolkitSetting -Adb $Adb -Device $Device `
    -Namespace secure -Name "enabled_accessibility_services"
$serviceList = if ($services) { @($services -split ":") } else { @() }
$accessibilityDump = (Invoke-ToolkitAdb -Adb $Adb -Device $Device `
    -Arguments @("shell", "dumpsys", "accessibility") -AllowFailure | Out-String)

$result = [ordered]@{
    SchemaVersion = 1
    CollectedAt = (Get-Date).ToString("o")
    Target = "authorized-adb-device"
    Device = $info
    State = [ordered]@{
        Home = Get-ToolkitHome -Adb $Adb -Device $Device
        ProjectivyInstalled = Test-ToolkitPackage -Adb $Adb -Device $Device `
            -Package $script:ToolkitProjectivyPackage
        HelperInstalled = Test-ToolkitPackage -Adb $Adb -Device $Device `
            -Package $script:ToolkitHelperPackage
        ProjectivyAccessibilityListed = $serviceList -contains $script:ToolkitProjectivyAccessibility
        HelperAccessibilityListed = $serviceList -contains $script:ToolkitHelperAccessibility
        ProjectivyMentionedInCrashedServices =
            $accessibilityDump -match "(?s)Crashed services:.*com\.spocky\.projengmenu"
        AccessibilityEnabled = Get-ToolkitSetting -Adb $Adb -Device $Device `
            -Namespace secure -Name "accessibility_enabled"
        AdbEnabled = Get-ToolkitSetting -Adb $Adb -Device $Device `
            -Namespace global -Name "adb_enabled"
        AdbdState = Get-ToolkitProp -Adb $Adb -Device $Device -Name "init.svc.adbd"
        PersistentAdbPort = Get-ToolkitProp -Adb $Adb -Device $Device -Name "persist.adb.tcp.port"
        ServiceAdbPort = Get-ToolkitProp -Adb $Adb -Device $Device -Name "service.adb.tcp.port"
        StartupApp = Get-ToolkitSetting -Adb $Adb -Device $Device `
            -Namespace system -Name "start_3rd_app"
        PeriodicEnabled = Get-ToolkitPeriodicEnabled -Adb $Adb -Device $Device
    }
}

$json = $result | ConvertTo-Json -Depth 8
if ($OutputFile) {
    $resolvedOutput = [IO.Path]::GetFullPath($OutputFile)
    $parent = Split-Path -Parent $resolvedOutput
    if ($parent) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    Set-Content -LiteralPath $resolvedOutput -Value $json -Encoding utf8
    Write-Host "Diagnosis saved: $resolvedOutput"
    Write-Warning "Review and redact the JSON before posting publicly."
}
else {
    $json
}

