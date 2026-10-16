# LookTube product spec
## Problem
Premium Giant Bomb subscribers should be able to open an Android app, paste a copied Premium feed URL, browse Premium video surfaces, and start playback without navigating the Giant Bomb website in a browser.

## Goals
- Support the smallest secure feed-first flow that reliably unlocks Premium video access.
- Show a compact, useful library view centered on Premium videos and fast resume behavior.
- Launch stable playback with progress persistence and clear recovery when the session expires.
- Surface new-release notifications reliably enough that repeat users can trust background refresh.
- Keep development spec-driven, fixture-driven, and easy to validate locally.

## Spec maintenance rule
- This document is the live product/design contract for the repository.
- Any change to user-visible behavior, UX flow, interaction model, navigation, supported scope, or validation expectations must update this spec in the same implementation slice.
- Work is not complete if the code and the current design diverge from this spec.
- When a change also affects the transferable behavior contract, update `docs/spec/reproducible-project-spec.md` and `docs/spec/agent-spec-package/` in the same slice.

## Non-goals for the initial Android scope
- iOS support
- community/wiki browsing
- downloads or offline playback before streaming is stable
- generalized Giant Bomb site parity

## Current validated state
The current app already covers a substantial first-use slice for a Premium subscriber:
- copied Premium RSS feed URLs can be configured and synced from the Settings surface
- copied feed URLs are protected at rest
- the app persists the last successful synced library, saved playback progress, recent playback history, and watched/unwatched engagement state
- Giant Bomb site-content heuristics now live in one shared library so feed-title, show grouping, and topic rule changes have a single update point
- Library combines grouped browsing, collapsible section headers, full-header tap targets, clearer collapsed versus expanded header states, grouped headers that now keep continuous group containment in both states, a circular top-left expand affordance when collapsed and an explicit Collapse group button when expanded, subdued collapsed mosaic art with stacked compact child cards visible beneath the header card and surfacing date, duration, and title metadata, compact opaque expanded headers labelled SHOW GROUP or TOPIC GROUP with a complete title, counts, a restrained accent and a separate Collapse group control, group-level watched/unwatched actions that sit above the divider and child list, sort/filter controls, a scroll-away overview panel above the episode list, a default-collapsed Browse options section, grouped-card containment that visually keeps headers and episodes together, inset episode cards with a group gutter, single-thumbnail art, numbered play badges and two-line grouped description previews, show-completion visualization, a custom video-card progress bar that avoids ambiguous endpoint dots, and snappier jump navigation that respects visible section anchors without a full-screen jump-rail overlay
- Player uses a shared Media3 session/service model with fullscreen, resume support, cast routing, YouTube-style double-tap skip confirmation feedback, roomier fullscreen-landscape controls, a polished History menu that sizes to content with a chrome-aware max height, compact supporting copy, a hardened controller-layout customization path that does not crash on playback startup, a top-pinned player surface that keeps video and playback context together, launch and notification opens that can preselect a video without forcing autoplay, cast sessions that keep the standard transport controls visible without keeping the local device awake just because playback is remote, and an offline-caption card that keeps the selected engine plus progress visible by default while moving detailed caption metrics into an expandable clinical stats section
- Library and Player share one global Look Points badge in the top app bar, and the shell can show a small centered playback icon between the title and badge while playback is active; tapping that indicator routes to Player and re-syncs the selected video with the active playback session when possible
- the Settings surface masks the saved feed URL until explicitly revealed, shows refresh errors even with cached content, and confirms clearing synced cache while preserving that feed URL
- the app no longer seeds placeholder library items on first open or after clearing synced data; instead, Library shows a clean empty-state panel until a real feed sync succeeds
- true cold starts show a brief, professional LookTube intro overlay that rotates through verified, attributed public Giant Bomb cast quotes via a shuffled deck, retains the new-video digest when available, and exposes the full quote/source catalogue in Settings, auto-dismisses with a short fade, can be skipped instantly with a tap, and does not replay when returning from background
- the Settings surface also shows offline caption model readiness, lets the user download the local caption model needed for provider-free caption generation, exposes a persistent toggle for automatically generating captions for newly discovered videos, lists videos with saved or partial caption data, and can clear all caption data or open an entry in Player without autoplay for inspection
- the Player surface can generate or regenerate captions for the selected video on-device, delete partial or completed caption data for that video, attach generated captions as explicit text tracks for local playback, and keep them available during cast handoff through explicit Cast subtitle mapping
- explicit same-video selections, launch or notification preselection opens, caption-data inspection opens from Settings, and stale remote-route recovery keep Player and cast session state aligned without forcing unwanted autoplay or leaving playback stuck on a dead remote route
- watched videos are worth 12 Look Points, app opens add 1 point once per local day, and the shell badge reflects the combined total without introducing show-completion bonus points
- newly discovered playable videos can auto-generate offline captions during manual sync or background refresh when the setting is enabled and the local model is ready, and background refresh promotes that work to a foreground-maintenance notification for better reliability while the app is backgrounded or the device is locked
- the default app build target stays on the lower-spec whisper.cpp caption path, while the opt-in `highspec` target defaults to Moonshine and still exposes Whisper.cpp as a compatibility fallback without regressing the baseline build
- the product remains explicitly feed-first and avoids unsupported website-login automation
- the main shell is covered by automated smoke validation and regular Ralph loop gates

