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
- copied Premium RSS feed URLs can be configured and synced from the Settings surface
- copied feed URLs are protected at rest
- the app persists the last successful synced library, saved playback progress, recent playback history, and watched/unwatched engagement state
- Giant Bomb site-content heuristics now live in one shared library so feed-title, grouping, cast, and topic rule changes have a single update point
- Library combines grouped browsing, collapsible section headers, full-header tap targets, clearer collapsed versus expanded header states, group-level watched/unwatched actions, sort/filter controls, a scroll-away overview panel above the episode list, a default-collapsed Library Config section, grouped-card containment that visually keeps headers and episodes together, rich video cards, show-completion visualization, and snappier jump navigation that respects visible section anchors without a full-screen jump-rail overlay
- Player uses a shared Media3 session/service model with fullscreen, resume support, cast routing, a polished History menu that sizes to content with a chrome-aware max height, compact supporting copy, and a top-pinned player surface that keeps video and playback context together
- Library and Player share one global Look Points badge in the top app bar, and the shell can show a compact `Playing` indicator between the title and badge while playback is active
- the Settings surface keeps the copied feed URL visible and supports clearing synced cache while preserving that feed URL
- the app no longer seeds placeholder library items on first open or after clearing synced data; instead, Library shows a clean empty-state panel until a real feed sync succeeds
- true cold starts show a brief, professional LookTube intro overlay that auto-dismisses after about two seconds, can be skipped instantly with a tap, and does not replay when returning from background
- the Settings surface also shows offline caption model readiness, lets the user download the local caption model needed for provider-free caption generation, and exposes a persistent toggle for automatically generating captions for newly discovered videos
- the Player surface can generate or regenerate captions for the selected video on-device, attach them as explicit text tracks for local playback, and keep them available during cast handoff through explicit Cast subtitle mapping
- watched videos are worth 12 Look Points, app opens add 1 point once per local day, and the shell badge reflects the combined total without introducing show-completion bonus points
- newly discovered playable videos can auto-generate offline captions during manual sync or background refresh when the setting is enabled and the local model is ready, and background refresh promotes that work to a foreground-maintenance notification for better reliability while the app is backgrounded or the device is locked
- the default app build target stays on the lower-spec whisper.cpp caption path, while the opt-in higher-spec Moonshine target defaults to Moonshine and still exposes Whisper.cpp as a compatibility fallback without regressing the baseline build
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
- the Library status and browsing controls remain in a collapsible `Library Config` element inside the overview panel above the episode list, that config element is collapsed by default, the overview panel can scroll off screen, and the jump rail anchors to the episode-list panel rather than overlapping the overview panel
- when grouped browsing is enabled, each section header can collapse or expand its own episodes without resetting scroll state, and overview controls expose explicit expand-all and collapse-all actions
- the app shell keeps one global Look Points badge in the top right next to the LookTube header on Library and Player while shell chrome is visible, shows a compact `Playing` indicator between the title and badge when playback is active, and does not embed Look Points inside Library or Player-local controls
- active show-filter feedback remains adjacent to the show-filter controls so library state is readable from one part of the overview panel
- library cards expose key per-video metadata, watched-state actions, and an explicit full-info affordance so stored video details remain inspectable even when descriptions are short
- watched-state actions use explicit `Mark as Watched` and `Mark as Unwatched` phrasing throughout the app
- watched videos are worth 12 Look Points, opening the app adds 1 point once per local day, the global header badge uses the app's gold/yellow accent outline treatment, and show completion remains a visual progress signal rather than a score bonus
- grouped section headers avoid redundant tap-instruction copy, allow tapping anywhere on the header to toggle expansion, use a clearly different visual treatment when collapsed versus expanded, keep the expand/collapse affordance in the top left of the group card, place the title and group info beneath that affordance so the control can hug the left edge without pushing text to the right, surface watched-versus-total completion status, and keep the single watched-state toggle directly above the child video list so fully watched shows are easy to spot and update without duplicated opposing buttons
- expanded grouped sections render as containing cards so the header and its videos read as one collapsible element
- any manual watched-state control in the app exposes only the next valid toggle action for its current state rather than parallel watched and unwatched buttons for the same item or group
- selecting a video with saved playback progress resumes from that stored point reliably, including after app reloads where the bookmark state and player controller restore asynchronously
- the Player tab keeps the player frame visible at the top when a video is opened from Library so the active surface stays in view above the supporting metadata
- rotating into and out of fullscreen keeps the same active video and playback session instead of pausing, restarting, or reverting to a previously played item
- when playback is remote, the Player surface clearly explains that video is casting and keeps normal playback controls available instead of presenting an unexplained black frame
- the compact remote-playback indicator is purely visual, does not intercept player input, and the player shows a single cast route control that stays visually in step with the standard controls
- explicit same-video selections, cast-session reconnects after app resume or screen lock, and recovery from cast-session loss restore or reload playback without spuriously restarting an already-active cast session or leaving the player in an idle black-screen state
- selecting a playable video starts playback reliably even if the device is locked immediately after the selection or play action, without requiring the user to wait for visible playback first
- player interactions follow a YouTube-like model where double taps on the left or right half of the video seek backward or forward 10 seconds, fullscreen remains available from the fullscreen control or device rotation, and next/previous controls are omitted because the app does not define an implicit queue
- the active Player surface uses compact supporting text beneath the frame instead of a redundant Playback Details card, shows a `History` affordance with a bounded surfaced dropdown that sizes to content and caps at about two-thirds of the usable header-to-footer space plus scroll feedback for long lists, and keeps unreliable per-item Premium yes/no presentation out of the user-facing contract unless a stable feed signal is validated later
- the Library jump rail fades in quickly when scrolling starts, fades back out about 0.2 seconds after passive scroll/touch input stops, and only keeps a longer linger after an explicit jump-rail selection so section jumping feels responsive without lingering on screen
- Settings, Library, and Player keep a consistent card/header/panel treatment so the main app surfaces feel visually coherent without changing the existing LookTube design language
- screenshot-oriented visual regression coverage is added for the now-stable browse/player experience
- saved feed URLs remain protected at rest
- users can clear synced cache without re-entering the copied feed URL, while local playback progress/history/watch state tied to the synced library is cleared with that cache reset
- users can download an on-device caption model from Settings, generate captions for a playable video from Player without configuring any external provider, and regenerate them later if needed
- Settings exposes a persistent toggle for automatically generating captions for newly discovered videos
- generated captions are stored locally as per-video WebVTT sidecars, surfaced through the player CC controls, and kept reachable for cast sessions through explicit Cast text-track propagation instead of relying on the default Media3 cast subtitle path
- when that toggle is enabled and the local caption model is ready, newly discovered playable videos found during manual sync or background refresh automatically generate captions if no local sidecar already exists
- background refresh bootstraps repository state before syncing and promotes long-running auto-caption work to a foreground-maintenance notification for better reliability while the app is backgrounded or the device is locked
- any future higher-quality or cloud-backed caption provider remains optional and must layer onto the same caption pipeline instead of replacing the local fallback
- the repository keeps the baseline build as the default validation target, while an opt-in higher-spec Moonshine build target can raise SDK or ABI expectations for extra local-engine support, default to Moonshine, and still keep Whisper.cpp available as a compatibility fallback without weakening the baseline support matrix
- docs reflect the validated product shape instead of the earlier scaffold-only phases

## Captions
- LookTube now treats offline-first captions as a supported product behavior rather than a future-only direction.
- Settings exposes local caption model readiness and a download action for the built-in on-device English model, so captions can work without any external provider once the model is present.
- Player exposes per-video on-device caption generation and regeneration, shows generation progress or errors, and enables the built-in CC control for turning generated captions on locally.
- Long-running on-device caption jobs continue to report hard transcription progress during the transcription phase itself, including processed audio time, chunk counts, measured throughput, per-chunk wall time, and ETA once enough work has completed to estimate it.
- The default build target keeps whisper.cpp as the guaranteed local fallback, and the Moonshine-capable target defaults to Moonshine on compatible devices while still exposing Whisper.cpp as a compatibility fallback.
- Generated captions are stored as per-video WebVTT sidecars instead of mutating feed-derived metadata.
- Cast delivery treats captions as first-class text tracks through explicit Cast mapping and sender-hosted sidecar serving, rather than assuming default Media3 subtitle propagation is sufficient.
- If a higher-quality or cloud-generated caption option is added later, keep any secondary credentials or account material in a clearly separate expandable Settings section and layer that provider on top of the existing local caption pipeline.

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
