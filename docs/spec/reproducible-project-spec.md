# LookTube reproducible project spec
## Purpose
LookTube is a native Android companion app for Giant Bomb Premium subscribers. Its primary job is to let a user paste a copied Giant Bomb Premium RSS feed URL, sync the feed directly, browse the resulting library, resume playback, generate local captions for playable videos, optionally auto-generate local captions for newly discovered videos, and receive local notifications when later background refreshes discover newly released videos.
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
- shell: one `app` module that owns app wiring, navigation, background work, and playback service integration, with the default `baseline` target preserved as the lower-spec build and an opt-in `highspec` target reserved for higher-spec capabilities such as Moonshine
- shared layers: `core:heuristics`, `core:model`, `core:data`, `core:database`, `core:network`, `core:designsystem`, `core:testing`
- feature layers: `feature:auth`, `feature:library`, `feature:player`
- background work: WorkManager periodic refresh plus immediate and rolling one-time catch-up refreshes
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
2. App may show a brief cold-start-only brand intro with a verified, attributed Giant Bomb cast quote and an optional new-video digest, and it can still be skipped immediately with any tap.
3. User pastes a copied Giant Bomb Premium feed URL.
4. App persists that feed URL locally with encrypted-at-rest protection where available.
5. User triggers sync.
6. App loads feed items into the library and exposes them for browsing and playback.

### Daily repeat use
1. App restores the saved feed URL, cached library snapshot, playback progress, and local engagement state for recent history and watched/unwatched tracking.
2. User can browse and resume without re-entering the feed URL.
3. WorkManager keeps periodic background refresh active while a non-blank feed URL remains saved, may enqueue an immediate catch-up refresh when scheduling is refreshed, and maintains a rolling delayed catch-up fallback alongside that stable periodic registration.
4. If a later successful refresh finds previously unseen video IDs for the same feed URL, the app posts a local notification that opens the newest discovered video for review without forcing autoplay.
5. If automatic caption generation for new videos is enabled and the on-device model is ready, newly discovered playable videos can be captioned automatically during sync or background refresh.

### Clear synced data
1. User clears synced data.
2. App removes cached library snapshot, playback progress, and local engagement state.
3. App preserves the saved feed URL so the next sync stays cheap.
## Surface inventory
### App shell
- three top-level destinations: `Settings`, `Library`, and `Player`
- app opens on the Settings page by default
- true cold starts may show a brief LookTube intro overlay that rotates through verified, attributed public Giant Bomb cast quotes with an optional new-video digest and a persistent quotes-and-sources catalogue in Settings, can be skipped immediately, and does not replay when resuming from background
- top app bar and bottom navigation stay visible outside player fullscreen mode
- while shell chrome is visible, the top app bar exposes one global Look Points badge on Library and Player rather than page-local Look Points controls, can show a small centered icon-only playback indicator between the title and badge when playback is active, and lets that indicator route directly to Player while re-syncing selection with the active session when possible
- notification launch intents can route directly to the Player page and optionally preselect a video for review without forcing autoplay

### Settings surface
- accepts the copied Premium feed URL as the only supported user input for feed access
- exposes a primary sync action
- exposes `Clear synced data` only when a successful sync or cached summary exists
- exposes offline caption model readiness plus a one-time download action for the local caption model
- exposes a persistent toggle for automatically generating captions for newly discovered videos
- exposes caption-data management for listing videos with partial or completed caption data, opening one in Player without autoplay, and clearing all caption data
- communicates five practical user-visible states: setup required, ready, syncing, synced, and needs attention

### Library surface
- shows a clean empty-state panel when no synced library data is available yet, rather than seeded placeholder content
- renders an overview panel above the scrolling episode list using the current sync state and last successful summary
- supports grouping by none, show, or topic
- supports sorting by latest, show, or oldest
- exposes search across titles, descriptions, shows, and topics plus All, Unwatched, In progress, and Watched chips with a result count and Reset filters action
- supports a default-collapsed Browse options section that wraps library status, grouping, group visibility, sorting, and show filtering
- supports show filtering with the filter tray collapsed by default
- supports collapsing and expanding individual grouped sections, plus overview-level expand-all and collapse-all actions when grouping is active
- applies the chosen sort mode consistently to flat lists, grouped section ordering, and episode ordering within each visible group
- renders grouped section headers as containing cards with progress-aware video cards beneath them, lets the whole header toggle expansion, keeps continuous visual group containment with individually lazy episode rows in both states, keeps a circular top-left expand affordance while collapsed and an explicit minimum-48-dp Collapse group button in the expanded header, uses subdued collapsed mosaic art behind a readable info plate while up to three subsequent-video cards remain stacked and partially visible beneath the header with compact date, duration, and title metadata, uses a shorter opaque expanded header with SHOW GROUP or TOPIC GROUP, an unobscured title and counts, and a restrained leading accent, insets child episode cards beside a group gutter and adds numbered play badges over their single-thumbnail art, with two-line description previews, places the single group watched-state toggle immediately above the divider and child video list, uses a custom progress bar that avoids ambiguous endpoint dots on video cards, and provides a permanently allocated browse bar with Top and a labelled Jump to show/topic control, a dismissible section popup, and a touch-passive scroll indicator with accessible progress actions
- shows watched-versus-total completion on grouped show headers when browsing by show
- uses explicit `Mark as Watched` and `Mark as Unwatched` labels for manual watched-state actions
- exposes key per-video metadata on cards and an explicit full-info affordance for inspecting each video's stored details
- keeps active show-filter feedback adjacent to the show-filter controls inside the overview panel
- keeps the primary Settings, Library, and Player surfaces visually consistent through shared card, header, and panel treatments
- opens the selected video in the Player surface

