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
- configurable repository tests for persisted settings, seeded fallback behavior, and feed sync transitions
- `app` unit tests
- managed-device smoke coverage now also checks the player fallback surface
- managed-device smoke coverage also verifies the Premium sign-in screen copy

## Full local gate
Run before a stable checkpoint commit:

```powershell path=null start=null
.\gradlew.bat verifyLocal -PskipManagedDevice=true
```

This adds:
- Android lint for the app shell
- optional managed-device smoke tests when `-PskipManagedDevice` is not supplied

## Managed-device smoke lane
The app is configured with a `pixel6Api36` managed virtual device. The smoke lane currently passes locally, though AGP still emits an ABI migration warning during setup:

```powershell path=null start=null
.\gradlew.bat verifyLocal
```

## Live integration probe
Use only when you have local Giant Bomb Premium credentials available as environment variables:

```powershell path=null start=null
$env:LOOKTUBE_GIANTBOMB_FEED_URL = "{{LOOKTUBE_GIANTBOMB_FEED_URL}}"
$env:LOOKTUBE_GIANTBOMB_USERNAME = "{{LOOKTUBE_GIANTBOMB_USERNAME}}"
$env:LOOKTUBE_GIANTBOMB_PASSWORD = "{{LOOKTUBE_GIANTBOMB_PASSWORD}}"
.\gradlew.bat integrationProbeGiantBomb
```

## Fixture policy
- prefer sanitized local fixtures in automated tests
- do not commit authenticated responses or cookies
- when external behavior changes, update the fixture, tests, integration notes, and learnings log together
- current app behavior persists feed URL, username, and auth mode locally, but keeps password input session-only
