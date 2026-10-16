# Group hierarchy validation: 2026-10-16

Candidate version: 0.1.9-highspec, version code 9. Signing and physical-device acceptance are pending.

## Automated checks

`verifyFast`, screenshot recording, `verifyLocal -PskipManagedDevice=true`, `verifyHighspec`, and `:app:assembleHighspecRelease` passed. Baseline and highspec each pass 120 app tests with zero failures/errors/skips; Library's 24 feature tests and Player's 20 feature tests remain passing. Lint and screenshot verification passed. Logs are under ignored `artifacts/group-hierarchy-*.log`.

- Dedicated collapse touch must only collapse the group, without playing a video or toggling twice through the parent header.
- Episode selection must play only the chosen episode and retain the expanded group state.
- The explicit group watch action must update exactly that group's video IDs without playing or collapsing anything.
- The collapse button must remain reachable at 320-dp width and 2x text, with a minimum 48-dp height at standard density.
- Retain existing edge-swipe safety, stable refresh anchoring, collapse persistence, independently scrolling jump-menu dismissal and lazy episode-composition checks.
- Record and verify focused light/dark expanded-group fixtures, the browse overview, and the preserved collapsed-stack fixture. The collapsed image has only a two-pixel border antialiasing difference after adding header-size animation.
- Transfer package YAML and TOML agree on version 0.2.22.

## Physical-device checklist

Verify signed artifact source, checksum, certificate, package/version and in-place data preservation before updating the normal highspec app. Capture the same real Grubbsnax Premium group used for the before image. Check title/count readability, the miniature artwork stack, group/episode separation and full-size collapse hit target. Record collapse and expansion to assess the stack motion with real artwork. Restore the original expanded/collapsed choices and All filter; do not change watch state or start a video merely to test collapse.

Local before evidence: ignored `artifacts/groups-before.png`, captured through ADB from the 0.1.8 release. No feed credentials or authenticated responses belong in committed evidence.
