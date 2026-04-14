# GitHub releases
This document covers how to package and publish signed APKs for GitHub Releases.
## Android app signing in plain terms
Android release signing is not the same thing as SSH key generation.

For Android, you create a long-lived signing keystore and use it to sign release APKs. Devices use that signature to verify the app, and future updates must be signed with the same key if you want users to upgrade cleanly.

## Public release policy
- prefer the baseline flavor as the default public release artifact
- publish baseline release APKs per ABI so most physical-device users can install the `arm64-v8a` build without downloading the `x86_64` emulator binary payload
- highspec flavor APKs may be published as optional public release artifacts only while they remain limited to Moonshine Voice code plus the MIT-licensed English Moonshine models already validated for this repository; re-review upstream terms before shipping any broader Moonshine model set
- both baseline Whisper.cpp model downloads and highspec Moonshine model downloads must stay pinned to immutable upstream revisions and pass SHA-256 verification before they are activated on-device
- publish signed APKs, SHA-256 checksum files, concise release notes, and supported-device notes together
## Generate a release keystore
Create the keystore once and back it up carefully outside the repository. Do not commit it.

Example command:

```powershell
keytool -genkeypair -v -keystore "{{LOOKTUBE_RELEASE_KEYSTORE_PATH}}" -alias "{{LOOKTUBE_RELEASE_KEY_ALIAS}}" -keyalg RSA -keysize 4096 -validity 3650
```

When the keystore and key passwords are supplied through environment variables named `LOOKTUBE_RELEASE_KEYSTORE_PASSWORD` and `LOOKTUBE_RELEASE_KEY_PASSWORD`, the helper script signs non-interactively without placing the password values in command-line arguments. If you created the keystore with only one password, use that same password value for both environment variables.

Notes:
- `keytool` will prompt for passwords and certificate metadata interactively
- keep the keystore file and its passwords somewhere you can recover later
- losing the signing key means you cannot ship seamless updates to users of existing releases

## Baseline build artifacts
Build the baseline release artifact for each public ABI explicitly:

```powershell
.\gradlew.bat :app:assembleBaselineRelease -PlooktubeBaselineReleaseAbi=arm64-v8a
.\gradlew.bat :app:assembleBaselineRelease -PlooktubeBaselineReleaseAbi=x86_64
```

Each command produces the unsigned baseline APK at the same path:

`app/build/outputs/apk/baseline/release/app-baseline-release-unsigned.apk`

The chosen ABI is controlled by the `looktubeBaselineReleaseAbi` property. Public release automation should sign and rename each build immediately after it is produced so both ABI-specific artifacts are preserved.

## Highspec build artifact
Build the highspec release artifact with:

```powershell
.\gradlew.bat :app:assembleHighspecRelease
```

This produces the unsigned highspec release APK at:

`app/build/outputs/apk/highspec/release/app-highspec-release-unsigned.apk`

## Signing
Keep keystores, passwords, and signing material out of the repository.

After building the unsigned baseline APKs, align and sign each one with Android build-tools, then rename the signed files to stable public artifact names such as:

- `LookTube-Baseline-arm64-v8a-{{LOOKTUBE_VERSION}}.apk`
- `LookTube-Baseline-x86_64-{{LOOKTUBE_VERSION}}.apk`
- `LookTube-Highspec-arm64-v8a-{{LOOKTUBE_VERSION}}.apk`

Example flow:

```powershell
$BUILD_TOOLS = "{{ANDROID_BUILD_TOOLS_PATH}}"
$UNSIGNED_APK = "app/build/outputs/apk/baseline/release/app-baseline-release-unsigned.apk"
$ALIGNED_APK = "app/build/outputs/apk/baseline/release/LookTube-Baseline-arm64-v8a-{{LOOKTUBE_VERSION}}-aligned.apk"
$SIGNED_APK = "app/build/outputs/apk/baseline/release/LookTube-Baseline-arm64-v8a-{{LOOKTUBE_VERSION}}.apk"

& "$BUILD_TOOLS\zipalign.exe" -p -f 4 $UNSIGNED_APK $ALIGNED_APK
& "$BUILD_TOOLS\apksigner.bat" sign --ks "{{LOOKTUBE_RELEASE_KEYSTORE_PATH}}" --ks-key-alias "{{LOOKTUBE_RELEASE_KEY_ALIAS}}" --out $SIGNED_APK $ALIGNED_APK
& "$BUILD_TOOLS\apksigner.bat" verify --verbose --print-certs $SIGNED_APK
```

This signs interactively, so passwords do not need to be placed in the command line or stored in git.

The repository also includes a helper script that signs either release flavor and writes the adjacent checksum file:

