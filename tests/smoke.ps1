$ErrorActionPreference="Stop"; & (Join-Path $PSScriptRoot "..\scripts\self-test.ps1");Write-Host "Smoke test uses no TV and performs no ADB mutations."
