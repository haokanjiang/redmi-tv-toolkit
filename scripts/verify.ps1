[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Device,
    [string]$AdbPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "common.ps1")

$Adb = Resolve-ToolkitAdb -AdbPath $AdbPath
Connect-ToolkitDevice -Adb $Adb -Device $Device

$resolvedHomeActivity = Get-ToolkitHome -Adb $Adb -Device $Device
$services = Get-ToolkitSetting -Adb $Adb -Device $Device `
    -Namespace secure -Name "enabled_accessibility_services"
$serviceList = if ($services) { @($services -split ":") } else { @() }
$periodicEnabled = Get-ToolkitPeriodicEnabled -Adb $Adb -Device $Device
$startupApp = Get-ToolkitSetting -Adb $Adb -Device $Device `
    -Namespace system -Name "start_3rd_app"
$devicePolicy = (Invoke-ToolkitAdb -Adb $Adb -Device $Device `
    -Arguments @("shell", "dumpsys", "device_policy") -AllowFailure | Out-String)

$checks = @(
    [pscustomobject]@{
        Check = "Projectivy installed"
        Pass = Test-ToolkitPackage -Adb $Adb -Device $Device -Package $script:ToolkitProjectivyPackage
        Actual = $script:ToolkitProjectivyPackage
        Required = $true
    },
    [pscustomobject]@{
        Check = "Helper installed"
        Pass = Test-ToolkitPackage -Adb $Adb -Device $Device -Package $script:ToolkitHelperPackage
        Actual = $script:ToolkitHelperPackage
        Required = $true
    },
    [pscustomobject]@{
        Check = "Projectivy is HOME"
        Pass = $resolvedHomeActivity -match "com\.spocky\.projengmenu"
        Actual = $resolvedHomeActivity
        Required = $true
    },
    [pscustomobject]@{
        Check = "Projectivy accessibility listed"
        Pass = $serviceList -contains $script:ToolkitProjectivyAccessibility
        Actual = [string]($serviceList -contains $script:ToolkitProjectivyAccessibility)
        Required = $true
    },
    [pscustomobject]@{
        Check = "Helper accessibility listed"
        Pass = $serviceList -contains $script:ToolkitHelperAccessibility
        Actual = [string]($serviceList -contains $script:ToolkitHelperAccessibility)
        Required = $true
    },
    [pscustomobject]@{
        Check = "Accessibility enabled"
        Pass = (Get-ToolkitSetting -Adb $Adb -Device $Device `
            -Namespace secure -Name "accessibility_enabled") -eq "1"
        Actual = Get-ToolkitSetting -Adb $Adb -Device $Device `
            -Namespace secure -Name "accessibility_enabled"
        Required = $true
    },
    [pscustomobject]@{
        Check = "ADB setting enabled"
        Pass = (Get-ToolkitSetting -Adb $Adb -Device $Device `
            -Namespace global -Name "adb_enabled") -eq "1"
        Actual = Get-ToolkitSetting -Adb $Adb -Device $Device `
            -Namespace global -Name "adb_enabled"
        Required = $true
    },
    [pscustomobject]@{
        Check = "PeriodicCleaner disabled"
        Pass = $periodicEnabled -eq $false
        Actual = if ($null -eq $periodicEnabled) { "unknown" } else { [string]$periodicEnabled }
        Required = $true
    },
    [pscustomobject]@{
        Check = "TVHome startup mediator"
        Pass = $startupApp -eq $script:ToolkitHelperPackage
        Actual = [string]$startupApp
        Required = $true
    },
    [pscustomobject]@{
        Check = "Helper device admin"
        Pass = $devicePolicy -match [regex]::Escape($script:ToolkitHelperAdmin)
        Actual = [string]($devicePolicy -match [regex]::Escape($script:ToolkitHelperAdmin))
        Required = $false
    },
    [pscustomobject]@{
        Check = "adbd running"
        Pass = (Get-ToolkitProp -Adb $Adb -Device $Device -Name "init.svc.adbd") -eq "running"
        Actual = Get-ToolkitProp -Adb $Adb -Device $Device -Name "init.svc.adbd"
        Required = $true
    }
)

$checks | Select-Object Check, Pass, Actual | Format-Table -AutoSize
$failed = @($checks | Where-Object { $_.Required -and -not $_.Pass })
if ($failed.Count -gt 0) {
    Write-Error "Verification failed: $($failed.Check -join ', ')"
    exit 1
}
if (-not ($checks | Where-Object Check -eq "Helper device admin").Pass) {
    Write-Warning "Core checks passed, but device admin is not active. Cold-boot reliability may be lower."
}
Write-Host "All required checks passed."
