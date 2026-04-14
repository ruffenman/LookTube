param(
    [ValidateSet('baseline', 'highspec')]
    [string]$Flavor = 'baseline',
    [string]$RepoRoot,
    [string]$BuildToolsPath,
    [Parameter(Mandatory = $true)]
    [string]$KeystorePath,
    [Parameter(Mandatory = $true)]
    [string]$KeyAlias,
    [string]$VersionName,
    [string]$UnsignedApkPath,
    [string]$OutputApkPath,
    [string]$KeystorePasswordEnvVar = 'LOOKTUBE_RELEASE_KEYSTORE_PASSWORD',
    [string]$KeyPasswordEnvVar = 'LOOKTUBE_RELEASE_KEY_PASSWORD'
)

$ErrorActionPreference = 'Stop'

if (-not $RepoRoot) {
    $RepoRoot = Split-Path -Parent $PSScriptRoot
}

$repoRoot = $RepoRoot
function Get-ConfiguredVersionName {
    $buildGradlePath = [IO.Path]::Combine($repoRoot, 'app', 'build.gradle.kts')
    if (-not (Test-Path $buildGradlePath)) {
        throw "Unable to find app build file at $buildGradlePath"
    }

    $versionMatch = Select-String -Path $buildGradlePath -Pattern 'versionName\s*=\s*"([^"]+)"'
    $resolvedVersionName = $versionMatch.Matches | Select-Object -First 1 | ForEach-Object {
        $_.Groups[1].Value
    }

    if ([string]::IsNullOrWhiteSpace($resolvedVersionName)) {
        throw "Unable to resolve versionName from $buildGradlePath"
    }

    return $resolvedVersionName
}

function Get-UnsignedApkPaths {
    if (-not [string]::IsNullOrWhiteSpace($UnsignedApkPath)) {
        $resolvedUnsignedApkPath = [IO.Path]::GetFullPath($UnsignedApkPath)
        if (-not (Test-Path $resolvedUnsignedApkPath)) {
            throw "Unsigned APK not found: $resolvedUnsignedApkPath"
        }
        return @($resolvedUnsignedApkPath)
    }

    $releaseOutputDirectory = [IO.Path]::Combine(
        $repoRoot,
        'app',
        'build',
        'outputs',
        'apk',
        $Flavor,
        'release'
    )
    if (-not (Test-Path $releaseOutputDirectory)) {
        throw "Release APK output directory not found: $releaseOutputDirectory"
    }

    $splitUnsignedApks = Get-ChildItem $releaseOutputDirectory -Filter "app-$Flavor-*-release-unsigned.apk" -File |
        Sort-Object Name
    if ($splitUnsignedApks.Count -gt 0) {
        return @($splitUnsignedApks.FullName)
    }

    $singleUnsignedApk = [IO.Path]::Combine(
        $releaseOutputDirectory,
        "app-$Flavor-release-unsigned.apk"
    )
    if (Test-Path $singleUnsignedApk) {
        return @($singleUnsignedApk)
    }

    throw "No unsigned release APKs were found for flavor '$Flavor' in $releaseOutputDirectory"
}

function Get-UnsignedApkAbi {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $fileNameWithoutExtension = [IO.Path]::GetFileNameWithoutExtension($Path)
    $splitPrefix = "app-$Flavor-"
    $splitSuffix = '-release-unsigned'
    if (
        $fileNameWithoutExtension.StartsWith($splitPrefix, [StringComparison]::OrdinalIgnoreCase) -and
        $fileNameWithoutExtension.EndsWith($splitSuffix, [StringComparison]::OrdinalIgnoreCase)
    ) {
        $candidate = $fileNameWithoutExtension.Substring(
            $splitPrefix.Length,
            $fileNameWithoutExtension.Length - $splitPrefix.Length - $splitSuffix.Length
        )
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
            return $candidate
        }
    }

    $zipArchive = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $abis = $zipArchive.Entries | ForEach-Object {
            if ($_.FullName -match '^lib/([^/]+)/') {
                $Matches[1]
            }
        } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique
        if ($abis.Count -eq 1) {
            return $abis[0]
        }
    } finally {
        $zipArchive.Dispose()
    }

    return $null
}

