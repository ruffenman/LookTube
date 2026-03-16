# Giant Bomb integration notes
## Verified findings
- Giant Bomb currently exposes feed surfaces, including Premium feed surfaces, from the feeds page.
- Current Premium feed links should be treated as copyable RSS inputs first; some copied feed URLs already carry the access key material needed for direct sync.
- Giant Bomb also states that legacy structured APIs for games, people, companies, and related content are not currently available.

## Design consequence
The first implementation path should stay feed-first, not legacy-API-first. Until Giant Bomb exposes an official supported media/mobile integration path, LookTube should avoid browser-backed sign-in flows, cookie harvesting, or other website automation. The main integration question is narrower:
- direct copied-feed access
- direct feed access with optional fallback basic-auth credentials

## What is implemented in the repo today
- fixture-driven RSS parsing in `core:network`
- a manual probe script in `scripts/Invoke-GiantBombFeedProbe.ps1`
- a configurable repository that persists feed URL, username, auth mode, and optional remembered password locally, with feed identity protected at rest inside the app
- a user-facing split between clearing synced library data and forgetting saved credentials while preserving the copied feed URL
- a live Premium RSS fetch path that can replace seeded library content when configured successfully
- seeded fallback library data that keeps the app usable and testable before live credentials are available
- a Media3-backed player screen that can attempt playback when a synced item exposes a stream URL
- a user-facing feed sync flow that treats a successful Premium feed sync as the current authenticated state

## What is still pending
- confirm exact production feed URLs that best represent the first supported Premium library surface
- confirm whether credentialed feed access is sufficient for real video playback, not just feed access
- confirm the minimum set of headers, cookies, and redirects required for authenticated playback handoff
- confirm how often the site behavior changes enough to require fixture refreshes
- validate whether copied feed URLs already cover repeat use, or whether some real Premium feeds still need direct-feed basic-auth fallback
- do not introduce website-login/session automation unless Giant Bomb publishes an official supported path

## Probe policy
- live probes are opt-in only
- the copied feed URL must come from an environment variable, and fallback basic-auth credentials must also come from environment variables if they are needed
- no secrets or raw authenticated payloads should be committed
- probe output should stay structural only: status, content type, item count, and other non-sensitive shape notes instead of raw XML previews
- when a live integration assumption changes, update this document before changing code behavior

## Next probe checklist
1. identify the exact target Premium feed URL
2. run `.\gradlew.bat integrationProbeGiantBomb`
3. capture whether the probe used feed-url-only access or fallback basic auth, plus status code, content type, item count, and non-sensitive structural notes
4. update this document and the learnings log with the validation date and outcome
