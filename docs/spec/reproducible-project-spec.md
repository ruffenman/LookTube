# LookTube reproducible project spec
## Purpose
LookTube is a native Android companion app for Giant Bomb Premium subscribers. Its primary job is to let a user paste a copied Giant Bomb Premium RSS feed URL, sync the feed directly, browse the resulting library, resume playback, and receive local notifications when later background refreshes discover newly released videos.

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
- shared layers: `core:model`, `core:data`, `core:database`, `core:network`, `core:designsystem`, `core:testing`
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

## Functional targets
### Feed configuration and persistence
- A saved feed URL survives app restarts.
- Secure persistence is preferred; plaintext fallback, if required, stores only the feed URL.
- Clearing synced data does not clear the saved feed URL.

### Sync and library state
- Blank feed URLs do not start a successful sync.
- Successful sync replaces the active library with feed-backed items.
- If no successful feed snapshot is available, the app may use seeded fallback data so the shell remains usable and testable.

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