```powershell
pwsh -NoLogo -File .\scripts\Sign-ReleaseApk.ps1 -Flavor baseline -KeystorePath "{{LOOKTUBE_RELEASE_KEYSTORE_PATH}}" -KeyAlias "{{LOOKTUBE_RELEASE_KEY_ALIAS}}" -VersionName "{{LOOKTUBE_VERSION}}"
pwsh -NoLogo -File .\scripts\Sign-ReleaseApk.ps1 -Flavor highspec -KeystorePath "{{LOOKTUBE_RELEASE_KEYSTORE_PATH}}" -KeyAlias "{{LOOKTUBE_RELEASE_KEY_ALIAS}}" -VersionName "{{LOOKTUBE_VERSION}}"
```

For the baseline flavor, the helper can infer the ABI from the unsigned APK contents and write an ABI-specific final file name automatically. The automated workflow signs each ABI build immediately after it is produced so both baseline assets are preserved.

## SHA-256 checksums
Generate a checksum file for each signed APK before publishing:

```powershell
$APK = "app/build/outputs/apk/baseline/release/LookTube-Baseline-arm64-v8a-{{LOOKTUBE_VERSION}}.apk"
$HASH = (Get-FileHash $APK -Algorithm SHA256).Hash.ToLower()
"$HASH *$(Split-Path $APK -Leaf)" | Set-Content "$APK.sha256" -Encoding ascii
```

## GitHub Actions automation
The repository now includes `.github/workflows/release.yml` for tag-driven signed releases.

Behavior:
- triggers when a tag matching `v*.*.*` is pushed
- verifies that the tag version matches `app/build.gradle.kts` `versionName`
- builds baseline and highspec release APKs
- signs them using GitHub Actions secrets
- uploads baseline `arm64-v8a`, baseline `x86_64`, and highspec `arm64-v8a` APKs together with their `.sha256` files to the GitHub release for that tag
- generates user-facing release notes with install guidance, a commit summary, and a GitHub compare link when an earlier version tag exists

To publish `0.1.2`, update `versionName` first and then push a matching tag:

```powershell
git tag v0.1.2
git push origin v0.1.2
```

## Required GitHub secrets
Configure these secrets in a GitHub Actions environment named `release` before using the automated release workflow:
- `LOOKTUBE_RELEASE_KEYSTORE_B64`
- `LOOKTUBE_RELEASE_KEYSTORE_PASSWORD`
- `LOOKTUBE_RELEASE_KEY_ALIAS`
- `LOOKTUBE_RELEASE_KEY_PASSWORD`

Notes:
- for this public repository, environment secrets are preferable to plain repository secrets because they can be paired with environment protection rules such as required reviewers
- `LOOKTUBE_RELEASE_KEYSTORE_B64` is the base64-encoded contents of the `.jks` file
- if you only set one password during `keytool -genkeypair`, use that same password value for both `LOOKTUBE_RELEASE_KEYSTORE_PASSWORD` and `LOOKTUBE_RELEASE_KEY_PASSWORD`
- the alias itself is not highly sensitive, but keeping it in secrets keeps all signing configuration in one place

PowerShell helper to copy the keystore as base64:

```powershell
$KEYSTORE_B64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("{{LOOKTUBE_RELEASE_KEYSTORE_PATH}}"))
Set-Clipboard $KEYSTORE_B64
```

If you are unsure whether your key password differs from the keystore password, start by setting both GitHub secrets to the same value. That is the common case for a single-password keystore setup.

## GitHub release contents
For each public release, upload:
- the signed baseline `arm64-v8a` APK
- the signed baseline `x86_64` APK when the release still supports x86_64 emulator installs
- the signed highspec APK when a release intentionally includes the higher-spec flavor
- the adjacent `.sha256` file for each APK
- short release notes covering install choice, major changes, supported Android floor, and any upgrade caveats

Suggested first release shape:
- tag: `v0.1.0`
- title: `LookTube 0.1.0`
- assets:
  - `LookTube-Baseline-arm64-v8a-{{LOOKTUBE_VERSION}}.apk`
  - `LookTube-Baseline-arm64-v8a-{{LOOKTUBE_VERSION}}.apk.sha256`
  - `LookTube-Baseline-x86_64-{{LOOKTUBE_VERSION}}.apk`
  - `LookTube-Baseline-x86_64-{{LOOKTUBE_VERSION}}.apk.sha256`

If a release also includes the higher-spec flavor, add:
- `LookTube-Highspec-arm64-v8a-{{LOOKTUBE_VERSION}}.apk`
- `LookTube-Highspec-arm64-v8a-{{LOOKTUBE_VERSION}}.apk.sha256`

## Release notes guidance
Keep release notes concise and user-facing:
- what changed
- which APK most users should install
- when the x86_64 baseline build is the right choice
- who should install the optional highspec build
- known limitations
- whether the release is baseline-only or also includes the optional highspec build
- a GitHub compare link at the bottom when the release has a previous version tag

## Final pre-publish checks
- verify the APK installs on a real device
- verify the checksum file matches the uploaded APK
- confirm no signing material is tracked in git
- confirm release notes do not include feed URLs, raw payloads, or personal data
