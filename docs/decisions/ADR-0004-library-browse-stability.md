# ADR-0004: Durable browse state and lazy grouped episodes

## Decision

Persist non-sensitive collapse choices, grouping mode, and sort order in feature-owned local preferences. Collapse keys include the grouping mode and stable group identity. Filtering or switching grouping never prunes hidden keys; expand/collapse-all changes only visible groups. Keep transient search and watch filters in saved UI state.

Render each expanded episode as a separately keyed lazy-list item. Use a shared surface and rounded final row to retain the visual grouping. Compute jump anchors from the actual visible header and episode rows, including collapsed sections.

Anchor Top and the explicit Index toggle to the browse viewport. The section index scrolls independently beneath Top; selecting a destination closes it. The scroll track accepts taps, drags, and accessibility progress actions.

## Rationale and tradeoffs

An expanded group previously composed its whole episode list inside one lazy item. Recycling or rebuilding section state could expose many unwanted episodes and make navigation expensive. Separate rows bound composition to the viewport while durable keys preserve the user's choices across filtering, route changes, and relaunches.

Persisted keys can outlive a feed snapshot. This small preference set is retained deliberately so temporary filters and refreshes cannot silently expand groups. It contains group identifiers rather than feed credentials or video descriptions. The explicit Index panel can cover content while open, but it closes on selection and its opaque surface keeps labels readable.

## Validation

Library interaction tests cover persistence, grouping changes, independent index scrolling, Top, lazy composition, and accessible track progress. Jump-anchor unit tests account for expanded episode rows. Physical-device screenshot checks remain required for visual acceptance.

## 2026-10-16 gesture-safety revision

Physical-device and interaction-test reproduction showed the 48-dp overlaid track converting ordinary edge swipes into enormous absolute jumps. Replace touch seeking with a passive indicator and keep explicit accessible progress actions. Reserve an always-visible browse bar for Top and Jump to show/topic. Use a focusable native popup for outside/Back dismissal and consume the dismissing event. This supersedes the original overlaid Top/Index and touch-track decision above.
