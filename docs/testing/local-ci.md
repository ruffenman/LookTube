# Local validation
This document covers the local validation and device-check workflow for contributors working on LookTube.

## One-command local CI

Use Python 3.11+ (standard library only), the configured Android/JDK toolchain and Gradle wrapper:

| Command | Coverage |
| --- | --- |
| `python scripts/local_ci.py fast` | JVM/unit and interaction tests, docs, runner failure-detection tests, all four package-ID contracts |
| `python scripts/local_ci.py full` | Fast checks plus both flavors' unit tests, lint, both-flavor screenshot verification, and both flavors’ debug app/test APK builds |
| `python scripts/local_ci.py device --serial <serial>` | Native populated-library tests on exactly the selected API-35+ arm64 Android device; isolated highspec debug target |

Each invocation writes a unique `artifacts/local-ci/<run-id>/summary.json` plus step logs. Reports include source commit, dirty-worktree flag, selected lane, step status/duration/exit code, and failures. Device reports also record the explicit serial, installed APK hashes and current-run screenshots. A failed build, offline/unauthorized phone, incompatible API/ABI, failed/partial/zero instrumentation tests or missing evidence exits nonzero. ADB exit zero alone does not pass the native lane. Commands have timeouts; interrupted runs are recorded as interrupted rather than passed.

The full lane is also available directly as `.\gradlew.bat verifyAllLocal`. It never starts an emulator or requires a Giant Bomb account, feed URL, release-signing key or phone. Normal Gradle dependency downloads may still be needed on an uncached machine. Set `-PlooktubePython=<interpreter>` when invoking Gradle directly with a non-default Python; the Python runner supplies its own interpreter automatically.

The device lane builds/updates only `com.looktube.app.highspec.debug` and its test APK. It does not uninstall, clear or install over either normal release package. Native tests render synthetic non-playable videos directly in a test Activity; only debug browse preferences are reset. Test screenshots are copied from a unique directory inside that debug app's external files. Device-side evidence remains there until the debug install is removed; no shared device folders are wiped. The runner does not seed the feed/cache or copy account data.

Native coverage now exercises header continuity and interrupted contraction, lazy outgoing rows, collapse persistence after scrolling away and returning with Top, actual outside-window pointer dismissal without activating the underlying header, and a 320-dp content width with 2x text. Five native endpoint/mid-transition screenshots accompany successful runs. This validates real Android mechanics and fixture layout; frame pacing, arbitrary production artwork, casting and real media playback remain separate acceptance checks. It does not claim that a passing screenshot proves an aesthetically seamless animation.

The existing managed-device lane runs the same native tests plus the empty-app smoke test on a disposable baseline emulator. Physical-device runs intentionally select only `NativeLibraryMechanicsTest`, so they do not assume an empty debug install.

## Fast validation lane
Run after a focused change:

```powershell path=null start=null
.\gradlew.bat verifyFast
```

Current coverage:
- explicit baseline/highspec debug/release application-ID contracts
- seven standard-library runner tests for false-success, failed command, timeout and device-preflight handling
- required docs presence and basic doc freshness checks
- `core:heuristics` JVM tests for centralized Giant Bomb site-content inference
- `core:model` JVM tests
- `core:data` JVM tests
- `core:database` JVM tests
- `core:network` fixture-driven parser tests
- `feature:library` grouping, ordering, and lazy-row jump-anchor tests, plus `feature:player` unit tests in both fast and highspec gates
- populated-library interaction tests for collapse persistence across scrolling, filters, grouping changes and recreation; stable browse navigation, short edge swipes, popup outside dismissal, refresh anchoring, accessible scroll progress, lazy episode composition, and description-first details
- Settings interaction tests for refresh-error priority, feed URL reveal, explicit feed drafts and validation, and cancel/confirm behavior for data-clearing actions
- Player interaction coverage for per-video caption-deletion cancellation and confirmation
- configurable repository tests for persisted feed URLs, clean empty-library startup behavior, and feed sync transitions
- `app` baseline unit tests, including no-autoplay launch, cast-control visibility, cast wake-behavior, background refresh diff, and notification-posting regressions
- committed Roborazzi screenshot baselines can be verified explicitly when UI work lands and currently cover Library, shell intro, Settings/feed, and Player surfaces
- managed-device smoke coverage now also checks the player empty-state surface
- managed-device smoke coverage also verifies the Premium sign-in screen copy

## Full local gate
Run before pushing a meaningful checkpoint:

```powershell path=null start=null
.\gradlew.bat verifyLocal -PskipManagedDevice=true
```

This adds:
- Android lint for the baseline app target
- screenshot baseline verification through the baseline Roborazzi lane
- optional managed-device smoke tests when `-PskipManagedDevice` is not supplied

## Managed-device smoke lane
The baseline app target is configured with a `pixel6Api36` managed virtual device. This opt-in lane requires the emulator image and virtualization support; a physical-device pass does not establish that the managed lane passed:

```powershell path=null start=null
.\gradlew.bat verifyLocal
```

## Highspec lane
Use this opt-in lane when validating the higher-spec build target:

```powershell path=null start=null
.\gradlew.bat verifyHighspec
```

This lane keeps the default contributor flow untouched while separately checking the higher-spec flavor's compile, unit, and lint behavior.

## Connected-device deployment
Use this sequence when validating builds on a physical Android device.

For an existing signed release with user data, use the signed device-update workflow documented in `docs/releases/` and install the matching package and signing certificate with `adb install -r`. Do not substitute a debug build or uninstall the release. Verify the installed APK, first-install timestamp, and data-directory inode before and after the update.

