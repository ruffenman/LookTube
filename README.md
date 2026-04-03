# LookTube
LookTube is an Android-first Giant Bomb companion app focused on one primary outcome: paste a copied Giant Bomb Premium feed URL, sync it, and watch Premium video content without navigating the website in a browser.

## Current status
The repository is now past the initial foundation spike and includes a usable Android browse/playback slice:
- native Android app shell in Kotlin + Compose with modular `app`, `core:*`, and `feature:*` boundaries
- copied Giant Bomb Premium RSS URLs are the only supported sync input and persist encrypted at rest
- persisted synced-library state and playback resume state across app restarts
- Giant Bomb site-content heuristics are consolidated in one shared library so title/grouping inference rules have a single update point
- WorkManager-backed background refresh plus immediate and rolling catch-up fallbacks for device notifications about newly discovered videos
- app-level Media3 playback service/session with background playback, fullscreen support, no-autoplay launch inspection flows, and persistent remote-playback controls
- default `baseline` app target keeps `minSdk 28` plus the existing whisper.cpp caption path, while an opt-in `moonshine` target adds a higher-spec local engine lane
- consolidated Library surface with grouping modes, rich video cards, and a flyout jump rail
- committed Roborazzi visual baseline coverage for the Library browse surface, key Settings/feed states, and stable Player status states
- fixture-driven parser/repository tests plus managed smoke coverage
- maintained docs, ADR, and Ralph loop validation commands
- the validated product shape remains feed-first rather than website-login automation

## Ralph loop commands
Use these commands as the default development loop on Windows:

```powershell path=null start=null
.\gradlew.bat verifyFast
.\gradlew.bat verifyLocal -PskipManagedDevice=true
.\gradlew.bat verifyMoonshine
.\gradlew.bat integrationProbeGiantBomb
.\gradlew.bat integrationProbeGiantBombPlayback
```

`integrationProbeGiantBomb` probes the copied feed URL directly and emits structural-only results.
`integrationProbeGiantBombPlayback` samples extracted playback targets from a real Premium feed and checks whether they respond directly the way the app's current Media3 handoff expects.

If `local.properties` is missing, bootstrap it first:

```powershell path=null start=null
pwsh -NoLogo -File .\scripts\Bootstrap-LocalAndroid.ps1
```

Run the managed-device smoke lane when emulator support is ready:

```powershell path=null start=null
.\gradlew.bat verifyLocal
```

Use the opt-in high-spec lane when validating the Moonshine-capable target:

```powershell path=null start=null
.\gradlew.bat verifyMoonshine
```

## Connected-device deployment
Use this standard flow when deploying to a physical Android device unless a task explicitly only needs one target.

1. Confirm at least one device is attached:

```powershell path=null start=null
adb devices
```

2. If more than one device is attached, pick the serial you want and pass `-s <serial>` to later `adb` commands.

### Standard all-target deploy
Use this when the connected device satisfies the Moonshine flavor requirements:
- API level 35 or newer
- `arm64-v8a` device ABI

```powershell path=null start=null
.\\\\gradlew.bat :app:installBaselineDebug :app:installMoonshineDebug
adb shell monkey -p com.looktube.app -c android.intent.category.LAUNCHER 1
adb shell monkey -p com.looktube.app.moonshine -c android.intent.category.LAUNCHER 1
```

### Single-target deploy
Use this when only the baseline target is needed:

```powershell path=null start=null
.\\\\gradlew.bat :app:installBaselineDebug
adb shell monkey -p com.looktube.app -c android.intent.category.LAUNCHER 1
```

### Optional targeted-device form
If multiple devices are attached, use the same install task and launch command, but target one serial explicitly:

```powershell path=null start=null
$DEVICE_SERIAL = "{{DEVICE_SERIAL}}"
.\\gradlew.bat :app:installBaselineDebug
adb -s $DEVICE_SERIAL shell monkey -p com.looktube.app -c android.intent.category.LAUNCHER 1
```

Swap `installBaselineDebug` and `com.looktube.app` for `installMoonshineDebug` and `com.looktube.app.moonshine` when deploying the Moonshine flavor.

## Documentation map
- `WARP.md` - short operational instructions for future dev-agent sessions
- `docs/spec/product-spec.md` - living scope, milestones, acceptance criteria, and the required source of truth for current design changes
- `docs/spec/reproducible-project-spec.md` - implementation-oriented project spec that should be updated whenever design changes affect the transferable behavior contract
- `docs/spec/agent-spec-package/` - transfer-ready `spec.yaml` + `metadata.toml` package aligned to the target agent-spec repository format
- `docs/architecture/overview.md` - module boundaries and data flow
- `docs/integration/giantbomb.md` - validated external integration notes and open risks
- `docs/testing/local-ci.md` - Ralph loop workflow and validation strategy
- `docs/decisions/ADR-0001-foundation.md` - foundation architecture decision record
- `docs/decisions/ADR-0002-auth-persistence.md` - feed URL persistence and synced-data-clearing decision
- `docs/learned-notes/2026-03.md` - rolling learnings log for future reference

## Repository layout
- `app` - Android application shell, navigation, and app-level state wiring
- `core:heuristics` - the single shared home for Giant Bomb site-content heuristics and inference rules
- `core:model` - domain models
- `core:data` - repository contracts and the current in-memory spike implementation
- `core:database` - playback bookmark storage seam
- `core:network` - RSS parsing and future feed/network integration seam
- `core:designsystem` - shared Compose theme and basic UI building blocks
- `core:testing` - shared fixture and coroutine testing helpers
- `feature:*` - user-facing screens split by concern

## Near-term implementation focus
1. validate additional copied Premium feed variants and playback targets against the same feed-only path
2. harden repeat-use reliability for background refresh and new-release notifications on real devices
3. continue improving browse ergonomics, visual polish, and show-grouping quality from live device feedback
4. keep expanding screenshot-oriented visual regression coverage around richer active playback states once deterministic non-`PlayerView` states are locked down
5. tighten any remaining Giant Bomb-specific playback edge cases found during device validation without drifting into unsupported site automation
