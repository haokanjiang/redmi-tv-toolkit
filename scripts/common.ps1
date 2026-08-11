Set-StrictMode -Version Latest

$script:ToolkitProjectivyPackage = "com.spocky.projengmenu"
$script:ToolkitProjectivyHome = "com.spocky.projengmenu/.ui.home.MainActivity"
$script:ToolkitProjectivyAccessibility =
    "com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService"
$script:ToolkitHelperPackage = "local.blueplanet.boothelper"
$script:ToolkitHelperAccessibility =
    "local.blueplanet.boothelper/local.blueplanet.boothelper.BootAccessibilityService"
$script:ToolkitHelperMain = "local.blueplanet.boothelper/.MainActivity"
$script:ToolkitHelperControl = "local.blueplanet.boothelper/.ControlActivity"
$script:ToolkitHelperAdmin = "local.blueplanet.boothelper/.BootAdminReceiver"

function Resolve-ToolkitAdb {
    param([string]$AdbPath)
    if ($AdbPath) {
        $resolved = Resolve-Path -LiteralPath $AdbPath -ErrorAction Stop
        return $resolved.Path
    }
    $command = Get-Command adb.exe -ErrorAction SilentlyContinue
    if (-not $command) {
        $command = Get-Command adb -ErrorAction SilentlyContinue
    }
    if ($command) {
        return $command.Source
    }
    $UserProfilePath = [Environment]::GetFolderPath("UserProfile")
    $candidate = Join-Path $UserProfilePath "platform-tools\adb.exe"
    if (Test-Path -LiteralPath $candidate) {
        return $candidate
    }
    throw "adb not found. Install Android Platform Tools or pass -AdbPath."
}

function Invoke-ToolkitAdb {
    param(
        [Parameter(Mandatory = $true)][string]$Adb,
        [Parameter(Mandatory = $true)][string]$Device,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowFailure
    )
    $output = & $Adb -s $Device @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if (-not $AllowFailure -and $exitCode -ne 0) {
        throw "adb failed ($exitCode): adb -s <device> $($Arguments -join ' ')`n$($output | Out-String)"
    }
    return $output
}

