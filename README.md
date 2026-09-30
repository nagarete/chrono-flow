# chrono-flow

A calmer Android notification panel. Fresh notifications come forward in **NEW**; previously checked ones settle into **EARLIER** and stay available until Android or you dismiss them.

This repository contains a native Android app, not a web mockup. Android 10+ (API 29); compiled and targeted against API 35. No third-party runtime libraries, network permission, backend, accounts, analytics, ads, or Play Store integration. Distribution is GitHub APK releases only.

## Daily use

1. Install the APK and open chrono-flow once for setup.
2. Grant notification access. On Android 13+, a sideloaded APK may need **App info → ⋮ → Allow restricted settings** before Android permits this grant. Labels and availability vary by firmware.
3. Add the **chrono-flow Quick Settings tile**. Open it from the system shade whenever you need your notifications.
4. Optionally enable the small **edge handle** and grant display-over-other-apps access. Tap it or swipe inward from the right edge while unlocked.
5. For a stock-style entry point, enable **chrono-flow swipe and banners** in Android Accessibility, then turn on **Top-left swipe** in setup. Swipe down from the left half of the top edge while unlocked. The right half still opens Android's shade/Quick Settings. Accessibility is optional and does not read screen content.
6. Optionally enable **Popup banners** using the same Accessibility grant. New or meaningfully changed notifications appear for six seconds while unlocked; **Open panel** lets you review them, and **Hide** only closes the popup. To avoid duplicate stock banners, use **Open notification settings** in setup and turn off **Floating notifications / Pop on screen** for the desired apps, keeping notifications enabled. Stock suppression requires this Android setting; chrono-flow cannot change other apps' channels through Accessibility.

The launcher opens setup. Normal use opens the panel from the tile, handle, top-left swipe or popup and does not require visiting setup again.

Popup banners do not mark notifications checked. Identical reposts do not restart a popup. Banners do not replay the existing notification set on connection or unlock, do not sound/vibrate, and respect Do Not Disturb and Android's peek restrictions. Low-importance notifications, ongoing notifications, calls, alarms and full-screen intents retain their Android behavior. Banners disappear on removal, screen-off/locking, access loss or feature disable. Neither optional Accessibility control appears while the panel is open or the device is locked.

Tap a card to open its original notification. Expand it with the chevron for detail and supported actions; swipe either way or use **Dismiss** to dismiss a clearable notification. Ongoing notifications stay under Android's control. Text replies go to the originating app's `RemoteInput` action. Data-only actions need the originating app. Chrono-flow does not emulate arbitrary custom `RemoteViews`, media seeking, or missing actions.

## The attention model

- New notifications and meaningful content changes are NEW, sorted newest first within each section.
- An **unlocked card visible for at least 1.2 seconds** qualifies as checked. Those exact versions move to EARLIER when the panel closes or loses focus. Opening a notification or sending an action also checks that version.
- Opening the panel briefly, seeing a redacted card on the lock screen, or leaving a card off screen does not check it. Notifications that change during a check remain NEW.
- EARLIER starts collapsed. Expand it to revisit older notifications with less visual emphasis.
- Repeated identical posts and ongoing progress/media updates do not continually resurface seen cards. Duplicate Android group summaries are omitted; their child notifications remain visible.
- EARLIER is the live notification set, **not a history archive**. Removed notifications disappear.

## Lock screen and Android boundaries

The tile can launch a `showWhenLocked` panel when Android/OEM Quick Settings policy permits it. Private text is redacted by default, secret notifications are omitted, and all open/dismiss/action operations require Android's unlock flow. Lock-screen glances do not count as checks. An optional preference permits text only for explicitly public notifications when readable system and channel privacy settings permit it. Unknown privacy settings fail closed. The edge handle stays hidden while locked; the panel never wakes the screen or launches automatically.

**An ordinary APK cannot replace Xiaomi's privileged system shade or native lock-screen notification list.** The optional Accessibility overlay takes over the top-left gesture while unlocked, giving direct access to chrono-flow. It does not replace SystemUI: the right half of the top edge, lock screen and system controls remain available. Popup replacement additionally needs the stock floating-notification setting described above. OEM settings that are not exposed to third-party apps cannot be fully reproduced; the default redacted mode is recommended on Xiaomi/Poco. Locked-down Quick Settings may require unlock before the tile is accessible.

Android 15+ may redact OTP/sensitive notification content before notification listeners receive it. Work-profile/private-space notifications can also be restricted by Android policy. chrono-flow respects those restrictions.

