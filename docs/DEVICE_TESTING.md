# Device acceptance testing

Physical devices intended for testing: **Poco X3 NFC** and **Xiaomi 17T Pro**. Neither has been connected during development. Record actual Android, MIUI/HyperOS, firmware, region, and security patch versions; model names alone do not establish behavior.

Use Android 10+ and install the development APK through ADB or GitHub. Test default settings first, without blanket battery exemptions. If Android blocks notification access on a sideloaded APK, check App info for Allow restricted settings. Only enable firmware-specific autostart/background controls if testing demonstrates the listener is being stopped.

| Scenario | Expected result |
| --- | --- |
| Access denied or listener stopped | Clear disconnected state; no stale payloads displayed. |
| Notification access granted | Current Android notifications appear, in newest-first order. |
| Tile from another app/home | Panel opens without navigating through setup. |
| Optional edge handle | Tap/inward swipe opens panel; disabling preference removes it. Verify touch target does not prevent system back gestures. |
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

## Development validation

See `docs/VALIDATION.md` for executed local checks. The two physical devices and their firmware-specific locked-tile/overlay behavior remain unverified. The GitHub release workflow has not been run on a hosted repository.
