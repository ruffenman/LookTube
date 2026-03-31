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
- `core:heuristics` JVM tests for centralized Giant Bomb site-content inference
- `core:model` JVM tests
- `core:data` JVM tests
- `core:database` JVM tests
- `core:network` fixture-driven parser tests
- configurable repository tests for persisted feed URLs, clean empty-library startup behavior, and feed sync transitions
- `app` baseline unit tests, including background refresh diff and notification-posting regressions
- committed Roborazzi screenshot baselines can be verified explicitly when UI work lands and currently cover Library, shell intro, Settings/feed, and Player surfaces
- managed-device smoke coverage now also checks the player empty-state surface
- managed-device smoke coverage also verifies the Premium sign-in screen copy

## Full local gate
Run before a stable checkpoint commit:

```powershell path=null start=null
.\gradlew.bat verifyLocal -PskipManagedDevice=true
```

This adds:
- Android lint for the baseline app target
- screenshot baseline verification through the baseline Roborazzi lane
- optional managed-device smoke tests when `-PskipManagedDevice` is not supplied

Stable-checkpoint policy:
- after the full local gate passes for a stable slice, commit it immediately
- push that checkpoint immediately
- deploy the latest build immediately when a deployable app build exists
- do those stable-checkpoint actions before finishing the response for the user, without pausing to ask for permission first

## Managed-device smoke lane
The baseline app target is configured with a `pixel6Api36` managed virtual device. The smoke lane currently passes locally, though AGP still emits an ABI migration warning during setup:

```powershell path=null start=null
.\gradlew.bat verifyLocal
```

## High-spec Moonshine lane
Use this opt-in lane when validating the Moonshine-capable build target:

```powershell path=null start=null
.\gradlew.bat verifyMoonshine
```

This lane keeps the default contributor flow untouched while separately checking the higher-spec flavor's compile, unit, and lint behavior.

## Connected-device notification validation
Use a connected Android device when notification reliability changes are under review:

```powershell path=null start=null
adb devices
adb shell dumpsys jobscheduler com.looktube.app
adb shell dumpsys notification --noredact | findstr "looktube.library.updates"
```

Functional target for manual validation:
- `LibraryRefreshWorker` is scheduled while a saved feed URL exists
- the `looktube.library.updates` channel exists on-device
- later successful detections produce distinct notification entries for distinct newly discovered latest video IDs
- the first sync for a feed URL remains silent

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
