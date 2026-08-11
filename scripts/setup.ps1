[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Device,
    [string]$AdbPath,
    [string]$ProjectivyApk,
    [string]$HelperApk,
    [switch]$AllowUntestedFirmware,
    [switch]$SkipProjectivy,
    [switch]$SkipDeviceAdmin,
    [switch]$RepairOnly
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "common.ps1")

function Get-LatestProjectivyApk {
    param([string]$DestinationDirectory)
    Write-Host "Resolving the latest official Projectivy GitHub release..."
    $release = Invoke-RestMethod -Headers @{ "User-Agent" = "redmi-tv-toolkit" } `
        -Uri "https://api.github.com/repos/spocky/miproja1/releases/latest"
    $asset = $release.assets |
        Where-Object { $_.name -match "(?i)Projectivy.*xda-release\.apk$" } |
        Sort-Object size -Descending |
        Select-Object -First 1
    if (-not $asset) {
        $asset = $release.assets |
            Where-Object { $_.name -match "(?i)\.apk$" } |
            Sort-Object size -Descending |
            Select-Object -First 1
    }
    if (-not $asset) {
        throw "The latest Projectivy release contains no APK asset. Pass -ProjectivyApk."
    }
    $path = Join-Path $DestinationDirectory $asset.name
    Invoke-WebRequest -Headers @{ "User-Agent" = "redmi-tv-toolkit" } `
        -Uri $asset.browser_download_url -OutFile $path
    if ($asset.PSObject.Properties.Name -contains "digest" -and
        $asset.digest -match "^sha256:([0-9a-fA-F]{64})$") {
        $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash
        if ($actual -ne $Matches[1]) {
            throw "Projectivy APK SHA-256 does not match GitHub release metadata."
        }
    }
    return $path
}