## Sourced splash quotes
- Cold starts rotate through a shuffled, non-repeating deck of short, verbatim public quotes from verified current or former regular Giant Bomb cast members.
- Each entry records the speaker, original public article, publication date, context, and separate evidence of the speaker's Giant Bomb role. Guest authorship alone does not establish eligibility. Excerpts preserve original wording and punctuation; no invented quotes, paraphrases, or inferred speakers ship.
- The splash displays the quote, speaker, and context, remains immediately skippable by tap or accessibility action, auto-dismisses on the existing timer, and does not replay on background resume. Any new-video digest remains available alongside the quote.
- Settings exposes a scrollable Splash quotes & sources screen with every shipped entry and links to its original source. Attribution does not imply endorsement of LookTube.
- Quotes are bundled locally and require no startup network request. Unsupported or disputed entries can be removed individually without changing persisted deck storage.
- Regression coverage checks deck cycling, attribution completeness, digest coexistence, source navigation, and immediate/automatic dismissal. Physical-device visual acceptance remains a separate check.

## Browse stability and interaction polish
- Collapse choices use stable mode-qualified section keys and survive scrolling, jump navigation, filters, grouping switches, screen recreation, and app restarts. Filtering never deletes choices for hidden groups. Expand/collapse-all affects only the visible groups.
- Expanded groups keep their visual containment while individual episodes are lazy rows with stable keys. Jump anchors and scroll position calculations account for visible episode rows.
- An always-visible browse bar reserves its own space beneath the list. It exposes Top (disabled at the beginning), Jump to show/topic, and the current section without covering cards. Its height does not change when scrolling starts. The bounded section popup closes after selection, outside taps, or Back; a dismissing tap never activates underlying content.
- The scroll indicator is touch-passive: ordinary swipes at the screen edge must scroll normally and never seek to an absolute library position. Explicit section selection and accessible progress actions remain supported. Pixel measurements use density-aware conversion.
- Library search and watch-state filters are visible above results, expose a clear reset action and matching result count, and preserve browse state when returning from Player.
- Video details lead with the description and useful metadata; diagnostic fields and raw URLs require an explicit Technical details disclosure that resets on close. Details scroll with Close kept fixed. Group headers announce expanded/collapsed state and actions.
- Feed URL edits remain local drafts until Save & sync; Discard changes restores the saved URL. Reject incomplete HTTP(S) URLs, and save the complete URL before requesting its feed. Editing must not reconfigure background refresh.
- Per-video caption deletion requires confirmation for the selected video, matching the existing bulk-clear flow. Cancellation preserves data and active generation prevents deletion.
- Settings refresh errors remain visible even when a cached library exists, and clearing library/caption data requires an explanation and confirmation. Caption action chips wrap on narrow screens.
- Regression tests exercise a populated library, short edge swipes, outside/Back popup dismissal, snapshot insertion without losing the visible episode, filter/grouping switches, restoration, stable navigation, lazy rows, details, URL draft submission/discard, and confirmation cancellation. Standard validation includes Library and Player feature unit tests.

## Next slice
Improve real-device local-caption runtime so the newly exposed caption-management flow is practical for regular use.

### Acceptance criteria
- caption generation for typical real-world videos is materially faster than the current observed slow-path baseline on the user's test devices
- runtime feedback remains grounded in measured throughput and real-device comparison between the baseline Whisper.cpp path and the higher-spec Moonshine path
- any additional speedups reuse the existing local-first caption pipeline and do not regress caption-data management, cast subtitle delivery, or background auto-caption behavior
- docs, tests, and validation continue to reflect the currently shipped feed-first product shape

