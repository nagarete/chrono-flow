package dev.chronoflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Device tests only: shell notifications exercise Android; synthetic entries exercise native UI. */
@android.annotation.TargetApi(35)
public final class PanelIntegrationTest extends InstrumentationTestCase {
    private Context context;
    private Activity activity;

    @Override protected void setUp() throws Exception {
        super.setUp(); context = getInstrumentation().getTargetContext();
        assertTrue("Emulator-only suite: select PhysicalDeviceTest on a phone",
                Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"));
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard");
        waitFor(() -> !Preferences.locked(context));
        shell("cmd notification allow_listener dev.chronoflow/dev.chronoflow.ChronoListener");
        waitFor(() -> ChronoApp.repository(context).connected());
    }
    @Override protected void tearDown() throws Exception {
        if (activity != null && !activity.isFinishing()) main(activity::finish);
        super.tearDown();
    }

    public void testRealListenerAttentionAndDismissal() throws Exception {
        String tag = "chrono-integration-" + System.nanoTime();
        shell("cmd notification post -t Integration-arrival " + tag + " First-message");
        waitFor(() -> findTag(tag) != null);
        NotificationEntry first = read(() -> findTag(tag));
        assertTrue(first.attention.isNew());
        activity = launch(PanelActivity.class);
        Thread.sleep(1800); getInstrumentation().waitForIdleSync();
        main(activity::finish); getInstrumentation().waitForIdleSync();
        assertFalse(read(() -> findTag(tag)).attention.isNew());
        shell("cmd notification post -t Integration-arrival " + tag + " Second-message");
        waitFor(() -> findTag(tag) != null && findTag(tag).attention.isNew());
        main(() -> assertTrue(ChronoListener.dismiss(findTag(tag).sbn.getKey())));
        waitFor(() -> findTag(tag) == null);
    }

    public void testZLockedPanelRedactsContentAndDoesNotCheckIt() throws Exception {
        // This class is deliberately intended only for a disposable emulator with no existing PIN.
        shell("locksettings set-pin 1234");
        try {
            assertTrue(context.getSystemService(android.app.KeyguardManager.class).isDeviceSecure());
            shell("settings put secure lock_screen_show_notifications 1");
            StatusBarNotification privateEntry = fixture(20, "Private-title", "Private-message", null);
            StatusBarNotification secret = fixture(21, "Secret-title", "Secret-message", null);
            secret.getNotification().visibility = Notification.VISIBILITY_SECRET;
            main(() -> ChronoApp.repository(context).connect(new StatusBarNotification[]{privateEntry, secret}, null));
            shell("input keyevent KEYCODE_SLEEP");
            shell("input keyevent KEYCODE_WAKEUP");
            waitFor(() -> Preferences.locked(context));
            activity = launch(PanelActivity.class);
            assertNotNull(read(() -> findText(activity.getWindow().getDecorView(), "NEW  ·  1")));
            assertNull(read(() -> findText(activity.getWindow().getDecorView(), "Private-title")));
            assertNull(read(() -> findText(activity.getWindow().getDecorView(), "Private-message")));
            assertNull(read(() -> findText(activity.getWindow().getDecorView(), "Secret-title")));
            assertNotNull(read(() -> findText(activity.getWindow().getDecorView(), "Unlock to view this notification.")));
            capture("panel-locked.png");
            Thread.sleep(1500);
            main(activity::finish); getInstrumentation().waitForIdleSync();
            assertTrue(read(() -> ChronoApp.repository(context).find(NotificationRepository.hash(privateEntry.getKey()))).attention.isNew());
        } finally {
            if (activity != null && !activity.isFinishing()) main(activity::finish);
            shell("locksettings clear --old 1234");
            shell("input keyevent KEYCODE_SLEEP"); shell("input keyevent KEYCODE_WAKEUP");
            shell("wm dismiss-keyguard");
            // Credential removal refreshes SystemUI asynchronously; this is the final device test.
        }
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // Only the pre-33 branch omits the unavailable export flag; intent is package-scoped.
    public void testNativeExpansionReplyAndEarlier() throws Exception {
        AtomicReference<String> reply = new AtomicReference<>();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                Bundle results = RemoteInput.getResultsFromIntent(intent);
                reply.set(results == null ? null : String.valueOf(results.getCharSequence("reply")));
            }
        };
        main(() -> {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, new IntentFilter("dev.chronoflow.TEST_REPLY"), Context.RECEIVER_NOT_EXPORTED);
            else context.registerReceiver(receiver, new IntentFilter("dev.chronoflow.TEST_REPLY"));
        });
        try {
            PendingIntent intent = PendingIntent.getBroadcast(context, 42,
                    new Intent("dev.chronoflow.TEST_REPLY").setPackage(context.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            RemoteInput input = new RemoteInput.Builder("reply").setLabel("Your reply").build();
            Notification.Action action = new Notification.Action.Builder(R.drawable.ic_flow, "Reply", intent).addRemoteInput(input).build();
            StatusBarNotification fresh = fixture(1, "Maya", "Coffee at 4? There’s a new place nearby.", action);
            StatusBarNotification second = fixture(2, "Design review", "Tomorrow, 10:00 · Bring your notes", null);
            StatusBarNotification older = fixture(3, "Your weekly reading list", "A few good things to come back to.", null);
            main(() -> {
                NotificationRepository repository = ChronoApp.repository(context);
                repository.connect(new StatusBarNotification[]{fresh, second, older}, null);
                NotificationEntry entry = repository.find(NotificationRepository.hash(older.getKey()));
                repository.checked(Map.of(entry.attention.key, entry.attention.revision));
            });
            activity = launch(PanelActivity.class);
            assertNotNull(read(() -> findText(activity.getWindow().getDecorView(), "NEW  ·  2")));
            assertNotNull(read(() -> findText(activity.getWindow().getDecorView(), "EARLIER  ·  1")));
            capture("panel-new-earlier.png");
            main(() -> {
                TextView expand = findText(activity.getWindow().getDecorView(), "⌄");
                assertNotNull(expand); expand.performClick();
            });
            getInstrumentation().waitForIdleSync();
            main(() -> { TextView button = findText(activity.getWindow().getDecorView(), "Reply"); assertNotNull(button); button.performClick(); });
            getInstrumentation().waitForIdleSync();
            main(() -> {
                try {
                    java.lang.reflect.Field field = PanelActivity.class.getDeclaredField("dialog"); field.setAccessible(true);
                    AlertDialog dialog = (AlertDialog) field.get(activity);
                    EditText edit = findEdit(dialog.getWindow().getDecorView());
                    assertNotNull(edit); edit.setText("See you at 4!");
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            waitFor(() -> "See you at 4!".equals(reply.get()));
        } finally { main(() -> context.unregisterReceiver(receiver)); }
    }

    public void testSetupRenders() throws Exception {
        activity = launch(SetupActivity.class);
        assertNotNull(read(() -> findText(activity.getWindow().getDecorView(), "A little less noise.\nA little more now.")));
        capture("setup.png");
    }

    public void testTileLaunchesFromHome() throws Exception {
        shell("input keyevent KEYCODE_HOME");
        shell("cmd statusbar add-tile dev.chronoflow/dev.chronoflow.ChronoTile");
        shell("cmd statusbar expand-settings");
        Thread.sleep(800);
        shell("cmd statusbar click-tile dev.chronoflow/dev.chronoflow.ChronoTile");
        waitForUi(() -> {
            android.view.accessibility.AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            return root != null && !root.findAccessibilityNodeInfosByText("A clear view of now.").isEmpty();
        });
        shell("input keyevent KEYCODE_BACK");
    }

    private StatusBarNotification fixture(int id, String title, String text, Notification.Action action) {
        Notification.Builder builder = new Notification.Builder(context, "test-only")
                .setSmallIcon(R.drawable.ic_flow).setContentTitle(title).setContentText(text)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setStyle(new Notification.BigTextStyle().bigText(text + "\nThis is an instrumentation fixture, never a production notification."));
        if (action != null) builder.addAction(action);
        return new StatusBarNotification("com.android.shell", "com.android.shell", id, "fixture-" + id,
                Process.myUid(), Process.myPid(), 0, builder.build(), UserHandle.getUserHandleForUid(Process.myUid()),
                System.currentTimeMillis() - id * 60000L);
    }
    private Activity launch(Class<? extends Activity> type) {
        return getInstrumentation().startActivitySync(new Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private NotificationEntry findTag(String tag) {
        for (NotificationEntry entry : ChronoApp.repository(context).snapshot()) if (tag.equals(entry.sbn.getTag())) return entry;
        return null;
    }
    private interface Read<T> { T get(); }
    private <T> T read(Read<T> action) {
        AtomicReference<T> result = new AtomicReference<>(); main(() -> result.set(action.get())); return result.get();
    }
    private void main(Runnable runnable) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            try { runnable.run(); } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    private void waitFor(Read<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < deadline) {
            if (read(condition)) return;
            Thread.sleep(100);
        }
        fail("Condition not met within 6 seconds");
    }
    private void waitForUi(Read<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < deadline) {
            // Accessibility requests must not block the app main thread that serves their nodes.
            if (condition.get()) return;
            Thread.sleep(100);
        }
        fail("UI condition not met within 6 seconds");
    }
    private void shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = getInstrumentation().getUiAutomation().executeShellCommand(command);
             FileInputStream stream = new FileInputStream(descriptor.getFileDescriptor())) {
            byte[] buffer = new byte[4096]; while (stream.read(buffer) != -1) { /* Drain shell output. */ }
        }
    }
    private TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            TextView found = findText(((ViewGroup) view).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private EditText findEdit(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            EditText found = findEdit(((ViewGroup) view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    private void capture(String name) throws Exception {
        // Screenshot permission exists only in this separately installed instrumentation APK.
        main(() -> activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE));
        Thread.sleep(300);
        Bitmap image = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File directory = new File(context.getExternalFilesDir(null), "test-screenshots"); directory.mkdirs();
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) { image.compress(Bitmap.CompressFormat.PNG, 100, output); }
        image.recycle();
        main(() -> activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE));
    }
}
