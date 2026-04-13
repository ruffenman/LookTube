# GitHub releases
This document covers how to package and publish signed APKs for GitHub Releases.
## Android app signing in plain terms
Android release signing is not the same thing as SSH key generation.

For Android, you create a long-lived signing keystore and use it to sign release APKs. Devices use that signature to verify the app, and future updates must be signed with the same key if you want users to upgrade cleanly.

## Public release policy
- prefer the baseline flavor as the default public release artifact
- highspec flavor APKs may be published as optional public release artifacts only while they remain limited to Moonshine Voice code plus the MIT-licensed English Moonshine models already validated for this repository; re-review upstream terms before shipping any broader Moonshine model set
- both baseline Whisper.cpp model downloads and highspec Moonshine model downloads must stay pinned to immutable upstream revisions and pass SHA-256 verification before they are activated on-device
- publish signed APKs, SHA-256 checksum files, concise release notes, and supported-device notes together
## Generate a release keystore
Create the keystore once and back it up carefully outside the repository. Do not commit it.

Example command:

```powershell
keytool -genkeypair -v -keystore "{{LOOKTUBE_RELEASE_KEYSTORE_PATH}}" -alias "{{LOOKTUBE_RELEASE_KEY_ALIAS}}" -keyalg RSA -keysize 4096 -validity 3650
```

Notes:
- `keytool` will prompt for passwords and certificate metadata interactively
- keep the keystore file and its passwords somewhere you can recover later
- losing the signing key means you cannot ship seamless updates to users of existing releases

## Baseline build artifact
Build the baseline release artifact with:

```powershell
.\gradlew.bat :app:assembleBaselineRelease
```

This produces the unsigned baseline APK at:

`app/build/outputs/apk/baseline/release/app-baseline-release-unsigned.apk`

## Highspec build artifact
Build the highspec release artifact with:

```powershell
.\gradlew.bat :app:assembleHighspecRelease
```

This produces the unsigned highspec APK at:

`app/build/outputs/apk/highspec/release/app-highspec-release-unsigned.apk`

## Signing
Keep keystores, passwords, and signing material out of the repository.

After building the unsigned baseline APK, align and sign it with Android build-tools, then rename the signed file to a stable public artifact name such as:

`LookTube-Baseline-0.1.0.apk`

Example flow:

```powershell
$BUILD_TOOLS = "{{ANDROID_BUILD_TOOLS_PATH}}"
$UNSIGNED_APK = "app/build/outputs/apk/baseline/release/app-baseline-release-unsigned.apk"
$ALIGNED_APK = "app/build/outputs/apk/baseline/release/LookTube-Baseline-0.1.0-aligned.apk"
$SIGNED_APK = "app/build/outputs/apk/baseline/release/LookTube-Baseline-0.1.0.apk"

& "$BUILD_TOOLS\zipalign.exe" -p -f 4 $UNSIGNED_APK $ALIGNED_APK
& "$BUILD_TOOLS\apksigner.bat" sign --ks "{{LOOKTUBE_RELEASE_KEYSTORE_PATH}}" --ks-key-alias "{{LOOKTUBE_RELEASE_KEY_ALIAS}}" --out $SIGNED_APK $ALIGNED_APK
& "$BUILD_TOOLS\apksigner.bat" verify --verbose --print-certs $SIGNED_APK
```

This signs interactively, so passwords do not need to be placed in the command line or stored in git.

The repository also includes a helper script that signs either release flavor and writes the adjacent checksum file:

```powershell
pwsh -NoLogo -File .\\scripts\\Sign-ReleaseApk.ps1 -Flavor baseline -KeystorePath "{{LOOKTUBE_RELEASE_KEYSTORE_PATH}}" -KeyAlias "{{LOOKTUBE_RELEASE_KEY_ALIAS}}"
pwsh -NoLogo -File .\\scripts\\Sign-ReleaseApk.ps1 -Flavor highspec -KeystorePath "{{LOOKTUBE_RELEASE_KEYSTORE_PATH}}" -KeyAlias "{{LOOKTUBE_RELEASE_KEY_ALIAS}}"
```

## SHA-256 checksums
Generate a checksum file for each signed APK before publishing:

```powershell
$APK = "app/build/outputs/apk/baseline/release/LookTube-Baseline-0.1.0.apk"
$HASH = (Get-FileHash $APK -Algorithm SHA256).Hash.ToLower()
"$HASH *$(Split-Path $APK -Leaf)" | Set-Content "$APK.sha256" -Encoding ascii
```

## GitHub release contents
For each public release, upload:
- the signed baseline APK
- the signed highspec APK when a release intentionally includes the higher-spec flavor
- the adjacent `.sha256` file
- short release notes covering major changes, supported Android floor, and any upgrade caveats

Suggested first release shape:
- tag: `v0.1.0`
- title: `LookTube 0.1.0`
- assets:
  - `LookTube-Baseline-0.1.0.apk`
  - `LookTube-Baseline-0.1.0.apk.sha256`

If a release also includes the higher-spec flavor, add:
- `LookTube-Highspec-0.1.0.apk`
- `LookTube-Highspec-0.1.0.apk.sha256`

## Release notes guidance
Keep release notes concise and user-facing:
- what changed
- who should install this build
- known limitations
- whether the release is baseline-only or also includes the optional highspec build

## Final pre-publish checks
- verify the APK installs on a real device
- verify the checksum file matches the uploaded APK
- confirm no signing material is tracked in git
- confirm release notes do not include feed URLs, raw payloads, or personal data