function Connect-ToolkitDevice {
    param(
        [Parameter(Mandatory = $true)][string]$Adb,
        [Parameter(Mandatory = $true)][string]$Device,
        [int]$TimeoutSeconds = 30
    )
    & $Adb start-server | Out-Null
    if ($Device.Contains(":")) {
        & $Adb connect $Device | Out-Null
    }
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $state = (& $Adb -s $Device get-state 2>$null | Out-String).Trim()
        if ($state -eq "device") {
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "ADB device is not authorized/online. Accept the TV authorization dialog and retry."
}

function Get-ToolkitProp {
    param(
        [Parameter(Mandatory = $true)][string]$Adb,
        [Parameter(Mandatory = $true)][string]$Device,
        [Parameter(Mandatory = $true)][string]$Name
    )
    return (Invoke-ToolkitAdb -Adb $Adb -Device $Device `
        -Arguments @("shell", "getprop", $Name) | Out-String).Trim()
}

function Get-ToolkitSetting {
    param(
        [Parameter(Mandatory = $true)][string]$Adb,
        [Parameter(Mandatory = $true)][string]$Device,
        [Parameter(Mandatory = $true)][ValidateSet("global", "secure", "system")][string]$Namespace,
        [Parameter(Mandatory = $true)][string]$Name
    )
    $value = (Invoke-ToolkitAdb -Adb $Adb -Device $Device `
        -Arguments @("shell", "settings", "get", $Namespace, $Name) `
        -AllowFailure | Out-String).Trim()
    if (-not $value -or $value -eq "null") {
        return $null
    }
    return $value
}

function Set-ToolkitSetting {
    param(
        [Parameter(Mandatory = $true)][string]$Adb,
        [Parameter(Mandatory = $true)][string]$Device,
        [Parameter(Mandatory = $true)][ValidateSet("global", "secure", "system")][string]$Namespace,
        [Parameter(Mandatory = $true)][string]$Name,
        [AllowNull()][string]$Value
    )
    if ($null -eq $Value -or $Value -eq "") {
        Invoke-ToolkitAdb -Adb $Adb -Device $Device `
            -Arguments @("shell", "settings", "delete", $Namespace, $Name) | Out-Null
    }
    else {
        Invoke-ToolkitAdb -Adb $Adb -Device $Device `
            -Arguments @("shell", "settings", "put", $Namespace, $Name, $Value) | Out-Null
    }
}

function Get-ToolkitHome {
    param([string]$Adb, [string]$Device)
    $output = Invoke-ToolkitAdb -Adb $Adb -Device $Device `
        -Arguments @("shell", "cmd", "package", "resolve-activity", "--brief",
            "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME") `
        -AllowFailure
    return ($output | Select-Object -Last 1 | Out-String).Trim()
}

function Test-ToolkitPackage {
    param([string]$Adb, [string]$Device, [string]$Package)
    $output = Invoke-ToolkitAdb -Adb $Adb -Device $Device `
        -Arguments @("shell", "pm", "path", $Package) -AllowFailure
    return (($output | Out-String) -match "package:")
}

function Get-ToolkitDeviceInfo {
    param([string]$Adb, [string]$Device)
    return [ordered]@{
        Model = Get-ToolkitProp -Adb $Adb -Device $Device -Name "ro.product.model"
        Product = Get-ToolkitProp -Adb $Adb -Device $Device -Name "ro.product.name"
        Device = Get-ToolkitProp -Adb $Adb -Device $Device -Name "ro.product.device"
        Board = Get-ToolkitProp -Adb $Adb -Device $Device -Name "ro.board.platform"
        Android = Get-ToolkitProp -Adb $Adb -Device $Device -Name "ro.build.version.release"
        Sdk = Get-ToolkitProp -Adb $Adb -Device $Device -Name "ro.build.version.sdk"
        Fingerprint = Get-ToolkitProp -Adb $Adb -Device $Device -Name "ro.build.fingerprint"
        BuildIncremental = Get-ToolkitProp -Adb $Adb -Device $Device -Name "ro.build.version.incremental"
    }
}

function Assert-ToolkitCompatibility {
    param(
        [Parameter(Mandatory = $true)]$DeviceInfo,
        [switch]$AllowUntestedFirmware
    )
    $supported = $DeviceInfo.Device -eq "blueplanet" -or $DeviceInfo.Board -eq "blueplanet"
    if (-not $supported -and -not $AllowUntestedFirmware) {
        throw "Unsupported device '$($DeviceInfo.Model)' device='$($DeviceInfo.Device)' board='$($DeviceInfo.Board)'. Use -AllowUntestedFirmware only after reviewing docs/compatibility.md."
    }
    if (-not $supported) {
        Write-Warning "Continuing on untested firmware by explicit request. The TV helper itself will still block non-blueplanet system-bridge actions."
    }
}

function Get-ToolkitPeriodicEnabled {
    param([string]$Adb, [string]$Device)
    $dump = (Invoke-ToolkitAdb -Adb $Adb -Device $Device `
        -Arguments @("shell", "dumpsys", "periodic") -AllowFailure | Out-String)
    if ($dump -match "Enable=(true|false)") {
        return [bool]::Parse($Matches[1])
    }
    return $null
}

function Get-ToolkitBackupRoot {
    $UserProfilePath = [Environment]::GetFolderPath("UserProfile")
    return Join-Path $UserProfilePath ".redmi-tv-toolkit\backups"
}

function Save-ToolkitBackup {
    param([string]$Adb, [string]$Device, $DeviceInfo)
    $root = Get-ToolkitBackupRoot
    New-Item -ItemType Directory -Force -Path $root | Out-Null
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $path = Join-Path $root "blueplanet-$timestamp.json"
    $backup = [ordered]@{
        SchemaVersion = 1
        CreatedAt = (Get-Date).ToString("o")
        DeviceInfo = $DeviceInfo
        Home = Get-ToolkitHome -Adb $Adb -Device $Device
        AccessibilityServices = Get-ToolkitSetting -Adb $Adb -Device $Device `
            -Namespace secure -Name "enabled_accessibility_services"
        AccessibilityEnabled = Get-ToolkitSetting -Adb $Adb -Device $Device `
            -Namespace secure -Name "accessibility_enabled"
        AdbEnabled = Get-ToolkitSetting -Adb $Adb -Device $Device `
            -Namespace global -Name "adb_enabled"
        StartupApp = Get-ToolkitSetting -Adb $Adb -Device $Device `
            -Namespace system -Name "start_3rd_app"
        PeriodicEnabled = Get-ToolkitPeriodicEnabled -Adb $Adb -Device $Device
    }
    $backup | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $path -Encoding utf8
    return $path
}

function Merge-ToolkitAccessibilityServices {
    param([AllowNull()][string]$Current)
    $services = [System.Collections.Generic.List[string]]::new()
    if ($Current) {
        foreach ($service in ($Current -split ":")) {
            if ($service -and
                $service -ne "com.spocky.projengmenu/.services.ProjectivyAccessibilityService" -and
                $service -ne "local.blueplanet.boothelper/.BootAccessibilityService" -and
                -not $services.Contains($service)) {
                $services.Add($service)
            }
        }
    }
    foreach ($required in @($script:ToolkitProjectivyAccessibility, $script:ToolkitHelperAccessibility)) {
        if (-not $services.Contains($required)) {
            $services.Add($required)
        }
    }
    return ($services -join ":")
}

