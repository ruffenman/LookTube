# UX mechanics and visual review — 2026-10-16

## Mechanical changes in this pass

| Finding | Evidence and resulting behavior |
| --- | --- |
| Ordinary edge swipes become enormous jumps | The old 48-dp rail intercepted the right side of cards. A short swipe reproduced a scroll value change from 500 to 17,500 in the interaction test; the installed release also jumped from the first few collapsed groups to a much later Blight Club episode. The replacement indicator is touch-passive. Explicit section selection and accessible progress actions remain available. |
| Navigation is hidden until scrolling and covers card content | A permanently allocated browse bar contains Top and a labelled Jump to show/topic control with the current section. It is available at the overview, reserves its own layout space, and does not alter list height when scrolling starts. Top is disabled at the beginning. |
| Popout stays open after outside taps | The section list uses a focusable native popup with outside-touch and Back dismissal. Selection also closes it. The dismissing tap must not activate a card or expand a group underneath. |
| Feed edits have immediate persistent effects | Typing previously saved each partial URL and reconfigured background refresh. A local, unpersisted draft now supports Discard changes and explicit Save & sync. Invalid or incomplete HTTP(S) URLs cannot be submitted. The view model saves the complete URL before requesting its feed. |
| Read more opens with diagnostics before the description | Details now begins with the description, followed by useful watch/date/duration metadata. Video IDs, feed category, and raw URLs require opening Technical details. The disclosure resets when the dialog closes, and Close remains fixed. |
| Per-video caption deletion is immediate | Deleting completed or partial captions now requires a confirmation naming the video. Cancel leaves captions intact. Active generation disables deletion and dismisses a pending confirmation. |

## Layout and interaction checks

- Retain stable episode keys and persisted group keys. A feed snapshot inserting a newer group must preserve the visible episode's position.
- Keep real thumbnails at a fixed aspect ratio; loading or missing artwork must not change row geometry.
- Keep the browse bar and popup bounded at narrow widths and large font scales. The popup scrolls independently and marks the current section through selection semantics as well as color.
- Use a transient popup rather than persisting its open state across app restarts or configuration changes.
- Retain explicit Play/selection behavior, no autoplay from notifications or caption inspection, existing fullscreen/history/cast behavior, and the cached-library fallback. Those pipelines are not redesigned by this UI pass.
- Exercise ordinary swipes across the former rail region, popup dismissal, collapse persistence, snapshot insertion, feed draft cancellation/submission, and caption-delete cancellation with regression tests. Physical-device appearance and gestures remain separate acceptance checks.

## Stylistic proposals

These are proposed directions, not additional shipped redesigns. The [illustrative style comparison](browse-style-proposals.html) lets you compare three layouts, light/dark palettes, and larger text using sample content.

| Priority | Proposal | Concrete treatment and tradeoff |
| --- | --- | --- |
| 1 | Offer a compact browse density | Use shorter group headers, a small artwork strip, and one restrained preview instead of several full-width stacked peeks. Show more actual episodes per screen. Keep the current artwork-rich density as an option. This is the strongest improvement for long browsing sessions. |
| 2 | Calm the artwork behind group titles | Use one primary image with an optional small mosaic accent; give the title an almost opaque neutral plate or a consistent dark scrim. Keep vivid thumbnails, while reducing repeated logos and competing image edges. |
| 3 | Reserve yellow for actionable emphasis | Keep yellow for the primary play/sync action, selected navigation, and progress. Give successful Settings status a neutral card with a small status pill instead of a large gold field. This reduces competition with video artwork. |
| 4 | Tighten the information hierarchy | Lead with episode title, then show/date/duration, then a two-line description in compact mode. Keep watch state and its action together. Use a smaller single-line Look Points badge so the top bar has more breathing room. |
| 5 | Unify shape and spacing | Use a small consistent radius/spacing scale across episode cards, group containers, sheets, and chips. Reduce the current nested rounded-card layers and duplicate outlines while retaining visible group boundaries. |

Recommended next visual direction: compact browsing plus calmer group artwork, using the existing dark palette and yellow accent. Avoid a wholesale theme change until those two options have been compared on the real populated library. Check both light and dark themes, landscape, and enlarged text before making either density the default.
