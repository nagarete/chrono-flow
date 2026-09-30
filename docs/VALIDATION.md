# Validation — 2026-09-30

## Optional swipe and banner controls

- Current debug build, Android lint and all **14 existing unit tests** pass via `scripts/check.sh`.
- **4 new API 35 emulator instrumentation tests** pass in 18.987 seconds. [Runner output](validation/override-tests.txt). They cover actual top-left gesture interception and native right-side shade access, popup arrival/update/deduplication/hiding/opening/removal, expiry, lock/screen-off privacy, DND, low-importance and critical-notification exclusions, feature disable and listener-access revocation.
- All **5 existing panel emulator tests** also pass on this build in 12.606 seconds. [Regression output](validation/override-panel-tests.txt). Current debug APK: [SHA-256](validation/override-debug-apk.sha256).
- The first gesture test exposed cancellation by SystemUI before the original drag threshold. Lowering the threshold to 12 dp fixed the real injected top-edge swipe; no screen-content retrieval or gesture injection was added.
- New controls are opt-in and disabled by default. Accessibility is system-bound and protected by `BIND_ACCESSIBILITY_SERVICE`; screen-content retrieval and gesture injection are disabled. No new use-permission, runtime dependency, DND manipulation or notification cancellation is introduced for stock popup suppression.
- Stock floating-notification suppression is a separate user-selected Android/OEM setting. The app explains and links to it; enabling Accessibility alone does not guarantee popup replacement.
- This change has not been installed or validated on a physical phone. MIUI gesture interception, cutouts/rotation, stock floating-notification settings, Android 10/11 behavior and battery impact remain unverified.

The remaining sections record baseline validation before these optional controls. Baseline physical APK hashes and release checks do not describe the new debug artifact.

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

An isolated API 35 Google APIs x86_64 emulator, 1080×2400 at 420 dpi, ran **5 instrumentation tests successfully** after the foreground reconciliation change, in 11.75 seconds. [Runner output](validation/emulator-tests.txt).

1. Real Android shell notification arrival, visibility-based NEW → EARLIER transition, updated content returning to NEW, and listener cancellation.
2. Native NEW/EARLIER rendering, expansion, and a real `RemoteInput` pending-intent reply using synthetic UI fixtures.
3. Setup surface rendering.
4. Actual Quick Settings tile launch from the home screen.
5. A PIN-secured locked panel: private titles/bodies absent, secret notifications omitted, and a lock-screen glance leaves notifications NEW. The emulator's temporary PIN was removed by test cleanup.

Screenshots use instrumentation fixtures, not real personal notifications. `FLAG_SECURE` is temporarily cleared by the separately installed test APK for capture; the production app exposes no such switch.

| Panel | Locked view | Setup |
| --- | --- | --- |
| [Screenshot](screenshots/test-screenshots/panel-new-earlier.png) | [Screenshot](screenshots/test-screenshots/panel-locked.png) | [Screenshot](screenshots/test-screenshots/setup.png) |

## Physical Poco

The owner connected and authorized baseline checks on Xiaomi M2007J20CG (`surya`, Poco X3 NFC): Android 12/API 31, MIUI 14 `V14.0.2.0.SJGIDXM`, security patch 2023-06-01, 1080×2400 at 440 dpi. The installed **156,496-byte** debug APK matched the baseline artifact byte-for-byte. [SHA-256](validation/debug-apk.sha256). Notification access was already granted.

The final selected `PhysicalDeviceTest` run passed **3 tests**, with zero failures/errors, in 15.816 seconds. [Runner output](validation/poco-tests.txt).

1. A genuine notification posted by the separate helper APK, displayed in NEW, becoming EARLIER after an unlocked visible check, returning to NEW after updated content, expansion, and dismissal through the panel.
2. Native `RemoteInput` dispatch to an isolated local receiver and opening the intended activity through a content pending intent. Actions use an in-memory fixture; no third-party recipient is involved.
3. Actual Quick Settings tile launch from the home screen.

After the suite, no `chrono-phone-test-` notifications remained in Android, and the notification listener was bound. Existing attention markers for unchanged personal notification versions were restored. No personal notification content was recorded in logs or screenshots.

MIUI initially rejected listener restart with `AutStart Unable to bind notification listener service`. The owner enabled autostart and restored notification access; binding then resumed. [Historical blocker record](validation/poco-listener-blocked.txt). No credential, privacy, access, autostart, or device-timeout settings were changed by the suite. Test activities use a keep-screen-on window flag, and phone UI automation keeps the owner's accessibility services running.

Testing also found intermittent missed arrival callbacks: the listener could retrieve a fixture directly from Android while it was absent from the repository. The production panel now reconciles Android's active notifications each time it resumes, recovering missed arrivals/updates/removals without idle polling. The main APK was updated in place, retaining app data. The real notification test checks the resulting foreground behavior; it does not prove that MIUI delivers every background callback.

A subsequent `dumpsys meminfo` snapshot reported **44,373 KiB total PSS** (approximately 43.3 MiB) and 133,260 KiB RSS for the debug app under the owner's existing settings. This is one snapshot, not a baseline comparison or a battery/CPU endurance measurement.

The phone helper publishes genuine notifications from its own separately installed APK because MIUI rejected shell-created content intents and shell-post fixtures were unreliable under instrumentation. Opening/reply fixtures verify chrono-flow's dispatch path, not compatibility with every third-party app. `PanelIntegrationTest` checks emulator hardware before its security/access changes; physical phones must explicitly select the phone class.

## Remaining validation

- Xiaomi 17T Pro and further Poco checks: OEM listener/overlay survival, locked Quick Settings access, app-specific lock-screen controls, and original-app navigation/action compatibility across real third-party apps.
- Android 10/API 29 on-device execution and newer physical Android releases; code/API compatibility is checked by lint, not by running these OS versions.
- Battery and resource comparisons on the physical devices. Event-driven architecture and APK size do not prove negligible battery impact.
- Authentication success/cancellation across OEM keyguards, choice-only/data-only action behavior across real apps, large-font/TalkBack/landscape use, and reboot behavior.
- Stable release key provisioning and a real GitHub Actions release run.

The app is an early device-testing implementation. Its optional top-left gesture opens chrono-flow while unlocked. It provides a companion panel over the lock screen; replacing privileged SystemUI or the native lock-screen list remains outside an ordinary APK's capabilities.
