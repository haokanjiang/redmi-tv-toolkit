[CmdletBinding()]
param([string]$Device=$env:TV_HOST,[string]$AdbPath)
$ErrorActionPreference="Stop";. (Join-Path $PSScriptRoot "common.ps1");& (Join-Path $PSScriptRoot "manage-home.ps1") -Device $Device -AdbPath $AdbPath -Restore
