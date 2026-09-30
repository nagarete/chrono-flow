package dev.chronoflow;

import android.app.Application;

public final class ChronoApp extends Application {
    private NotificationRepository repository;
    private AppUpdater updater;
    @Override public void onCreate() {
        super.onCreate();
        repository = new NotificationRepository(this);
        updater = new AppUpdater(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(android.app.Activity activity) { updater.resumed(activity); }
            @Override public void onActivityPaused(android.app.Activity activity) { updater.paused(activity); }
            @Override public void onActivityCreated(android.app.Activity activity, android.os.Bundle state) {}
            @Override public void onActivityStarted(android.app.Activity activity) {}
            @Override public void onActivityStopped(android.app.Activity activity) {}
            @Override public void onActivitySaveInstanceState(android.app.Activity activity, android.os.Bundle state) {}
            @Override public void onActivityDestroyed(android.app.Activity activity) {}
        });
    }
    static AppUpdater updater(android.content.Context context) {
        return ((ChronoApp) context.getApplicationContext()).updater;
    }
    public static NotificationRepository repository(android.content.Context context) {
        return ((ChronoApp) context.getApplicationContext()).repository;
    }
}
