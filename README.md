# LookTube
LookTube is an Android-first Giant Bomb companion app focused on one primary outcome: sign into a Giant Bomb Premium account and watch Premium video content without navigating the website in a browser.

## Current status
The repository is now past the initial foundation spike and includes a usable Android browse/playback slice:
- native Android app shell in Kotlin + Compose with modular `app`, `core:*`, and `feature:*` boundaries
- copied Giant Bomb Premium RSS URLs supported as the primary sync path, with optional fallback basic-auth fields
- persisted synced-library state and playback resume state across app restarts
- app-level Media3 playback service/session with background playback and fullscreen support
- consolidated Library surface with grouping modes, rich video cards, and a flyout jump rail
- committed Roborazzi visual baseline coverage for the Library browse surface plus Auth and Player fallback states
- fixture-driven parser/repository tests plus managed smoke coverage
- maintained docs, ADR, and Ralph loop validation commands
- live Giant Bomb auth/session strategy and exact long-term playback integration still need additional hardening beyond the current feed-first path

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
1. replace session-only password handling with a secure persisted credential/session strategy if copied feed URLs prove insufficient
2. continue improving browse ergonomics, visual polish, and show-grouping quality from live device feedback
3. keep expanding screenshot-oriented visual regression coverage around the app shell and richer playback states
4. tighten any remaining Giant Bomb-specific playback or session edge cases found during device validation
