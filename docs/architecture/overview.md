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
  - feed parsing and future authenticated fetch logic
- `core:designsystem`
  - shared theme and reusable UI primitives
- `core:testing`
  - shared fixtures and coroutine-test rules
- `feature:auth`
  - auth mode validation surface
- `feature:library`
  - consolidated Premium library browse surface with flat and grouped exploration modes
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
3. the repository loads persisted feed identity settings, keeps password session-only and optional, and seeds fallback library data
4. feature modules render and mutate repository state through the app shell, including a Premium feed access screen that calls an explicit sync action
5. an explicit sync action attempts a direct RSS fetch first and replaces the seeded library on success, while still passing optional basic-auth credentials when present

## Why the current spike still keeps a seeded fallback
The project still needs a short external integration spike to confirm the best Giant Bomb Premium auth strategy and exact production feed targets. The configurable repository now supports a real Premium feed sync path, but it keeps seeded fallback content so the app remains usable and testable before live credentials are available.

## Near-term evolution path
- replace the in-memory repository with a feed-backed implementation
- replace session-only password handling with a secure auth/session storage strategy
- harden Media3 playback and persist live watch progress
- replace the in-memory bookmark store with Room or another persisted local store once the first playback slice is stable
