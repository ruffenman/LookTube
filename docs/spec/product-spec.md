# LookTube product spec
## Problem
Premium Giant Bomb subscribers should be able to open an Android app, authenticate, browse Premium video surfaces, and start playback without navigating the Giant Bomb website in a browser.

## Goals
- Authenticate a Premium user with the smallest secure flow that reliably unlocks Premium video access.
- Show a compact, useful library view centered on Premium videos and fast resume behavior.
- Launch stable playback with progress persistence and clear recovery when the session expires.
- Keep development spec-driven, fixture-driven, and easy to validate locally.

## Non-goals for the initial Android scope
- iOS support
- community/wiki browsing
- downloads or offline playback before streaming is stable
- generalized Giant Bomb site parity

## Phase 0 slice
Build the Android foundation, doc system, repository seams, validation commands, and feed parsing spike harness.

### Acceptance criteria
- the repository contains a buildable Android app shell with modular boundaries aligned to the architecture docs
- `verifyFast` runs fixture-driven tests plus doc checks
- `verifyLocal` adds lint and a managed-device smoke lane
- the app exposes an auth spike screen, a library baseline screen, a player placeholder, and a diagnostics/settings surface
- Giant Bomb integration assumptions and open questions are documented in one maintained place

## Phase 1 slice
Validate the first real Premium-capable integration path and prove an authenticated video can move from library to player.

### Acceptance criteria
- one chosen auth mode is documented and justified
- the app can bootstrap authenticated library data from a real Giant Bomb Premium-capable surface
- selecting a library item reaches a playable screen with the real media handoff seam in place
- failures for expired credentials or missing access are visible to the user

## Phase 2 slice
Improve daily usability for a Premium subscriber.

### Acceptance criteria
- latest Premium videos and continue-watching are available from the home or library surface
- playback resume state persists across app restarts
- the main navigation flow is covered by automated smoke coverage
- docs and ADRs reflect the validated architecture, not earlier assumptions

## Ralph loop definition
1. pick the thinnest user-visible slice from this spec
2. update or add acceptance criteria first
3. add the smallest failing automated check that proves the slice is incomplete
4. implement the smallest change that makes the check pass
5. run `verifyFast`
6. update docs and learnings while context is fresh
7. run `verifyLocal` before merging the slice

## Open questions
- Is a direct credentialed-feed path enough for the first usable release, or does Giant Bomb Premium video require a browser-backed session flow?
- Which exact Giant Bomb feed surface should become the first supported library view?
- When real playback is wired, what minimum Media3 configuration is required for background and resume behavior?
