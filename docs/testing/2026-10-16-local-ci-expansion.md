# Local CI expansion — 2026-10-16

## Added coverage

- `verifyAllLocal`: the fast suite, both flavors' unit tests/lint/screenshot verification, all four planned debug/release package identities, and both flavors' debug app/instrumentation APK assembly. Both native test source sets compile without requiring a device.
- Python standard-library runner with fast/full/explicit-serial device lanes, unique step logs and atomic JSON status reports. Native APK hashes and current-run screenshots accompany successful device runs. Failures, timeouts, interruptions and unexpected validation errors cannot silently pass; zero/partial/crashed instrumentation is rejected even with ADB exit zero.
- Seven runner regression tests and three real-Android Library tests covering contraction/reversal without playback, measured outgoing-row laziness, collapse persistence across scrolling/Top, real outside-window pointer dismissal, and a constrained 320-dp/2x-font layout.
- Native screenshot review found the large-text group-role label wrapping letter by letter beside the control. A wrapping role/control row fixes it, with an explicit label-height assertion in both JVM and native tests. Existing normal-size screenshot baselines remain unchanged.

## Problems exposed during rollout

The existing Android smoke test lacked the `click` import and failed compilation. The new full lane now compiles that source for both flavors. The first phone run then failed before completing its native tests because transitive Espresso 3.5.0 used reflective `InputManager.getInstance` on API 37. Explicit test-only Espresso 3.7.0 resolves this, matching the [AndroidX release note](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0). The runner correctly recorded that run as failed and retained the stack traces.

## Evidence and limits

The full lane passed with both flavors' 121 app tests (zero failures/errors/skips), module tests, both screenshot verifiers, lint, identity checks and all four debug app/test APK builds. The native lane passed on the explicitly selected Pixel 10 Pro XL / API 37 after the large-text fix: all three tests passed in 8.934 seconds, and all five expected screenshots were retrieved. Native screenshot inspection confirmed the group role is readable at 2x text. An explicitly nonexistent serial exited 1 at preflight before any build/install and saved its failed report.

Ignored local run directories: `artifacts/local-ci/20261016t194701z-a3247a7a` (expected initial Espresso failure), `20261016t194957z-49f68966` (offline-device negative check), `20261016t195343z-24494c97` (full pass with both screenshot flavors), and `20261016t195526z-eeb1fe58` (native pass with large-text assertion). The final cached full pass, including all seven runner regressions, is `20261016t195707z-2c60e358`. These runs honestly record the base commit plus a dirty-worktree flag; they are not clean-commit or hosted-CI claims.

Only the isolated debug app/test packages were installed. Normal release packages and account data were not changed. The emulator/managed-device lane was not run in this slice. Native tests use in-memory synthetic non-playable videos and do not establish production artwork, aesthetic animation quality, real playback/casting or frame pacing. The run-scoped screenshots remain in the debug app's external files for later inspection; no shared device folders were cleared.
