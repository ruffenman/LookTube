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
- receive local new-release notifications from background refresh
## Explicit non-goals
- browser-login automation
- cookie harvesting
- generalized Giant Bomb site parity
- server-push notification infrastructure
- offline downloads in the current scope
## User-visible surfaces
### Auth
- sole user input for access is the copied Premium feed URL
- communicates setup, ready, syncing, synced, and error-adjacent states
- can clear synced data while preserving the saved feed URL
### Library
- combines status, grouping, sorting, filtering, and jump navigation in one browse surface
- grouped headers can collapse or expand independently, with overview controls for expanding or collapsing all groups
- shows watched-versus-total progress for shows and a compact Look Points summary that scores watched videos only
- uses explicit `Mark as Watched` and `Mark as Unwatched` wording for manual watch-state actions
- should remain usable and informative even before a successful live sync
### Player
- must handle empty, unavailable, preparing, and active playback states clearly
- keeps the player frame above the supporting metadata when a video is selected
- supports resume, cast routing, fullscreen from controls/rotation, left/right double-tap seek behavior, recent-play history, and manual watched/unwatched actions
- outlines Look Points UI with the gold/yellow accent treatment used by the app theme
- explains remote playback on the player surface with a non-blocking visual indicator and should recover local playback cleanly after cast-session loss, reconnect, or same-video reselection
## External integration stance
The copied feed URL is the validated access path.
Future implementations should not assume a browser session, cookies, or a secondary private API unless Giant Bomb exposes an official supported path.
## Background behavior
- background refresh exists only while a non-blank saved feed URL remains present
- initial sync is silent
- later successful refreshes may notify only when newly discovered video IDs appear for the same feed URL
- notification timing is best-effort, not exact-time guaranteed
## Validation posture
The source repository validates behavior with unit tests, visual tests, smoke tests, and opt-in live probes.
Future implementations may use different tooling, but should preserve the same externally visible behavior and constraints described in the package spec.
