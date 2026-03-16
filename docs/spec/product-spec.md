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

## Current validated state
The current app already covers a substantial first-use slice for a Premium subscriber:
- copied Premium RSS feed URLs can be configured and synced from the Auth surface
- copied feed identity and optional remembered basic-auth passwords are protected at rest
- the app persists the last successful synced library and saved playback progress
- Library combines grouped browsing, sort/filter controls, rich video cards, and jump navigation
- Player uses a shared Media3 session/service model with fullscreen and resume support
- the Auth surface now separates clearing synced cache from forgetting saved credentials while preserving the copied feed URL
- the main shell is covered by automated smoke validation and regular Ralph loop gates

## Next slice
Harden the path from copied feed sync to daily repeat use.

### Acceptance criteria
- copied-feed sync remains the primary user-facing path unless a more robust Premium session flow proves necessary
- playback and background/session behavior remain stable across more real Giant Bomb feed variants
- browse ergonomics continue improving from device feedback without regressing the grouped-library model
- screenshot-oriented visual regression coverage is added for the now-stable browse/player experience
- saved feed identity and any opted-in remembered password remain protected at rest
- users can clear synced cache separately from forgetting saved credentials
- docs reflect the validated product shape instead of the earlier scaffold-only phases

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
- Which additional Giant Bomb feed or page metadata should be folded into show-grouping heuristics?
- What is the lightest-weight visual regression strategy that fits the current Android/Compose workflow?
