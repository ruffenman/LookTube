# Group hierarchy and card-pack treatment

The expanded group header previously used a full-width, 236-dp artwork mosaic and a small minus affordance. Its silhouette and imagery closely resembled an episode thumbnail. Group-wide watch actions also used the same wording as individual video actions. This made collapse targets harder to distinguish from playback targets.

The revised expanded header is a shorter opaque structural panel. It names its role as SHOW GROUP or TOPIC GROUP, exposes the title and counts without placing text over artwork, and provides a labelled Collapse group button with at least 48 dp of height. The entire header remains a collapse target. The expanded group watch action says Mark all watched/unwatched, separately from episode watch actions.

Preserve the user's card-pack metaphor. A small, decorative stack uses the group's actual thumbnails inside the expanded header. Collapsed groups keep the existing mosaic, lead card and tucked preview cards. Header resizing and lazy item placement/fades animate expansion and contraction. This is a connected stack treatment, rather than a literal shared-element morph of every full episode card: episode rows remain individually lazy and retain stable keys.

Episodes sit in an inset column with a connecting gutter. Numbered play badges distinguish playable video cards from structural group chrome without relying on color alone. Grouped description previews use two lines, with the full description available through Read more. The single full thumbnail stays on each episode.

Touch regression checks cover the dedicated collapse control (including avoiding a double toggle through the parent), actual episode selection, group watch action scoping, and the collapse action at 320-dp width / 2x text. Existing collapse persistence, edge-scroll safety, popup dismissal, lazy composition and refresh-anchor checks remain required. Light and dark JVM fixtures are regression checks, separate from the physical-device screenshots and transition recording needed for visual acceptance.

This pass does not introduce a separate compact-density preference or redesign the collapsed artwork gallery. It applies a clearer hierarchy to the existing browse layout while retaining its stack identity.
