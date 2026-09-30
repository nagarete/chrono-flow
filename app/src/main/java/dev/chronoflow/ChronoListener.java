package dev.chronoflow;

import android.content.ComponentName;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public final class ChronoListener extends NotificationListenerService {
    private static ChronoListener current;
    private EdgeHandle edge;

    @Override public void onCreate() {
        super.onCreate();
        edge = new EdgeHandle(this);
    }
    @Override public void onListenerConnected() {
        current = this;
        ChronoApp.repository(this).connect(getActiveNotifications(), getCurrentRanking());
        edge.start();
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        if (sbn != null) {
            NotificationRepository repository = ChronoApp.repository(this);
            String key = NotificationRepository.hash(sbn.getKey());
            NotificationEntry previous = repository.find(key);
            long revision = previous == null ? 0 : previous.attention.revision;
            repository.post(sbn, rankingMap);
            ChronoAccessibility.posted(repository.find(key), revision, rankingMap);
        }
    }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) ChronoApp.repository(this).remove(sbn);
    }
    @Override public void onNotificationRankingUpdate(RankingMap rankingMap) {
        ChronoApp.repository(this).rankings(rankingMap);
        ChronoAccessibility.rankingsChanged(rankingMap);
    }
    @Override public void onListenerDisconnected() {
        current = null;
        edge.stop();
        ChronoApp.repository(this).disconnect();
        // One platform-managed rebind request, no retry timer or keep-alive service.
        requestRebind(new ComponentName(this, ChronoListener.class));
    }
    @Override public void onDestroy() {
        if (current == this) current = null;
        edge.stop();
        ChronoApp.repository(this).disconnect();
        super.onDestroy();
    }
    static boolean dismiss(String systemKey) {
        if (current == null) return false;
        try { current.cancelNotification(systemKey); return true; }
        catch (SecurityException | IllegalStateException e) { return false; }
    }
    static void shown(String[] systemKeys) {
        if (current != null && systemKeys.length > 0) {
            try { current.setNotificationsShown(systemKeys); }
            catch (SecurityException | IllegalStateException ignored) { }
        }
    }
    static void refreshEdge() { if (current != null) current.edge.refresh(); }
    static void refreshActive() {
        ChronoListener listener = current;
        if (listener == null) return;
        NotificationRepository repository = ChronoApp.repository(listener);
        try {
            // A foreground check reconciles missed OEM callbacks without idle polling.
            StatusBarNotification[] active = listener.getActiveNotifications();
            if (active == null) repository.disconnect();
            else repository.connect(active, listener.getCurrentRanking());
        } catch (SecurityException | IllegalStateException e) { repository.disconnect(); }
    }
}
