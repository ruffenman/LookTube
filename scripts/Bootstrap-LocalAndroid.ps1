$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$localPropertiesPath = Join-Path $repoRoot 'local.properties'

if (Test-Path $localPropertiesPath) {
    Write-Host "local.properties already exists; leaving it unchanged."
    exit 0
}

$candidateSdkDirs = @(
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT,
    (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
) | Where-Object { $_ -and (Test-Path $_) }

if (-not $candidateSdkDirs) {
    throw "Unable to find an Android SDK directory. Set ANDROID_HOME or ANDROID_SDK_ROOT, or install the SDK."
}

$sdkDir = @($candidateSdkDirs)[0].ToString().Replace('\', '\\').Replace(':', '\:')
"sdk.dir=$sdkDir" | Set-Content -Path $localPropertiesPath -Encoding UTF8
Write-Host "Created local.properties with sdk.dir pointing at the detected Android SDK."
