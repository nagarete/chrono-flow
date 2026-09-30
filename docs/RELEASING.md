# GitHub release setup

Release builds require a persistent signing key, distinct from the disposable development key. Keep encrypted backups: future APK updates must use the same certificate. Never commit a keystore or password.

Configure these GitHub Actions secrets in the repository:

| Secret | Value |
| --- | --- |
| `CHRONO_KEYSTORE_BASE64` | Base64-encoded release keystore. |
| `CHRONO_STORE_PASSWORD` | Keystore password. |
| `CHRONO_KEY_ALIAS` | Signing key alias. |
| `CHRONO_KEY_PASSWORD` | Signing key password. |

Update `versionCode`, `versionName`, and `docs/RELEASE_NOTES.md`. Push a tag exactly matching `v` plus `versionName`. The workflow checks versions and required secrets, runs tests/lint, builds a signed release, and publishes only to GitHub Releases, with `SHA256SUMS`.

For local release signing set `CHRONO_KEYSTORE` to an absolute keystore path and the three password/alias variables, then run `./gradlew :app:assembleRelease`. Without a keystore, release builds are intentionally unsigned and must not be presented as installable releases. Debug builds are installable but cannot be upgraded directly to a differently signed release; uninstalling a debug build resets its local attention metadata.

The repository `nagarete/chrono-flow` is configured with these secrets and has published `v0.1.0` as a pre-release. The release signing key and its passwords are held outside the repository (locally in ignored `.tools/`); back them up, because every future APK update must use the same certificate. Any later owner setup beyond rotating that key or secret is not already completed.
