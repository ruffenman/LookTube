# LookTube reproducible project spec
## Purpose
LookTube is a native Android companion app for Giant Bomb Premium subscribers. Its primary job is to let a user paste a copied Giant Bomb Premium RSS feed URL, sync the feed directly, browse the resulting library, resume playback, and receive local notifications when later background refreshes discover newly released videos.
## Transfer-ready package
The upload-oriented package now lives in `docs/spec/agent-spec-package/`.
Use that directory as the starting point for future publication to the external agent-spec repository.
This markdown document remains the repo-grounded narrative reference, while the package captures a more implementation-agnostic behavior contract.
The package now includes:
- `spec.yaml` for types, contract, scenarios, and constraints
- `metadata.toml` for discovery metadata
- `references/architecture.md` for supporting descriptive context that stays behavior-first rather than implementation-binding

## Product boundary
The supported product is intentionally feed-first:
- input is a copied Giant Bomb Premium feed URL
- sync reads that feed URL directly
- playback uses URLs exposed by the feed items
- background notifications derive from local snapshot comparison after successful refresh

The app does not depend on browser automation, cookie harvesting, or unsupported website sign-in replay.

## Supported implementation shape
- platform: Android-first, Kotlin, Jetpack Compose
- shell: one `app` module that owns app wiring, navigation, background work, and playback service integration
- shared layers: `core:heuristics`, `core:model`, `core:data`, `core:database`, `core:network`, `core:designsystem`, `core:testing`
- feature layers: `feature:auth`, `feature:library`, `feature:player`
- background work: WorkManager periodic refresh
- playback engine: Media3 session/service model

An implementation that materially changes these choices can still be valid, but it must preserve the functional behavior and constraints below.

## External dependency contract
- Giant Bomb feed access is treated as the only validated integration surface
- the copied feed URL may contain the access material required for feed sync
- playback URLs are taken from feed item fields in feed-first order: `media:content`, then `enclosure`, then `link`
- no live Giant Bomb credentials, cookies, or raw authenticated payloads are committed

## Core user flows
### First-use setup
1. User opens the app.
2. User pastes a copied Giant Bomb Premium feed URL.
3. App persists that feed URL locally with encrypted-at-rest protection where available.
4. User triggers sync.
5. App loads feed items into the library and exposes them for browsing and playback.

### Daily repeat use
1. App restores the saved feed URL, cached library snapshot, and playback progress.
2. User can browse and resume without re-entering the feed URL.
3. WorkManager keeps periodic background refresh active while a non-blank feed URL remains saved.
4. If a later successful refresh finds previously unseen video IDs for the same feed URL, the app posts a local notification that opens the newest discovered video.

### Clear synced data
1. User clears synced data.
2. App removes cached library snapshot and playback progress.
3. App preserves the saved feed URL so the next sync stays cheap.
## Surface inventory
### App shell
- three top-level destinations: `Auth`, `Library`, and `Player`
- app opens on the Auth page by default
- top app bar and bottom navigation stay visible outside player fullscreen mode
- notification launch intents can route directly to the Player page and optionally preselect a video

### Auth surface
- accepts the copied Premium feed URL as the only supported user input for feed access
- exposes a primary sync action
- exposes `Clear synced data` only when a successful sync or cached summary exists
- communicates five practical user-visible states: setup required, ready, syncing, synced, and needs attention

### Library surface
- renders an overview panel above the scrolling episode list using the current sync state and last successful summary
- supports grouping by none, show, cast, or topic
- supports sorting by latest, show, or oldest
- supports show filtering with the filter tray collapsed by default
- applies the chosen sort mode consistently to flat lists, grouped section ordering, and episode ordering within each visible group
- renders grouped section headers, progress-aware video cards, and a right-side jump rail that anchors to the episode-list panel for quick section navigation
- exposes key per-video metadata on cards and an explicit full-info affordance for inspecting each video's stored details
- opens the selected video in the Player surface

### Player surface
- shows clear empty, unavailable, preparing, and active playback states
- resumes playback from saved progress when available
- supports fullscreen entry from the player control or double tap
- supports landscape-driven fullscreen behavior
- exposes a cast route button while player controls are visible

## Functional targets
### Feed configuration and persistence
- A saved feed URL survives app restarts.
- Secure persistence is preferred; plaintext fallback, if required, stores only the feed URL.
- Clearing synced data does not clear the saved feed URL.