## Captions
- LookTube now treats offline-first captions as a supported product behavior rather than a future-only direction.
- Settings exposes local caption model readiness and a download action for the built-in on-device English model, so captions can work without any external provider once the model is present.
- Settings also exposes caption-data management so users can inspect videos with saved or partial caption data in Player without autoplay and can clear all caption data from one place.
- Player exposes per-video on-device caption generation and regeneration, shows generation progress or errors, enables the built-in CC control for turning generated captions on locally, and can delete the current video's saved or partial caption data.
- Long-running on-device caption jobs keep extraction file-backed for memory safety, conservatively skip long silent stretches before transcription, and continue to report hard transcription progress during the transcription phase itself, including speech-processed time, chunk counts, measured throughput, per-chunk wall time, and ETA once enough work has completed to estimate it; Player defaults that data to a compact engine-plus-progress presentation and exposes the full metric set through an expandable stats section.
- The default build target keeps whisper.cpp as the guaranteed local fallback, and the `highspec` target defaults to Moonshine on compatible devices while still exposing Whisper.cpp as a compatibility fallback.
- Generated captions are stored as per-video WebVTT sidecars instead of mutating feed-derived metadata.
- Cast delivery treats captions as first-class text tracks through explicit Cast mapping and sender-hosted sidecar serving, rather than assuming default Media3 subtitle propagation is sufficient.
- If a higher-quality or cloud-generated caption option is added later, keep any secondary credentials or account material in a clearly separate expandable Settings section and layer that provider on top of the existing local caption pipeline.

## Notification functional targets
- Scheduling target: a saved non-blank feed URL results in one active periodic library refresh registration owned by WorkManager plus an opportunistic immediate catch-up refresh when scheduling is refreshed and a rolling delayed catch-up fallback that is maintained after background refresh runs; clearing the saved feed URL removes all of that background work.
- Detection target: background refresh compares the latest synced snapshot only against the previously persisted snapshot for the same feed URL, using video IDs as the change detector.
- Silence target: no library-update notification is posted for first-time sync, empty snapshots, feed switches, unchanged snapshots, or when notification permission is unavailable.
- Visibility target: each successful refresh that discovers at least one new video posts a distinct `Library updates` notification entry that opens the newest discovered video in the player for review when tapped without forcing autoplay.
- Device-validation target: notification permission granted, a scheduled `LibraryRefreshWorker` job visible on-device, and repeated notification entries observable from the same installed build when distinct latest video IDs are posted.

## Ralph loop definition
1. pick the thinnest user-visible slice from this spec
2. update or add acceptance criteria first, including any spec changes required by the new design
3. add the smallest failing automated check that proves the slice is incomplete
4. implement the smallest change that makes the check pass
5. run `verifyFast`
6. update docs, transferable specs when applicable, and learnings while context is fresh
7. run `verifyLocal` before merging the slice

## Open questions
- Does the validated copied-feed path hold across additional real Premium feed variants?
- Which additional Giant Bomb feed or page metadata should be folded into show-grouping heuristics?
- What is the lightest-weight visual regression strategy that fits the current Android/Compose workflow?
- How aggressively do Android standby quotas delay WorkManager execution for LookTube on real daily-driver devices that have not opened the app recently?

## Optional autoplay
- Autoplay next video defaults to enabled on new installations and upgrades. One persisted preference is exposed in Settings and Player and survives restart; it does not change automatic caption generation.
- Continue only after an item that actually played reaches its end with play intent still active. Preview-only selections, paused ends, stop/idle, errors, and toggling the setting on after completion do not start another item. Consume each completion once and recheck state before handoff.
- Choose playable, unwatched videos from the cached library, independent of browsing filters. Exclude the current video and videos completed in this playback-service session. Prefer the next chronological episode in the same show and inferred topic, then the next same-show episode, then same-show unwatched backfill. Use shared show/topic heuristics.
- If no same-show continuation exists, rank shows using the last twenty played-video history records, with stronger weights for more recent records; manually marking watched alone does not establish affinity. Prefer an incomplete resume in the selected show, otherwise its oldest unwatched item. Without relevant history, choose the newest unwatched playable item. Break ties by stable video ID; missing dates sort last for continuation.
- Player shows the proposed next title and a short reason, with Play next now available even when autoplay is disabled. The service recomputes at completion against current watch state and history. Resume incomplete progress; explicitly unwatched items with completed progress restart at zero. An exhausted library stops.
- The shared Media3 service owns continuation for local/background playback and the existing cast route. Preserve metadata, saved captions, watch progress, and fullscreen state. Recommendations are deterministic and on-device, with no external AI service or transmission of watch history.

