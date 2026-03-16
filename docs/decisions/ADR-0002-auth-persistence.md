# ADR-0002: Feed credential persistence and clear-action split
## Status
Accepted
## Context
The feed-first Giant Bomb path is currently the only validated production-shaped auth flow in the repo. The app already persisted copied feed URLs and usernames at rest, but the optional password remained session-only and the Auth surface still treated clearing cached library state and forgetting saved credentials as one action.
## Decision
Keep copied Premium feed URLs as the primary auth path and add an explicit opt-in remembered-password capability on top of the existing encrypted-at-rest app store.
Separate two user actions:
- `Clear synced data` removes the cached library snapshot and playback progress but keeps feed settings ready for the next sync.
- `Forget saved credentials` clears the saved username and any remembered password while preserving the copied feed URL.
Do not introduce browser-backed sign-in, cookie persistence, or website automation unless Giant Bomb publishes an official supported path for that integration.
When encrypted persistence is unavailable, continue falling back only for non-password fields and degrade password handling back to session-only behavior instead of storing a password in plaintext.
## Consequences
### Positive
- daily repeat use improves without committing the product to an unvalidated browser-backed session model
- the app’s security posture stays explicit because remembered passwords remain opt-in and encrypted at rest
- cache clearing and credential forgetting now map more closely to what users expect from the Auth surface
### Negative
- the repo still does not prove that feed access alone is sufficient for every Giant Bomb playback/session edge case
- the auth surface and repository state machine grow slightly more complex because password persistence is now conditional
## Follow-up ADRs
- whether live Giant Bomb playback requires a reusable browser-backed session beyond remembered feed credentials
- any future change to cookie/header persistence for playback handoff
