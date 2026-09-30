package dev.chronoflow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/** Optional small window attached only to the system-bound listener; no foreground service. */
final class EdgeHandle {
    private final Context context;
    private final WindowManager windows;
    private FrameLayout handle;
    private boolean started;
    private float touchStart;
    private boolean gestureOpened;
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) { refresh(); }
    };
    EdgeHandle(Context context) { this.context = context; windows = context.getSystemService(WindowManager.class); }
    void start() {
        if (!started) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
            else context.registerReceiver(screen, filter);
            started = true;
        }
        refresh();
    }
    void refresh() {
        boolean visible = started && Preferences.get(context).getBoolean("edge", false)
                && Settings.canDrawOverlays(context) && !Preferences.locked(context)
                && context.getSystemService(PowerManager.class).isInteractive();
        if (!visible) { remove(); return; }
        if (handle != null) return;
        handle = new FrameLayout(context);
        handle.setContentDescription("Open chrono-flow notifications");
        handle.setFocusable(true);
        View pill = new View(context);
        pill.setBackground(Ui.shape(Ui.ACCENT, 4, context));
        FrameLayout.LayoutParams bar = new FrameLayout.LayoutParams(Ui.dp(context, 4), Ui.dp(context, 48), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        bar.setMarginEnd(Ui.dp(context, 3));
        handle.addView(pill, bar);
        handle.setOnClickListener(view -> open());
        handle.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { touchStart = event.getRawX(); gestureOpened = false; return true; }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) { if (!gestureOpened) view.performClick(); return true; }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && touchStart - event.getRawX() > Ui.dp(context, 28)) {
                if (!gestureOpened) { gestureOpened = true; open(); } return true;
            }
            return true;
        });
        WindowManager.LayoutParams layout = new WindowManager.LayoutParams(Ui.dp(context, 32), Ui.dp(context, 88),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT);
        layout.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        layout.setTitle("chrono-flow edge handle");
        try { windows.addView(handle, layout); }
        catch (SecurityException | WindowManager.BadTokenException e) { handle = null; }
    }
    private void open() {
        if (Preferences.locked(context)) { remove(); return; }
        try { context.startActivity(new Intent(context, PanelActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (SecurityException | android.content.ActivityNotFoundException ignored) { }
    }
    private void remove() {
        if (handle != null) {
            try { windows.removeView(handle); } catch (IllegalArgumentException ignored) { }
            handle = null;
        }
    }
    void stop() {
        remove();
        if (started) { context.unregisterReceiver(screen); started = false; }
    }
}
