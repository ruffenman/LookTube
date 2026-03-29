# ADR-0001: Android-first modular foundation
## Status
Accepted

## Context
The repository started empty, and the highest-value user outcome is Android Premium video access for Giant Bomb. Giant Bomb's legacy structured APIs are not currently available, while authenticated feed surfaces are.

## Decision
Start with:
- a native Android app in Kotlin + Compose
- a modular repository split into `app`, `core:*`, and `feature:*`
- a feed-first integration strategy
- fixture-driven validation and opt-in live probes
- a spec-driven Ralph loop workflow backed by `verifyFast` and `verifyLocal`

## Consequences
### Positive
- the app can evolve in thin slices without waiting for the full integration spike to finish
- future agents have maintained context, not just code
- feed parsing, playback, and persistence stay behind explicit seams

### Negative
- the first foundation checkpoint ships placeholders instead of real auth or playback
- some modules, especially `core:database`, exist as seams before their production implementations arrive

## Follow-up ADRs
- chosen Premium feed access shape
- playback engine and Media3 configuration
- persistence strategy for watch progress and cached feed data
- offline-first caption generation and cast subtitle delivery
