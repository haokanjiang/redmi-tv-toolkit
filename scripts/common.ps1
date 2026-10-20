Set-StrictMode -Version Latest
$script:ToolkitHelperPackage="com.redmi.tvtoolkit"
$script:ToolkitHelperAccessibility="com.redmi.tvtoolkit/com.redmi.tvtoolkit.HomeOverrideService"
$script:ToolkitHelperMain="com.redmi.tvtoolkit/.MainActivity"
$script:ToolkitControlReceiver="com.redmi.tvtoolkit/.ToolkitControlReceiver"
function Resolve-ToolkitAdb { param([string]$AdbPath) if($AdbPath){return (Resolve-Path $AdbPath).Path}; $c=Get-Command adb.exe -ErrorAction SilentlyContinue; if($c){return $c.Source}; $p=Join-Path ([Environment]::GetFolderPath("UserProfile")) "platform-tools\adb.exe"; if(Test-Path $p){return $p}; throw "adb not found" }
function Invoke-ToolkitAdb {
    param([string]$Adb,[string]$Device,[string[]]$Arguments,[switch]$AllowFailure)
    $old = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $o = & $Adb -s $Device @Arguments 2>&1
        $code = $LASTEXITCODE
        if (-not $AllowFailure -and $code -ne 0) { throw "adb failed: $($o -join "`n")" }
        return @($o | ForEach-Object { $_.ToString() })
    }
    finally { $ErrorActionPreference = $old }
}
function Connect-ToolkitDevice { param([string]$Adb,[string]$Device,[int]$TimeoutSeconds=30); & $Adb start-server | Out-Null; if($Device.Contains(":")){& $Adb connect $Device | Out-Null}; $end=(Get-Date).AddSeconds($TimeoutSeconds); do{$s=(& $Adb -s $Device get-state 2>$null|Out-String).Trim();if($s -eq "device"){return};Start-Sleep 1}while((Get-Date)-lt $end);throw "ADB device unavailable" }
function Get-ToolkitProp { param([string]$Adb,[string]$Device,[string]$Name); (Invoke-ToolkitAdb $Adb $Device @("shell","getprop",$Name)|Out-String).Trim() }
function Get-ToolkitSetting { param([string]$Adb,[string]$Device,[string]$Namespace,[string]$Name); $v=(Invoke-ToolkitAdb $Adb $Device @("shell","settings","get",$Namespace,$Name) -AllowFailure|Out-String).Trim(); if(!$v -or $v -eq "null"){return $null};return $v }
function Set-ToolkitSetting { param([string]$Adb,[string]$Device,[string]$Namespace,[string]$Name,[AllowNull()][string]$Value); if($null -eq $Value){Invoke-ToolkitAdb $Adb $Device @("shell","settings","delete",$Namespace,$Name)|Out-Null}else{Invoke-ToolkitAdb $Adb $Device @("shell","settings","put",$Namespace,$Name,$Value)|Out-Null} }
function Get-ToolkitHome { param([string]$Adb,[string]$Device); (Invoke-ToolkitAdb $Adb $Device @("shell","cmd","package","resolve-activity","--brief","-a","android.intent.action.MAIN","-c","android.intent.category.HOME") -AllowFailure|Select-Object -Last 1|Out-String).Trim() }
function Get-ToolkitDeviceInfo { param([string]$Adb,[string]$Device); [ordered]@{Model=Get-ToolkitProp $Adb $Device "ro.product.model";Device=Get-ToolkitProp $Adb $Device "ro.product.device";Android=Get-ToolkitProp $Adb $Device "ro.build.version.release";Sdk=Get-ToolkitProp $Adb $Device "ro.build.version.sdk";Build=Get-ToolkitProp $Adb $Device "ro.build.display.id";Manufacturer=Get-ToolkitProp $Adb $Device "ro.product.manufacturer"} }
function Assert-ToolkitCompatibility { param($DeviceInfo); if(!(($DeviceInfo.Model -eq "MiTV-MFTR0" -and $DeviceInfo.Device -eq "freeguy") -or ($DeviceInfo.Device -eq "blueplanet"))){throw "Unsupported device $($DeviceInfo.Model)/$($DeviceInfo.Device)"} }
function Merge-ToolkitAccessibilityServices { param([AllowNull()][string]$Current,[switch]$Remove); $a=[System.Collections.Generic.List[string]]::new();if($Current){foreach($x in $Current -split ":"){if($x -and !$a.Contains($x) -and (!$Remove -or $x -ne $script:ToolkitHelperAccessibility)){$a.Add($x)}}};if(!$Remove -and !$a.Contains($script:ToolkitHelperAccessibility)){$a.Add($script:ToolkitHelperAccessibility)};return ($a -join ":") }
function Get-ToolkitLaunchCandidates { param([string]$Adb,[string]$Device); $seen=@{};$result=@();foreach($cat in @("android.intent.category.HOME","android.intent.category.LEANBACK_LAUNCHER","android.intent.category.LAUNCHER")){ $out=Invoke-ToolkitAdb $Adb $Device @("shell","cmd","package","query-activities","--brief","-a","android.intent.action.MAIN","-c",$cat) -AllowFailure; foreach($line in $out){$s="$line".Trim();if($s -notmatch "^[\w.$]+/.+" ){continue};$comp=$s.Split()[0];$pkg=$comp.Split('/')[0];if($pkg -in @("android","com.xiaomi.mitv.settings") -or $comp -match "FallbackHome"){continue};if(!$seen.ContainsKey($pkg)){$seen[$pkg]=$true;$result+=[pscustomobject]@{Package=$pkg;Activity=$comp.Substring($pkg.Length+1);Component=$comp;Category=$cat;IsHome=($cat -eq "android.intent.category.HOME")}}}};return $result|Sort-Object @{e={if($_.IsHome){0}else{1}}},Package }
