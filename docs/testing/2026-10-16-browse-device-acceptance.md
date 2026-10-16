# Browse and Settings acceptance: 2026-10-16

## Build and automated validation

- Application source: `7b9ef7bd9e38f7d75dcb54b70db0fce70d55df6a`.
- Installed target: `com.looktube.app.highspec`, version `0.1.6-highspec`, version code 6.
- Signed build: [GitHub Actions run 38860862471](https://github.com/ruffenman/LookTube/actions/runs/38860862471), successful validation, signing, and upload.
- APK SHA-256: `445636aa6f893d68999d064eb8d22b080f33506908da08380cb19a495408d067`.
- Local `verifyLocal -PskipManagedDevice=true`, `verifyHighspec`, and `:app:assembleHighspecRelease` passed. Baseline and highspec each passed 92 app tests; Library passed 25 feature tests and Player passed 20. Screenshot verification and both app lint gates passed.
- The transfer package YAML parses successfully, and its manifest and spec versions now agree.

## Physical-device checks

Checked the signed release on a Pixel 10 Pro XL, API 37, in portrait. Screenshots came from ADB on the physical phone, separately from the JVM screenshot baselines.

1. Verified artifact checksum, exact source commit, package/version, and release signing certificate against the installed app before using an in-place `adb install -r` update.
2. Verified the installed APK checksum equals the artifact. The original first-install timestamp and credential-encrypted data-directory inode stayed unchanged. Only the normal highspec package remained installed.
3. The existing library retained 300 videos, 161 watched, and 1,982 Look Points.
4. Collapsed This Is the Run, scrolled away, opened Index, independently scrolled its section list, jumped to Endurance Run, then used Top to return. The original group stayed collapsed, with the overview showing 31 of 32 groups open.
5. Top retained physical bounds `[1092,440][1332,584]` while the index scrolled. Index closed after destination selection, and Top returned directly to the overview.
6. Force-stopped and cold-launched the release. The same group remained collapsed.
7. Watched narrowed the list to 161 videos. Search for Bombcast narrowed it to 19 videos. Reset filters restored all 300.
8. Settings showed the saved feed URL masked with an explicit Show control. Clear synced data opened a scoped confirmation; selecting Cancel preserved the library and watch totals.
9. Opened real video details and swiped the content. Close remained reachable at the same position and dismissed the dialog.

Local diagnostic files and screenshots are under ignored `artifacts/ux-*` and `artifacts/looktube-ux-*` paths. They are not bundled into the transferable package. Confirmation execution is covered with fixtures; the real library check uses cancellation. Managed-device instrumentation, other physical devices, landscape, and additional font scales were not part of this physical acceptance pass.
