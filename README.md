# LookTube
LookTube is an Android-first Giant Bomb companion app built around a simple flow: paste a copied Giant Bomb Premium feed URL, sync it into a local library, and watch Premium video content without depending on a browser session.
## Unofficial status
LookTube is an independent fan project. It is not affiliated with, endorsed by, or published by Giant Bomb.
## What it does
- syncs a copied Giant Bomb Premium feed URL into a local library
- supports grouped library browsing, playback resume, fullscreen playback, and cast-aware playback flows
- keeps the product feed-first instead of relying on browser-login automation
- supports local caption-generation paths, with `baseline` as the default lower-spec target and `highspec` as an opt-in higher-spec target that defaults to Moonshine while keeping Whisper.cpp available as a fallback
## Releases
Public APK builds, when available, are published in GitHub Releases.
- prefer the baseline APK unless a release explicitly says otherwise
- verify the published checksum before installing
- installed caption-model downloads are pinned to immutable upstream revisions and verified with SHA-256 before activation
- read the release notes for supported Android versions, flavor differences, and known limitations
## Privacy
- copied Premium feed URLs are stored on-device and are intended to remain local to the app
- the project does not rely on any LookTube-operated backend or account system
- project tooling and probes are intended to avoid emitting raw authenticated feed payloads or full secret-bearing feed URLs
## Development quickstart
If `local.properties` is missing, bootstrap it first:

```powershell path=null start=null
pwsh -NoLogo -File .\scripts\Bootstrap-LocalAndroid.ps1
```

Common validation commands:

```powershell path=null start=null
.\gradlew.bat verifyFast
.\gradlew.bat verifyLocal -PskipManagedDevice=true
```

For contributor workflow, connected-device deployment, integration probes, screenshots, and release packaging, see `CONTRIBUTING.md`.
## Documentation map
- `CONTRIBUTING.md` - contributor setup, validation, deployment, and release workflow entry point
- `SECURITY.md` - responsible disclosure and sensitive-data reporting guidance
- `THIRD_PARTY_NOTICES.txt` - third-party attribution notes for bundled and flavor-specific components
- `WARP.md` - short operational instructions for future dev-agent sessions
- `docs/architecture/overview.md` - module boundaries and data flow
- `docs/integration/giantbomb.md` - validated external integration notes and open risks
- `docs/releases/github-releases.md` - Android signing, GitHub release packaging, and checksum workflow
- `docs/testing/local-ci.md` - local validation and device-check guidance
- `docs/spec/product-spec.md` - living scope, milestones, and acceptance criteria for shipped behavior
- `docs/spec/reproducible-project-spec.md` - implementation-oriented transferable project contract
## License
LookTube is licensed under the MIT license in `LICENSE`.

Third-party and bundled-component attribution notes live in `THIRD_PARTY_NOTICES.txt`. Public release artifacts should preserve any required upstream notices for bundled or flavor-specific components.
