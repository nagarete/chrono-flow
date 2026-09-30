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

The initial repository has no configured GitHub remote or release signing identity. Publishing and key provisioning are owner setup steps, not actions already completed.