### Player surface
- shows clear empty, unavailable, preparing, and active playback states
- keeps the player frame pinned above the supporting metadata when a video is selected from Library or a launch intent
- resumes playback from saved progress when available
- supports fullscreen entry from the player control and landscape-driven presentation with larger, more spread-out controls in fullscreen landscape
- uses left-side and right-side double taps to seek backward or forward 10 seconds during playback and shows a brief on-player confirmation animation for each skip
- keeps playback on the same active item when entering or leaving fullscreen, including activity recreation from rotation
- omits next/previous transport controls because there is no implicit app-owned queue
- exposes exactly one cast route control as part of the player controls
- exposes a `History` affordance and manual watched/unwatched actions below the player in a compact supporting area, with the history list presented in a bounded surfaced menu that sizes to content, caps at roughly two-thirds of the usable shell height, and can scroll for longer histories
- exposes offline caption generation and regeneration for the selected video, shows caption progress or failure state with a compact default view plus expandable detailed metrics, enables standard subtitle controls when a generated text track exists, and lets the user delete the selected video's saved or partial caption data
- explains remote playback directly on the player surface so cast sessions do not appear as an unexplained black frame, keeps standard transport controls persistently visible while playback is remote, avoids keeping the local device awake solely because playback is remote, and keeps the remote-playback indicator purely visual without intercepting player input

## Functional targets
### Feed configuration and persistence
- A saved feed URL survives app restarts.
- Secure persistence is preferred; plaintext fallback, if required, stores only the feed URL.
- The persisted feed configuration also carries the automatic-caption toggle for newly discovered videos and daily-open Look Points bookkeeping.
- Clearing synced data does not clear the saved feed URL. Library and caption-data clearing each require an explicit confirmation naming their scope.
- Saved feed URLs are masked until explicitly revealed. Refresh errors remain visible even when cached data is available.
- Video details scroll independently with Close kept reachable; watch and caption actions wrap on narrow layouts.

### Sync and library state
- Blank feed URLs do not start a successful sync.
- Successful sync replaces the active library with feed-backed items.
- If no successful feed snapshot is available, the library remains empty and presents a clean next-step empty state instead of placeholder content.
- Library sorting semantics stay consistent across grouped and ungrouped browsing: latest and oldest are chronological, while show ordering is alphabetical by group/show with newest episodes first within a show.
- The library overview panel remains visually separate from the scrolling episode list, can scroll off screen before the episode list takes over the viewport, and the fixed Top action remains reachable after scrolling without following recycled item coordinates.
- Grouped section collapse choices use persistent, grouping-qualified keys and survive scrolling, filtering, grouping changes, route recreation, and relaunch. Hidden groups retain their choices; expand/collapse-all affects visible groups only.
- Grouped sections read as containing cards, with a circular top-left expand control while collapsed and a labelled Collapse group control in the compact expanded header, a subdued collapsed style whose lead header uses the group's mosaic art while up to three slightly staggered subsequent-video cards remain visibly stacked beneath it with compact date, duration, and title metadata, an opaque expanded header explicitly labelled SHOW GROUP or TOPIC GROUP with title and counts, a single explicitly scoped Mark all watched/unwatched group action, and expanded episode cards that remain visually nested under the header they belong to with a group gutter and numbered play badge over their single-thumbnail art.
- The browse bar remains visible even at the overview, with Top disabled at the beginning and a labelled Jump to show/topic control showing the current section. It reserves space beneath the list. The section popup scrolls independently and closes on selection, outside tap, or Back without activating underlying cards. The scroll indicator passes touch through to normal list scrolling; accessible progress actions remain supported. Grouped anchors count every visible lazy episode row.
- Feed input uses an unpersisted draft with Discard changes and explicit Save & sync; typing never changes the saved configuration or refresh schedule. Validate complete HTTP(S) URLs and save the submitted value before requesting its feed.
- Per-video caption deletion requires confirmation and is unavailable during generation. Details lead with the description and hide diagnostic fields behind Technical details.

