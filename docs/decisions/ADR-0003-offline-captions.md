# ADR-0003: Offline-first caption generation with explicit Cast subtitle delivery
## Status
Accepted
## Context
The remaining captions request required a local path first, with no dependency on optional external providers. LookTube's current validated Giant Bomb playback path commonly exposes direct progressive `.mp4` URLs, so the app can decode audio locally from the same playback target it already hands to Media3. The Android implementation also needs to keep `minSdk = 28` support and preserve x86_64 validation coverage for local development.

Research ruled out simpler Android caption options as the primary path for this slice. Public `whisper-android` examples appeared focused on plain-text transcription rather than timestamped segments, which is a poor fit for subtitle generation. Moonshine remains interesting for future research, but its currently published Android artifact appeared mismatched with LookTube's current support matrix, including a higher minimum SDK and narrower ABI assumptions than this app can currently accept.

The cast path introduced a second constraint: locally generated subtitle sidecars are not reliably preserved by default Media3 cast conversion alone, so the app needed an explicit sender-side subtitle delivery contract.
## Decision
Adopt an offline-first caption architecture built around a vendored `whisper.cpp` native integration.

- manage a local caption model on-device and expose its readiness plus download state from Auth
- use the local model to generate captions on-device from playable video audio without requiring any external provider
- store generated captions as per-video WebVTT sidecars instead of mutating feed-derived video metadata
- attach those sidecars to local playback as explicit `MediaItem` subtitle configurations and enable the standard CC control in the player surface
- replace the default cast subtitle assumption with explicit Cast text-track mapping, and serve local sidecar files over a lightweight sender-hosted HTTP endpoint so receivers can fetch them during cast playback
- keep any future provider-backed caption flow optional and layered on top of the same caption pipeline rather than replacing the local fallback
## Consequences
### Positive
- captions remain available even when the user has not configured any advanced or cloud-backed provider
- the implementation matches the validated feed-first playback boundary instead of requiring browser-login or session automation
- generated captions have a durable local representation that can be reused for resume, regeneration, local playback, and cast handoff
- caption delivery during cast sessions becomes explicit and testable rather than depending on undocumented Media3 receiver behavior
### Negative
- the app now owns native build, vendoring, model download, and JNI maintenance complexity
- local caption generation increases storage, CPU, and battery costs on the user's device
- the first shipped local model is a quality-versus-size compromise rather than the highest-accuracy possible model
## Follow-up ADRs
- whether a future provider-backed caption path is worth the added complexity on top of the local fallback
- whether LookTube should switch models or engines once another on-device solution matches the current SDK and ABI requirements better than the present `whisper.cpp` path
