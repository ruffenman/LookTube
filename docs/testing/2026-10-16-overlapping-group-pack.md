# Overlapping group pack — 2026-10-16

## Behavior

The unified header contains Mark all watched/unwatched in both states; the separate action strip is removed. It draws above the first expanded episode, which tucks 24 dp underneath; the episode badge is inset below that overlap. Collapsed cards tuck 78 dp of their 100-dp faces under the header, exposing 22-dp stepped artwork/border edges without cropped preview text. Up to four cards participate. Header identity, individually lazy episode keys and jump-index accounting remain unchanged.

The layout modifier changes the row's allocated height while placing its face upward. Animated clipping permits the tucked top region to draw behind the header and clips the contracting lower edge. Header layering retains input ownership over the overlap; compact peeks remain decorative. This is overlap and contraction, not a shared-element morph of every full card.

## Automated acceptance

`verifyFast recordScreenshots :app:assembleHighspecDebug` passed, with four intended Library screenshot updates. The combined full local-CI lane passed at `artifacts/local-ci/20261016t200450z-49191a2d`: both flavors' unit tests/lint/screenshots, all four debug app/test APK builds, identity checks and runner tests. App suites retain 121 tests per flavor with zero failures/errors/skips.

The native lane passed on the explicitly selected Pixel 10 Pro XL / API 37 at `artifacts/local-ci/20261016t200716z-206f8828`: three tests, 9.539 seconds, all five screenshots retrieved. Added assertions check that the group watch action's bounds lie inside the header, the first episode begins above the header's bottom edge, and the compact front/rear card bounds genuinely overlap and reveal staggered edges. Actual lower-edge header taps through expansion and collapse must toggle only the group and never select a video. Existing reversal, scrolling/Top persistence, outside dismissal, lazy composition and constrained 320-dp/2x-text checks remain passing.

Reports record the source base and dirty-worktree status honestly; these are local candidate results, not hosted-CI or signed-release claims. Transfer package YAML/TOML agree on 0.2.27.

## Physical visual review

Installed the debug candidate alongside the normal highspec release. On the existing 40-video debug fixture library, captured expanded and collapsed Grubbsnax Premium states and inspected a native recording of collapse, expansion and rapid reversal. The watch action is inside the filled header; the first episode runs under its rounded lower edge, and collapsed colored card strips visibly peek out under it. The badge remains visible below the expanded overlap. Fixture progress stayed 27/40 watched and 325 points. No group watch action or video playback was started during the manual review.

Ignored evidence: `artifacts/stack-overlap-phone-expanded.png`, `stack-overlap-phone-collapsed.png`, `stack-overlap-phone-motion.mp4`, `stack-overlap-phone-motion-strip.png`, `stack-overlap-phone-return-strip.png` and `stack-overlap-phone-final.png`. The separate debug app is left on the collapsed pack for user review. Temporary device-side manual evidence files were removed; run-scoped native CI evidence remains in the debug app's files as documented. The normal release and its account data were not changed.

Native visual review covers portrait/dark mode with fixture artwork at the phone's existing settings; automated native coverage also exercises an in-app 320-dp/2x-font constraint. Light-mode JVM snapshots remain regression checks, not physical light-mode acceptance. Final signed normal-app acceptance and production artwork are separate from this debug iteration.
