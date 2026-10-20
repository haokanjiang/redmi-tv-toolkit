$ErrorActionPreference="Stop"
Set-StrictMode -Version Latest
$repo=Split-Path $PSScriptRoot -Parent
. (Join-Path $repo "scripts\common.ps1")
$script:checks=0
function Assert($condition, [string]$message) {
    $script:checks++
    if (-not $condition) { throw "FAIL: $message" }
}
function Expect-Failure([scriptblock]$Action) {
    $script:checks++
    try { & $Action | Out-Null } catch { return }
    throw "Expected operation to fail"
}
$stateRoot=Join-Path $repo "out\script-tests"
New-Item -ItemType Directory -Force $stateRoot | Out-Null
$env:TOOLKIT_TEST_STATE=Join-Path $stateRoot "adb-state.json"
$adb=Join-Path $PSScriptRoot "mock-adb.cmd"
$apk=Join-Path $stateRoot "fixture.apk"
Set-Content -LiteralPath $apk -Value "offline fixture"
function Reset-Fixture([string]$Fault="") {
    @{fault=$Fault;calls=@();enabled=$false;recovery=$false;revision=0} | ConvertTo-Json -Depth 5 |
        Set-Content -LiteralPath $env:TOOLKIT_TEST_STATE -Encoding utf8
}
function Read-Fixture { Get-Content -LiteralPath $env:TOOLKIT_TEST_STATE -Raw | ConvertFrom-Json }
Reset-Fixture
$diagnostic=Get-ToolkitAdbDiagnostic $adb "fake:5555"
Assert ($diagnostic.NetworkReady) "read-only ADB diagnostic checks setting, ports, and process"
$broken=Read-Fixture
$broken | Add-Member servicePort ""
$broken | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $env:TOOLKIT_TEST_STATE -Encoding utf8
Assert (-not (Get-ToolkitAdbDiagnostic $adb "fake:5555").NetworkReady) "adb_enabled alone is insufficient"
Assert (@((Read-Fixture).calls | Where-Object {$_ -contains "settings" -and $_ -contains "put"}).Count -eq 0) "diagnostic never changes settings"
Reset-Fixture
$report=(& (Join-Path $repo "scripts\diagnose.ps1") -Device "fake:5555" -AdbPath $adb | Out-String | ConvertFrom-Json)
Assert ($report.Adb.NetworkReady -and $report.Adb.AdbEnabled -eq '1') "diagnose reports independent ADB conditions"
Assert ($report.SystemHome -eq 'com.mitv.tvhome/.MainActivityUserMode') "diagnose preserves actual system Home"
Assert (@((Read-Fixture).calls | Where-Object {$_ -contains "settings" -and $_ -contains "put"}).Count -eq 0) "diagnose is read-only"
Reset-Fixture
$items=@(Get-ToolkitLaunchCandidates $adb "fake:5555")
Assert ($items.Count -eq 4) "HOME and LEANBACK entries, duplicate category merged"
Assert ($items[0].IsHome) "HOME sorted first"
Assert (@($items | Where-Object {$_.Component -eq "a.desktop/a.desktop.Home"})[0].Categories.Count -eq 2) "entry capability union"
Assert (@($items | Where-Object {$_.Component -eq "a.desktop/a.desktop.TvEntry"}).Count -eq 1) "alternate component retained"
Assert (@($items | Where-Object {$_.Package -eq "com.mitv.tvhome"}).Count -eq 0) "system Home excluded from overrides"
Expect-Failure { ConvertTo-ToolkitComponent 'a.b/.Home;reboot' }
Assert ((ConvertTo-ToolkitComponent 'a.b/.Outer$Nested') -ceq 'a.b/a.b.Outer$Nested') "nested Activity preserved"

Reset-Fixture
Install-ToolkitHelper $adb "fake:5555" $apk
Assert ((Read-Fixture).granted) "grant read-back"
foreach($fault in @("grantfail","grantmissing")) {
    Reset-Fixture $fault
    Expect-Failure { Install-ToolkitHelper $adb "fake:5555" $apk }
}
Reset-Fixture "denied"
Expect-Failure { Invoke-ToolkitControl $adb "fake:5555" HOME_STATUS }
Reset-Fixture "reject"
Expect-Failure { Invoke-ToolkitControl $adb "fake:5555" HOME_SET -TargetComponent "a.desktop/.Home" }