### Sync and library state
- Blank feed URLs do not start a successful sync.
- Successful sync replaces the active library with feed-backed items.
- If no successful feed snapshot is available, the app may use seeded fallback data so the shell remains usable and testable.
- Library sorting semantics stay consistent across grouped and ungrouped browsing: latest and oldest are chronological, while show ordering is alphabetical by group/show with newest episodes first within a show.
- The library overview/status/settings panel remains visually separate from the scrolling episode list, can scroll off screen before the episode list takes over the viewport, and the jump rail does not overlap that overview panel.

### Background refresh and notifications
- Saving a non-blank feed URL results in one active periodic background refresh registration.
- Clearing the saved feed URL cancels that registration.
- The initial successful sync for a feed URL is silent.
- Notification detection compares the latest persisted snapshot only against the immediately previous persisted snapshot for the same feed URL.
- New-release detection uses video IDs as the diff key.
- No notification is posted for first sync, feed switches, unchanged snapshots, empty snapshots, or missing notification permission.
- Each successful refresh that discovers at least one new video posts a distinct library-update notification entry.
- Tapping that notification opens the newest discovered video in the player route.
- Delivery timing is best-effort and constrained by WorkManager and Android power policy; correctness matters more than exact cadence.

### Playback and resume
- Selecting a playable item hands the resolved playback URL directly to Media3.
- Playback progress persists locally and is restored on later playback attempts.
- The app remains functional when a selected item lacks a playable URL by showing a clear fallback state instead of crashing.

### Feed parsing and sync semantics
- Feed sync performs a direct GET against the copied feed URL.
- Feed requests use RSS/XML accept headers and do not attach authorization headers or cookies.
- Playback URLs resolve in this priority order: `media:content`, then `enclosure`, then `link`.
- Thumbnail URLs resolve from `media:thumbnail` first, then the first image found in the description HTML.
- Published time may come from `pubDate` or `dc:date`.
- Duration may come from `media:content@duration` or `itunes:duration`.
- Show titles are inferred heuristically from category, page URL slug, and title patterns when the feed category itself is generic.
- Site-content heuristics used for show, cast, topic, and grouping inference are centralized in one shared heuristics layer rather than duplicated across parsing and UI modules.

### Persistence semantics
- feed URL persists separately from synced library data and playback progress
- secure storage is preferred for the feed URL, with migration from legacy plaintext when available
- cached library snapshot persists feed URL, videos, and last successful summary
- playback progress persists per video and updates during playback

## Security and privacy constraints
- Do not commit live Giant Bomb feed URLs, cookies, or authenticated XML payloads.
- Persist only the minimum local state needed for repeat use: feed URL, cached library snapshot, and playback progress.
- Avoid browser-login automation, scraping, or credential capture unless Giant Bomb publishes an official supported path.

## Validation contract
### Fast loop
- run `.\gradlew.bat verifyFast`
- expect docs checks plus JVM/unit coverage for model, data, database, network, and app modules

### Full local gate
- run `.\gradlew.bat verifyLocal -PskipManagedDevice=true`
- expect fast-loop coverage plus lint and screenshot verification

### Managed-device gate
- run `.\gradlew.bat verifyLocal`
- expect the configured managed-device smoke lane to pass when local emulator support is ready

### Connected-device checks
- verify notification permission is granted when testing notification behavior
- verify `LibraryRefreshWorker` appears in JobScheduler while a feed URL is saved
- verify the `looktube.library.updates` channel exists
- verify later successful detections create distinct visible notification entries
## Implementation-agnostic invariants
- Any future implementation may change internal architecture, libraries, or UI toolkit, but it must preserve the feed-first product boundary and the functional targets described here.
- The spec package in `docs/spec/agent-spec-package/` should be treated as the behavior-first artifact for external reuse.
- The repo-local markdown docs remain useful references, but the transferable spec should avoid binding future implementations to this repository's exact Kotlin module layout unless that behavior is externally observable.

## Non-goals
- website parity with Giant Bomb
- browser-driven login flows
- iOS support in the current scope
- offline downloads before streaming reliability is settled
- server-push notification infrastructure beyond local background refresh

## Repository references
- product scope: `docs/spec/product-spec.md`
- architecture: `docs/architecture/overview.md`
- integration notes: `docs/integration/giantbomb.md`
- local validation workflow: `docs/testing/local-ci.md`
- architectural decisions: `docs/decisions/ADR-0001-foundation.md`, `docs/decisions/ADR-0002-auth-persistence.md`