For browse design acceptance, capture real-device screenshots with `adb shell screencap`: collapse a populated group, scroll past several other groups, return with Top in the browse bar, and check that the collapse choice survives. Check short swipes at the right edge. Open Jump to show/topic, scroll its list, and confirm the bar stays fixed; outside taps and Back should dismiss without activating underlying cards. Also inspect search/filter reset, description-first details with the fixed Close action, and masked Settings feed drafts. Use Discard changes and Cancel when checking draft and deletion flows against real user data.

1. Confirm device visibility first:

```powershell path=null start=null
adb devices
```

2. If multiple devices are attached, choose one serial and use `adb -s <serial>` for launch or follow-up shell commands.

### Fast, isolated UI iteration

Prefer a debug build on the phone before requesting release signing. Debug variants use separate package IDs and the launcher label **LookTube Debug**:

| Target | Debug package | Normal release package |
| --- | --- | --- |
| Baseline | `com.looktube.app.debug` | `com.looktube.app` |
| Highspec | `com.looktube.app.highspec.debug` | `com.looktube.app.highspec` |

For a single selected phone:

```powershell
.\gradlew.bat :app:assembleHighspecDebug
adb -s <serial> install -r app/build/outputs/apk/highspec/debug/app-highspec-debug.apk
python scripts/Seed-DebugBrowseFixture.py --serial <serial> --package com.looktube.app.highspec.debug
```

The seed helper replaces only the chosen debug app's feed/cache/watch fixture preferences with 40 non-playable sample videos, including a multi-video stack, a longer title and a large collection. It does not read the normal app's data or credentials. Its deliberately invalid feed URL may produce a refresh warning; the local fixture cache remains usable. Do not interpret fixture artwork as acceptance of real library artwork. Use a separately configured debug feed when that is needed and authorized; do not copy or log release credentials to populate a preview.

Iterate locally with debug installs, ADB screenshots and transition recordings. Keep the normal signed app and its data intact. After UI/mechanics checks pass, request the signed update and verify it once against the normal populated library. Debug installs use the automatic development key; normal release updates still require the matching release certificate.

### Standard all-target deploy
Use the default lower-spec target plus the higher-spec target together when the connected device meets the highspec flavor requirements:
- API 35+
- `arm64-v8a`

```powershell path=null start=null
.\gradlew.bat :app:installBaselineDebug :app:installHighspecDebug
adb shell monkey -p com.looktube.app.debug -c android.intent.category.LAUNCHER 1
adb shell monkey -p com.looktube.app.highspec.debug -c android.intent.category.LAUNCHER 1
```

### Single-target deploy
If the task only needs the default lower-spec target:

```powershell path=null start=null
.\gradlew.bat :app:installBaselineDebug
adb shell monkey -p com.looktube.app.debug -c android.intent.category.LAUNCHER 1
```

### Targeting a specific connected device
The Gradle install tasks deploy to connected eligible devices. If you need to launch a specific device afterward, use its serial explicitly:

```powershell path=null start=null
$DEVICE_SERIAL = "{{DEVICE_SERIAL}}"
adb -s $DEVICE_SERIAL shell monkey -p com.looktube.app.debug -c android.intent.category.LAUNCHER 1
```

Swap the package name to `com.looktube.app.highspec.debug` when launching the highspec flavor.

## Connected-device notification validation
Use a connected Android device when notification reliability changes are under review:

```powershell path=null start=null
adb devices
adb shell dumpsys jobscheduler com.looktube.app.debug
adb shell dumpsys notification --noredact | findstr "looktube.library.updates"
```

Functional target for manual validation:
- `LibraryRefreshWorker` is scheduled while a saved feed URL exists
- the `looktube.library.updates` channel exists on-device
- later successful detections produce distinct notification entries for distinct newly discovered latest video IDs
- the first sync for a feed URL remains silent
- tapping a library-update notification opens Player with the newest discovered video selected for review without forcing autoplay

## Visual regression lane
Record or refresh committed screenshot baselines when the UI intentionally changes:

```powershell path=null start=null
.\gradlew.bat recordScreenshots
```

Verify the current UI against committed baselines:

```powershell path=null start=null
.\gradlew.bat verifyScreenshots
```

Current committed baselines cover:
- Library browse surface
- Library empty state
- shell top bar with active playback indicator
- launch intro overlay
- Settings setup-required state
- Settings syncing state
- Settings synced state
- Settings ready state with a saved feed URL
- Player empty-queue state
- Player preparing state
- Player playback-unavailable state

## Live integration probe
Use only when you have a local Giant Bomb Premium feed URL available as an environment variable:

```powershell path=null start=null
$env:LOOKTUBE_GIANTBOMB_FEED_URL = "{{LOOKTUBE_GIANTBOMB_FEED_URL}}"
.\gradlew.bat integrationProbeGiantBomb
.\gradlew.bat integrationProbeGiantBombPlayback
```

The feed probe validates the copied feed URL directly and reports only structural metadata.
The playback probe samples extracted playback targets from the configured feed and checks whether they respond directly to a ranged request without cookie-backed session state, which matches the app's current Media3 handoff.

## Fixture policy
- prefer sanitized local fixtures in automated tests
- do not commit authenticated responses or cookies
- when external behavior changes, update the fixture, tests, integration notes, and learnings log together
- current app behavior persists only the copied feed URL locally, keeps the product strictly feed-first, and treats library-update notifications as a local snapshot-diff result rather than a separate server-push feature


## Native dependency compatibility

The API-37 phone run exposed Espresso 3.5.0 arriving transitively through Compose tests: input injection failed in reflective `InputManager.getInstance`. Android instrumentation explicitly depends on Espresso 3.7.0, whose [official release notes](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0) record the switch to `getSystemService`. This is a test-only dependency. Both instrumentation APKs are compiled by the full offline gate so broken imports/dependencies cannot hide until a phone is available.
