# Group hierarchy validation: 2026-10-16

Installed version: 0.1.9-highspec, version code 9. Signed build and physical-device acceptance completed on a Pixel 10 Pro XL running API 37.

## Automated checks

`verifyFast`, screenshot recording, `verifyLocal -PskipManagedDevice=true`, `verifyHighspec`, and `:app:assembleHighspecRelease` passed. Baseline and highspec each pass 120 app tests with zero failures/errors/skips; Library's 24 feature tests and Player's 20 feature tests remain passing. Lint and screenshot verification passed. Logs are under ignored `artifacts/group-hierarchy-*.log`.

- Dedicated collapse touch must only collapse the group, without playing a video or toggling twice through the parent header.
- Episode selection must play only the chosen episode and retain the expanded group state.
- The explicit group watch action must update exactly that group's video IDs without playing or collapsing anything.
- The collapse button must remain reachable at 320-dp width and 2x text, with a minimum 48-dp height at standard density.
- Retain existing edge-swipe safety, stable refresh anchoring, collapse persistence, independently scrolling jump-menu dismissal and lazy episode-composition checks.
- Record and verify focused light/dark expanded-group fixtures, the browse overview, and the preserved collapsed-stack fixture. The collapsed image has only a two-pixel border antialiasing difference after adding header-size animation.
- Transfer package YAML and TOML agree on version 0.2.23, including the isolated debug iteration workflow.

## Signed update and physical-device acceptance

GitHub Actions [run 38935592100](https://github.com/ruffenman/LookTube/actions/runs/38935592100) passed after release-environment approval. The signed artifact identifies source commit `47f74b97be8c1c1436299e4a0ffeb5d0914e3f9b`, package `com.looktube.app.highspec`, version code 9. Its SHA-256 is `fe7f20c2559747d83c68ae9834a55574bc31bea962dac4e6cfe3f2cbc93596dc`; the signing certificate SHA-256 is `21e7aa14a333d2f2bc4d3cc8d0444b9ec122a1a2d7b47fd63c2d181105415ae1`. Source, checksum, package, version and certificate were checked before `adb install -r`. The installed base APK matches the artifact hash. Existing app-data directory identities and first-install timestamp were preserved.

On the populated normal app, the real Grubbsnax Premium group has a distinct compact expanded header, readable title/count, miniature artwork stack and separate Collapse group control. Episode cards are visibly inset and numbered. The collapsed group retains its artwork mosaic and tucked cards. A native screen recording of collapse and expansion was inspected, including its restored expanded final frame. The transition uses resizing, placement and fades to retain the card-stack treatment; it does not implement a shared-element morph of every full-size episode card.

After touching Collapse group and re-expanding through the group header, Player still showed “Nothing queued yet.” No production video playback or group watch action was started. Grubbsnax remained at three videos/two watched. The final All overview retained 300 videos, 163 watched, 2006 points and 28/32 groups open; autoplay remained enabled. Original collapse choices were restored.

Evidence is local and ignored: `artifacts/groups-before-current.png`, `groups-before-transition.mp4`, `groups-after-expanded.png`, `groups-after-collapsed.png`, `groups-after-transition.mp4`, `groups-after-restored.png` and `groups-final-library.png`. Artifact verification is recorded in `artifacts/group-hierarchy-artifact-verification.json`. Temporary device-side XML, screenshots and recordings were removed. No feed credentials or raw authenticated responses belong in committed evidence.

## Debug iteration workflow

The follow-up adds a separate `.debug` application ID and LookTube Debug label, plus an explicitly selected debug-only fixture seeder. The highspec debug APK was built and installed alongside the release, seeded with 40 offline fixture videos, and checked on the same phone. Native checks covered expanded/collapsed states, collapse/expand without selecting a video, and a wrapping long group title with a separate collapse control. The temporary fixture app was removed after acceptance; only the normal highspec package remains installed.

After introducing the debug suffix, the screenshot fixture URI was changed to use the activity's actual package name. Existing goldens were retained. `verifyFast verifyLocal -PskipManagedDevice=true verifyHighspec` then passed, including lint and screenshots; log: `artifacts/debug-isolation-gates.log`. These follow-up changes affect debug packaging, test fixtures and documentation; the production UI remains the signed source above.

Physical acceptance covers portrait at the phone's existing display/font settings. The 320-dp/2x-font case is automated JVM coverage, not a separate physical-device acceptance claim.
