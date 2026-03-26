# LookTube product spec
## Problem
Premium Giant Bomb subscribers should be able to open an Android app, paste a copied Premium feed URL, browse Premium video surfaces, and start playback without navigating the Giant Bomb website in a browser.

## Goals
- Support the smallest secure feed-first flow that reliably unlocks Premium video access.
- Show a compact, useful library view centered on Premium videos and fast resume behavior.
- Launch stable playback with progress persistence and clear recovery when the session expires.
- Surface new-release notifications reliably enough that repeat users can trust background refresh.
- Keep development spec-driven, fixture-driven, and easy to validate locally.

## Spec maintenance rule
- This document is the live product/design contract for the repository.
- Any change to user-visible behavior, UX flow, interaction model, navigation, supported scope, or validation expectations must update this spec in the same implementation slice.
- Work is not complete if the code and the current design diverge from this spec.
- When a change also affects the transferable behavior contract, update `docs/spec/reproducible-project-spec.md` and `docs/spec/agent-spec-package/` in the same slice.

## Non-goals for the initial Android scope
- iOS support
- community/wiki browsing
- downloads or offline playback before streaming is stable
- generalized Giant Bomb site parity

## Current validated state
The current app already covers a substantial first-use slice for a Premium subscriber:
- copied Premium RSS feed URLs can be configured and synced from the Auth surface
- copied feed URLs are protected at rest
- the app persists the last successful synced library and saved playback progress
- Giant Bomb site-content heuristics now live in one shared library so feed-title, grouping, cast, and topic rule changes have a single update point
- Library combines grouped browsing, collapsible section headers, expand/collapse-all controls, sort/filter controls, a scroll-away overview panel above the episode list, rich video cards, and jump navigation that respects visible section anchors
- Player uses a shared Media3 session/service model with fullscreen, resume support, cast routing, and a top-pinned player surface that keeps video and playback context together
- the Auth surface keeps the copied feed URL visible and supports clearing synced cache while preserving that feed URL
- the product remains explicitly feed-first and avoids unsupported website-login automation
- the main shell is covered by automated smoke validation and regular Ralph loop gates

## Next slice
Harden the path from copied feed sync to daily repeat use.

### Acceptance criteria
- copied-feed sync remains the only supported user-facing path unless Giant Bomb publishes an official broader integration path
- playback and background/session behavior remain stable across more real Giant Bomb feed variants
- background refresh stays scheduled while a non-blank feed URL is saved and is cancelled when the saved feed URL is cleared
- the initial successful sync for a feed URL remains silent; notifications start only when later successful refreshes discover previously unseen video IDs for that same feed URL
- each successful new-release detection posts a user-visible library-update notification instead of silently overwriting the earlier one
- notification posting remains a best-effort WorkManager flow rather than an exact-time delivery guarantee, but repeated detections must remain observable in testing and in the system notification tray
- browse ergonomics continue improving from device feedback without regressing the grouped-library model
- the chosen sort mode applies consistently to flat episode lists, grouped section ordering, and episode ordering within each visible group
- the Library status and settings remain in an overview panel above the episode list, the overview panel can scroll off screen, and the jump rail anchors to the episode-list panel rather than overlapping the overview panel
- when grouped browsing is enabled, each section header can collapse or expand its own episodes without resetting scroll state, and overview controls expose explicit expand-all and collapse-all actions
- active show-filter feedback remains adjacent to the show-filter controls so library state is readable from one part of the overview panel
- library cards expose key per-video metadata and an explicit full-info affordance so stored video details remain inspectable even when descriptions are short
- selecting a video with saved playback progress resumes from that stored point reliably, including after app reloads where the bookmark state and player controller restore asynchronously
- the Player tab keeps the player frame visible at the top when a video is opened from Library so the active surface stays in view above the supporting metadata
- when playback is remote, the Player surface clearly explains that video is casting and keeps normal playback controls available instead of presenting an unexplained black frame
- the compact remote-playback indicator is purely visual, does not intercept player input, and the cast route control stays visually in step with the standard player controls when remote playback is not active
- explicit same-video selections, cast-session reconnects after app resume or screen lock, and recovery from cast-session loss restore or reload playback without spuriously restarting an already-active cast session or leaving the player in an idle black-screen state
- player interactions follow a YouTube-like model where double taps on the left or right half of the video seek backward or forward 10 seconds, while fullscreen remains available from the fullscreen control or device rotation
- Playback Details show resume and handoff information only; unreliable per-item Premium yes/no presentation is not part of the user-facing player contract unless a stable feed signal is validated later
- Auth, Library, and Player keep a consistent card/header/panel treatment so the main app surfaces feel visually coherent without changing the existing LookTube design language
- screenshot-oriented visual regression coverage is added for the now-stable browse/player experience
- saved feed URLs remain protected at rest
- users can clear synced cache without re-entering the copied feed URL
- docs reflect the validated product shape instead of the earlier scaffold-only phases

## Notification functional targets
- Scheduling target: a saved non-blank feed URL results in one active periodic library refresh registration owned by WorkManager; clearing the saved feed URL removes that registration.
- Detection target: background refresh compares the latest synced snapshot only against the previously persisted snapshot for the same feed URL, using video IDs as the change detector.
- Silence target: no library-update notification is posted for first-time sync, empty snapshots, feed switches, unchanged snapshots, or when notification permission is unavailable.
- Visibility target: each successful refresh that discovers at least one new video posts a distinct `Library updates` notification entry that opens the newest discovered video in the player when tapped.
- Device-validation target: notification permission granted, a scheduled `LibraryRefreshWorker` job visible on-device, and repeated notification entries observable from the same installed build when distinct latest video IDs are posted.

## Ralph loop definition
1. pick the thinnest user-visible slice from this spec
2. update or add acceptance criteria first, including any spec changes required by the new design
3. add the smallest failing automated check that proves the slice is incomplete
4. implement the smallest change that makes the check pass
5. run `verifyFast`
6. update docs, transferable specs when applicable, and learnings while context is fresh
7. run `verifyLocal` before merging the slice

## Open questions
- Does the validated copied-feed path hold across additional real Premium feed variants?
- Which additional Giant Bomb feed or page metadata should be folded into show-grouping heuristics?
- What is the lightest-weight visual regression strategy that fits the current Android/Compose workflow?
- How aggressively do Android standby quotas delay WorkManager execution for LookTube on real daily-driver devices that have not opened the app recently?