Reset-Fixture
$result=Set-ToolkitTarget $adb "fake:5555" "a.desktop/.TvEntry" -PollAttempts 1
Assert ($result.target -ceq "a.desktop/a.desktop.TvEntry") "explicit LEANBACK component selected"
$fixture=Read-Fixture
Assert (@($fixture.calls | Where-Object {$_ -contains "monkey"}).Count -eq 0) "no random monkey events"
Assert (@($fixture.calls | Where-Object {$_ -contains "dumpsys" -and $_ -contains "activities"}).Count -gt 0) "actual foreground checked"
foreach($fault in @("noforeground","mismatch")) {
    Reset-Fixture $fault
    Expect-Failure { Set-ToolkitTarget $adb "fake:5555" "a.desktop/.Home" -PollAttempts 1 }
    Assert (-not (Read-Fixture).enabled) "$fault rolls back to disabled"
}
Reset-Fixture "noforeground"
$prior=Read-Fixture
$prior | Add-Member target "b.desktop/b.desktop.Home"
$prior | Add-Member fallback ""
$prior.enabled=$true
$prior.recovery=$true
$prior | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $env:TOOLKIT_TEST_STATE -Encoding utf8
Expect-Failure { Set-ToolkitTarget $adb "fake:5555" "a.desktop/.Home" -PollAttempts 1 }
Assert (-not (Read-Fixture).enabled -and -not (Read-Fixture).recovery) "failed prior-launcher rollback disables recovery"
Reset-Fixture
Expect-Failure { Set-ToolkitTarget $adb "fake:5555" "missing.app/.Home" -PollAttempts 1 }
Assert (@((Read-Fixture).calls | Where-Object {$_ -contains "com.redmi.tvtoolkit.action.HOME_SET"}).Count -eq 0) "invalid component never sent"

Reset-Fixture
& (Join-Path $repo "scripts\setup.ps1") -Device "fake:5555" -AdbPath $adb -ApkPath $apk -TargetComponent "b.desktop/.Home" -FallbackComponent "a.desktop/.Home"
$fixture=Read-Fixture
Assert ($fixture.target -ceq "b.desktop/b.desktop.Home") "setup forwards exact TargetComponent"
Assert ($fixture.fallback -ceq "a.desktop/a.desktop.Home") "setup forwards fallback"
Reset-Fixture "grantmissing"
Expect-Failure { & (Join-Path $repo "scripts\setup.ps1") -Device "fake:5555" -AdbPath $adb -ApkPath $apk -TargetComponent "b.desktop/.Home" }
Assert (@((Read-Fixture).calls | Where-Object {$_ -contains "com.redmi.tvtoolkit.action.HOME_SET"}).Count -eq 0) "setup grant failure prevents configuration"

Reset-Fixture
& (Join-Path $repo "scripts\manage-home.ps1") -Device "fake:5555" -AdbPath $adb -TargetComponent "b.desktop/.Home"
Assert ((Read-Fixture).target -ceq "b.desktop/b.desktop.Home") "manager explicit component"
& (Join-Path $repo "scripts\manage-home.ps1") -Device "fake:5555" -AdbPath $adb -Restore
Assert (-not (Read-Fixture).enabled -and -not (Read-Fixture).recovery) "restore disables boot policy"
Reset-Fixture
& (Join-Path $repo "scripts\manage-home.ps1") -Device "fake:5555" -AdbPath $adb -Select 1
Assert ((Read-Fixture).target -ceq "a.desktop/a.desktop.Home") "number selection starts at 1"
Reset-Fixture
function global:Read-Host { return "2" }
try {
    & (Join-Path $repo "scripts\manage-home.ps1") -Device "fake:5555" -AdbPath $adb
    Assert ((Read-Fixture).target -ceq "b.desktop/b.desktop.Home") "interactive selection"
} finally { Remove-Item Function:\Read-Host }
Write-Host "PASS: $script:checks PowerShell behavioral checks (real scripts, offline fake ADB)"
Remove-Item Env:\TOOLKIT_TEST_STATE
