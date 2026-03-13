# LookTube
LookTube is an Android-first Giant Bomb companion app focused on one primary outcome: sign into a Giant Bomb Premium account and watch Premium video content without navigating the website in a browser.

## Current status
The repository is in Phase 0 foundation work:
- native Android project scaffolded in Kotlin + Compose
- app architecture split into `app`, `core:*`, and `feature:*` modules
- fixture-driven parser and repository tests in place
- configurable feed-backed repository path wired behind a persisted local settings seam
- Media3-backed player surface wired for videos that expose a playback URL
- docs, ADR, and Ralph loop validation commands established
- live Giant Bomb auth and playback still require the dedicated integration spike, but the app can now persist feed URL/username/auth mode and attempt a real credentialed feed sync

## Ralph loop commands
Use these commands as the default development loop on Windows:

```powershell path=null start=null
.\gradlew.bat verifyFast
.\gradlew.bat verifyLocal -PskipManagedDevice=true
.\gradlew.bat integrationProbeGiantBomb
```

If `local.properties` is missing, bootstrap it first:

```powershell path=null start=null
pwsh -NoLogo -File .\scripts\Bootstrap-LocalAndroid.ps1
```

Run the managed-device smoke lane when emulator support is ready:

```powershell path=null start=null
.\gradlew.bat verifyLocal
```

## Documentation map
- `WARP.md` - short operational instructions for future dev-agent sessions
- `docs/spec/product-spec.md` - living scope, milestones, and acceptance criteria
- `docs/architecture/overview.md` - module boundaries and data flow
- `docs/integration/giantbomb.md` - validated external integration notes and open risks
- `docs/testing/local-ci.md` - Ralph loop workflow and validation strategy
- `docs/decisions/ADR-0001-foundation.md` - foundation architecture decision record
- `docs/learned-notes/2026-03.md` - rolling learnings log for future reference

## Repository layout
- `app` - Android application shell, navigation, and app-level state wiring
- `core:model` - domain models
- `core:data` - repository contracts and the current in-memory spike implementation
- `core:database` - playback bookmark storage seam
- `core:network` - RSS parsing and future feed/network integration seam
- `core:designsystem` - shared Compose theme and basic UI building blocks
- `core:testing` - shared fixture and coroutine testing helpers
- `feature:*` - user-facing screens split by concern

## Near-term implementation focus
1. validate the exact Giant Bomb Premium feed URL and successful credentialed sync with real account inputs
2. replace session-only password handling with a secure persisted credential/session strategy
3. validate a real Giant Bomb Premium playback URL end to end through the Media3 player
4. add screenshot regression coverage once the first stable visual slice lands
