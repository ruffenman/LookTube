# Giant Bomb integration notes
## Verified findings
- Giant Bomb currently exposes feed surfaces, including Premium feed surfaces, from the feeds page.
- Giant Bomb states that Premium feeds require account login credentials.
- Giant Bomb also states that legacy structured APIs for games, people, companies, and related content are not currently available.

## Design consequence
The first implementation path should be feed-first, not legacy-API-first. The auth spike must determine whether the most reliable Android companion experience is:
- direct credentialed feed access
- a browser-backed sign-in flow that yields a reusable session

## What is implemented in the repo today
- fixture-driven RSS parsing in `core:network`
- a manual probe script in `scripts/Invoke-GiantBombFeedProbe.ps1`
- a configurable repository that persists feed URL, username, and auth mode locally
- a session-only password field in the app shell
- a live credentialed RSS fetch path that can replace seeded library content when configured successfully
- seeded fallback library data that keeps the app usable and testable before live credentials are available
- a Media3-backed player screen that can attempt playback when a synced item exposes a stream URL

## What is still pending
- confirm exact production feed URLs that best represent the first supported Premium library surface
- confirm whether credentialed feed access is sufficient for real video playback, not just feed access
- confirm the minimum set of headers, cookies, and redirects required for authenticated playback handoff
- confirm how often the site behavior changes enough to require fixture refreshes
- replace session-only password handling with a more secure persisted credential or session strategy

## Probe policy
- live probes are opt-in only
- credentials must come from environment variables
- no secrets or raw authenticated payloads should be committed
- when a live integration assumption changes, update this document before changing code behavior

## Next probe checklist
1. identify the exact target Premium feed URL
2. run `.\gradlew.bat integrationProbeGiantBomb`
3. capture status code, content type, item count, and non-sensitive structural notes
4. update this document and the learnings log with the validation date and outcome