function Get-LatestHelperApk {
    param([string]$DestinationDirectory)
    $path = Join-Path $DestinationDirectory "redmi-tv-toolkit.apk"
    $checksumPath = Join-Path $DestinationDirectory "redmi-tv-toolkit.apk.sha256"
    $url = "https://github.com/haiktl/redmi-tv-toolkit/releases/latest/download/redmi-tv-toolkit.apk"
    Write-Host "Downloading the latest Redmi TV Toolkit release APK..."
    Invoke-WebRequest -Headers @{ "User-Agent" = "redmi-tv-toolkit" } -Uri $url -OutFile $path
    Invoke-WebRequest -Headers @{ "User-Agent" = "redmi-tv-toolkit" } `
        -Uri "$url.sha256" -OutFile $checksumPath
    $expected = ((Get-Content -Raw -LiteralPath $checksumPath).Trim() -split "\s+")[0]
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
    if ($expected.ToLowerInvariant() -ne $actual) {
        throw "Helper APK SHA-256 mismatch. Download aborted."
    }
    return $path
}

$Adb = Resolve-ToolkitAdb -AdbPath $AdbPath
Connect-ToolkitDevice -Adb $Adb -Device $Device
$DeviceInfo = Get-ToolkitDeviceInfo -Adb $Adb -Device $Device
Assert-ToolkitCompatibility -DeviceInfo $DeviceInfo -AllowUntestedFirmware:$AllowUntestedFirmware

Write-Host "Detected: $($DeviceInfo.Model) / $($DeviceInfo.Device) / Android $($DeviceInfo.Android)"

if ($RepairOnly) {
    if (-not (Test-ToolkitPackage -Adb $Adb -Device $Device -Package $script:ToolkitHelperPackage)) {
        throw "RepairOnly requires the helper package to be installed."
    }
    Invoke-ToolkitAdb -Adb $Adb -Device $Device -Arguments @(
        "shell", "pm", "grant", $script:ToolkitHelperPackage,
        "android.permission.WRITE_SECURE_SETTINGS") | Out-Null
    Invoke-ToolkitAdb -Adb $Adb -Device $Device -Arguments @(
        "shell", "am", "start", "-n", $script:ToolkitHelperControl,
        "--es", "action", "repair") | Out-Null
    Start-Sleep -Seconds 2
    & (Join-Path $PSScriptRoot "verify.ps1") -Device $Device -AdbPath $Adb
    exit $LASTEXITCODE
}

$BackupPath = Save-ToolkitBackup -Adb $Adb -Device $Device -DeviceInfo $DeviceInfo
Write-Host "Backup saved: $BackupPath"

$CacheRoot = Join-Path (Split-Path $PSScriptRoot -Parent) ".cache"
New-Item -ItemType Directory -Force -Path $CacheRoot | Out-Null

$projectivyInstalled = Test-ToolkitPackage -Adb $Adb -Device $Device `
    -Package $script:ToolkitProjectivyPackage
if ($ProjectivyApk -or (-not $SkipProjectivy -and -not $projectivyInstalled)) {
    if (-not $ProjectivyApk) {
        $ProjectivyApk = Get-LatestProjectivyApk -DestinationDirectory $CacheRoot
    }
    $ProjectivyApk = (Resolve-Path -LiteralPath $ProjectivyApk).Path
    Write-Host "Installing Projectivy from official/user-supplied APK..."
    Invoke-ToolkitAdb -Adb $Adb -Device $Device `
        -Arguments @("install", "-r", $ProjectivyApk) | Out-Null
}
if (-not (Test-ToolkitPackage -Adb $Adb -Device $Device -Package $script:ToolkitProjectivyPackage)) {
    throw "Projectivy is not installed. Remove -SkipProjectivy or pass -ProjectivyApk."
}

if (-not $HelperApk) {
    $localApk = Join-Path (Split-Path $PSScriptRoot -Parent) "app\dist\redmi-tv-toolkit.apk"
    if (Test-Path -LiteralPath $localApk) {
        $HelperApk = $localApk
    }
    else {
        $HelperApk = Get-LatestHelperApk -DestinationDirectory $CacheRoot
    }
}
$HelperApk = (Resolve-Path -LiteralPath $HelperApk).Path
Write-Host "Installing Redmi TV Toolkit helper..."
Invoke-ToolkitAdb -Adb $Adb -Device $Device -Arguments @("install", "-r", $HelperApk) | Out-Null

Invoke-ToolkitAdb -Adb $Adb -Device $Device -Arguments @(
    "shell", "pm", "grant", $script:ToolkitHelperPackage,
    "android.permission.WRITE_SECURE_SETTINGS") | Out-Null

Invoke-ToolkitAdb -Adb $Adb -Device $Device -Arguments @(
    "shell", "cmd", "package", "set-home-activity", $script:ToolkitProjectivyHome) | Out-Null

$currentServices = Get-ToolkitSetting -Adb $Adb -Device $Device `
    -Namespace secure -Name "enabled_accessibility_services"
$updatedServices = Merge-ToolkitAccessibilityServices -Current $currentServices
Set-ToolkitSetting -Adb $Adb -Device $Device -Namespace secure `
    -Name "enabled_accessibility_services" -Value $updatedServices
Set-ToolkitSetting -Adb $Adb -Device $Device -Namespace secure `
    -Name "accessibility_enabled" -Value "1"
Set-ToolkitSetting -Adb $Adb -Device $Device -Namespace global `
    -Name "adb_enabled" -Value "1"

Invoke-ToolkitAdb -Adb $Adb -Device $Device `
    -Arguments @("shell", "cmd", "periodic", "enable", "false") -AllowFailure | Out-Null

if (-not $SkipDeviceAdmin) {
    $adminOutput = Invoke-ToolkitAdb -Adb $Adb -Device $Device -Arguments @(
        "shell", "dpm", "set-active-admin", $script:ToolkitHelperAdmin) -AllowFailure
    if (($adminOutput | Out-String) -match "Error|Exception|denied") {
        Write-Warning "The TV requires its standard device-admin confirmation. The helper will open it once."
    }
}

Invoke-ToolkitAdb -Adb $Adb -Device $Device -Arguments @(
    "shell", "am", "start", "-n", $script:ToolkitHelperMain) | Out-Null
Start-Sleep -Seconds 3

& (Join-Path $PSScriptRoot "verify.ps1") -Device $Device -AdbPath $Adb
if ($LASTEXITCODE -ne 0) {
    throw "Setup completed but verification failed. Backup: $BackupPath"
}

Write-Host "Setup verified. Cold-boot and soft-power tests are still recommended."
Write-Host "Rollback backup: $BackupPath"