## Privacy and resources

Notification content, icons, and pending actions remain in memory while the listener is connected. Disk storage contains SHA-256 hashes of notification keys and content signatures, arrival timestamps, and seen-version markers; it contains no notification text or attachment archive. Hashes are metadata, not encryption, and low-entropy content could theoretically be guessed by someone with access to private app storage. App backup/device transfer is disabled. Screenshots and screen sharing of app surfaces are blocked. No notification payload is logged.

Android binds the `NotificationListenerService` and, only if enabled by the user, the optional `AccessibilityService`. The Accessibility service adds secure touch overlays; it cannot retrieve window contents, perform gestures or take screenshots. Window-state events only re-check whether the device is locked; no event text or app identity is inspected or stored. There is no foreground service, wake lock, alarm, periodic background polling, or keep-alive loop. Overlay visibility is event-driven; a six-second timer runs only while a popup is showing. Visibility checks run only while the panel is open; the list creates views only for visible rows. Writes are coalesced. This design aims for low resource use; **battery impact has not yet been measured on the target devices**, so negligible impact is not a verified claim.

## Build and test

Use JDK 17, Android SDK platform 35, and build-tools 35.0.0. Import the repository in Android Studio or set `ANDROID_HOME`/`JAVA_HOME` and run:

```sh
./scripts/check.sh
```

This runs unit tests, Android lint, and the debug build. The installable APK is `app/build/outputs/apk/debug/app-debug.apk`. The Gradle 8.13 wrapper has a pinned distribution checksum. Gradle generates development signing material in ignored `.tools/`; keep your release key separate. Local test scripts default to repository-local Gradle/Android caches.

With an isolated Android emulator running and physical phones disconnected:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chronoflow.PanelIntegrationTest
```

Instrumentation tests grant notification access on the **test emulator** using Android's shell API, exercise real notification listener arrival/dismissal, check attention transitions, and test native expansion/`RemoteInput` with synthetic fixtures. Use an isolated emulator; do not run these tests on your everyday phone. Instrumentation screenshots temporarily clear `FLAG_SECURE` in test code only; the production app has no screenshot bypass or demo notifications.

The current device suite is validated on API 35. It temporarily sets/removes a PIN on the disposable emulator for its final lock-screen test. Executed checks and screenshots are in [validation](docs/VALIDATION.md).

For an owner-authorized, already-unlocked physical phone with notification access already granted, use the separate `dev.chronoflow.PhysicalDeviceTest` class. It never changes credentials, access grants, or privacy settings; it posts/dismisses only uniquely tagged test notifications and preserves existing attention markers for unchanged notification versions. It tests tile launch and live attention/expansion/dismissal, plus isolated opening/reply fixtures with no external recipient. See [physical-device test commands](docs/DEVICE_TESTING.md#automated-phone-checks). The emulator suite refuses to run on physical hardware.

All three baseline phone checks passed on the Poco X3 NFC with Android 12/MIUI 14 after the owner enabled autostart, before the optional swipe/banner controls were added. Those controls have been tested on API 35 emulation and still need physical Xiaomi/Poco validation. The panel reconciles Android's active list when it resumes to recover missed OEM callbacks, without background polling. Physical lock-screen behavior, endurance/battery measurements, and Xiaomi 17T Pro remain unverified; see [validation](docs/VALIDATION.md).

See [device testing](docs/DEVICE_TESTING.md) for Poco X3 NFC and Xiaomi 17T Pro, and [architecture](docs/ARCHITECTURE.md) for the implementation.

## GitHub distribution

Push this repository to your chosen GitHub repo. Pull requests and branch pushes run the checks and attach a development APK artifact. For a stable signed release, configure the secrets in [release setup](docs/RELEASING.md), update `versionName`/`versionCode`, and push its matching `v*` tag. The release workflow refuses to publish without signing secrets and attaches an APK plus checksums to GitHub Releases. It does not publish to any other service.

No GitHub repository, public release, or release signing identity has been created by this implementation.

## Platform references

- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) — live callbacks, cancellation, and access lifecycle.
- [Quick Settings tiles](https://developer.android.com/develop/ui/views/quicksettings-tiles) — tile access and locked-device behavior.
- [Android 15 sensitive-notification restrictions](https://developer.android.com/about/versions/15/behavior-changes-all#otp-redaction).
- [Background activity launch security](https://developer.android.com/guide/components/activities/background-starts) — user-triggered pending-intent actions and optional overlay access.
