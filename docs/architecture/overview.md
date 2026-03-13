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
  - repository interface and current in-memory spike implementation
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
  - Premium library browse surface
- `feature:player`
  - playback handoff surface
- `feature:settings`
  - diagnostics and operator commands

## Dependency direction
- `app` depends on `core:*` and `feature:*`
- `feature:*` depend only on the `core` modules they need
- `core:model` stays dependency-light
- `core:data` depends on `core:model`
- `core:database` depends on `core:model`
- `core:network` depends on `core:model`
- `core:testing` is shared by test configurations only

## Current data flow
1. `app` creates a repository instance from the app container
2. `LookTubeAppViewModel` bootstraps the repository
3. state flows are collected into the app shell
4. feature modules render pure UI based on state passed from the app shell

## Why the current spike uses in-memory data
The project still needs a short external integration spike to confirm the best Giant Bomb Premium auth strategy. Using an in-memory repository for the first foundation loop keeps the app buildable and testable while the network and playback seams remain explicit.

## Near-term evolution path
- replace the in-memory repository with a feed-backed implementation
- validate a secure auth/session storage strategy
- add Media3-backed playback
- replace the in-memory bookmark store with Room or another persisted local store once the first playback slice is stable