function Get-LatestBuildToolsPath {
    $localAppDataSdkRoot = $null
    if ($env:LOCALAPPDATA) {
        $localAppDataSdkRoot = [IO.Path]::Combine($env:LOCALAPPDATA, 'Android', 'Sdk')
    }
    $sdkRoot = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        $localAppDataSdkRoot
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
if (-not $VersionName) {
    $VersionName = Get-ConfiguredVersionName
}
if ([string]::IsNullOrWhiteSpace($Flavor)) {
    throw 'Flavor must be provided.'
}

$flavorDisplayName = if ($Flavor.Length -eq 1) {
    $Flavor.ToUpperInvariant()
} else {
    $Flavor.Substring(0, 1).ToUpperInvariant() + $Flavor.Substring(1)
}
$flavorDisplayName = (Get-Culture).TextInfo.ToTitleCase($Flavor)
$unsignedApkPaths = Get-UnsignedApkPaths
if (-not [string]::IsNullOrWhiteSpace($OutputApkPath) -and $unsignedApkPaths.Count -gt 1) {
    throw 'OutputApkPath can only be used when signing a single unsigned APK.'
}

$zipalignExecutable = if ($IsWindows) { 'zipalign.exe' } else { 'zipalign' }
$apksignerExecutable = if ($IsWindows) { 'apksigner.bat' } else { 'apksigner' }
$zipalignPath = Join-Path $BuildToolsPath $zipalignExecutable
$apksignerPath = Join-Path $BuildToolsPath $apksignerExecutable

if (-not (Test-Path $KeystorePath)) {
    throw "Keystore not found: $KeystorePath"
}

if (-not (Test-Path $zipalignPath)) {
    throw "zipalign not found: $zipalignPath"
}

if (-not (Test-Path $apksignerPath)) {
    throw "apksigner not found: $apksignerPath"
}

$apksignerArguments = @(
    'sign',
    '--ks', $KeystorePath,
    '--ks-key-alias', $KeyAlias
)

$resolvedKeystorePasswordEnvVar = $null
if (-not [string]::IsNullOrWhiteSpace($KeystorePasswordEnvVar)) {
    $resolvedKeystorePasswordEnvVar = $KeystorePasswordEnvVar.Trim()
}

$resolvedKeyPasswordEnvVar = $null
if (-not [string]::IsNullOrWhiteSpace($KeyPasswordEnvVar)) {
    $resolvedKeyPasswordEnvVar = $KeyPasswordEnvVar.Trim()
}
$keystorePasswordConfigured = -not [string]::IsNullOrWhiteSpace($resolvedKeystorePasswordEnvVar) -and
    -not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($resolvedKeystorePasswordEnvVar))
$keyPasswordConfigured = -not [string]::IsNullOrWhiteSpace($resolvedKeyPasswordEnvVar) -and
    -not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($resolvedKeyPasswordEnvVar))

if ($keystorePasswordConfigured) {
    $apksignerArguments += @('--ks-pass', "env:$resolvedKeystorePasswordEnvVar")
}

if ($keyPasswordConfigured) {
    $apksignerArguments += @('--key-pass', "env:$resolvedKeyPasswordEnvVar")
} elseif ($keystorePasswordConfigured) {
    $apksignerArguments += @('--key-pass', "env:$resolvedKeystorePasswordEnvVar")
}
foreach ($resolvedUnsignedApkPath in $unsignedApkPaths) {
    $resolvedOutputApkPath = $OutputApkPath
    if ([string]::IsNullOrWhiteSpace($resolvedOutputApkPath)) {
        $abi = Get-UnsignedApkAbi -Path $resolvedUnsignedApkPath
        $artifactName = if ([string]::IsNullOrWhiteSpace($abi)) {
            "LookTube-$flavorDisplayName-$VersionName.apk"
        } else {
            "LookTube-$flavorDisplayName-$abi-$VersionName.apk"
        }
        $resolvedOutputApkPath = [IO.Path]::Combine(
            [IO.Path]::GetDirectoryName($resolvedUnsignedApkPath),
            $artifactName
        )
    }

    $outputDir = Split-Path -Parent $resolvedOutputApkPath
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

    $alignedApkPath = Join-Path $outputDir (([IO.Path]::GetFileNameWithoutExtension($resolvedOutputApkPath)) + '-aligned.apk')
    $checksumPath = "$resolvedOutputApkPath.sha256"

    & $zipalignPath -p -f 4 $resolvedUnsignedApkPath $alignedApkPath

    $perApkApksignerArguments = @($apksignerArguments)
    $perApkApksignerArguments += @('--out', $resolvedOutputApkPath, $alignedApkPath)

    & $apksignerPath @perApkApksignerArguments
    & $apksignerPath verify --verbose --print-certs $resolvedOutputApkPath

    $hash = (Get-FileHash $resolvedOutputApkPath -Algorithm SHA256).Hash.ToLower()
    "$hash *$(Split-Path $resolvedOutputApkPath -Leaf)" | Set-Content $checksumPath -Encoding ascii

    Write-Host "Signed APK: $resolvedOutputApkPath"
    Write-Host "Checksum: $checksumPath"
}
