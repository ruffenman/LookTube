# ADR-0002: Feed URL persistence and synced-data clearing
## Status
Accepted
## Context
The feed-first Giant Bomb path is currently the only validated production-shaped access flow in the repo. Live probes showed that a copied Premium feed URL was sufficient for the validated `premium-videos` feed shape, so the extra fallback credential branch no longer matched the product direction.
## Decision
Support copied Premium feed URLs as the only user-facing sync input.
Persist the copied feed URL in the existing encrypted-at-rest app store.
Keep a single user action:
- `Clear synced data` removes the cached library snapshot and playback progress but keeps the copied feed URL ready for the next sync.
Do not introduce browser-backed sign-in, cookie persistence, or website automation unless Giant Bomb publishes an official supported path for that integration.
When encrypted persistence is unavailable, continue falling back to legacy plaintext storage for the feed URL only.
## Consequences
### Positive
- daily repeat use improves without committing the product to an unvalidated browser-backed session model
- the app’s security posture is simpler because only the feed URL is persisted
- the Auth surface now matches the validated feed-only product shape
### Negative
- the repo still does not prove that feed access alone is sufficient for every Giant Bomb playback/session edge case
- clearing synced data no longer preserves any alternate direct-feed escape hatch because that path has been removed
## Follow-up ADRs
- whether live Giant Bomb playback requires a reusable browser-backed session beyond the copied feed URL
- any future change to cookie/header persistence for playback handoff
