package dev.chronoflow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Runs in the separate helper APK's process so Android posts a genuine external notification. */
public final class FixturePublisher extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String tag = intent.getStringExtra("tag");
        if (tag == null || !tag.startsWith("chrono-phone-test-")) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if ("cancel".equals(intent.getStringExtra("operation"))) {
            manager.cancel(tag, 912);
            return;
        }
        NotificationChannel channel = new NotificationChannel("physical-fixtures", "chrono-flow test fixtures",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
        String text = intent.getBooleanExtra("updated", false) ? "Updated-test-message" : "First-test-message";
        manager.notify(tag, 912, new Notification.Builder(context, channel.getId())
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("Chrono-Poco-test").setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setVisibility(Notification.VISIBILITY_PRIVATE).build());
    }
}
