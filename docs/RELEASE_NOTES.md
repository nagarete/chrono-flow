chrono-flow 0.1.0 is an early Android build for device testing.

- NEW and EARLIER organize live notifications around what you have actually checked.
- Open the panel from a Quick Settings tile or an optional edge handle.
- Optional Accessibility access adds a top-left swipe and six-second popup banners while unlocked, without reading screen content. The top-right swipe stays with Android. For popup replacement, disable stock Floating notifications / Pop on screen for selected apps in Android settings; Accessibility cannot change other apps' channels.
- Open, dismiss, expand, and use supported notification actions, including text replies.
- Lock-screen use redacts content by default and requires Android authentication for interactions.
- Each panel opening reconciles Android's live notification set to recover missed OEM callbacks without background polling.
- No network permission, accounts, analytics, backend, or notification-content archive.

Android 10 or later. The stock system shade and lock-screen list remain under Android/OEM control. Xiaomi/Poco firmware may restrict tile access while locked, overlays, or the notification listener. Baseline Poco X3 NFC checks passed on Android 12/MIUI 14 with owner-enabled autostart, before swipe/banner support was added. The new controls need physical-device validation. Physical lock-screen behavior, Xiaomi 17T Pro compatibility, and battery measurements remain pending. See the repository's device-testing guide before relying on this build.
