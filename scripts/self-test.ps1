[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = Split-Path $PSScriptRoot -Parent
$failures = [System.Collections.Generic.List[string]]::new()
$selfPath = $MyInvocation.MyCommand.Path

function Add-Failure([string]$Message) {
    $failures.Add($Message)
}

$required = @(
    "README.md", "LICENSE", "SECURITY.md", "CONTRIBUTING.md",
    "app/AndroidManifest.xml", "app/build.ps1",
    "scripts/setup.ps1", "scripts/verify.ps1", "scripts/diagnose.ps1",
    "scripts/rollback.ps1", "docs/root-status.md",
    "tests/mock-adb.cmd", "tests/mock-adb.ps1", "tests/smoke.ps1",
    ".github/workflows/ci.yml", ".github/PULL_REQUEST_TEMPLATE.md"
)
foreach ($relative in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $relative))) {
        Add-Failure "Missing required file: $relative"
    }
}

$manifestPath = Join-Path $repoRoot "app\AndroidManifest.xml"
[xml]$manifest = Get-Content -Raw -LiteralPath $manifestPath
$manifestText = Get-Content -Raw -LiteralPath $manifestPath
if ($manifestText -notmatch 'android:targetSdkVersion="29"') {
    Add-Failure "Manifest must intentionally target SDK 29."
}
if ($manifestText -notmatch 'android:name="\.ControlActivity"[\s\S]*android:permission="android.permission.DUMP"') {
    Add-Failure "ControlActivity must remain protected by DUMP."
}
if ($manifestText -notmatch 'android:name="\.BootReceiver"[\s\S]*android:exported="false"') {
    Add-Failure "BootReceiver must not be exported."
}

$allTextFiles = Get-ChildItem -LiteralPath $repoRoot -Recurse -File |
    Where-Object {
        $_.Extension -in @(".md", ".ps1", ".java", ".xml", ".yml", ".yaml") -and
        $_.FullName -ne $selfPath
    }
$forbiddenPatterns = [ordered]@{
    "private LAN address" = '192\.168\.[0-9]{1,3}\.[0-9]{1,3}'
    "local username" = '(?i)xionghw'
    "raw MAC address" = '(?i)\b[0-9a-f]{2}(:[0-9a-f]{2}){5}\b'
    "research probe class" = '(SystemBridgeActivity|InstallTestActivity|BspProbeActivity|ProviderProbeActivity)'
}
foreach ($file in $allTextFiles) {
    $content = Get-Content -Raw -LiteralPath $file.FullName
    foreach ($entry in $forbiddenPatterns.GetEnumerator()) {
        if ($content -match $entry.Value) {
            Add-Failure "$($entry.Key) found in $($file.FullName.Substring($repoRoot.Length + 1))"
        }
    }
}

$javaRoot = Join-Path $repoRoot "app\src"
$javaText = (Get-ChildItem -LiteralPath $javaRoot -Recurse -Filter *.java |
    ForEach-Object { Get-Content -Raw -LiteralPath $_.FullName }) -join "`n"
foreach ($forbidden in @("Runtime.getRuntime().exec", "packageURI", "CreateConfigInterface")) {
    if ($javaText.Contains($forbidden)) {
        Add-Failure "Forbidden release capability: $forbidden"
    }
}

$parseTargets = Get-ChildItem -LiteralPath $PSScriptRoot -Filter *.ps1 -File
$parseTargets += Get-Item -LiteralPath (Join-Path $repoRoot "app\build.ps1")
foreach ($target in $parseTargets) {
    $tokens = $null
    $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile(
        $target.FullName, [ref]$tokens, [ref]$errors)
    if ($errors.Count -gt 0) {
        Add-Failure "PowerShell parse error in $($target.Name): $($errors.Message -join '; ')"
    }
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Host "ERROR: $_" -ForegroundColor Red }
    exit 1
}
Write-Host "Repository self-test passed ($($allTextFiles.Count) text files scanned)."
