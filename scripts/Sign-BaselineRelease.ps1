param(
    [string]$BuildToolsPath,
    [Parameter(Mandatory = $true)]
    [string]$KeystorePath,
    [Parameter(Mandatory = $true)]
    [string]$KeyAlias,
    [string]$UnsignedApkPath,
    [string]$OutputApkPath
)

& (Join-Path $PSScriptRoot 'Sign-ReleaseApk.ps1') `
    -Flavor baseline `
    -BuildToolsPath $BuildToolsPath `
    -KeystorePath $KeystorePath `
    -KeyAlias $KeyAlias `
    -UnsignedApkPath $UnsignedApkPath `
    -OutputApkPath $OutputApkPath
