package dev.chronoflow;

import android.app.Notification;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;

final class MessageData {
    final long time;
    final String text, sender;
    MessageData(long time, CharSequence text, CharSequence sender) {
        this.time = time;
        this.text = text == null ? "" : text.toString();
        this.sender = sender == null ? "" : sender.toString();
    }
    static List<MessageData> from(Notification notification) {
        ArrayList<MessageData> out = new ArrayList<>();
        Parcelable[] bundles = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (bundles == null) return out;
        if (Build.VERSION.SDK_INT >= 30) {
            for (Notification.MessagingStyle.Message message : Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)) {
                out.add(new MessageData(message.getTimestamp(), message.getText(), message.getSender()));
            }
        } else {
            // Android 10's public EXTRA_MESSAGES uses the platform Message Bundle format.
            // Its typed reader became public in API 30. No hidden APIs or reflection.
            for (Parcelable item : bundles) if (item instanceof Bundle) {
                Bundle bundle = (Bundle) item;
                out.add(new MessageData(bundle.getLong("time"), bundle.getCharSequence("text"), bundle.getCharSequence("sender")));
            }
        }
        return out;
    }
}
