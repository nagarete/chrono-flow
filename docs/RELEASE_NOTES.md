chrono-flow 0.1.0 is an early Android build for device testing.

- NEW and EARLIER organize live notifications around what you have actually checked.
- Open the panel from a Quick Settings tile or an optional edge handle.
- Open, dismiss, expand, and use supported notification actions, including text replies.
- Lock-screen use redacts content by default and requires Android authentication for interactions.
- Each panel opening reconciles Android's live notification set to recover missed OEM callbacks without background polling.
- No network permission, accounts, analytics, backend, or notification-content archive.

Android 10 or later. The stock system shade and lock-screen list remain under Android/OEM control. Xiaomi/Poco firmware may restrict tile access while locked, overlays, or the notification listener. Basic Poco X3 NFC checks pass on Android 12/MIUI 14 with owner-enabled autostart. Physical lock-screen behavior, Xiaomi 17T Pro compatibility, and battery measurements remain pending. See the repository's device-testing guide before relying on this build.
