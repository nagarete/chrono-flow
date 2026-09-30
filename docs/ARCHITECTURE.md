# Architecture

The app uses Java and Android platform Views, with no AndroidX, Compose, cross-platform runtime, or runtime dependency. The only external test dependency is JUnit 4. All repository mutation and listener callbacks are confined to Android's main thread.

| Component | Responsibility |
| --- | --- |
| `AttentionLedger` | Pure Java versioned attention state; exact-version checks and deterministic chronological order. |
| `LockPolicy` | Pure Java conservative lock-screen presentation decision. Secret and more restrictive app/channel settings win. |
| `NotificationRepository` | Live Android notification objects in memory; hashed persisted ledger; reconnect reconciliation; main-thread observers. |
| `ChronoListener` | System-owned listener lifecycle, real cancellation, ranking changes, foreground reconciliation, and one platform rebind request on disconnect. |
| `PanelActivity` | Native list, visible-card dwell, pending-intent actions, text/choice input, authentication and redaction. |
| `ChronoTile` | A user-triggered panel launch from Quick Settings, using the API-appropriate launch method. |
| `EdgeHandle` | Optional unlocked-only overlay attached to the listener lifetime; no independent persistent service. |
| `ChronoAccessibility` | Optional system-bound Accessibility service with an unlocked top-left gesture strip and secure, six-second popup banners. No window-content retrieval or gesture injection. |
| `AppUpdater` | Foreground-only, daily GitHub release checks; bounded HTTPS downloads; checksum, package/version and certificate verification; user-confirmed installation. |
| `UpdateProvider` | Read-only access to one private cached APK through a temporary installer URI grant; not exported. |
| `UpdatePolicy` | Pure Java release metadata validation and download URL restrictions. |
| `SetupActivity` | Notification/tile/Accessibility setup and optional experience preferences; links to Android's stock popup controls. |

On listener connection and each panel resume, active Android notifications are reconciled with the saved hashed ledger. The foreground read recovers arrivals, content changes, and removals missed by OEM callbacks. Notifications Android removed while the listener was stopped are pruned. This uses one read per panel resume, with no background polling. Failed foreground reads clear live payloads and display a disconnected state. The listener does not archive payloads. On disconnect, all in-memory notification objects are dropped; hashes remain for reconciliation.

Each meaningful non-ongoing content change receives a new revision. A card's checked revision is committed only if it is still current. This prevents a new message arriving during review from being marked seen by an earlier glance. Arrivals already received while an activity is open remain in NEW until they independently satisfy the visibility check. Attention is not inferred from Android's native shade: third-party listeners cannot reliably determine what the user meaningfully viewed in another surface.

Persisted signatures are content hashes with explicit fields, including message timestamps and supported action identities. Progress extras are excluded. Ongoing updates do not issue a new attention revision. Key removal clears its ledger record. An identical notification removed/reposted while the listener was disconnected can be indistinguishable from one that remained active; this is an Android-observability limit.

Android's original `PendingIntent` opens notifications and dispatches actions. Text and choice replies use `RemoteInput`; data-only inputs are directed to the original app. Before a deferred action is dispatched, the live key, revision, and action pending-intent identity are checked again. Authentication is requested through `KeyguardManager`, and no operation is dispatched while the keyguard remains visible. On API 34+, background activity launch privileges are explicitly permitted only for the foreground user action.

The panel uses `FLAG_SECURE`. It blanks/finishes on screen-off and re-evaluates privacy on resume/focus/system events. Its default locked view shows no notification body, title, or actions; app labels are shown only if system lock notifications are allowed, and secret notifications are absent. The optional public-text mode still applies restrictive app/channel visibility. Because proprietary OEM app-specific lock-screen rules may not be exposed, the default conservative setting is recommended.

The listener, tile and Accessibility service are exported only behind Android signature binding permissions. The panel is not exported. Display-over-other-apps permission remains optional for the edge handle. Top-left swipe and banners use `TYPE_ACCESSIBILITY_OVERLAY`, available only after the user enables this app's Accessibility service. Its configuration explicitly disables screen-content retrieval and gesture injection, and declares that it is not an accessibility tool for disabilities. Window-state events only re-check keyguard/power availability. No event payload is read.

The top strip covers only the physical left half of the top 24 dp, intentionally independent of layout direction. It launches the panel after a downward drag or an accessible click; the right side stays available to SystemUI. API 31+ uses the dedicated dismiss-shade action on user launch; older versions do not inject Back or manipulate another app. Controls are detached on screen-off, keyguard, listener disconnection, panel resume, preference disable, service unbind and destruction. Rotation recreates their bounds. Static service lookup uses a weak reference.

Live listener posts compare the previous attention revision to the new one before offering a banner; reconciliation never alerts. Eligibility requires known ranking at default importance or above, an allowed interruption filter, no suppressed peek, and DND off. Ongoing/group-summary/call/alarm/full-screen notifications and only-alert-once updates do not alert. Popup hide/expiry never cancels the original notification or marks attention checked. Removal/revision/ranking changes invalidate stale popups. Banners have `FLAG_SECURE`, contain no sound/vibration effects and appear only while unlocked. Stock popups cannot be selectively disabled through these grants; setup links to Android channel/floating-notification controls instead of cancelling notifications or altering DND.

The app requests `INTERNET` only for GitHub release checks/downloads, and `REQUEST_INSTALL_PACKAGES` for Android’s user-confirmed APK update flow. Cleartext traffic is disabled. No notification content enters updater requests. Automatic checks are triggered by activity resume while unlocked, throttled to once per day, and can be disabled; manual checks bypass the throttle. APK transfer and verification use one worker thread; UI updates stay on the main thread. The release workflow generates `update.json` from the packaged APK identity. The app has no storage, usage access, foreground service, boot-start, full-screen intent, device administrator, DND-policy-access or battery-optimization-exemption permission.
