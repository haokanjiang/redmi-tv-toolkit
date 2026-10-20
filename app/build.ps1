[CmdletBinding()]
param(
    [string]$SdkRoot = $env:ANDROID_SDK_ROOT,
    [string]$JavaHomePath = $env:JAVA_HOME,
    [string]$BuildRoot,
    [string]$KeystorePath,
    [string]$KeystorePassword = "redmi-tv-toolkit",
    [string]$KeyAlias = "redmi-tv-toolkit"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not $SdkRoot) {
    $SdkRoot = $env:ANDROID_HOME
}
if (-not $SdkRoot) {
    $SdkRoot = Join-Path ([Environment]::GetFolderPath("LocalApplicationData")) "Android\Sdk"
}
if (-not (Test-Path -LiteralPath $SdkRoot)) {
    throw "Set ANDROID_SDK_ROOT (or ANDROID_HOME) to an installed Android SDK."
}
if ($JavaHomePath) {
    # Android SDK .bat launchers resolve Java through this process environment.
    $env:JAVA_HOME = $JavaHomePath
}

$AppRoot = $PSScriptRoot
if (-not $BuildRoot) {
    # Older Android build tools mishandle non-ASCII paths. Keep intermediates
    # in the OS temp directory and copy only the final APK back to the repo.
    $BuildRoot = Join-Path ([IO.Path]::GetTempPath()) "redmi-tv-toolkit-build"
}
$GeneratedRoot = Join-Path $BuildRoot "gen"
$ClassesRoot = Join-Path $BuildRoot "classes"
$DexRoot = Join-Path $BuildRoot "dex"
$DistRoot = Join-Path $AppRoot "dist"

function Resolve-JavaTool {
    param([Parameter(Mandatory = $true)][string]$Name)
    if ($JavaHomePath) {
        $candidate = Join-Path $JavaHomePath "bin\$Name.exe"
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }
    $command = Get-Command "$Name.exe" -ErrorAction SilentlyContinue
    if (-not $command) {
        $command = Get-Command $Name -ErrorAction SilentlyContinue
    }
    if (-not $command) {
        throw "Java tool not found: $Name. Install JDK 17 and set JAVA_HOME."
    }
    return $command.Source
}

function Get-LatestBuildTools {
    $root = Join-Path $SdkRoot "build-tools"
    if (-not (Test-Path -LiteralPath $root)) {
        throw "Android build-tools directory not found: $root"
    }
    $candidates = Get-ChildItem -LiteralPath $root -Directory |
        Sort-Object Name -Descending
    foreach ($candidate in $candidates) {
        if ((Test-Path -LiteralPath (Join-Path $candidate.FullName "aapt.exe")) -and
            (Test-Path -LiteralPath (Join-Path $candidate.FullName "d8.bat")) -and
            (Test-Path -LiteralPath (Join-Path $candidate.FullName "zipalign.exe")) -and
            (Test-Path -LiteralPath (Join-Path $candidate.FullName "lib\apksigner.jar"))) {
            return $candidate.FullName
        }
    }
    throw "No complete Android build-tools version found (aapt, d8, zipalign, apksigner)."
}

$Javac = Resolve-JavaTool -Name "javac"
$Java = Resolve-JavaTool -Name "java"
$Jar = Resolve-JavaTool -Name "jar"
$Keytool = Resolve-JavaTool -Name "keytool"
$BuildTools = Get-LatestBuildTools
$Aapt = Join-Path $BuildTools "aapt.exe"
$D8 = Join-Path $BuildTools "d8.bat"
$Zipalign = Join-Path $BuildTools "zipalign.exe"
$ApksignerJar = Join-Path $BuildTools "lib\apksigner.jar"
$AndroidJar = Join-Path $SdkRoot "platforms\android-34\android.jar"

if (-not (Test-Path -LiteralPath $AndroidJar)) {
    throw "Android 34 platform is required: sdkmanager 'platforms;android-34'"
}

if (-not $KeystorePath) {
    $UserProfilePath = [Environment]::GetFolderPath("UserProfile")
    $KeystorePath = Join-Path $UserProfilePath ".redmi-tv-toolkit\signing.keystore"
}

$resolvedTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
$resolvedBuildRoot = [IO.Path]::GetFullPath($BuildRoot).TrimEnd('\') + '\'
if (-not $resolvedBuildRoot.StartsWith($resolvedTempRoot, [StringComparison]::OrdinalIgnoreCase) -or
    (Split-Path $resolvedBuildRoot.TrimEnd('\') -Leaf) -ne "redmi-tv-toolkit-build") {
    throw "Refusing to clean unexpected build path: $resolvedBuildRoot"
}
if (Test-Path -LiteralPath $BuildRoot) {
    Remove-Item -LiteralPath $BuildRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $GeneratedRoot, $ClassesRoot, $DexRoot, $DistRoot | Out-Null

& $Aapt package -f -m `
    -M (Join-Path $AppRoot "AndroidManifest.xml") `
    -S (Join-Path $AppRoot "res") `
    -I $AndroidJar `
    -J $GeneratedRoot
if ($LASTEXITCODE -ne 0) { throw "aapt R generation failed" }

$Sources = @(
    Get-ChildItem -LiteralPath (Join-Path $AppRoot "src") -Recurse -Filter *.java |
        ForEach-Object FullName
) + @(
    Get-ChildItem -LiteralPath $GeneratedRoot -Recurse -Filter *.java |
        ForEach-Object FullName
)

& $Javac --release 8 -g:none -encoding UTF-8 -classpath $AndroidJar -d $ClassesRoot $Sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$ClassesJar = Join-Path $BuildRoot "classes.jar"
Push-Location $ClassesRoot
try {
    & $Jar --create --file $ClassesJar .
    if ($LASTEXITCODE -ne 0) { throw "jar packaging failed" }
}
finally {
    Pop-Location
}

& $D8 --min-api 23 --output $DexRoot $ClassesJar
$DexFile = Join-Path $DexRoot "classes.dex"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $DexFile)) {
    # Build-tools 29's D8 launcher can exit successfully without output on
    # newer JDKs. Its bundled dx.jar remains a reliable local fallback.
    $DxJar = Join-Path $BuildTools "lib\dx.jar"
    if (-not (Test-Path -LiteralPath $DxJar)) {
        throw "d8 failed to create classes.dex and no dx.jar fallback is available"
    }
    & $Java -Xmx1024M -jar $DxJar --dex --min-sdk-version=23 `
        "--output=$DexFile" $ClassesJar
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $DexFile)) {
        throw "dex conversion failed"
    }
}

$UnsignedApk = Join-Path $BuildRoot "redmi-tv-toolkit-unsigned.apk"
$AlignedApk = Join-Path $BuildRoot "redmi-tv-toolkit-aligned.apk"
$SignedTempApk = Join-Path $BuildRoot "redmi-tv-toolkit.apk"
$SignedApk = Join-Path $DistRoot "redmi-tv-toolkit.apk"

& $Aapt package -f `
    -M (Join-Path $AppRoot "AndroidManifest.xml") `
    -S (Join-Path $AppRoot "res") `
    -I $AndroidJar `
    -F $UnsignedApk
if ($LASTEXITCODE -ne 0) { throw "aapt package failed" }

Push-Location $DexRoot
try {
    & $Aapt add $UnsignedApk "classes.dex"
    if ($LASTEXITCODE -ne 0) { throw "aapt add classes.dex failed" }
}
finally {
    Pop-Location
}

& $Zipalign -f 4 $UnsignedApk $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

if (-not (Test-Path -LiteralPath $KeystorePath)) {
    $KeystoreDirectory = Split-Path -Parent $KeystorePath
    New-Item -ItemType Directory -Force -Path $KeystoreDirectory | Out-Null
    & $Keytool -genkeypair -noprompt `
        -keystore $KeystorePath `
        -storepass $KeystorePassword `
        -keypass $KeystorePassword `
        -alias $KeyAlias `
        -keyalg RSA `
        -keysize 3072 `
        -validity 10000 `
        -dname "CN=Redmi TV Toolkit Community Build, OU=Open Source, O=Community, C=XX"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
    Write-Warning "Created a signing key outside the repository: $KeystorePath. Back it up securely for upgrades."
}

& $Java -jar $ApksignerJar sign `
    --ks $KeystorePath `
    --ks-key-alias $KeyAlias `
    --ks-pass "pass:$KeystorePassword" `
    --key-pass "pass:$KeystorePassword" `
    --out $SignedTempApk `
    $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

& $Java -jar $ApksignerJar verify --verbose $SignedTempApk
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed" }

Copy-Item -LiteralPath $SignedTempApk -Destination $SignedApk -Force

$Hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $SignedApk).Hash.ToLowerInvariant()
$HashFile = "$SignedApk.sha256"
Set-Content -LiteralPath $HashFile -Value "$Hash  redmi-tv-toolkit.apk" -Encoding ascii

Write-Host "Built: $SignedApk"
Write-Host "SHA-256: $Hash"




