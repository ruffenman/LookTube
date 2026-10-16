# Browse gesture and UX acceptance: 2026-10-16

## Build and automated validation

- Application source: `1879e28f53a96cfb1678855d776449c85f7e8a65`.
- Installed package: `com.looktube.app.highspec`, version `0.1.7-highspec`, version code 7.
- Signed build: [GitHub Actions run 38926678396](https://github.com/ruffenman/LookTube/actions/runs/38926678396), successful validation, signing, and upload after the user's release-environment approval.
- APK SHA-256: `eb6c3f8d0fb7eff166e2ce30b0de5a0e5d39c7bfb1ce24a15e011c7b6a4093a8`.
- Local `verifyFast`, screenshot recording, `verifyLocal -PskipManagedDevice=true`, `verifyHighspec`, and `:app:assembleHighspecRelease` passed. Baseline and highspec each passed 102 app tests; Library passed 24 feature tests and Player passed 20. Screenshot verification and app lint gates passed.
- Regression checks cover edge swipes, collapse persistence, snapshot insertion and rename preserving the visible episode's position, independent popup scrolling, outside and Back dismissal, narrow layouts with enlarged text, details disclosure and overflowing description, feed draft cancellation/submission, and caption-delete cancellation/confirmation.
- The transferable package YAML parses, with manifest and spec version 0.2.20 in agreement.

## Physical-device checks

Checked the signed release on a Pixel 10 Pro XL, API 37, in portrait at its existing display settings. Screenshots came from ADB on the physical phone, separately from JVM screenshot baselines.

1. Reproduced the old release's defect: an ordinary short swipe on the invisible 48-dp right-edge rail jumped from early collapsed groups to a distant Blight Club episode. The regression test reproduced a scroll-value change from 500 to 17,500. The new rail is a touch-passive visual indicator.
2. Verified artifact checksum, exact source commit, package/version, and matching release signing certificate before an in-place `adb install -r` update. The installed APK checksum matches the artifact. The first-install timestamp and credential-encrypted data-directory inode remained unchanged. Only the normal highspec package is installed.
3. The existing library retained 300 videos, 163 watched, 2,006 Look Points, and 28 of 32 groups open.
4. Repeated short swipes across the former wide rail region and the new narrow indicator. Both scrolled normally within nearby content instead of jumping to distant sections.
5. The browse bar remains visible below the list. Opened the popup and tapped outside directly over a collapsed group's expand button. The popup closed without expanding the group; the overview still reported 28 of 32 groups open.
6. Independently scrolled the section menu. The fixed Back to top label retained its top Y coordinate of 1,222 physical pixels. Android Back dismissed the popup while retaining the Library screen. The popup's Back to top action returned to the overview.
7. Selected Grubbsnax Premium in the popup. The popup closed and the selected group appeared at the destination.
8. Opened real video details: the description appears first, technical URLs and IDs are initially hidden, and Close dismisses the dialog. Actual long-description overflow is covered by the automated fixture.
9. Edited the masked feed field to an invalid local draft. Validation appeared. Discard restored the original field value without requesting a sync. No credential value was printed or added to the report.
10. Opened a saved partial-caption entry in Player without autoplay. Delete partial data opened a confirmation naming the video. Cancel preserved all five caption-data entries.
11. Returned to Library and Top with All selected. A final physical screenshot confirms 300 of 300 videos, 163 watched, 2,006 Look Points, and 28 of 32 groups open.

Local evidence is under ignored `artifacts/looktube-mechanics-*`, `artifacts/ux-mechanics-*`, and `artifacts/highspec-device-run-38926678396/`. Temporary device UI dumps were removed. No saved feed URL or raw authenticated response is committed. UIAutomator sometimes could not reach idle after opening the native player; those captures were not used as fresh UI evidence, and physical screenshots confirmed the displayed state instead.

Managed-device instrumentation was skipped. Other physical devices, landscape, and changed physical font scales were not part of this acceptance pass; the narrow 320-dp / 2x-font layout is covered by JVM interaction tests. Proposed styling in the design review is separate from the installed mechanical fixes. The HTML comparison's script passes syntax checking, but its browser rendering was not accepted visually because the in-app browser blocks local file URLs.
