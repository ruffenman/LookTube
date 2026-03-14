# LookTube Warp notes
- Treat `docs/spec/product-spec.md` as the source of truth for what to build next.
- Run `.\gradlew.bat verifyFast` after each thin Ralph loop slice.
- Run `.\gradlew.bat verifyLocal -PskipManagedDevice=true` before committing unless you also validated the managed-device lane.
- Never commit live Giant Bomb credentials, cookies, or raw authenticated feed responses.
- Password input in the current app slice is session-only; do not assume credentials persist across restarts.
- Signing out currently clears the saved username and active session password but preserves the feed URL for convenience.
- Record integration findings in `docs/integration/giantbomb.md` before changing implementation assumptions.
- Record architecture tradeoffs as ADRs under `docs/decisions/`.
- Keep this file short; deeper context belongs under `docs/`.
