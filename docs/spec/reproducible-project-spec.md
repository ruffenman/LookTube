# LookTube reproducible project spec
## Purpose
LookTube is a native Android companion app for Giant Bomb Premium subscribers. Its primary job is to let a user paste a copied Giant Bomb Premium RSS feed URL, sync the feed directly, browse the resulting library, resume playback, generate local captions for playable videos, and receive local notifications when later background refreshes discover newly released videos.
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
- shell: one `app` module that owns app wiring, navigation, background work, and playback service integration, with the default `baseline` target preserved as the lower-spec build and an opt-in `moonshine` target reserved for higher-spec capabilities
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
1. App restores the saved feed URL, cached library snapshot, playback progress, and local engagement state for recent history and watched/unwatched tracking.
2. User can browse and resume without re-entering the feed URL.
3. WorkManager keeps periodic background refresh active while a non-blank feed URL remains saved.
4. If a later successful refresh finds previously unseen video IDs for the same feed URL, the app posts a local notification that opens the newest discovered video.

### Clear synced data
1. User clears synced data.
2. App removes cached library snapshot, playback progress, and local engagement state.
3. App preserves the saved feed URL so the next sync stays cheap.
## Surface inventory
### App shell
- three top-level destinations: `Auth`, `Library`, and `Player`
- app opens on the Auth page by default
- top app bar and bottom navigation stay visible outside player fullscreen mode
- while shell chrome is visible, the top app bar exposes one global Look Points badge on Library and Player rather than page-local Look Points controls
- notification launch intents can route directly to the Player page and optionally preselect a video

### Auth surface
- accepts the copied Premium feed URL as the only supported user input for feed access
- exposes a primary sync action
- exposes `Clear synced data` only when a successful sync or cached summary exists
- exposes offline caption model readiness plus a one-time download action for the local caption model
- communicates five practical user-visible states: setup required, ready, syncing, synced, and needs attention

### Library surface
- renders an overview panel above the scrolling episode list using the current sync state and last successful summary
- supports grouping by none, show, cast, or topic
- supports sorting by latest, show, or oldest
- supports a default-collapsed Library Config section that wraps library status, grouping, group visibility, sorting, and show filtering
- supports show filtering with the filter tray collapsed by default
- supports collapsing and expanding individual grouped sections, plus overview-level expand-all and collapse-all actions when grouping is active
- applies the chosen sort mode consistently to flat lists, grouped section ordering, and episode ordering within each visible group
- renders grouped section headers as containing cards with progress-aware video cards beneath them, keeps the expand/collapse control in the top right and the single group watched-state toggle in the bottom right, and provides a right-side jump rail that anchors to the episode-list panel for quick section navigation based on the currently visible section anchors
- shows watched-versus-total completion on grouped show headers when browsing by show
- uses explicit `Mark as Watched` and `Mark as Unwatched` labels for manual watched-state actions
- exposes key per-video metadata on cards and an explicit full-info affordance for inspecting each video's stored details
- keeps active show-filter feedback adjacent to the show-filter controls inside the overview panel
- keeps the primary Auth, Library, and Player surfaces visually consistent through shared card, header, and panel treatments
- opens the selected video in the Player surface

