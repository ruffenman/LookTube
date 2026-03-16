# Giant Bomb integration notes
## Verified findings
- Giant Bomb currently exposes feed surfaces, including Premium feed surfaces, from the feeds page.
- Current Premium feed links should be treated as copyable RSS inputs first; some copied feed URLs already carry the access key material needed for direct sync.
- Giant Bomb also states that legacy structured APIs for games, people, companies, and related content are not currently available.

## 2026-03-16 live probe result
- A real `premium-videos` 1080p feed URL succeeded in `feed-url-only` mode with HTTP 200 and `application/rss+xml; charset=utf-8`.
- The same feed URL also succeeded with `direct-feed-basic-auth-fallback`, and the structural counts matched the direct URL-only pass exactly: 84 items, 84 `media:content` tags, 84 `enclosure` tags, and 84 item links.
- For this validated Premium feed shape, the copied feed URL alone appears sufficient for feed access; optional fallback Basic auth did not change the structural result.
- Playback handoff is still a separate question. This probe validated feed access shape only, not whether stream URLs remain directly playable without broader session behavior.

## 2026-03-16 live playback probe result
- The app's current playback handoff is intentionally simple: `RssVideoFeedParser` maps `media:content`, then `enclosure`, then `link` into `VideoSummary.playbackUrl`, and `LookTubeApp` passes that URL directly to Media3 with `setUri(...)` and no extra cookies or playback headers.
- A live manual sample of the first three items from the same real `premium-videos` 1080p feed succeeded under those same constraints: each extracted playback target responded to a direct ranged request with HTTP 206 and `video/mp4`.
- All three sampled playback targets were plain `.mp4` URLs on `cdn.jwplayer.com` with no query string required, which is stronger evidence that this feed shape already exposes directly playable media URLs instead of page-only indirection.
- This still does not prove every Premium feed variant behaves the same way, but it meaningfully lowers the risk that browser-login or cookie-backed playback will be required for the validated `premium-videos` path.

## Design consequence
The first implementation path should stay feed-first, not legacy-API-first. Until Giant Bomb exposes an official supported media/mobile integration path, LookTube should avoid browser-backed sign-in flows, cookie harvesting, or other website automation. The main integration question is narrower:
- direct copied-feed access
- direct feed access with optional fallback basic-auth credentials

## What is implemented in the repo today
- fixture-driven RSS parsing in `core:network`
- a manual probe script in `scripts/Invoke-GiantBombFeedProbe.ps1`
- a manual playback probe script in `scripts/Invoke-GiantBombPlaybackProbe.ps1`
- a configurable repository that persists feed URL, username, and optional remembered password locally, with feed identity protected at rest inside the app
- a user-facing split between clearing synced library data and forgetting saved fallback details while preserving the copied feed URL
- a live Premium RSS fetch path that can replace seeded library content when configured successfully
- seeded fallback library data that keeps the app usable and testable before live credentials are available
- a Media3-backed player screen that can attempt playback when a synced item exposes a stream URL
- a user-facing feed sync flow that treats a successful Premium feed sync as the current authenticated state

## What is still pending
- confirm exact production feed URLs that best represent the first supported Premium library surface
- validate whether additional real Premium feed variants also expose directly playable media URLs through the current feed fields, not just feed access
- confirm the minimum set of headers, cookies, and redirects required for authenticated playback handoff
- confirm how often the site behavior changes enough to require fixture refreshes
- validate whether other real Premium feed variants also behave like the validated `premium-videos` probe, or whether any still need direct-feed basic-auth fallback
- do not introduce website-login/session automation unless Giant Bomb publishes an official supported path

## Probe policy
- live probes are opt-in only
- the copied feed URL must come from an environment variable, and fallback basic-auth credentials must also come from environment variables if they are needed
- no secrets or raw authenticated payloads should be committed
- probe output should stay structural only: status, content type, item count, and other non-sensitive shape notes instead of raw XML previews
- the probe should compare feed-url-only access first and only add a direct-feed basic-auth pass when fallback credentials are available
- playback probes should mirror the app's real handoff shape by testing the extracted playback target directly, without cookies or extra session headers, and should report only non-sensitive host/type/status metadata
- when a live integration assumption changes, update this document before changing code behavior

## Next probe checklist
1. identify the exact target Premium feed URL
2. run `.\gradlew.bat integrationProbeGiantBomb`
3. capture the outcome of feed-url-only access first
4. if fallback credentials are present, compare that result against the direct-feed basic-auth pass
5. record status code, content type, item count, and other non-sensitive structural notes only
6. run `.\gradlew.bat integrationProbeGiantBombPlayback` to sample extracted playback targets
7. update this document and the learnings log with the validation date and outcome
