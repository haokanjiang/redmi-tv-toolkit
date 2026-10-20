Set-StrictMode -Version Latest
$script:ToolkitHelperPackage="com.redmi.tvtoolkit"
$script:ToolkitHelperAccessibility="com.redmi.tvtoolkit/com.redmi.tvtoolkit.HomeOverrideService"
$script:ToolkitHelperMain="com.redmi.tvtoolkit/.MainActivity"
$script:ToolkitControlReceiver="com.redmi.tvtoolkit/.ToolkitControlReceiver"
function Resolve-ToolkitAdb {
    param([string]$AdbPath)
    if ($AdbPath) { return (Resolve-Path -LiteralPath $AdbPath -ErrorAction Stop).Path }
    $command = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $roots = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME,
        (Join-Path ([Environment]::GetFolderPath("LocalApplicationData")) "Android\Sdk"))
    foreach ($root in $roots) {
        if (-not $root) { continue }
        $candidate = Join-Path $root "platform-tools\adb.exe"
        if (Test-Path -LiteralPath $candidate) { return (Resolve-Path -LiteralPath $candidate).Path }
    }
    $candidate = Join-Path ([Environment]::GetFolderPath("UserProfile")) "platform-tools\adb.exe"
    if (Test-Path -LiteralPath $candidate) { return (Resolve-Path -LiteralPath $candidate).Path }
    throw "adb not found; install Android SDK platform-tools or pass -AdbPath"
}
function Invoke-ToolkitAdb {
    param([string]$Adb,[string]$Device,[string[]]$Arguments,[switch]$AllowFailure)
    $old = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $nativeArguments = $Arguments
        if ($Arguments.Count -gt 1 -and $Arguments[0] -eq "shell") {
            $quoted = @($Arguments[1..($Arguments.Count-1)] | ForEach-Object {
                "'" + $_.Replace("'", "'\''") + "'"
            })
            $nativeArguments = @("shell", ($quoted -join " "))
        }
        $o = & $Adb -s $Device @nativeArguments 2>&1
        $code = $LASTEXITCODE
        if (-not $AllowFailure -and $code -ne 0) { throw "adb failed: $($o -join "`n")" }
        return @($o | ForEach-Object { $_.ToString() })
    }
    finally { $ErrorActionPreference = $old }
}
function Connect-ToolkitDevice { param([string]$Adb,[string]$Device,[int]$TimeoutSeconds=30); & $Adb start-server | Out-Null; if($Device.Contains(":")){& $Adb connect $Device | Out-Null}; $end=(Get-Date).AddSeconds($TimeoutSeconds); do{$s=(& $Adb -s $Device get-state 2>$null|Out-String).Trim();if($s -eq "device"){return};Start-Sleep 1}while((Get-Date)-lt $end);throw "ADB device unavailable" }
function Get-ToolkitProp { param([string]$Adb,[string]$Device,[string]$Name); (Invoke-ToolkitAdb $Adb $Device @("shell","getprop",$Name)|Out-String).Trim() }
function Get-ToolkitSetting { param([string]$Adb,[string]$Device,[string]$Namespace,[string]$Name); $v=(Invoke-ToolkitAdb $Adb $Device @("shell","settings","get",$Namespace,$Name)|Out-String).Trim(); if(!$v -or $v -eq "null"){return $null};return $v }
function Get-ToolkitAdbDiagnostic {
    param([string]$Adb,[string]$Device)
    $setting=Get-ToolkitSetting $Adb $Device global adb_enabled
    $persistent=Get-ToolkitProp $Adb $Device persist.adb.tcp.port
    $service=Get-ToolkitProp $Adb $Device service.adb.tcp.port
    $state=Get-ToolkitProp $Adb $Device init.svc.adbd
    [ordered]@{
        AdbEnabled=$setting
        PersistTcpPort=$persistent
        ServiceTcpPort=$service
        AdbdState=$state
        StartThirdApp=(Get-ToolkitSetting $Adb $Device system start_3rd_app)
        NetworkReady=($setting -eq '1' -and $persistent -eq '5555' -and
            $service -eq '5555' -and $state -eq 'running')
    }
}
function Set-ToolkitSetting { param([string]$Adb,[string]$Device,[string]$Namespace,[string]$Name,[AllowNull()][string]$Value); if([string]::IsNullOrEmpty($Value)){Invoke-ToolkitAdb $Adb $Device @("shell","settings","delete",$Namespace,$Name)|Out-Null}else{Invoke-ToolkitAdb $Adb $Device @("shell","settings","put",$Namespace,$Name,$Value)|Out-Null} }
function Get-ToolkitHome { param([string]$Adb,[string]$Device); (Invoke-ToolkitAdb $Adb $Device @("shell","cmd","package","resolve-activity","--brief","-a","android.intent.action.MAIN","-c","android.intent.category.HOME") -AllowFailure|Select-Object -Last 1|Out-String).Trim() }
function Get-ToolkitDeviceInfo { param([string]$Adb,[string]$Device); [ordered]@{Model=Get-ToolkitProp $Adb $Device "ro.product.model";Device=Get-ToolkitProp $Adb $Device "ro.product.device";Android=Get-ToolkitProp $Adb $Device "ro.build.version.release";Sdk=Get-ToolkitProp $Adb $Device "ro.build.version.sdk";Product=Get-ToolkitProp $Adb $Device "ro.product.name";Board=Get-ToolkitProp $Adb $Device "ro.board.platform";Build=Get-ToolkitProp $Adb $Device "ro.build.display.id";Manufacturer=Get-ToolkitProp $Adb $Device "ro.product.manufacturer"} }
function Assert-ToolkitCompatibility {
    param($DeviceInfo)
    if ($DeviceInfo.Model -eq "MiTV-MFTR0" -or $DeviceInfo.Device -eq "freeguy") {
        if ($DeviceInfo.Model -eq "MiTV-MFTR0" -and $DeviceInfo.Device -eq "freeguy") { return }
        throw "Conflicting MFTR0 identity"
    }
    if ($DeviceInfo.Device -ne "blueplanet" -and $DeviceInfo.Product -ne "blueplanet" -and $DeviceInfo.Board -ne "blueplanet") {
        throw "Unsupported device $($DeviceInfo.Model)/$($DeviceInfo.Device)"
    }
}
function Merge-ToolkitAccessibilityServices { param([AllowNull()][string]$Current,[switch]$Remove); $a=[System.Collections.Generic.List[string]]::new();if($Current){foreach($x in $Current -split ":"){if($x -and !$a.Contains($x) -and (!$Remove -or $x -ne $script:ToolkitHelperAccessibility)){$a.Add($x)}}};if(!$Remove -and !$a.Contains($script:ToolkitHelperAccessibility)){$a.Add($script:ToolkitHelperAccessibility)};return ($a -join ":") }

function ConvertTo-ToolkitComponent {
    param([string]$Component)
    if ($Component -notmatch '^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+$') {
        throw "Invalid component: $Component"
    }
    $parts = $Component.Split('/')
    if ($parts[1].StartsWith('.')) { return "$($parts[0])/$($parts[0])$($parts[1])" }
    return $Component
}

function Get-ToolkitLaunchCandidates {
    param([string]$Adb, [string]$Device)
    $entries = @{}
    foreach ($category in @("HOME", "LEANBACK_LAUNCHER")) {
        $fullCategory = "android.intent.category.$category"
        $output = Invoke-ToolkitAdb $Adb $Device @("shell", "cmd", "package", "query-activities",
            "--brief", "-a", "android.intent.action.MAIN", "-c", $fullCategory)
        foreach ($line in $output) {
            if ($line.Trim() -notmatch '^[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+$') { continue }
            $component = ConvertTo-ToolkitComponent $line.Trim()
            $pkg, $activity = $component.Split('/')
            if ($pkg -in @("android", "com.mitv.tvhome", "com.xiaomi.mitv.settings", $script:ToolkitHelperPackage)) { continue }
            if (-not $entries.ContainsKey($component)) {
                $entries[$component] = [pscustomobject]@{
                    Package=$pkg; Activity=$activity; Component=$component
                    Category=$fullCategory; IsHome=($category -eq "HOME"); Categories=@($fullCategory)
                }
            } elseif ($entries[$component].Categories -notcontains $fullCategory) {
                $entries[$component].Categories += $fullCategory
            }
        }
    }
    return @($entries.Values | Sort-Object @{Expression={-not $_.IsHome}}, Component)
}

function Invoke-ToolkitControl {
    param([string]$Adb, [string]$Device,
        [ValidateSet("HOME_STATUS", "HOME_SET", "HOME_RESTORE")][string]$Action,
        [string]$TargetComponent, [AllowNull()][string]$FallbackComponent)
    $arguments = @("shell", "am", "broadcast", "--include-stopped-packages", "--user", "current",
        "-a", "com.redmi.tvtoolkit.action.$Action", "-n", $script:ToolkitControlReceiver)
    if ($TargetComponent) { $arguments += @("--es", "target", (ConvertTo-ToolkitComponent $TargetComponent)) }
    if ($PSBoundParameters.ContainsKey("FallbackComponent")) {
        if ($FallbackComponent) { $arguments += @("--es", "fallback", (ConvertTo-ToolkitComponent $FallbackComponent)) }
        else { $arguments += @("--es", "fallback", "") }
    }
    $output = (Invoke-ToolkitAdb $Adb $Device $arguments | Out-String)
    if ($output -notmatch 'Broadcast completed: result=(\d+), data="TOOLKIT1:([A-Za-z0-9+/=]+)"') {
        throw "No Toolkit protocol response (missing helper, denied broadcast or incompatible APK): $output"
    }
    $code = [int]$Matches[1]
    $data = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Matches[2])) | ConvertFrom-Json
    if ($data.protocol -ne 1 -or $code -ne 1 -or $data.ok -ne $true) {
        throw "Toolkit rejected $($Action): $($data.message)"
    }
    return $data
}

function Test-ToolkitForeground {
    param([string]$Adb, [string]$Device, [string]$Component)
    $pkg = (ConvertTo-ToolkitComponent $Component).Split('/')[0]
    $dump = Invoke-ToolkitAdb $Adb $Device @("shell", "dumpsys", "activity", "activities") | Out-String
    return $dump -match ('(?m)^\s*(?:topResumedActivity|mResumedActivity)\s*[:=][^\r\n]*\s' + [regex]::Escape($pkg) + '/')
}

function Set-ToolkitTarget {
    param([string]$Adb, [string]$Device, [string]$TargetComponent,
        [AllowNull()][string]$FallbackComponent, [int]$PollAttempts=48)
    $target = ConvertTo-ToolkitComponent $TargetComponent
    $candidates = @(Get-ToolkitLaunchCandidates $Adb $Device)
    $entry = @($candidates | Where-Object { $_.Component -ceq $target })
    if ($entry.Count -ne 1) { throw "Target is not an installed HOME/LEANBACK entry: $target" }
    $options = @{Adb=$Adb; Device=$Device; Action="HOME_SET"; TargetComponent=$target}
    if ($PSBoundParameters.ContainsKey("FallbackComponent")) {
        if ($FallbackComponent) {
            $fallback = ConvertTo-ToolkitComponent $FallbackComponent
            if (@($candidates | Where-Object { $_.Component -ceq $fallback }).Count -ne 1) { throw "Invalid fallback component" }
        } else { $fallback = "" }
        $options.FallbackComponent = $fallback
    }
    $previous = Invoke-ToolkitControl $Adb $Device HOME_STATUS
    $accepted = $false
    try {
        $ack = Invoke-ToolkitControl @options
        $accepted = $true
        $read = Invoke-ToolkitControl $Adb $Device HOME_STATUS
        if (-not $read.enabled -or $read.target -cne $target -or $read.revision -ne $ack.revision) {
            throw "Configuration read-back mismatch"
        }
        for ($i=0; $i -lt $PollAttempts; $i++) {
            $read = Invoke-ToolkitControl $Adb $Device HOME_STATUS
            if (-not $read.enabled -or $read.target -cne $target) { throw "Helper disabled or fell back while verifying target" }
            if ($read.bound -and $read.phase -eq "target_foreground" -and
                (Test-ToolkitForeground $Adb $Device $target)) {
                Write-Host "Verified target foreground: $target"
                return $read
            }
            Start-Sleep -Milliseconds 250
        }
        throw "Target did not reach verified foreground within the bounded verification window"
    } catch {
        $failure = $_
        if ($accepted) {
            try {
                if ($previous.enabled -and $previous.target) {
                    Invoke-ToolkitControl $Adb $Device HOME_SET -TargetComponent $previous.target -FallbackComponent $previous.fallback | Out-Null
                    $restored = Invoke-ToolkitControl $Adb $Device HOME_STATUS
                    if (-not $restored.enabled -or $restored.target -cne $previous.target) { throw "Rollback read-back failed" }
                    $rollbackConfirmed=$false
                    for ($j=0; $j -lt $PollAttempts; $j++) {
                        $restored=Invoke-ToolkitControl $Adb $Device HOME_STATUS
                        if ($restored.bound -and $restored.phase -eq "target_foreground" -and
                            (Test-ToolkitForeground $Adb $Device $previous.target)) { $rollbackConfirmed=$true; break }
                        if (-not $restored.enabled) { break }
                        Start-Sleep -Milliseconds 250
                    }
                    if (-not $rollbackConfirmed) { throw "Previous launcher did not regain foreground" }
                } else { Invoke-ToolkitControl $Adb $Device HOME_RESTORE | Out-Null }
            } catch {
                Invoke-ToolkitControl $Adb $Device HOME_RESTORE | Out-Null
                Write-Warning "Previous target could not be restored; Home Override disabled."
            }
        }
        throw $failure
    }
}

function Install-ToolkitHelper {
    param([string]$Adb, [string]$Device, [string]$ApkPath)
    if (-not (Test-Path -LiteralPath $ApkPath)) { throw "APK missing: $ApkPath. Build app/build.ps1 first." }
    $output = Invoke-ToolkitAdb $Adb $Device @("install", "-r", $ApkPath) | Out-String
    if ($output -notmatch '(?m)^Success\s*$') { throw "APK install did not report Success: $output" }
    Invoke-ToolkitAdb $Adb $Device @("shell", "pm", "grant", $script:ToolkitHelperPackage,
        "android.permission.WRITE_SECURE_SETTINGS") | Out-Null
    $dump = Invoke-ToolkitAdb $Adb $Device @("shell", "dumpsys", "package", $script:ToolkitHelperPackage) | Out-String
    if ($dump -notmatch '(?m)^\s*android\.permission\.WRITE_SECURE_SETTINGS:\s*granted=true\s*$') {
        throw "WRITE_SECURE_SETTINGS grant could not be verified"
    }
}
