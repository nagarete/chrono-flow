package dev.chronoflow;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

final class Preferences {
    static SharedPreferences get(Context context) { return context.getSharedPreferences("preferences", Context.MODE_PRIVATE); }
    static boolean locked(Context context) { return context.getSystemService(KeyguardManager.class).isKeyguardLocked(); }
    static boolean systemAllowsLockNotifications(Context context) {
        // OEMs can omit this setting. Fail closed rather than guessing about private content.
        try { return Settings.Secure.getInt(context.getContentResolver(), "lock_screen_show_notifications", 0) == 1; }
        catch (SecurityException e) { return false; }
    }
    static boolean accessGranted(Context context) {
        return context.getSystemService(android.app.NotificationManager.class)
                .isNotificationListenerAccessGranted(new android.content.ComponentName(context, ChronoListener.class));
    }
    private Preferences() {}
}
