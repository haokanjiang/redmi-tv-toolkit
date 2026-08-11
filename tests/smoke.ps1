[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = Split-Path $PSScriptRoot -Parent
$mockAdb = Join-Path $PSScriptRoot "mock-adb.cmd"
$diagnosis = Join-Path $repoRoot ".cache\mock-diagnosis.json"

& (Join-Path $repoRoot "scripts\diagnose.ps1") `
    -Device "mock:5555" -AdbPath $mockAdb -OutputFile $diagnosis
if ($LASTEXITCODE -ne 0) { throw "diagnose smoke test failed" }

$result = Get-Content -Raw -LiteralPath $diagnosis | ConvertFrom-Json
if ($result.Device.Device -ne "blueplanet" -or
    -not $result.State.ProjectivyInstalled -or
    $result.State.PeriodicEnabled -ne $false) {
    throw "diagnose smoke result is incorrect"
}

& (Join-Path $repoRoot "scripts\verify.ps1") -Device "mock:5555" -AdbPath $mockAdb
if ($LASTEXITCODE -ne 0) { throw "verify smoke test failed" }

& (Join-Path $repoRoot "scripts\setup.ps1") `
    -Device "mock:5555" -AdbPath $mockAdb -RepairOnly
if ($LASTEXITCODE -ne 0) { throw "RepairOnly smoke test failed" }

Write-Host "Offline ADB smoke tests passed."

