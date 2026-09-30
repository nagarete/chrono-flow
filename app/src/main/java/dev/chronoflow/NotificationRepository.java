package dev.chronoflow;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import dev.chronoflow.core.AttentionLedger;
import dev.chronoflow.core.LockPolicy;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Main-thread only. Android owns notification lifetimes; no dismissed-content archive. */
final class NotificationRepository {
    interface Observer { void onChanged(); }
    private final Context context;
    private final SharedPreferences saved;
    private final AttentionLedger ledger = new AttentionLedger();
    private final Map<String, StatusBarNotification> active = new HashMap<>();
    private final Map<String, Integer> visibility = new HashMap<>();
    private final Set<Observer> observers = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable persist = this::persistNow;
    private boolean connected;

    NotificationRepository(Context context) {
        this.context = context.getApplicationContext();
        saved = context.getSharedPreferences("attention", Context.MODE_PRIVATE);
        ArrayList<AttentionLedger.Record> restored = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(saved.getString("records", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                restored.add(new AttentionLedger.Record(item.getString("key"), item.getString("signature"),
                        item.getLong("time"), item.getLong("revision"), item.getLong("seen")));
            }
        } catch (JSONException ignored) { restored.clear(); }
        ledger.restore(restored);
    }

    boolean connected() { return connected; }
    void observe(Observer observer) { observers.add(observer); }
    void unobserve(Observer observer) { observers.remove(observer); }

    void connect(StatusBarNotification[] notifications, NotificationListenerService.RankingMap ranking) {
        active.clear();
        visibility.clear();
        Set<String> keys = new HashSet<>();
        if (notifications != null) for (StatusBarNotification sbn : notifications) {
            if (!eligible(sbn)) continue;
            keys.add(hash(sbn.getKey()));
            put(sbn, ranking);
        }
        ledger.retain(keys);
        connected = true;
        changed();
    }

    private boolean eligible(StatusBarNotification sbn) {
        // Group summaries duplicate their children and have no independent attention value.
        return !context.getPackageName().equals(sbn.getPackageName())
                && (sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) == 0;
    }

    private void put(StatusBarNotification sbn, NotificationListenerService.RankingMap ranking) {
        String key = hash(sbn.getKey());
        Notification n = sbn.getNotification();
        String signature = signature(n);
        ledger.post(key, signature, sbn.getPostTime(), sbn.isOngoing());
        active.put(key, sbn);
        updateVisibility(key, sbn.getKey(), ranking);
    }

    void post(StatusBarNotification sbn, NotificationListenerService.RankingMap ranking) {
        if (!connected) return;
        if (!eligible(sbn)) {
            remove(sbn);
            return;
        }
        put(sbn, ranking);
        changed();
    }

    private void updateVisibility(String key, String systemKey, NotificationListenerService.RankingMap ranking) {
        NotificationListenerService.Ranking r = new NotificationListenerService.Ranking();
        int value = LockPolicy.PRIVATE;
        if (ranking != null && ranking.getRanking(systemKey, r)) {
            if (android.os.Build.VERSION.SDK_INT >= 31) value = r.getLockscreenVisibilityOverride();
            else if (r.getChannel() != null) value = r.getChannel().getLockscreenVisibility();
        }
        visibility.put(key, value);
    }

    void rankings(NotificationListenerService.RankingMap ranking) {
        if (!connected) return;
        for (Map.Entry<String, StatusBarNotification> item : active.entrySet()) {
            updateVisibility(item.getKey(), item.getValue().getKey(), ranking);
        }
        notifyObservers();
    }

    void remove(StatusBarNotification sbn) {
        String key = hash(sbn.getKey());
        active.remove(key);
        visibility.remove(key);
        ledger.remove(key);
        changed();
    }

    void disconnect() {
        connected = false;
        active.clear();
        visibility.clear();
        // Keep hashes for reattachment, erase all payloads from the repository immediately.
        notifyObservers();
    }

    List<NotificationEntry> snapshot() {
        List<NotificationEntry> entries = new ArrayList<>();
        for (AttentionLedger.Record record : ledger.snapshot()) {
            StatusBarNotification sbn = active.get(record.key);
            if (sbn != null) entries.add(new NotificationEntry(sbn, record,
                    visibility.getOrDefault(record.key, LockPolicy.NO_OVERRIDE)));
        }
        return entries;
    }

    NotificationEntry find(String hashedKey) {
        StatusBarNotification sbn = active.get(hashedKey);
        AttentionLedger.Record record = ledger.get(hashedKey);
        return sbn == null || record == null ? null : new NotificationEntry(sbn, record,
                visibility.getOrDefault(hashedKey, LockPolicy.NO_OVERRIDE));
    }

    void checked(Map<String, Long> observed) {
        ledger.check(observed);
        persistNow();
        notifyObservers();
    }

    private void changed() {
        // Coalesce bursty notification updates into one asynchronous disk write.
        handler.removeCallbacks(persist);
        handler.postDelayed(persist, 350);
        notifyObservers();
    }

    private void notifyObservers() {
        for (Observer observer : new ArrayList<>(observers)) observer.onChanged();
    }

    private void persistNow() {
        handler.removeCallbacks(persist);
        JSONArray array = new JSONArray();
        for (AttentionLedger.Record r : ledger.snapshot()) {
            try {
                JSONObject item = new JSONObject();
                item.put("key", r.key).put("signature", r.signature).put("time", r.receivedAt)
                        .put("revision", r.revision).put("seen", r.seenRevision);
                array.put(item);
            } catch (JSONException ignored) { /* All values are finite primitives. */ }
        }
        saved.edit().putString("records", array.toString()).apply();
    }

    private static String signature(Notification n) {
        // Explicit fields avoid unstable Bundle.toString(), progress churn, or persisting content.
        StringBuilder value = new StringBuilder();
        String[] fields = {Notification.EXTRA_TITLE, Notification.EXTRA_TEXT,
                Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_SUMMARY_TEXT};
        for (String field : fields) append(value, NotificationEntry.text(n.extras, field));
        CharSequence[] lines = n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) for (CharSequence line : lines) append(value, line == null ? "" : line.toString());
        for (MessageData message : MessageData.from(n)) {
            append(value, String.valueOf(message.time));
            append(value, message.text);
            append(value, message.sender);
        }
        if (n.actions != null) for (Notification.Action action : n.actions) {
            append(value, String.valueOf(action.title));
            android.app.RemoteInput[] inputs = action.getRemoteInputs();
            if (inputs != null) for (android.app.RemoteInput input : inputs) {
                append(value, input.getResultKey());
                append(value, String.valueOf(input.getAllowFreeFormInput()));
                CharSequence[] choices = input.getChoices();
                if (choices != null) for (CharSequence choice : choices) append(value, String.valueOf(choice));
            }
        }
        return hash(value.toString());
    }

    private static void append(StringBuilder builder, String text) { builder.append(text.length()).append(':').append(text); }
    static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            String hex = "0123456789abcdef";
            for (byte b : bytes) out.append(hex.charAt((b & 0xff) >>> 4)).append(hex.charAt(b & 15));
            return out.toString();
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
