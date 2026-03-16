# Local CI and Ralph loops
## Philosophy
Local validation should make thin vertical slices cheap to test and hard to regress. The default flow is spec first, test next, implementation after that.

## Fast Ralph loop lane
Run after every narrow slice:

```powershell path=null start=null
.\gradlew.bat verifyFast
```

Current coverage:
- required docs presence and basic doc freshness checks
- `core:model` JVM tests
- `core:data` JVM tests
- `core:database` JVM tests
- `core:network` fixture-driven parser tests
- configurable repository tests for persisted feed URLs, seeded fallback behavior, and feed sync transitions
- `app` unit tests
- committed Roborazzi screenshot baselines can be verified explicitly when UI work lands and currently cover Library, Auth, and Player surfaces
- managed-device smoke coverage now also checks the player empty-state surface
- managed-device smoke coverage also verifies the Premium sign-in screen copy

## Full local gate
Run before a stable checkpoint commit:

```powershell path=null start=null
.\gradlew.bat verifyLocal -PskipManagedDevice=true
```

This adds:
- Android lint for the app shell
- screenshot baseline verification through Roborazzi
- optional managed-device smoke tests when `-PskipManagedDevice` is not supplied

## Managed-device smoke lane
The app is configured with a `pixel6Api36` managed virtual device. The smoke lane currently passes locally, though AGP still emits an ABI migration warning during setup:

```powershell path=null start=null
.\gradlew.bat verifyLocal
```

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
- Auth setup-required state
- Auth synced state
- Auth ready state with a saved feed URL
- Player empty-queue state
- Player preparing state

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
- current app behavior persists only the copied feed URL locally and keeps the product strictly feed-first
