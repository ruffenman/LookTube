# Contributing to LookTube
## Setup
Prerequisites:
- Android Studio or a working Android SDK installation
- Java 17
- PowerShell on Windows

If `local.properties` is missing, bootstrap it with:

```powershell
pwsh -NoLogo -File .\scripts\Bootstrap-LocalAndroid.ps1
```

## Validation
Common contributor commands:

```powershell
.\gradlew.bat verifyFast
.\gradlew.bat verifyLocal -PskipManagedDevice=true
```

Use the managed-device lane when emulator support is available:

```powershell
.\gradlew.bat verifyLocal
```

Use the Moonshine-specific lane only when you need to validate the higher-spec flavor:

```powershell
.\gradlew.bat verifyMoonshine
```

More detail lives in `docs/testing/local-ci.md`.

## Device deployment
Typical debug deployment commands:

```powershell
adb devices
.\gradlew.bat :app:installBaselineDebug
adb shell monkey -p com.looktube.app -c android.intent.category.LAUNCHER 1
```

If you are validating the Moonshine flavor on a compatible device:

```powershell
.\gradlew.bat :app:installMoonshineDebug
adb shell monkey -p com.looktube.app.moonshine -c android.intent.category.LAUNCHER 1
```

For broader device-validation and screenshot guidance, see `docs/testing/local-ci.md`.

## Sensitive data rules
- never commit copied Premium feed URLs, cookies, raw authenticated RSS payloads, or signing material
- sanitize URLs and payloads before sharing logs, screenshots, or repro steps
- follow `SECURITY.md` when reporting vulnerabilities or security-sensitive bugs

## Documentation expectations
- update `docs/spec/product-spec.md` when user-visible behavior changes
- update `docs/integration/giantbomb.md` when live integration assumptions change
- record architecture tradeoffs as ADRs under `docs/decisions/`

## Releases
The baseline flavor is the default public release target.

For GitHub release packaging, Android signing, checksum generation, and release asset policy, see `docs/releases/github-releases.md`.