### Background refresh and notifications
- Saving a non-blank feed URL results in one active periodic background refresh registration, may enqueue an immediate catch-up refresh request without resetting the long-lived periodic cadence, and maintains a rolling delayed catch-up fallback while the feed URL remains saved.
- Clearing the saved feed URL cancels that registration.
- The initial successful sync for a feed URL is silent.
- Notification detection compares the latest persisted snapshot only against the immediately previous persisted snapshot for the same feed URL.
- New-release detection uses video IDs as the diff key.
- No notification is posted for first sync, feed switches, unchanged snapshots, empty snapshots, or missing notification permission.
- Each successful refresh that discovers at least one new video posts a distinct library-update notification entry.
- Tapping that notification opens the newest discovered video in the player route without forcing autoplay.
- Background refresh bootstraps repository state before syncing.
- When automatic caption generation for new videos is enabled and the local model is ready, background refresh can auto-caption newly discovered playable videos that do not already have a saved caption sidecar.
- Long-running background auto-caption work may run as foreground-maintenance work for better reliability while the app is backgrounded or the device is locked.
- Delivery timing is best-effort and constrained by WorkManager and Android power policy; correctness matters more than exact cadence.

### Playback and resume
- Selecting a playable item hands the resolved playback URL directly to Media3 and must not crash while the player surface applies layout customizations around the controller chrome.
- Playback progress persists locally and is restored on later playback attempts.
- A saved resume point is applied reliably when playback starts, even after app reloads where controller setup and bookmark restoration do not complete in the same frame.
- Starting playback for a playable item remains reliable even if the device is locked immediately after the play request, without requiring the user to wait for the first visible rendered frame.
- Entering or leaving fullscreen must not restart playback, swap back to a previously played item, or lose the current selection request boundary.
- Reopening the app, opening a notification-selected video, or opening a video from Settings for inspection must not auto-start playback solely because the app surfaced that target.
- Opening a video from Settings for caption-data inspection must route to Player without forcing autoplay when the user only wants to review status.
- Explicitly selecting the currently selected video again, reconnecting to an already-active cast session after app resume or device lock, or returning from a lost cast session must not leave playback stuck on a black screen or unnecessarily restart the active cast item.
- When playback is remote, the player surface communicates the handoff state, keeps standard transport controls visibly usable from the app, and does not keep the local device awake solely because playback continues on the cast target.
- Double-tap skip gestures show brief direction-aware skip confirmation feedback that avoids overlapping the main control clusters, and fullscreen landscape uses visibly roomier playback controls than embedded playback.
- The app remains functional when a selected item lacks a playable URL by showing a clear fallback state instead of crashing.

### Engagement, history, and completion
- The app persists a recent-play history and manual watched/unwatched overrides per video alongside playback progress.
- Watched videos are worth 12 Look Points, app opens add 1 point once per local day, Look Points appear as a single global shell badge on Library and Player, and the score does not move into Library-local or Player-local control clusters.
- Completing every video in a show is visualized in browse UI but does not grant extra score.
- A video can become watched either through playback completion heuristics or an explicit manual watched action, and a manual unwatched action overrides that status until the user watches or marks it watched again.

### Captions
- Users can download a local caption model once and then generate captions for any playable video without configuring an external provider.
- Users can enable a persistent setting that automatically generates captions for newly discovered playable videos once the local caption model is ready.
- Caption-generation state persists per video as partial or completed caption data so Settings can list those entries, clear them in bulk, and Player can delete the current video's entry directly.
- Caption generation extracts audio from the selected playback URL into a file-backed 16 kHz mono PCM working copy, conservatively isolates speech-heavy spans from that audio, runs the on-device transcription path locally against those spans, and stores the result as a per-video WebVTT sidecar.
- Long-running caption generation continues surfacing hard transcription progress after extraction has completed, including speech-processed duration, chunk-level completion, measured throughput, and the latest chunk wall time with native timing breakdown when available, plus an ETA once enough work has finished to estimate it, while Player keeps the default caption UI compact and exposes the full metric set through an expandable stats section.
- Local playback attaches generated captions as selectable subtitle tracks on the active `MediaItem`.
- Cast playback preserves generated captions by mapping subtitle configurations into explicit Cast text tracks and serving local sidecars from the sender over a reachable local HTTP URL.
- The default build target preserves a lower-spec whisper.cpp-based local caption path, while the opt-in `highspec` target may default to a faster local engine such as Moonshine behind stricter SDK or ABI assumptions and still retain Whisper.cpp as a compatibility fallback.
- Any future cloud or provider-backed caption flow must remain optional and should layer onto the same local-first caption pipeline rather than replacing it.

