# Architecture overview
## Topline approach
LookTube is built as a native Android app in Kotlin with Jetpack Compose. The repository is structured around a thin app shell, shared `core` modules, and feature-oriented UI modules.

## Modules
- `app`
  - Android application entrypoint
  - navigation host
  - app-level state wiring
  - managed-device instrumentation smoke tests
- `core:model`
  - auth, video, and playback domain models
- `core:data`
  - repository interface, configurable feed-backed repository, and in-memory spike implementation
- `core:database`
  - persistence seam for playback bookmarks
- `core:network`
  - feed parsing and HTTP fetch logic for copied feed URLs
- `core:designsystem`
  - shared theme and reusable UI primitives
- `core:testing`
  - shared fixtures and coroutine-test rules
- `feature:auth`
  - feed configuration surface
- `feature:library`
  - consolidated Premium library browse surface with grouped exploration, rich cards, and flyout jump navigation
- `feature:player`
  - Media3-backed playback surface with graceful fallback when no playback URL is available

## Dependency direction
- `app` depends on `core:*` and `feature:*`
- `feature:*` depend only on the `core` modules they need
- `core:model` stays dependency-light
- `core:data` depends on `core:model`
- `core:database` depends on `core:model`
- `core:network` depends on `core:model`
- `core:testing` is shared by test configurations only

## Current data flow
1. `app` creates a `SharedPreferencesFeedConfigurationStore` plus the configurable repository from the app container
2. `LookTubeAppViewModel` bootstraps the repository
3. the repository loads the copied feed URL from an app-owned encrypted-at-rest store, restores the last synced library snapshot, and exposes persisted playback progress for the feed-first path
4. feature modules render and mutate repository state through the app shell, including a Premium feed access screen centered on the copied feed URL, a consolidated grouped library surface, and a shared Media3 player route
5. an explicit sync action fetches the RSS feed directly from the copied URL and replaces the cached library on success; the architecture intentionally stops short of website-login automation
6. while a feed URL is saved, WorkManager keeps a periodic background refresh registration alive and the app posts a library-update notification when a later successful refresh discovers previously unseen video IDs for that same feed URL

## Why the current spike still keeps a seeded fallback
The project still needs a short external integration spike to confirm the best Giant Bomb Premium auth strategy and exact production feed targets. The configurable repository now supports a real Premium feed sync path, but it keeps seeded fallback content so the app remains usable and testable before live credentials are available.

## Near-term evolution path
- validate whether copied feed URLs stay sufficient across more Giant Bomb feed variants, or whether Giant Bomb eventually exposes an official broader auth/session path
- keep tightening repeat-use reliability around periodic refresh and notification visibility on real devices
- continue hardening Media3 playback and real-device resume behavior
- improve visual regression coverage for the now-richer library/player surfaces
- keep refining grouping heuristics and browse affordances as more live feed edge cases appear
