# LookTube package reference
## Purpose
This reference document accompanies the transfer-ready spec package for LookTube. It adds domain context and cross-cutting behavioral notes without binding future implementations to this repository's Kotlin structure.
## Product shape
LookTube is a feed-first Android client for Giant Bomb Premium videos.
Its central promise is deliberately narrow:
- paste a copied Premium RSS feed URL
- sync directly against that feed
- browse the resulting Premium library
- resume playback
- generate local captions for a playable video once the on-device model is available
- receive local new-release notifications from background refresh
## Explicit non-goals
- browser-login automation
- cookie harvesting
- generalized Giant Bomb site parity
- server-push notification infrastructure
- offline downloads in the current scope
## User-visible surfaces
### App shell
- keeps the standard top app bar and bottom navigation visible outside fullscreen playback
- may show a short, cold-start-only LookTube intro overlay that summarizes videos published since the previous app open when available, otherwise rotates through generic product messages, auto-dismisses with a short fade, can be skipped immediately with any tap, and does not replay on background resume
- exposes one global Look Points badge in the top app bar on Library and Player so score remains visible without being embedded into page-local control rows, and can show a small centered icon-only playback indicator while playback is active that routes straight to Player when tapped
### Settings
- sole user input for access is the copied Premium feed URL
- communicates setup, ready, syncing, synced, and error-adjacent states
- can clear synced data while preserving the saved feed URL
- exposes offline caption model readiness, engine selection where the higher-spec target supports it, a local-model download action, a persistent toggle for automatically generating captions for newly discovered videos, and caption-data inspection/clear actions without mixing any future provider credentials into the main feed URL flow
### Library
- combines status, grouping, sorting, filtering, jump navigation, and top-level progress context in one browse surface
- uses a clean empty-state panel before the first successful sync or after clearing synced data, instead of seeded placeholder library items
- wraps status plus browse controls inside a default-collapsed Library Config element that sits above the scrolling episode list
- grouped headers can collapse or expand independently with compact circular icon-based expand/collapse controls, allow the entire header to toggle expansion, keep dimmed art visible while collapsed with stacked compact child cards from later videos remaining visible beneath the header and surfacing date, duration, and title metadata, use a subdued neutral collapsed style and a content-derived expanded full-card rectangular frame-tile mosaic with a tighter readable bottom info plate that stays visually separated from the affordance, expose one whole-group watched-state toggle at a time above the divider and child list, keep overview controls for expanding or collapsing all groups, render as containing cards, and visually nest their episode cards beneath the owning header
- shows watched-versus-total progress for shows while Look Points scoring remains available from the global shell badge and reflects watched videos plus one daily app-open point without adding show-completion bonus points
- uses explicit `Mark as Watched` and `Mark as Unwatched` wording for manual watch-state actions
- should remain usable and informative even before a successful live sync by showing an intentional empty-state treatment and next-step guidance
### Player
- must handle empty, unavailable, preparing, and active playback states clearly
- keeps the player frame above the supporting metadata when a video is selected
- supports resume, cast routing, fullscreen from controls/rotation, left/right double-tap seek behavior, a bounded surfaced History menu that sizes to content with a chrome-aware cap, manual watched/unwatched actions, per-video offline caption generation, and per-video caption-data deletion with standard subtitle controls
- should still start local playback if the user locks the device immediately after issuing the play request
- explains remote playback on the player surface with a non-blocking visual indicator and should recover local playback cleanly after cast-session loss, reconnect, or same-video reselection
## Captions
- the supported caption path is offline-first and on-device once the local caption model has been downloaded
- the default build keeps a lower-spec local caption engine available, while a higher-spec target may default to a faster local engine without removing that fallback
- long-running local caption generation should keep extraction file-backed for memory safety, conservatively focus local transcription on speech-heavy spans, and continue surfacing hard progress during transcription itself, including speech-processed duration and chunk-level completion, so multi-hour jobs do not look stalled or opaque
- generated captions should be stored as explicit WebVTT sidecars keyed by video rather than folded back into feed-derived source metadata
- local playback should expose generated captions through the standard subtitle controls on the player surface
- cast delivery should treat captions as an explicit sender-managed text-track problem and keep local sidecars reachable to the receiver, rather than assuming default Media3 cast subtitle propagation is sufficient
- any future cloud or provider-backed caption option should remain optional and separate from the primary feed URL flow
## External integration stance
The copied feed URL is the validated access path.
Future implementations should not assume a browser session, cookies, or a secondary private API unless Giant Bomb exposes an official supported path.
## Background behavior
- background refresh exists only while a non-blank saved feed URL remains present
- initial sync is silent
- later successful refreshes may notify only when newly discovered video IDs appear for the same feed URL
- when the persistent auto-caption setting is enabled and the local model is ready, those newly discovered playable videos may also be captioned automatically during sync or background refresh
- long-running background auto-caption work may run as foreground-maintenance work for better reliability while the app is backgrounded or the device is locked
- notification timing is best-effort, not exact-time guaranteed
## Validation posture
The source repository validates behavior with unit tests, visual tests, smoke tests, and opt-in live probes.
Future implementations may use different tooling, but should preserve the same externally visible behavior and constraints described in the package spec.
