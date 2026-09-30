package dev.chronoflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import dev.chronoflow.core.AttentionLedger;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Explicitly selected physical-phone checks. Never changes access, credentials or privacy settings. */
@android.annotation.TargetApi(31)
public final class PhysicalDeviceTest extends InstrumentationTestCase {
    private Context context;
    private NotificationRepository repository;
    private Activity activity;
    private final String namespace = "chrono-phone-test-" + System.nanoTime();
    private final Set<String> testKeys = new HashSet<>();
    private final ArrayList<StatusBarNotification> synthetic = new ArrayList<>();
    private final Map<String, AttentionLedger.Record> original = new HashMap<>();

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        // MIUI can start the instrumentation worker before Application.onCreate finishes.
        // Queue singleton access on the app main thread, after application initialization.
        main(() -> repository = ChronoApp.repository(context));
        assertTrue("Notification access must already be granted by the owner", Preferences.accessGranted(context));
        assertFalse("Owner must unlock the phone before these checks", Preferences.locked(context));
        await(() -> repository.connected());
        main(() -> {
            for (AttentionLedger.Record record : ledger().snapshot()) original.put(record.key, record);
        });
    }

    @Override protected void tearDown() throws Exception {
        try {
            if (activity != null && !activity.isFinishing()) finishPanel();
            main(() -> {
                for (NotificationEntry entry : repository.snapshot()) {
                    if (isTest(entry)) ChronoListener.dismiss(entry.sbn.getKey());
                }
                for (StatusBarNotification sbn : synthetic) repository.remove(sbn);
                Map<String, AttentionLedger.Record> live = field(ledger(), "records");
                for (AttentionLedger.Record before : original.values()) {
                    AttentionLedger.Record now = live.get(before.key);
                    // Preserve real changes arriving during the test; undo only checks of unchanged versions.
                    if (now != null && now.revision == before.revision) now.seenRevision = before.seenRevision;
                }
                repository.checked(new HashMap<>());
                // Instrumentation can terminate its target process immediately after completion.
                // Flush the restored hash-only state before that happens.
                android.content.SharedPreferences saved = context.getSharedPreferences("attention", Context.MODE_PRIVATE);
                require(saved.edit().putString("records", saved.getString("records", "[]")).commit(),
                        "Restored attention state must reach disk before test completion");
            });
            await(() -> {
                for (NotificationEntry entry : repository.snapshot()) if (isTest(entry)) return false;
                return true;
            });
        } finally { super.tearDown(); }
    }

    public void testRealNotificationAttentionExpandAndDismiss() throws Exception {
        String tag = namespace + "-arrival";
        post("cmd notification post -t Chrono-Poco-test -S bigtext "
                + tag + " First-test-message");
        await(() -> findTag(tag) != null);
        NotificationEntry first = read(() -> findTag(tag)); testKeys.add(first.attention.key);
        assertTrue(first.attention.isNew());
        activity = launchPanel();
        await(() -> findText(activity.getWindow().getDecorView(), "Chrono-Poco-test") != null);
        Thread.sleep(1800);
        finishPanel(); getInstrumentation().waitForIdleSync();
        assertFalse("Visible test notification should become EARLIER", read(() -> findTag(tag)).attention.isNew());

        post("cmd notification post -t Chrono-Poco-test -S bigtext "
                + tag + " Updated-test-message");
        await(() -> findTag(tag) != null && findTag(tag).attention.isNew());
        activity = launchPanel();
        await(() -> findText(activity.getWindow().getDecorView(), "Chrono-Poco-test") != null);
        main(() -> {
            TextView title = findText(activity.getWindow().getDecorView(), "Chrono-Poco-test");
            ViewGroup card = (ViewGroup) title.getParent();
            TextView expand = findText(card, "⌄"); require(expand != null, "Test notification must offer expansion");
            expand.performClick();
        });
        getInstrumentation().waitForIdleSync();
        assertNotNull("Test notification expansion must offer Dismiss", read(() -> findText(activity.getWindow().getDecorView(), "Dismiss")));

        main(() -> {
            keepOnlyTestObservations();
            TextView title = findText(activity.getWindow().getDecorView(), "Chrono-Poco-test");
            TextView dismiss = findText((View) title.getParent(), "Dismiss");
            require(dismiss != null, "Only the known test card may be dismissed"); dismiss.performClick();
        });
        await(() -> findTag(tag) == null);
        finishPanel();
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // Pre-33 branch; package-scoped test intent.
    public void testSupportedReplyAndOpenOnNativePanel() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        String actionName = "dev.chronoflow.PHYSICAL_TEST_REPLY";
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                Bundle input = RemoteInput.getResultsFromIntent(intent);
                received.set(input == null ? null : String.valueOf(input.getCharSequence("reply")));
            }
        };
        main(() -> {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, new IntentFilter(actionName), Context.RECEIVER_NOT_EXPORTED);
            else context.registerReceiver(receiver, new IntentFilter(actionName));
        });
        try {
            PendingIntent pending = PendingIntent.getBroadcast(context, 91,
                    new Intent(actionName).setPackage(context.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            Notification.Action reply = new Notification.Action.Builder(R.drawable.ic_flow, "Test reply", pending)
                    .addRemoteInput(new RemoteInput.Builder("reply").setLabel("Test reply input").build()).build();
            Notification notification = new Notification.Builder(context, "test-only")
                    .setSmallIcon(R.drawable.ic_flow).setContentTitle("Chrono-Poco-reply-test")
                    .setContentText("Synthetic instrumentation fixture; no external recipient.")
                    .setContentIntent(PendingIntent.getActivity(context, 92, new Intent(context, SetupActivity.class),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                    .setVisibility(Notification.VISIBILITY_PRIVATE).addAction(reply).build();
            StatusBarNotification fixture = new StatusBarNotification("com.android.shell", "com.android.shell", 912,
                    namespace + "-reply", Process.myUid(), Process.myPid(), 0, notification,
                    UserHandle.getUserHandleForUid(Process.myUid()), System.currentTimeMillis());
            synthetic.add(fixture); testKeys.add(NotificationRepository.hash(fixture.getKey()));
            main(() -> repository.post(fixture, null));
            activity = launchPanel();
            await(() -> findText(activity.getWindow().getDecorView(), "Chrono-Poco-reply-test") != null);
            main(() -> {
                TextView title = findText(activity.getWindow().getDecorView(), "Chrono-Poco-reply-test");
                TextView expand = findText((View) title.getParent(), "⌄");
                require(expand != null, "Test reply card must expand"); expand.performClick();
            });
            getInstrumentation().waitForIdleSync();
            main(() -> {
                TextView button = findText(activity.getWindow().getDecorView(), "Test reply");
                require(button != null, "Test reply action must be visible"); button.performClick();
            });
            getInstrumentation().waitForIdleSync();
            main(() -> {
                AlertDialog dialog = field(activity, "dialog");
                require(dialog != null, "Reply form must open");
                EditText input = findEdit(dialog.getWindow().getDecorView());
                require(input != null, "Reply form must accept text"); input.setText("Poco test reply");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            await(() -> "Poco test reply".equals(received.get()));
            getInstrumentation().waitForIdleSync();
            Instrumentation.ActivityMonitor opened = getInstrumentation().addMonitor(SetupActivity.class.getName(), null, false);
            try {
                main(() -> {
                    keepOnlyTestObservations();
                    TextView title = findText(activity.getWindow().getDecorView(), "Chrono-Poco-reply-test");
                    ((View) title.getParent()).performClick();
                });
                Activity setup = opened.waitForActivityWithTimeout(10000);
                assertNotNull("Test content intent must open its intended activity", setup);
                main(setup::finish); getInstrumentation().waitForIdleSync();
            } finally { getInstrumentation().removeMonitor(opened); }
        } finally { main(() -> context.unregisterReceiver(receiver)); }
    }

    public void testTileLaunchFromHome() throws Exception {
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(PanelActivity.class.getName(), null, false);
        try {
            shell("input keyevent KEYCODE_HOME");
            shell("cmd statusbar add-tile dev.chronoflow/dev.chronoflow.ChronoTile");
            shell("cmd statusbar expand-settings"); Thread.sleep(1500);
            shell("cmd statusbar click-tile dev.chronoflow/dev.chronoflow.ChronoTile");
            activity = monitor.waitForActivityWithTimeout(10000);
            assertNotNull("Quick Settings tile must launch the panel from home", activity);
            assertNotNull(read(() -> findText(activity.getWindow().getDecorView(), "A clear view of now.")));
            finishPanel();
        } finally { getInstrumentation().removeMonitor(monitor); }
    }

    private boolean isTest(NotificationEntry entry) {
        return "com.android.shell".equals(entry.sbn.getPackageName()) && entry.sbn.getTag() != null
                && entry.sbn.getTag().startsWith(namespace);
    }
    private NotificationEntry findTag(String tag) {
        for (NotificationEntry entry : repository.snapshot()) if ("com.android.shell".equals(entry.sbn.getPackageName())
                && tag.equals(entry.sbn.getTag())) return entry;
        return null;
    }
    private AttentionLedger ledger() { return field(repository, "ledger"); }
    private Activity launchPanel() {
        return getInstrumentation().startActivitySync(new Intent(context, PanelActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private void keepOnlyTestObservations() {
        if (activity instanceof PanelActivity) {
            Map<String, Long> observations = field(activity, "observed"); observations.keySet().retainAll(testKeys);
        }
    }
    private void finishPanel() { main(() -> { keepOnlyTestObservations(); activity.finish(); }); }
    private interface Read<T> { T get(); }
    private <T> T read(Read<T> action) { AtomicReference<T> result = new AtomicReference<>(); main(() -> result.set(action.get())); return result.get(); }
    private void main(Runnable runnable) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> { try { runnable.run(); } catch (Throwable failure) { error.set(failure); } });
        if (error.get() != null) throw new AssertionError(error.get());
    }
    private void await(Read<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 12000;
        while (System.currentTimeMillis() < deadline) { if (read(condition)) return; Thread.sleep(100); }
        fail("Physical-device condition did not complete within 12 seconds");
    }
    private void post(String command) throws Exception {
        // MIUI's shell-created PendingIntent is rejected for package-identity mismatch.
        // Use plain real posts here; the separate local fixture exercises content intents/actions.
        require(shell(command).contains("posting:"), "Android did not enqueue the test notification");
    }
    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = getInstrumentation().getUiAutomation().executeShellCommand(command);
             FileInputStream stream = new FileInputStream(descriptor.getFileDescriptor())) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(java.nio.charset.StandardCharsets.UTF_8.name());
        }
    }
    @SuppressWarnings("unchecked") private static <T> T field(Object owner, String name) {
        try { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(owner); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
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
}
