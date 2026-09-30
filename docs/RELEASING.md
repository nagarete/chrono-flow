# GitHub release setup

Release builds require a persistent signing key, distinct from the disposable development key. Keep encrypted backups: future APK updates must use the same certificate. Never commit a keystore or password.

Configure these GitHub Actions secrets in the repository:

| Secret | Value |
| --- | --- |
| `CHRONO_KEYSTORE_BASE64` | Base64-encoded release keystore. |
| `CHRONO_STORE_PASSWORD` | Keystore password. |
| `CHRONO_KEY_ALIAS` | Signing key alias. |
| `CHRONO_KEY_PASSWORD` | Signing key password. |

Update `docs/RELEASE_NOTES.md`, then run `python3 scripts/prepare-release.py patch` (or `minor` / `major`). The helper increments `versionCode`, bumps the numeric `versionName`, commits those values with the release notes, and creates the matching annotated `v*` tag. It requires a clean worktree except for changes to the release notes. Review the created commit and tag, then push the branch and tag using the command it prints. Pushing the tag starts the release workflow.

The workflow checks the tag/version match and required secrets, runs tests/lint, builds a signed release, and publishes only to GitHub Releases, with `SHA256SUMS` and `update.json`. The metadata generator reads the built APK’s version with SDK `aapt` and records its download URL, byte size, and SHA-256. Do not edit the APK after metadata generation.

For local release signing set `CHRONO_KEYSTORE` to an absolute keystore path and the three password/alias variables, then run `./gradlew :app:assembleRelease`. Without a keystore, release builds are intentionally unsigned and must not be presented as installable releases. Debug builds are installable but cannot be upgraded directly to a differently signed release; uninstalling a debug build resets its local attention metadata.

The repository `nagarete/chrono-flow` is configured with these secrets and has published `v0.1.0` as a pre-release. The release signing key and its passwords are held outside the repository (locally in ignored `.tools/`); back them up, because every future APK update must use the same certificate. Any later owner setup beyond rotating that key or secret is not already completed.

## In-app updates

The updater reads `https://github.com/nagarete/chrono-flow/releases/latest/download/update.json`. Publish updater-compatible releases as normal releases: GitHub’s latest endpoint excludes pre-releases. Keep the existing signing key and increase `versionCode` for every update. The app rejects APKs signed with a different current certificate; signing-key rotation is not supported by this updater. Never replace an APK asset without regenerating its metadata.

The existing v0.1.0 APK has no updater, so its users must manually install the first release containing this feature. Subsequent releases can be downloaded and installed from inside the app, with Android confirmation. This change does not publish a release or change repository secrets.
