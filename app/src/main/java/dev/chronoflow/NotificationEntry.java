package dev.chronoflow;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import dev.chronoflow.core.AttentionLedger;

final class NotificationEntry {
    final StatusBarNotification sbn;
    final AttentionLedger.Record attention;
    final int lockVisibility;

    NotificationEntry(StatusBarNotification sbn, AttentionLedger.Record attention, int lockVisibility) {
        this.sbn = sbn;
        this.attention = attention;
        this.lockVisibility = lockVisibility;
    }
    Notification notification() { return sbn.getNotification(); }
    String appLabel(Context context) {
        try {
            return context.getPackageManager().getApplicationLabel(
                    context.getPackageManager().getApplicationInfo(sbn.getPackageName(), 0)).toString();
        } catch (android.content.pm.PackageManager.NameNotFoundException | SecurityException e) {
            return sbn.getPackageName();
        }
    }
    static String text(Bundle extras, String key) {
        if (extras == null) return "";
        CharSequence value = extras.getCharSequence(key);
        return value == null ? "" : value.toString();
    }
    String title() { return text(notification().extras, Notification.EXTRA_TITLE); }
    String body(boolean expanded) {
        Bundle extras = notification().extras;
        if (expanded) {
            java.util.List<MessageData> messages = MessageData.from(notification());
            if (!messages.isEmpty()) {
                StringBuilder conversation = new StringBuilder();
                for (MessageData message : messages) {
                    if (conversation.length() > 0) conversation.append('\n');
                    if (!message.sender.isEmpty()) conversation.append(message.sender).append(": ");
                    conversation.append(message.text);
                }
                if (conversation.length() > 0) return conversation.toString();
            }
            String big = text(extras, Notification.EXTRA_BIG_TEXT);
            if (!big.isEmpty()) return big;
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null && lines.length > 0) return android.text.TextUtils.join("\n", lines);
        }
        return text(extras, Notification.EXTRA_TEXT);
    }
    boolean dismissible() { return sbn.isClearable(); }
}
