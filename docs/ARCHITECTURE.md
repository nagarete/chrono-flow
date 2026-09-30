# Architecture

The app uses Java and Android platform Views, with no AndroidX, Compose, cross-platform runtime, or runtime dependency. The only external test dependency is JUnit 4. All repository mutation and listener callbacks are confined to Android's main thread.

| Component | Responsibility |
| --- | --- |
| `AttentionLedger` | Pure Java versioned attention state; exact-version checks and deterministic chronological order. |
| `LockPolicy` | Pure Java conservative lock-screen presentation decision. Secret and more restrictive app/channel settings win. |
| `NotificationRepository` | Live Android notification objects in memory; hashed persisted ledger; reconnect reconciliation; main-thread observers. |
| `ChronoListener` | System-owned listener lifecycle, real cancellation, ranking changes, and one platform rebind request on disconnect. |
| `PanelActivity` | Native list, visible-card dwell, pending-intent actions, text/choice input, authentication and redaction. |
| `ChronoTile` | A user-triggered panel launch from Quick Settings, using the API-appropriate launch method. |
| `EdgeHandle` | Optional unlocked-only overlay attached to the listener lifetime; no independent persistent service. |
| `SetupActivity` | One-time access/tile setup and two optional preferences. |

On listener connection, active Android notifications are reconciled with the saved hashed ledger. Notifications Android removed while the listener was stopped are pruned. The listener does not archive payloads. On disconnect, all in-memory notification objects are dropped; hashes remain for reconciliation.

Each meaningful non-ongoing content change receives a new revision. A card's checked revision is committed only if it is still current. This prevents a new message arriving during review from being marked seen by an earlier glance. Arrivals already received while an activity is open remain in NEW until they independently satisfy the visibility check. Attention is not inferred from Android's native shade: third-party listeners cannot reliably determine what the user meaningfully viewed in another surface.

Persisted signatures are content hashes with explicit fields, including message timestamps and supported action identities. Progress extras are excluded. Ongoing updates do not issue a new attention revision. Key removal clears its ledger record. An identical notification removed/reposted while the listener was disconnected can be indistinguishable from one that remained active; this is an Android-observability limit.

Android's original `PendingIntent` opens notifications and dispatches actions. Text and choice replies use `RemoteInput`; data-only inputs are directed to the original app. Before a deferred action is dispatched, the live key, revision, and action pending-intent identity are checked again. Authentication is requested through `KeyguardManager`, and no operation is dispatched while the keyguard remains visible. On API 34+, background activity launch privileges are explicitly permitted only for the foreground user action.

The panel uses `FLAG_SECURE`. It blanks/finishes on screen-off and re-evaluates privacy on resume/focus/system events. Its default locked view shows no notification body, title, or actions; app labels are shown only if system lock notifications are allowed, and secret notifications are absent. The optional public-text mode still applies restrictive app/channel visibility. Because proprietary OEM app-specific lock-screen rules may not be exposed, the default conservative setting is recommended.

The listener and tile are exported only behind Android signature binding permissions. The panel is not exported. Overlay permission is optional. The app has no `INTERNET`, storage, accessibility, usage access, foreground service, boot-start, full-screen intent, device administrator, or battery-optimization-exemption permission.
