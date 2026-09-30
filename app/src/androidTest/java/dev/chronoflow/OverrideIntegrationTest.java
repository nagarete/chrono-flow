package dev.chronoflow;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationManager;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.FileInputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/** Real accessibility windows and external notifications, on the repository emulator only. */
public final class OverrideIntegrationTest extends InstrumentationTestCase {
    private Context context;
    private String oldServices, oldEnabled;
    private boolean oldSwipe, oldBanners, oldAccess;
    private int oldFilter;
    private final List<String> tags = new ArrayList<>();
    private final AtomicReference<Activity> panel = new AtomicReference<>();
    private final Application.ActivityLifecycleCallbacks lifecycle = new Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityResumed(Activity activity) {
            if (activity instanceof PanelActivity) panel.set(activity);
        }
        @Override public void onActivityPaused(Activity activity) { panel.compareAndSet(activity, null); }
        @Override public void onActivityCreated(Activity activity, Bundle state) {}
        @Override public void onActivityStarted(Activity activity) {}
        @Override public void onActivityStopped(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
        @Override public void onActivityDestroyed(Activity activity) {}
    };

    @Override protected void setUp() throws Exception {
        super.setUp();
        assertTrue("Emulator-only: never change a phone's accessibility or DND settings",
                Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"));
        context = getInstrumentation().getTargetContext();
        getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        oldServices = shell("settings get secure enabled_accessibility_services").trim();
        oldEnabled = shell("settings get secure accessibility_enabled").trim();
        oldAccess = Preferences.accessGranted(context);
        oldFilter = context.getSystemService(NotificationManager.class).getCurrentInterruptionFilter();
        oldSwipe = Preferences.get(context).getBoolean("top_swipe", false);
        oldBanners = Preferences.get(context).getBoolean("banners", false);
        main(() -> {
            ((Application) context.getApplicationContext()).registerActivityLifecycleCallbacks(lifecycle);
            Preferences.get(context).edit().putBoolean("top_swipe", true).putBoolean("banners", true).commit();
        });
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard"); shell("input keyevent KEYCODE_HOME");
        shell("cmd notification set_dnd all");
        shell("cmd notification allow_listener dev.chronoflow/dev.chronoflow.ChronoListener");
        String services = "null".equals(oldServices) || oldServices.isEmpty() ? "" : oldServices + ":";
        shell("settings put secure enabled_accessibility_services " + services + "dev.chronoflow/dev.chronoflow.ChronoAccessibility");
        shell("settings put secure accessibility_enabled 1");
        await(() -> ChronoAccessibility.connected() && serviceView("swipe") != null);
    }

    @Override protected void tearDown() throws Exception {
        try {
            finishPanel();
            for (String tag : tags) fixture(tag, "cancel", false, "");
            main(() -> {
                Preferences.get(context).edit().putBoolean("top_swipe", oldSwipe).putBoolean("banners", oldBanners).commit();
                ((Application) context.getApplicationContext()).unregisterActivityLifecycleCallbacks(lifecycle);
            });
            restoreSetting("enabled_accessibility_services", oldServices);
            restoreSetting("accessibility_enabled", oldEnabled);
            if (!oldAccess) shell("cmd notification disallow_listener dev.chronoflow/dev.chronoflow.ChronoListener");
            shell("cmd notification set_dnd " + (oldFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    ? "priority" : oldFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS ? "alarms"
                    : oldFilter == NotificationManager.INTERRUPTION_FILTER_NONE ? "none" : "all"));
            shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard");
        } finally { super.tearDown(); }
    }

    public void testTopSwipeAndStockRightSwipe() throws Exception {
        await(() -> serviceView("swipe").getWidth() > 0);
        int[] location = read(() -> {
            int[] position = new int[2]; serviceView("swipe").getLocationOnScreen(position); return position;
        });
        assertEquals("Gesture strip must cover the top edge", 0, location[1]);
        shell("input swipe 100 5 100 550 350");
        await(() -> panel.get() != null);
        assertNull(read(() -> serviceView("swipe")));
        finishPanel();
        await(() -> serviceView("swipe") != null);
        shell("input keyevent KEYCODE_HOME");
        shell("input swipe 980 5 980 1000 350");
        Thread.sleep(500);
        assertNull("The right edge must not launch chrono-flow", panel.get());
        android.view.accessibility.AccessibilityNodeInfo root = getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();
        assertNotNull(root);
        assertEquals("com.android.systemui", String.valueOf(root.getPackageName()));
        shell("input keyevent KEYCODE_BACK");
        main(() -> Preferences.get(context).edit().putBoolean("top_swipe", false).commit());
        await(() -> serviceView("swipe") == null);
    }

    public void testBannerUpdateHideAndRemoval() throws Exception {
        String tag = newTag();
        fixture(tag, "post", false, "");
        await(() -> serviceView("banner") != null);
        assertTrue(read(() -> contains(serviceView("banner"), "First-test-message")));
        NotificationEntry entry = read(() -> find(tag));
        assertTrue("A popup does not check attention", entry.attention.isNew());
        View original = read(() -> serviceView("banner"));
        fixture(tag, "post", false, "");
        Thread.sleep(300);
        assertSame("Identical reposts must not restart the popup", original, read(() -> serviceView("banner")));
        fixture(tag, "post", true, "");
        await(() -> contains(serviceView("banner"), "Updated-test-message"));
        TextView hide = read(() -> findText(serviceView("banner"), "Hide"));
        main(hide::performClick);
        await(() -> serviceView("banner") == null);
        assertNotNull("Hiding a popup must retain its stock notification", read(() -> find(tag)));
        fixture(tag, "post", false, "");
        await(() -> serviceView("banner") != null);
        TextView open = read(() -> findText(serviceView("banner"), "Open panel"));
        main(open::performClick);
        await(() -> panel.get() != null && serviceView("banner") == null);
        finishPanel();
        fixture(tag, "post", true, "");
        await(() -> serviceView("banner") != null);
        fixture(tag, "cancel", false, "");
        await(() -> serviceView("banner") == null && find(tag) == null);
    }

    public void testLockDndCriticalQuietAndOptOut() throws Exception {
        String tag = newTag();
        fixture(tag, "post", false, "");
        await(() -> serviceView("banner") != null);
        shell("input keyevent KEYCODE_SLEEP");
        await(() -> serviceView("banner") == null && serviceView("swipe") == null);
        fixture(newTag(), "post", false, "");
        Thread.sleep(300);
        assertNull(read(() -> serviceView("banner")));
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard");
        await(() -> serviceView("swipe") != null);
        assertNull("Unlock must not replay notifications", read(() -> serviceView("banner")));
        shell("cmd notification set_dnd none");
        fixture(newTag(), "post", false, "");
        Thread.sleep(300);
        assertNull("DND must not be bypassed", read(() -> serviceView("banner")));
        shell("cmd notification set_dnd all");
        for (String mode : new String[] {"call", "alarm", "ongoing", "quiet"}) {
            String critical = newTag(); fixture(critical, "post", false, mode);
            await(() -> find(critical) != null);
            assertNull(mode + " should stay with Android", read(() -> serviceView("banner")));
        }
        main(() -> Preferences.get(context).edit().putBoolean("banners", false).commit());
        String disabled = newTag(); fixture(disabled, "post", false, "");
        await(() -> find(disabled) != null);
        assertNull(read(() -> serviceView("banner")));
    }

    public void testBannerExpiryAndAccessRevocation() throws Exception {
        fixture(newTag(), "post", false, "");
        await(() -> serviceView("banner") != null);
        await(() -> serviceView("banner") == null);
        fixture(newTag(), "post", false, "");
        await(() -> serviceView("banner") != null);
        shell("cmd notification disallow_listener dev.chronoflow/dev.chronoflow.ChronoListener");
        await(() -> serviceView("banner") == null && serviceView("swipe") == null);
    }

    private String newTag() { String tag = "chrono-phone-test-override-" + System.nanoTime(); tags.add(tag); return tag; }
    private void fixture(String tag, String operation, boolean updated, String mode) throws Exception {
        shell("am broadcast -n dev.chronoflow.test/dev.chronoflow.FixturePublisher --es tag " + tag
                + " --es operation " + operation + " --ez updated " + updated + (mode.isEmpty() ? "" : " --es mode " + mode));
    }
    private NotificationEntry find(String tag) {
        for (NotificationEntry entry : ChronoApp.repository(context).snapshot()) if (tag.equals(entry.sbn.getTag())) return entry;
        return null;
    }
    private View serviceView(String name) throws Exception {
        java.lang.reflect.Field field = ChronoAccessibility.class.getDeclaredField("current"); field.setAccessible(true);
        Object service = ((WeakReference<?>) field.get(null)).get();
        if (service == null) return null;
        field = ChronoAccessibility.class.getDeclaredField(name); field.setAccessible(true);
        return (View) field.get(service);
    }
    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            TextView found = findText(((ViewGroup) view).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private static boolean contains(View view, String text) { return findText(view, text) != null; }
    private void finishPanel() { Activity activity = panel.get(); if (activity != null) main(activity::finish); }
    private void restoreSetting(String key, String value) throws Exception {
        shell("null".equals(value) ? "settings delete secure " + key : "settings put secure " + key + " " + value);
    }
    private void main(Runnable action) { getInstrumentation().runOnMainSync(action); }
    private <T> T read(Callable<T> action) throws Exception {
        AtomicReference<T> value = new AtomicReference<>(); AtomicReference<Exception> error = new AtomicReference<>();
        main(() -> { try { value.set(action.call()); } catch (Exception exception) { error.set(exception); } });
        if (error.get() != null) throw error.get(); return value.get();
    }
    private void await(Callable<Boolean> condition) throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + 10000;
        while (android.os.SystemClock.uptimeMillis() < deadline) { if (read(condition)) return; Thread.sleep(100); }
        fail("Timed out waiting for accessibility/notification state");
    }
    private String shell(String command) throws Exception {
        android.os.ParcelFileDescriptor descriptor = getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command);
        try (FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } finally { descriptor.close(); }
    }
}
