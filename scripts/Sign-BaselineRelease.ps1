$ErrorActionPreference = 'Stop'

param(
    [string]$BuildToolsPath,
    [Parameter(Mandatory = $true)]
    [string]$KeystorePath,
    [Parameter(Mandatory = $true)]
    [string]$KeyAlias,
    [string]$UnsignedApkPath,
    [string]$OutputApkPath
)

$repoRoot = Split-Path -Parent $PSScriptRoot

function Get-LatestBuildToolsPath {
    $sdkRoot = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
    ) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1

    if (-not $sdkRoot) {
        throw 'Unable to find Android SDK root. Set ANDROID_HOME or ANDROID_SDK_ROOT, or install the Android SDK.'
    }

    $buildToolsRoot = Join-Path $sdkRoot 'build-tools'
    if (-not (Test-Path $buildToolsRoot)) {
        throw "Android build-tools directory not found at $buildToolsRoot"
    }

    $latest = Get-ChildItem $buildToolsRoot -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1

    if (-not $latest) {
        throw "No Android build-tools versions found under $buildToolsRoot"
    }

    return $latest.FullName
}

if (-not $BuildToolsPath) {
    $BuildToolsPath = Get-LatestBuildToolsPath
}

if (-not $UnsignedApkPath) {
    $UnsignedApkPath = Join-Path $repoRoot 'app\build\outputs\apk\baseline\release\app-baseline-release-unsigned.apk'
}

if (-not $OutputApkPath) {
    $OutputApkPath = Join-Path $repoRoot 'app\build\outputs\apk\baseline\release\LookTube-Baseline-0.1.0.apk'
}

$zipalignPath = Join-Path $BuildToolsPath 'zipalign.exe'
$apksignerPath = Join-Path $BuildToolsPath 'apksigner.bat'

if (-not (Test-Path $KeystorePath)) {
    throw "Keystore not found: $KeystorePath"
}

if (-not (Test-Path $UnsignedApkPath)) {
    throw "Unsigned APK not found: $UnsignedApkPath"
}

if (-not (Test-Path $zipalignPath)) {
    throw "zipalign not found: $zipalignPath"
}

if (-not (Test-Path $apksignerPath)) {
    throw "apksigner not found: $apksignerPath"
}

$outputDir = Split-Path -Parent $OutputApkPath
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$alignedApkPath = Join-Path $outputDir (([IO.Path]::GetFileNameWithoutExtension($OutputApkPath)) + '-aligned.apk')
$checksumPath = "$OutputApkPath.sha256"

& $zipalignPath -p -f 4 $UnsignedApkPath $alignedApkPath
& $apksignerPath sign --ks $KeystorePath --ks-key-alias $KeyAlias --out $OutputApkPath $alignedApkPath
& $apksignerPath verify --verbose --print-certs $OutputApkPath

$hash = (Get-FileHash $OutputApkPath -Algorithm SHA256).Hash.ToLower()
"$hash *$(Split-Path $OutputApkPath -Leaf)" | Set-Content $checksumPath -Encoding ascii

Write-Host "Signed APK: $OutputApkPath"
Write-Host "Checksum: $checksumPath"
