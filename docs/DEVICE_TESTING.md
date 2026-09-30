# Device acceptance testing

Physical devices intended for testing: **Poco X3 NFC** and **Xiaomi 17T Pro**. The Poco baseline was tested: Xiaomi M2007J20CG (`surya`), Android 12/API 31, MIUI 14 (`V14.0.2.0.SJGIDXM`), security patch 2023-06-01, display 1080×2400 at 440 dpi. The owner enabled autostart after MIUI blocked a listener restart. The phone suite passed all three checks, and its installed APK matched the baseline debug build tested then. The new swipe/banner build has not been installed or tested on that phone. Xiaomi 17T Pro has not been connected. Record firmware, region, and security patch versions; model names alone do not establish behavior.

Use Android 10+ and install the development APK through ADB or GitHub. Test default settings first, without blanket battery exemptions. If Android blocks notification access on a sideloaded APK, check App info for Allow restricted settings. Only enable firmware-specific autostart/background controls if testing demonstrates the listener is being stopped.

| Scenario | Expected result |
| --- | --- |
| Access denied or listener stopped | Clear disconnected state; no stale payloads displayed. |
| Notification access granted | Current Android notifications appear, in newest-first order. |
| Tile from another app/home | Panel opens without navigating through setup. |
| Optional edge handle | Tap/inward swipe opens panel; disabling preference removes it. Verify touch target does not prevent system back gestures. |
| Optional top-left swipe | Enable the Accessibility service and preference. Left-half top swipe opens chrono-flow while unlocked; the right half still opens stock shade/Quick Settings. Disable removes the touch strip. Check cutouts, landscape and MIUI gesture behavior. |
| Optional popup banners | Enable Accessibility and banners, then disable stock floating notifications for selected apps in Android settings. New/changed notifications show a six-second popup; identical reposts do not restart it. Hide/expiry retain the original notification and leave attention unchecked. |
| Popup privacy and interruption controls | No banners while locked, screen-off, panel-open, disconnected, disabled or under DND. Low-importance/ongoing/call/alarm/full-screen notifications retain Android behavior. Remove/change/rank a visible notification and verify stale popup disappears. |
| Brief open under 1.2 seconds | Cards remain NEW. |
| Unlocked card visible over 1.2 seconds, then close | That exact version becomes EARLIER. Off-screen cards stay NEW. |
| Incoming message while checking its previous version | New version stays NEW. |
| System app removes notification | It disappears from both sections immediately. |
| Expand/collapse EARLIER | Earlier cards remain accessible with less visual emphasis. |
| Open email/chat/calendar notification | Original app opens the notification's intended destination; auto-cancel follows original notification flags. |
| Dismiss button and horizontal swipe | Clearable notifications disappear from Android and chrono-flow. Ongoing notifications are retained. |
| Expanded supported action | Originating app receives the action. Test reply, mark read, media play/pause if supplied. |
| Free-text and choice-only reply | Reply reaches the original app. Empty reply is not sent. Data-only input asks the user to open the app. |
| Notification removed/changed during reply | Stale action is rejected without sending. |
| Tile on lock screen | Where firmware allows tile access, panel shows only safe notification information. Otherwise Android requires unlock first. |
| Locked private/public/secret notifications | Default: private/public text redacted, secret notifications absent. Glance leaves cards NEW. |
| Optional public-text mode | Only explicitly public notification text displays; restrictive channel settings still win. |
| System lock notifications disabled | No notification app names, counts, titles, bodies, or actions reveal the active set. |
| Open/dismiss/action from locked panel | Android authentication precedes dispatch. Canceling authentication performs no action. |
| Press power while viewing or replying | Panel closes and content/reply UI vanishes before locked use. Previously qualified unlocked observations may be committed. |
| Private app previews / screen recording | Content is blocked by FLAG_SECURE; no private text leaks. |
| Reboot / listener process death | System rebind restores live set and hashed seen state. Removed notifications are pruned. Check firmware-specific survival. |
| Android 15+ sensitive/OTP posts | OS-redacted contents stay redacted; chrono-flow makes no bypass attempt. |
| Calls, alarms, progress, grouped apps | System behavior is preserved; ongoing updates do not repeatedly resurface; child notifications appear without duplicate summaries. |
| Larger fonts/TalkBack/landscape | Actions are labeled and scrollable; content remains usable; no gesture-only essential interaction. |

