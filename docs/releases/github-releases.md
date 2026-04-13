# GitHub release process
Use GitHub Releases as the official distribution surface for signed APKs.

## Public release policy
- prefer the baseline flavor as the default public release artifact
- do not publish Moonshine flavor APKs as official public releases until its distribution and notice requirements are explicitly confirmed
- publish signed APKs, SHA-256 checksum files, concise release notes, and supported-device notes together

## Baseline build artifact
Build the baseline release artifact with:

```powershell
.\gradlew.bat :app:assembleBaselineRelease
```

This produces the unsigned baseline APK at:

`app/build/outputs/apk/baseline/release/app-baseline-release-unsigned.apk`

## Signing
Keep keystores, passwords, and signing material out of the repository.

Sign the baseline APK with your local release-signing process, then rename the signed file to a stable public artifact name such as:

`LookTube-Baseline-0.1.0.apk`

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
- the adjacent `.sha256` file
- short release notes covering major changes, supported Android floor, and any upgrade caveats

Suggested first release shape:
- tag: `v0.1.0`
- title: `LookTube 0.1.0`
- assets:
  - `LookTube-Baseline-0.1.0.apk`
  - `LookTube-Baseline-0.1.0.apk.sha256`

## Release notes guidance
Keep release notes concise and user-facing:
- what changed
- who should install this build
- known limitations
- whether the release is baseline-only

## Final pre-publish checks
- verify the APK installs on a real device
- verify the checksum file matches the uploaded APK
- confirm no signing material is tracked in git
- confirm release notes do not include feed URLs, raw payloads, or personal data
