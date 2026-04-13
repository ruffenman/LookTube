# Security policy

## Reporting a vulnerability
If you discover a security issue in LookTube, do not open a public issue with sensitive details.

Prefer GitHub private vulnerability reporting for the repository when it is available. If a private reporting channel is not yet configured, open a minimal public issue without exploit details, secret-bearing URLs, raw payloads, or personal data, and state that you need a private contact path for follow-up.

## Sensitive data handling
Do not include any of the following in issues, pull requests, logs, screenshots, or shared repro steps:
- copied Giant Bomb Premium feed URLs
- raw authenticated RSS payloads
- playback URLs that contain secret-bearing query parameters
- local keystores, signing material, or private keys

When reporting bugs, sanitize URLs and payloads before sharing them.

## Supported versions
Security fixes are expected to land on the current `main` branch and in the most recent GitHub release artifacts.
