# Validation — 2026-09-30

## Executed checks

- JDK 17, Gradle 8.13, Android Gradle Plugin 8.13.2, Android SDK/build-tools 35.
- Debug build: passed. Installable development APK: `app/build/outputs/apk/debug/app-debug.apk`.
- JUnit: **14 tests passed**, zero failures/errors: 8 attention-state tests and 6 lock-policy tests. [Machine-readable results](validation/unit-tests.json).
- Debug and release Android lint: passed with no errors. Remaining warnings include newer available toolchain/target versions and intentional fixed-right-edge overlay placement. No error baseline was added.
- Minified/resource-shrunk release build: passed, approximately 53 KiB unsigned.
- Local release-signing configuration: passed with the development key, producing an approximately 57 KiB APK whose signature verifies. This is a pipeline check, not a public release signing identity.
- APK permission inspection: only the optional `SYSTEM_ALERT_WINDOW` use-permission. Listener/tile services are guarded by Android signature binding permissions. No network permission or native runtime libraries.
- Release runtime dependency report: no external dependencies.
- GitHub workflow YAML: parsed successfully. Hosted CI/release publication has not been executed.

## Android emulator

An isolated API 35 Google APIs x86_64 emulator, 1080×2400 at 420 dpi, ran **5 instrumentation tests successfully**. [Runner output](validation/emulator-tests.txt).

1. Real Android shell notification arrival, visibility-based NEW → EARLIER transition, updated content returning to NEW, and listener cancellation.
2. Native NEW/EARLIER rendering, expansion, and a real `RemoteInput` pending-intent reply using synthetic UI fixtures.
3. Setup surface rendering.
4. Actual Quick Settings tile launch from the home screen.
5. A PIN-secured locked panel: private titles/bodies absent, secret notifications omitted, and a lock-screen glance leaves notifications NEW. The emulator's temporary PIN was removed by test cleanup.

Screenshots use instrumentation fixtures, not real personal notifications. `FLAG_SECURE` is temporarily cleared by the separately installed test APK for capture; the production app exposes no such switch.

| Panel | Locked view | Setup |
| --- | --- | --- |
| [Screenshot](screenshots/test-screenshots/panel-new-earlier.png) | [Screenshot](screenshots/test-screenshots/panel-locked.png) | [Screenshot](screenshots/test-screenshots/setup.png) |

## Remaining validation

- Physical Poco X3 NFC and Xiaomi 17T Pro, including actual firmware versions, OEM listener/overlay survival, locked Quick Settings access, app-specific lock-screen controls, and original-app navigation/action compatibility.
- Android 10/API 29 on-device execution and newer physical Android releases; code/API compatibility is checked by lint, not by running these OS versions.
- Battery and resource comparisons on the physical devices. Event-driven architecture and APK size do not prove negligible battery impact.
- Authentication success/cancellation across OEM keyguards, choice-only/data-only action behavior across real apps, large-font/TalkBack/landscape use, and reboot behavior.
- Stable release key provisioning and a real GitHub Actions release run.

The app is an early device-testing implementation. It provides a companion panel over the lock screen; replacing the privileged stock notification shade/native lock-screen list is outside an ordinary APK's capabilities.
