package dev.chronoflow;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/** User-enabled overlay controls. Never retrieves screen content or cancels stock alerts. */
public final class ChronoAccessibility extends AccessibilityService implements NotificationRepository.Observer {
    private static WeakReference<ChronoAccessibility> current = new WeakReference<>(null);
    private static boolean panelVisible;
    private WindowManager windows;
    private View swipe;
    private LinearLayout banner;
    private String bannerKey;
    private long bannerRevision;
    private boolean registered;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable expire = this::removeBanner;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChanged = (preferences, key) -> refresh();
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };

    @Override protected void onServiceConnected() {
        current = new WeakReference<>(this);
        windows = getSystemService(WindowManager.class);
        if (!registered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screen, filter);
            Preferences.get(this).registerOnSharedPreferenceChangeListener(preferenceChanged);
            ChronoApp.repository(this).observe(this);
            registered = true;
        }
        refresh();
    }

    static boolean connected() { return current.get() != null; }
    static void panelVisible(boolean visible) {
        panelVisible = visible;
        ChronoAccessibility service = current.get();
        if (service != null) service.refresh();
    }
    private boolean available() {
        return registered && !panelVisible && !Preferences.locked(this)
                && getSystemService(PowerManager.class).isInteractive()
                && ChronoApp.repository(this).connected();
    }
    private void refresh() {
        boolean available = available();
        if (!available || !Preferences.get(this).getBoolean("top_swipe", false)) removeSwipe();
        else if (swipe == null) addSwipe();
        if (!available || !Preferences.get(this).getBoolean("banners", false)
                || getSystemService(NotificationManager.class).getCurrentInterruptionFilter()
                != NotificationManager.INTERRUPTION_FILTER_ALL) removeBanner();
    }

    private WindowManager.LayoutParams layout(int width, int height, String title) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.setTitle(title);
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        if (Build.VERSION.SDK_INT >= 30) params.setFitInsetsTypes(0);
        return params;
    }
    private int width() {
        if (Build.VERSION.SDK_INT >= 30) return windows.getCurrentWindowMetrics().getBounds().width();
        return getResources().getDisplayMetrics().widthPixels;
    }
    private void addSwipe() {
        View control = new View(this);
        control.setContentDescription("Open chrono-flow notifications");
        control.setFocusable(true);
        control.setOnClickListener(view -> openPanel());
        control.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private boolean opened, cancelled;
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX(); startY = event.getRawY();
                        opened = false; cancelled = false;
                        return true;
                    case MotionEvent.ACTION_POINTER_DOWN:
                    case MotionEvent.ACTION_CANCEL:
                        cancelled = true;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - startX) > Ui.dp(ChronoAccessibility.this, 40)) cancelled = true;
                        // Claim a deliberate downward drag before SystemUI takes the top-edge stream.
                        if (!cancelled && !opened && event.getRawY() - startY > Ui.dp(ChronoAccessibility.this, 12)) {
                            opened = true; openPanel();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!cancelled && !opened && Math.abs(event.getRawY() - startY) < Ui.dp(ChronoAccessibility.this, 12)) view.performClick();
                        return true;
                    default: return true;
                }
            }
        });
        if (add(control, layout(width() / 2, Ui.dp(this, 24), "chrono-flow top swipe"))) swipe = control;
    }
    private void openPanel() {
        if (!available()) { refresh(); return; }
        removeBanner();
        if (Build.VERSION.SDK_INT >= 31) performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
        try { startActivity(new Intent(this, PanelActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (SecurityException | android.content.ActivityNotFoundException ignored) { }
    }

    static void posted(NotificationEntry entry, long previousRevision, NotificationListenerService.RankingMap rankings) {
        ChronoAccessibility service = current.get();
        if (service != null) service.showBanner(entry, previousRevision, rankings);
    }
    private boolean mayAlert(NotificationEntry entry, NotificationListenerService.RankingMap rankings) {
        if (!available() || !Preferences.get(this).getBoolean("banners", false) || entry == null) return false;
        Notification notification = entry.notification();
        // Keep calls, alarms, media/progress and full-screen intents entirely with Android.
        if (entry.sbn.isOngoing() || notification.fullScreenIntent != null
                || Notification.CATEGORY_CALL.equals(notification.category)
                || Notification.CATEGORY_ALARM.equals(notification.category)
                || (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return false;
        NotificationListenerService.Ranking ranking = new NotificationListenerService.Ranking();
        return rankings != null && rankings.getRanking(entry.sbn.getKey(), ranking)
                && ranking.getImportance() >= NotificationManager.IMPORTANCE_DEFAULT
                && ranking.matchesInterruptionFilter()
                && (ranking.getSuppressedVisualEffects() & NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK) == 0
                && getSystemService(NotificationManager.class).getCurrentInterruptionFilter()
                    == NotificationManager.INTERRUPTION_FILTER_ALL;
    }
    private void showBanner(NotificationEntry entry, long previousRevision, NotificationListenerService.RankingMap rankings) {
        if (!mayAlert(entry, rankings) || entry.attention.revision == previousRevision) return;
        if (previousRevision != 0 && (entry.notification().flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0) return;
        removeBanner();
        LinearLayout card = Ui.column(this);
        Ui.padded(card, 18, 14);
        card.setBackground(Ui.shape(Ui.CARD, 20, this));
        TextView label = Ui.text(this, entry.appLabel(this) + "  ·  chrono-flow", 11, Ui.ACCENT);
        card.addView(label);
        TextView title = Ui.text(this, entry.title(), 16, Ui.TEXT);
        Ui.bold(title); title.setMaxLines(2); card.addView(title);
        TextView body = Ui.text(this, entry.body(false), 14, Ui.MUTED);
        body.setMaxLines(3); card.addView(body);
        LinearLayout actions = Ui.row(this);
        actions.addView(Ui.button(this, "Open panel", true, view -> openPanel()));
        actions.addView(Ui.button(this, "Hide", false, view -> removeBanner()));
        card.addView(actions);
        WindowManager.LayoutParams params = layout(Math.min(width() - Ui.dp(this, 24), Ui.dp(this, 440)),
                WindowManager.LayoutParams.WRAP_CONTENT, "chrono-flow banner");
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = Ui.dp(this, 36);
        if (!add(card, params)) return;
        banner = card; bannerKey = entry.attention.key; bannerRevision = entry.attention.revision;
        handler.postDelayed(expire, 6000);
    }
    static void rankingsChanged(NotificationListenerService.RankingMap rankings) {
        ChronoAccessibility service = current.get();
        if (service != null && service.bannerKey != null
                && !service.mayAlert(ChronoApp.repository(service).find(service.bannerKey), rankings)) service.removeBanner();
    }
    @Override public void onChanged() {
        refresh();
        NotificationEntry live = bannerKey == null ? null : ChronoApp.repository(this).find(bannerKey);
        if (live == null || live.attention.revision != bannerRevision) removeBanner();
    }
    // Window events only re-check keyguard state. Do not inspect packages, text, nodes or gestures.
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { refresh(); }
    @Override public void onInterrupt() { removeBanner(); }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        removeSwipe(); removeBanner(); refresh();
    }
    private boolean add(View view, WindowManager.LayoutParams params) {
        try { windows.addView(view, params); return true; }
        catch (SecurityException | WindowManager.BadTokenException e) { return false; }
    }
    private void remove(View view) {
        if (view != null) try { windows.removeViewImmediate(view); } catch (IllegalArgumentException ignored) { }
    }
    private void removeSwipe() { remove(swipe); swipe = null; }
    private void removeBanner() {
        handler.removeCallbacks(expire);
        remove(banner); banner = null; bannerKey = null;
    }
    @Override public boolean onUnbind(Intent intent) { stop(); return super.onUnbind(intent); }
    @Override public void onDestroy() { stop(); super.onDestroy(); }
    private void stop() {
        if (current.get() == this) current.clear();
        removeSwipe(); removeBanner();
        if (registered) {
            unregisterReceiver(screen);
            Preferences.get(this).unregisterOnSharedPreferenceChangeListener(preferenceChanged);
            ChronoApp.repository(this).unobserve(this);
            registered = false;
        }
    }
}
