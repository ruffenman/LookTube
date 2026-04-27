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
- Giant Bomb site-content heuristics now live in one shared library so feed-title, show grouping, and topic rule changes have a single update point
- Library combines grouped browsing, collapsible section headers, full-header tap targets, clearer collapsed versus expanded header states, grouped headers that now keep one stable containing card in both states, a circular top-left expand/collapse affordance, subdued collapsed mosaic art with stacked compact child cards visible beneath the header card and surfacing date, duration, and title metadata, more vibrant expanded multi-episode rectangular mosaic art with a stable readable info plate, group-level watched/unwatched actions that sit above the divider and child list, sort/filter controls, a scroll-away overview panel above the episode list, a default-collapsed Library Config section, grouped-card containment that visually keeps headers and episodes together, rich video cards whose child episode art remains a single full thumbnail rather than inheriting the grouped mosaic treatment, show-completion visualization, a custom video-card progress bar that avoids ambiguous endpoint dots, and snappier jump navigation that respects visible section anchors without a full-screen jump-rail overlay
- Player uses a shared Media3 session/service model with fullscreen, resume support, cast routing, YouTube-style double-tap skip confirmation feedback, roomier fullscreen-landscape controls, a polished History menu that sizes to content with a chrome-aware max height, compact supporting copy, a hardened controller-layout customization path that does not crash on playback startup, a top-pinned player surface that keeps video and playback context together, launch and notification opens that can preselect a video without forcing autoplay, cast sessions that keep the standard transport controls visible without keeping the local device awake just because playback is remote, and an offline-caption card that keeps the selected engine plus progress visible by default while moving detailed caption metrics into an expandable clinical stats section
- Library and Player share one global Look Points badge in the top app bar, and the shell can show a small centered playback icon between the title and badge while playback is active; tapping that indicator routes to Player and re-syncs the selected video with the active playback session when possible
- the Settings surface keeps the copied feed URL visible and supports clearing synced cache while preserving that feed URL
- the app no longer seeds placeholder library items on first open or after clearing synced data; instead, Library shows a clean empty-state panel until a real feed sync succeeds
- true cold starts show a brief, professional LookTube intro overlay that summarizes videos published since the previous app open when available, otherwise rotates through generic LookTube messages via a shuffled deck, auto-dismisses with a short fade, can be skipped instantly with a tap, and does not replay when returning from background
- the Settings surface also shows offline caption model readiness, lets the user download the local caption model needed for provider-free caption generation, exposes a persistent toggle for automatically generating captions for newly discovered videos, lists videos with saved or partial caption data, and can clear all caption data or open an entry in Player without autoplay for inspection
- the Player surface can generate or regenerate captions for the selected video on-device, delete partial or completed caption data for that video, attach generated captions as explicit text tracks for local playback, and keep them available during cast handoff through explicit Cast subtitle mapping
- explicit same-video selections, launch or notification preselection opens, caption-data inspection opens from Settings, and stale remote-route recovery keep Player and cast session state aligned without forcing unwanted autoplay or leaving playback stuck on a dead remote route
- watched videos are worth 12 Look Points, app opens add 1 point once per local day, and the shell badge reflects the combined total without introducing show-completion bonus points
- newly discovered playable videos can auto-generate offline captions during manual sync or background refresh when the setting is enabled and the local model is ready, and background refresh promotes that work to a foreground-maintenance notification for better reliability while the app is backgrounded or the device is locked
- the default app build target stays on the lower-spec whisper.cpp caption path, while the opt-in `highspec` target defaults to Moonshine and still exposes Whisper.cpp as a compatibility fallback without regressing the baseline build
- the product remains explicitly feed-first and avoids unsupported website-login automation
- the main shell is covered by automated smoke validation and regular Ralph loop gates

## Next slice
Improve real-device local-caption runtime so the newly exposed caption-management flow is practical for regular use.

### Acceptance criteria
- caption generation for typical real-world videos is materially faster than the current observed slow-path baseline on the user's test devices
- runtime feedback remains grounded in measured throughput and real-device comparison between the baseline Whisper.cpp path and the higher-spec Moonshine path
- any additional speedups reuse the existing local-first caption pipeline and do not regress caption-data management, cast subtitle delivery, or background auto-caption behavior
- docs, tests, and validation continue to reflect the currently shipped feed-first product shape

## Captions
- LookTube now treats offline-first captions as a supported product behavior rather than a future-only direction.
- Settings exposes local caption model readiness and a download action for the built-in on-device English model, so captions can work without any external provider once the model is present.
- Settings also exposes caption-data management so users can inspect videos with saved or partial caption data in Player without autoplay and can clear all caption data from one place.
- Player exposes per-video on-device caption generation and regeneration, shows generation progress or errors, enables the built-in CC control for turning generated captions on locally, and can delete the current video's saved or partial caption data.
- Long-running on-device caption jobs keep extraction file-backed for memory safety, conservatively skip long silent stretches before transcription, and continue to report hard transcription progress during the transcription phase itself, including speech-processed time, chunk counts, measured throughput, per-chunk wall time, and ETA once enough work has completed to estimate it; Player defaults that data to a compact engine-plus-progress presentation and exposes the full metric set through an expandable stats section.
- The default build target keeps whisper.cpp as the guaranteed local fallback, and the `highspec` target defaults to Moonshine on compatible devices while still exposing Whisper.cpp as a compatibility fallback.
- Generated captions are stored as per-video WebVTT sidecars instead of mutating feed-derived metadata.
- Cast delivery treats captions as first-class text tracks through explicit Cast mapping and sender-hosted sidecar serving, rather than assuming default Media3 subtitle propagation is sufficient.
- If a higher-quality or cloud-generated caption option is added later, keep any secondary credentials or account material in a clearly separate expandable Settings section and layer that provider on top of the existing local caption pipeline.

## Notification functional targets
- Scheduling target: a saved non-blank feed URL results in one active periodic library refresh registration owned by WorkManager plus an opportunistic immediate catch-up refresh when scheduling is refreshed and a rolling delayed catch-up fallback that is maintained after background refresh runs; clearing the saved feed URL removes all of that background work.
- Detection target: background refresh compares the latest synced snapshot only against the previously persisted snapshot for the same feed URL, using video IDs as the change detector.
- Silence target: no library-update notification is posted for first-time sync, empty snapshots, feed switches, unchanged snapshots, or when notification permission is unavailable.
- Visibility target: each successful refresh that discovers at least one new video posts a distinct `Library updates` notification entry that opens the newest discovered video in the player for review when tapped without forcing autoplay.
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