### Feed parsing and sync semantics
- Feed sync performs a direct GET against the copied feed URL.
- Feed requests use RSS/XML accept headers and do not attach authorization headers or cookies.
- Playback URLs resolve in this priority order: `media:content`, then `enclosure`, then `link`.
- Thumbnail URLs resolve from `media:thumbnail` first, then the first image found in the description HTML.
- Published time may come from `pubDate` or `dc:date`.
- Duration may come from `media:content@duration` or `itunes:duration`.
- Show titles are inferred heuristically from category, page URL slug, and title patterns when the feed category itself is generic.
- Site-content heuristics used for show, topic, and grouping inference are centralized in one shared heuristics layer rather than duplicated across parsing and UI modules.

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

### Highspec gate
- run `.\gradlew.bat verifyHighspec`
- expect docs checks plus compile, unit, and lint coverage for the opt-in highspec app target

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
- architectural decisions: `docs/decisions/ADR-0001-foundation.md`, `docs/decisions/ADR-0002-auth-persistence.md`, `docs/decisions/ADR-0003-offline-captions.md`, `docs/decisions/ADR-0004-library-browse-stability.md`


## Optional autoplay
- Autoplay next video defaults to enabled on new installations and upgrades. One persisted preference is exposed in Settings and Player and survives restart; it does not change automatic caption generation.
- Continue only after an item that actually played reaches its end with play intent still active. Preview-only selections, paused ends, stop/idle, errors, and toggling the setting on after completion do not start another item. Consume each completion once and recheck state before handoff.
- Choose playable, unwatched videos from the cached library, independent of browsing filters. Exclude the current video and videos completed in this playback-service session. Prefer the next chronological episode in the same show and inferred topic, then the next same-show episode, then same-show unwatched backfill. Use shared show/topic heuristics.
- If no same-show continuation exists, rank shows using the last twenty played-video history records, with stronger weights for more recent records; manually marking watched alone does not establish affinity. Prefer an incomplete resume in the selected show, otherwise its oldest unwatched item. Without relevant history, choose the newest unwatched playable item. Break ties by stable video ID; missing dates sort last for continuation.
- Player shows the proposed next title and a short reason, with Play next now available even when autoplay is disabled. The service recomputes at completion against current watch state and history. Resume incomplete progress; explicitly unwatched items with completed progress restart at zero. An exhausted library stops.
- The shared Media3 service owns continuation for local/background playback and the existing cast route. Preserve metadata, saved captions, watch progress, and fullscreen state. Recommendations are deterministic and on-device, with no external AI service or transmission of watch history.

## Group header and episode hierarchy
- Expanded groups use a compact opaque structural header labelled SHOW GROUP or TOPIC GROUP, with an unobscured wrapping title and counts, a restrained leading accent, and a distinct Collapse group button with a minimum 48-dp height. The whole header remains a collapse target; neither it nor its dedicated control starts playback.
- Preserve the card-pack metaphor: a small decorative stack of the group's own thumbnails remains in the expanded header; the existing collapsed mosaic and staggered preview cards remain. Animate header size and lazy item placement/fades on expansion/collapse so the pack opens and compresses without giving the scroll indicator touch ownership or composing all offscreen episodes.
- Episode cards are inset beside a connecting gutter and show a numbered play badge over their single-thumbnail artwork. Grouped descriptions preview two lines with Read more for overflow. Group watch actions explicitly say Mark all watched/unwatched; episode watch actions stay individually scoped.
- Preserve stable episode keys, persisted collapse choices, filtering, jump navigation, autoplay, and individually lazy episode rows. Check native collapse touches do not trigger playback or toggle twice, group actions do not play a video, and the collapse control remains reachable at 320-dp width / 2x text. Use physical-device screenshots and a transition recording for visual acceptance separately from JVM fixtures.

## Isolated debug iteration
- Debug builds use a separate `.debug` application ID suffix and LookTube Debug launcher label, allowing baseline/highspec debug installs alongside their normal releases. Release application IDs and signing remain unchanged.
- Prefer debug deployment and physical UI/motion checks before requesting release signing. The optional seed helper populates only a selected debug package with 40 non-playable fixture videos; it does not read normal account data. Real artwork/data acceptance remains a separate final check on the signed normal app.
