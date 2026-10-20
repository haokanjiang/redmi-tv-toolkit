[CmdletBinding()]
param([string]$JavaHomePath=$env:JAVA_HOME)
$ErrorActionPreference="Stop"
$repo=Split-Path $PSScriptRoot -Parent
$javac=if($JavaHomePath){Join-Path $JavaHomePath "bin\javac.exe"}else{(Get-Command javac -ErrorAction Stop).Source}
$java=if($JavaHomePath){Join-Path $JavaHomePath "bin\java.exe"}else{(Get-Command java -ErrorAction Stop).Source}
$classes=Join-Path $repo "out\host-tests"
New-Item -ItemType Directory -Force $classes | Out-Null
$source=Join-Path $repo "app\src\local\blueplanet\boothelper"
$files=@("HomeOverrideRetry","LaunchBudget","DeviceCapabilities","RecoveryPlan","RecoveryBudget","VerifiedStore","ConfigValues","ServiceList","AdbState","HomeEventHint") | ForEach-Object {Join-Path $source "$_.java"}
& $javac --release 8 -d $classes $files (Join-Path $PSScriptRoot "java\BehaviorTests.java")
if($LASTEXITCODE -ne 0){throw "Host test compilation failed"}
& $java -cp $classes com.redmi.tvtoolkit.BehaviorTests
if($LASTEXITCODE -ne 0){throw "Host behavior tests failed"}