## Group header and episode hierarchy
- Both group states use one neutral structural header with SHOW GROUP or TOPIC GROUP, fully wrapping title/counts and a minimum-48-dp explicit Expand group / Collapse group button. The full header toggles only expansion.
- Preserve the card-pack metaphor with the group's decorative thumbnail stack in the persistent header, tucked collapsed previews and coordinated vertical contraction/reveal of individually lazy episode rows.
- Episode cards are inset beside a connecting gutter and show a numbered play badge over their single-thumbnail artwork. Grouped descriptions preview two lines with Read more for overflow. Group watch actions explicitly say Mark all watched/unwatched; episode watch actions stay individually scoped.
- Preserve stable episode keys, persisted collapse choices, filtering, jump navigation, autoplay, and individually lazy episode rows. Check native collapse touches do not trigger playback or toggle twice, group actions do not play a video, and the collapse control remains reachable at 320-dp width / 2x text. Use physical-device screenshots and a transition recording for visual acceptance separately from JVM fixtures.

## Isolated debug iteration
- Debug builds use a separate `.debug` application ID suffix and LookTube Debug launcher label, allowing baseline/highspec debug installs alongside their normal releases. Release application IDs and signing remain unchanged.
- Prefer debug deployment and physical UI/motion checks before requesting release signing. The optional seed helper populates only a selected debug package with 40 non-playable fixture videos; it does not read normal account data. Real artwork/data acceptance remains a separate final check on the signed normal app.


## Unified surfaces and continuous group motion
- Use the shared 24-dp structural panel shape, neutral surface treatment and thin outline across Settings, Library and Player. Specify every Material surface-container tone in the slate/gold and warm-light palettes so default cards, dialogs and menus cannot introduce an unrelated tint. Keep semantic status colors and media readability overlays.
- A group uses the same header composition, title, metadata, artwork stack and explicit 48-dp expand/collapse control in both states. Collapsed groups show compact episode edges tucked under that header; do not swap between an artwork hero and an opaque structural header.
- Contract/reveal individually lazy episode rows with one 300-ms vertical motion. Clip the contracting rows without translating their lower controls into view; reveal compact previews in the latter portion of contraction and retract them early on expansion. Retain exiting rows until contraction completes, disable their playback during contraction, and support reversing a toggle mid-transition. Header identity and bounds stay fixed. Transient render retention must not change the persisted collapse choice or make far-offscreen episodes eager.
- Prefer physical debug-build motion and layout iteration before release signing. Automated continuity/reversal, touch safety, persistence, jump indices and large-text checks supplement native inspection.


## Local CI acceptance lanes
- Adopt `verifyAllLocal` (or the report-producing `python scripts/local_ci.py full`) before committing: docs, all four package identities, runner regression tests, JVM/interaction tests for both flavors, both-flavor screenshots, both-flavor lint and debug app/instrumentation APK builds. No signing credentials or product account are required.
- Keep the quick `verifyFast` loop and opt-in managed-device lane. Explicit-serial native device checks render synthetic non-playable Library fixtures in a debug-only test Activity and cover contraction/reversal, scrolling/collapse persistence, outside-window dismissal and constrained large text. Capture native evidence per run and fail on partial/zero/crashed instrumentation even if ADB exits zero.
- Save unique local-CI reports with source/dirty-state metadata, step logs, failures and durations; record debug APK hashes and native screenshots for device runs. A device test pass is not production-artwork, frame-pacing, casting or playback acceptance.

- Native large-text evidence must keep the group role label readable rather than wrapping letter by letter beside the control. Group role/control rows wrap as units when the available width or font size requires it.


## Overlapping group card packs
- Keep Mark all watched/unwatched inside the unified header in both states. It remains a separate minimum-48-dp action; do not reserve an exposed strip between header and episodes for it.
- The header sits above the first expanded episode with a 24-dp overlap. Move that episode's play/position badge below the overlap so header controls cannot obscure it. Preserve independently lazy rows, their video keys and jump indices.
- Collapsed previews tuck 78 dp of their 100-dp card face under the header, leaving a 22-dp front edge and equally stepped rear card edges. These are decorative artwork/border strips, without cropped episode-title text. The header draws and receives touches above the overlapped cards.
- Clip contracting rows at their animated lower edge while allowing the tucked upper region to draw under the header. Verify both geometry and real phone appearance: the group watch action stays inside the header, the expanded card starts above its bottom edge, and collapsed rear edges extend below the front edge.