### Player surface
- shows clear empty, unavailable, preparing, and active playback states
- keeps the player frame pinned above the supporting metadata when a video is selected from Library or a launch intent
- resumes playback from saved progress when available
- supports fullscreen entry from the player control and landscape-driven presentation
- uses left-side and right-side double taps to seek backward or forward 10 seconds during playback
- keeps playback on the same active item when entering or leaving fullscreen, including activity recreation from rotation
- omits next/previous transport controls because there is no implicit app-owned queue
- exposes exactly one cast route control as part of the player controls
- exposes a `History` affordance and manual watched/unwatched actions below the player in a compact supporting area, with the history list presented in a bounded surfaced menu that can scroll for longer histories
- exposes offline caption generation and regeneration for the selected video, shows caption progress or failure state, and enables standard subtitle controls when a generated text track exists
- explains remote playback directly on the player surface so cast sessions do not appear as an unexplained black frame, and the remote-playback indicator remains purely visual without intercepting player input

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
- The library overview panel remains visually separate from the scrolling episode list, can scroll off screen before the episode list takes over the viewport, and the jump rail does not overlap that overview panel.
- Grouped section collapse state is local UI state that survives scrolling and jump-rail use during the current session without needing cross-launch persistence.
- Grouped sections read as containing cards, with compact header controls that use icon-only expand/collapse affordances, a single current-state watched toggle instead of parallel watched and unwatched buttons, and expanded episode cards that remain visually nested under the header they belong to.
- The jump rail becomes visible quickly when scrolling starts and fades back out quickly after scrolling stops.

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
- A saved resume point is applied reliably when playback starts, even after app reloads where controller setup and bookmark restoration do not complete in the same frame.
- Starting playback for a playable item remains reliable even if the device is locked immediately after the play request, without requiring the user to wait for the first visible rendered frame.
- Entering or leaving fullscreen must not restart playback, swap back to a previously played item, or lose the current selection request boundary.
- Explicitly selecting the currently selected video again, reconnecting to an already-active cast session after app resume or device lock, or returning from a lost cast session must not leave playback stuck on a black screen or unnecessarily restart the active cast item.
- When playback is remote, the player surface communicates the handoff state and keeps standard transport controls usable from the app.
- The app remains functional when a selected item lacks a playable URL by showing a clear fallback state instead of crashing.

### Engagement, history, and completion
- The app persists a recent-play history and manual watched/unwatched overrides per video alongside playback progress.
- Look Points are derived only from watched videos, appear as a single global shell badge on Library and Player, and do not move into Library-local or Player-local control clusters.
- Completing every video in a show is visualized in browse UI but does not grant extra score.
- A video can become watched either through playback completion heuristics or an explicit manual watched action, and a manual unwatched action overrides that status until the user watches or marks it watched again.

### Captions
- Users can download a local caption model once and then generate captions for any playable video without configuring an external provider.
- Caption generation extracts audio from the selected playback URL, runs the on-device transcription path locally, and stores the result as a per-video WebVTT sidecar.
- Local playback attaches generated captions as selectable subtitle tracks on the active `MediaItem`.
- Cast playback preserves generated captions by mapping subtitle configurations into explicit Cast text tracks and serving local sidecars from the sender over a reachable local HTTP URL.
- The default build target preserves a lower-spec whisper.cpp-based local caption path, while an opt-in higher-spec target may expose additional local engines such as Moonshine behind stricter SDK or ABI assumptions.
- Any future cloud or provider-backed caption flow must remain optional and should layer onto the same local-first caption pipeline rather than replacing it.

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
- engagement state persists recent-play timestamps, completion timestamps, and manual watched/unwatched overrides per video

## Security and privacy constraints
- Do not commit live Giant Bomb feed URLs, cookies, or authenticated XML payloads.
- Persist only the minimum local state needed for repeat use: feed URL, cached library snapshot, playback progress, and local engagement state for history/completion.
- Avoid browser-login automation, scraping, or credential capture unless Giant Bomb publishes an official supported path.

## Validation contract
### Fast loop
- run `.\gradlew.bat verifyFast`
- expect docs checks plus JVM/unit coverage for model, data, database, network, and the baseline app target

### Full local gate
- run `.\gradlew.bat verifyLocal -PskipManagedDevice=true`
- expect fast-loop coverage plus baseline lint and baseline screenshot verification

### Managed-device gate
- run `.\gradlew.bat verifyLocal`
- expect the configured managed-device smoke lane to pass when local emulator support is ready

### High-spec Moonshine gate
- run `.\\gradlew.bat verifyMoonshine`
- expect docs checks plus compile, unit, and lint coverage for the opt-in Moonshine-capable app target

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
- architectural decisions: `docs/decisions/ADR-0001-foundation.md`, `docs/decisions/ADR-0002-auth-persistence.md`, `docs/decisions/ADR-0003-offline-captions.md`
