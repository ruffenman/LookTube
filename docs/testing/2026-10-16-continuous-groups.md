# Continuous groups and shared surfaces — 2026-10-16

## Candidate scope

A separate debug candidate replaces the 0.1.9 group-header swap with one persistent header in both states. Episode rows slide/contract into the group and reveal in reverse over 300 ms. Only episode rows measured at the collapse tap remain for the exit; later offscreen episodes cannot enter solely to animate out. Transient render counts also drive jump indices. Rapid toggles cancel the retention cleanup and reverse the existing transition. Playback and watch mutations on outgoing rows are ignored.

The group retains the same title, count, miniature artwork and explicit control throughout. Collapsed episode previews use the existing staggered stack; the unrelated mosaic hero is removed. Settings, Library and Player share neutral opaque panel colors, 24-dp outer corners, 16-dp inset corners and thin borders. Both themes explicitly define all Material surface-container tones. Semantic status colors and thumbnail text overlays remain separate.

## Validation

Automated coverage includes the existing native-touch dispatch, group-watch scoping, 320-dp/2x-font, collapse persistence, edge-swipe, jump-menu and offscreen-composition checks. A controlled Compose-clock regression verifies that the header bounds remain identical during contraction and reversal, an outgoing episode remains during the transition, the last episode does not enter, and the episode disappears after collapse and returns after expansion. Jump-index coverage includes a partially retained outgoing group.

The debug APK builds successfully. Baseline and highspec each pass 121 app tests with zero failures/errors/skips; Library passes 22 and Player passes 20. Two obsolete mosaic-generation tests were removed together with their unused implementation. Screenshot regression fixtures were updated for the intended styling; the dark Library fixture now explicitly paints the theme background rather than the test window default.

Local logs: ignored `artifacts/consistent-ui-fast.log`, `artifacts/consistent-ui-record.log`, and `artifacts/consistent-ui-gates.log`. JVM screenshot fixtures are regression evidence only.

## Physical acceptance pending

At this checkpoint ADB lists no connected phone, mDNS finds no service and the previously supplied endpoint refuses the connection. A wireless-debugging/unlock request is pending. Do not treat automated screenshots as native animation acceptance. Deploy the highspec debug APK and seed the isolated fixture app when the phone reconnects, then capture and inspect collapse, reversal, expansion, long titles and Settings/Library/Player surfaces. Keep the normal populated release and its data intact. No signed release is requested or installed for this candidate.
