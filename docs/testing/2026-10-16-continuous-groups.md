# Continuous groups and shared surfaces — 2026-10-16

## Candidate scope

A separate debug candidate replaces the 0.1.9 group-header swap with one persistent header in both states. Episode rows contract toward the group and reveal in reverse over 300 ms. Only episode rows measured at the collapse tap remain for the exit; later offscreen episodes cannot enter solely to animate out. Transient render counts also drive jump indices. Rapid toggles cancel the retention cleanup and reverse the existing transition. Playback and watch mutations on outgoing rows are ignored.

The group retains the same title, count, miniature artwork and explicit control throughout. Collapsed episode previews use the existing staggered stack; the unrelated mosaic hero is removed. Settings, Library and Player share neutral opaque panel colors, 24-dp outer corners, 16-dp inset corners and thin borders. Both themes explicitly define all Material surface-container tones. Semantic status colors and thumbnail text overlays remain separate.

## Validation

Automated coverage includes the existing native-touch dispatch, group-watch scoping, 320-dp/2x-font, collapse persistence, edge-swipe, jump-menu and offscreen-composition checks. A controlled Compose-clock regression verifies that the header bounds remain identical during contraction and reversal, an outgoing episode remains during the transition, the last episode does not enter, and the episode disappears after collapse and returns after expansion. Jump-index coverage includes a partially retained outgoing group.

The debug APK builds successfully. Baseline and highspec each pass 121 app tests with zero failures/errors/skips; Library passes 22 and Player passes 20. Two obsolete mosaic-generation tests were removed together with their unused implementation. Screenshot regression fixtures were updated for the intended styling; the dark Library fixture now explicitly paints the theme background rather than the test window default.

Local logs: ignored `artifacts/consistent-ui-fast.log`, `artifacts/consistent-ui-record.log`, and `artifacts/consistent-ui-gates.log`. JVM screenshot fixtures are regression evidence only.

## Physical debug acceptance completed

Connected to the supplied wireless endpoint and installed the highspec debug APK alongside the normal release on the Pixel 10 Pro XL (API 37, portrait, existing display/font settings). Seeded only the separate debug app with the 40-video fixture library. The normal populated app was not updated, cleared or seeded.

The first native recording revealed a detached footer strip: combining whole-row upward translation with height contraction brought lower episode controls into view. Removed that extra translation. Full rows now contract without exposing their bottom controls; compact preview entry starts after 160 ms and reveals over the final 140 ms, while preview exit lasts 140 ms. The header remains the same composition and bounds.

Inspected the second native recording and its sampled frames through collapse, expansion and a rapid toggle pair. The corrected sequence no longer exposes the detached footer strip. The miniature artwork and header stay visually consistent in both states. A collapsed earlier group followed by a jump to the long-title group landed at the correct header; its wrapping title and separate collapse control remained readable. Settings and empty Player surfaces share the same neutral panel language. Player showed “Nothing queued yet” after both recording passes; fixture engagement remained 27/40 watched and 325 points.

Evidence is ignored under `artifacts/`: `continuous-debug-expanded.png`, `continuous-debug-collapsed.png`, `continuous-debug-motion.mp4` (first pass), `continuous-debug-motion-v2.mp4` (corrected pass), `continuous-debug-motion-v2-strip.png`, `continuous-debug-reversal-strip.png`, `continuous-debug-long-title.png`, `continuous-debug-settings.png`, `continuous-debug-player.png` and `continuous-debug-final.png`. Temporary device-side evidence files were removed. Debug notification permission was declined. The separate LookTube Debug install remains available for user iteration.

After the native correction, `verifyFast :app:assembleHighspecDebug` passed, followed by `verifyLocal -PskipManagedDevice=true verifyHighspec` with lint, screenshot verification and all 121 app tests per flavor passing. Logs: `artifacts/continuous-device-iteration.log` and `artifacts/continuous-device-gates.log`.

This confirms native layout and mechanics using fixture artwork at the phone's existing settings. It does not claim a shared-element morph of full video cards, native light-mode/2x-font acceptance, or final signed-release acceptance with production artwork. No signed release was requested or installed for this debug candidate.
