package dev.chronoflow;

import android.app.Application;

public final class ChronoApp extends Application {
    private NotificationRepository repository;
    @Override public void onCreate() {
        super.onCreate();
        repository = new NotificationRepository(this);
    }
    public static NotificationRepository repository(android.content.Context context) {
        return ((ChronoApp) context.getApplicationContext()).repository;
    }
}