## Resource measurements

Compare a normal baseline to chrono-flow with the tile only, then with the optional handle. Run at least one overnight idle period and one daily workload on each device; keep firmware, radios, apps, and notification volume comparable. Record battery delta, process PSS (`adb shell dumpsys meminfo dev.chronoflow`), notification burst responsiveness, and app-background restrictions. Use Android batterystats/Perfetto for CPU/wake activity if there is a measurable regression. A tiny APK and an event-driven design do not by themselves prove negligible battery cost.

## Automated phone checks

Obtain the owner's approval, unlock the phone normally, and grant notification access through setup first. Then select only the phone suite:

```sh
./gradlew :app:assembleDebugAndroidTest
adb -s <device-serial> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <device-serial> shell am instrument -w -e class dev.chronoflow.PhysicalDeviceTest dev.chronoflow.test/android.test.InstrumentationTestRunner
```

This installs a separate test helper, not a replacement of the main app. Install the matching main debug build before running tests after production source changes. The suite requires an existing listener grant and an unlocked device. On Android 13+, the owner must also allow notifications for the test helper. It does not set a PIN, alter security/privacy settings, revoke/regrant notification access, clear app data, cancel personal notifications, or send replies to another person. It adds the chrono-flow tile if necessary. Its helper APK posts genuine notifications under a unique test namespace through a receiver protected by Android's signature-level `DUMP` permission. Cleanup cancels only suite fixtures, including leftovers from interrupted runs. Native reply/opening dispatch uses an in-memory fixture and a package-scoped receiver. Personal notification text is not saved to logs or screenshots. Existing seen markers for unchanged notification versions are preserved, while real content updates retain their new state.

Instrumentation restarts the target process. The helper opens setup and requests one platform listener rebind when needed; test windows keep the screen awake without changing the system timeout. UI automation preserves the owner's accessibility services. On this Poco, reconnect attempts were initially rejected by MIUI with `AutStart Unable to bind notification listener service`, despite the existing notification-access grant and foreground setup screen. The owner enabled autostart; binding then resumed. The helper does not change that setting.

On the tested MIUI build, `cmd notification post --content-intent` rejects shell-created pending intents with a package-identity permission error. Background arrival callbacks were also intermittently missed despite the notification being retrievable through the connected listener. The production panel now reconciles Android's live set on each resume, with no background polling. Phone lifecycle checks use genuine helper-app notifications and exercise this foreground behavior. Opening/replies use an in-memory fixture whose pending intents are created by their owning app; this checks chrono-flow's dispatch path, without establishing compatibility with every third-party app or proving delivery of every background callback.

Never run an unqualified instrumentation suite on an everyday phone: select the class explicitly. `PanelIntegrationTest` is emulator-only and checks hardware before any access/security mutation.

The optional-control suite is also emulator-only. Run it on the repository's disposable emulator with the matching main and helper APKs installed and notification permission granted to the helper:

```sh
adb -s <emulator-serial> shell pm grant dev.chronoflow.test android.permission.POST_NOTIFICATIONS
adb -s <emulator-serial> shell am instrument -w -e class dev.chronoflow.OverrideIntegrationTest dev.chronoflow.test/android.test.InstrumentationTestRunner
```

`OverrideIntegrationTest` temporarily enables this app's Accessibility service and notification access, changes DND, toggles the new preferences and turns the emulator screen off/on. It preserves other Accessibility services and restores the saved settings and preferences after each test. It never sets a PIN. Its fixtures use the helper's isolated notification namespace. This suite refuses physical hardware before altering settings. Existing phone results predate the swipe/banner implementation; manually granting and testing the new controls on a physical phone requires owner authorization.

## Development validation

See `docs/VALIDATION.md` for executed local and phone checks. Xiaomi 17T Pro, long-term battery behavior, and firmware-specific locked-tile/overlay behavior remain unverified. The GitHub release workflow has not been run on a hosted repository.
