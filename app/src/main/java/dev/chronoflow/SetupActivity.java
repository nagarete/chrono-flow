package dev.chronoflow;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class SetupActivity extends Activity implements NotificationRepository.Observer {
    private LinearLayout content;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = Ui.column(this);
        scroll.addView(content);
        setContentView(scroll);
        Ui.secureWindow(this, scroll, 24, 18, 24);
    }
    @Override protected void onResume() {
        super.onResume();
        ChronoApp.repository(this).observe(this);
        ChronoListener.refreshEdge();
        render();
    }
    @Override protected void onPause() {
        ChronoApp.repository(this).unobserve(this);
        super.onPause();
    }
    @Override public void onChanged() { render(); }

    private void render() {
        content.removeAllViews();
        TextView logo = Ui.text(this, "≡  chrono-flow", 16, Ui.ACCENT);
        Ui.bold(logo); content.addView(logo);
        Ui.space(content, 36);
        TextView heading = Ui.text(this, "A little less noise.\nA little more now.", 34, Ui.TEXT);
        Ui.bold(heading); content.addView(heading);
        Ui.space(content, 14);
        content.addView(Ui.text(this, "Fresh notifications come forward. Everything you’ve seen settles into Earlier.", 16, Ui.MUTED));
        Ui.space(content, 28);
        boolean granted = Preferences.accessGranted(this);
        boolean connected = ChronoApp.repository(this).connected();
        box("01  CONNECT YOUR NOTIFICATIONS", connected ? "Connected. Everything stays on this device."
                : granted ? "Access granted. Waiting for Android to connect the listener."
                : "Allow notification access to display and interact with your real notifications.",
                connected ? "Manage access" : "Allow notification access", view -> settings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        Ui.space(content, 12);
        box("02  MAKE IT ONE TAP AWAY", "Add chrono-flow to Quick Settings. Pull down your shade and tap the tile, including from the lock screen when your device allows it.",
                "Add Quick Settings tile", view -> addTile());
        Ui.space(content, 24);
        TextView options = Ui.text(this, "YOUR EXPERIENCE", 11, Ui.MUTED);
        options.setLetterSpacing(0.12f); content.addView(options);
        Ui.space(content, 16);
        toggle("Edge handle", "Swipe inward or tap the small handle at the right edge. Optional; hidden while locked.",
                "edge", value -> {
                    if (value && !Settings.canDrawOverlays(this)) {
                        try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
                        catch (android.content.ActivityNotFoundException e) { settings(Settings.ACTION_SETTINGS); }
                    }
                    ChronoListener.refreshEdge();
                });
        Ui.space(content, 20);
        toggle("Public content while locked", "Off by default. Only notifications explicitly marked public may show their text. Private content stays hidden; actions require unlock.",
                "public_on_lock", value -> {});
        Ui.space(content, 28);
        TextView open = Ui.button(this, "Open notification panel  →", true,
                view -> startActivity(new Intent(this, PanelActivity.class)));
        content.addView(open);
        Ui.space(content, 24);
        content.addView(Ui.text(this, "ON XIAOMI / POCO", 11, Ui.ACCENT));
        Ui.space(content, 8);
        content.addView(Ui.text(this, "If access stops, check notification access and your firmware’s background or autostart controls. Android may ask you to allow restricted settings for a GitHub-installed APK.\n\nchrono-flow is a companion panel. Your system shade and lock screen remain controlled by Android.", 13, Ui.MUTED));
        Ui.space(content, 26);
        content.addView(Ui.text(this, "LOCAL ONLY  ·  NO ACCOUNTS  ·  NO TRACKING\nchrono-flow 0.1.0", 10, Ui.MUTED));
    }

    private void box(String title, String description, String label, View.OnClickListener action) {
        LinearLayout box = Ui.column(this);
        Ui.padded(box, 18, 18);
        box.setBackground(Ui.shape(Ui.CARD, 22, this));
        TextView name = Ui.text(this, title, 11, Ui.ACCENT);
        name.setLetterSpacing(0.08f); box.addView(name);
        Ui.space(box, 10);
        box.addView(Ui.text(this, description, 14, Ui.MUTED));
        Ui.space(box, 14);
        box.addView(Ui.button(this, label, false, action));
        content.addView(box);
    }
    private interface ToggleChange { void changed(boolean value); }
    private void toggle(String label, String description, String key, ToggleChange change) {
        Switch control = new Switch(this);
        control.setText(label); control.setTextSize(16); control.setTextColor(Ui.TEXT);
        control.setMinHeight(Ui.dp(this, 48));
        control.setChecked(Preferences.get(this).getBoolean(key, false));
        control.setOnCheckedChangeListener((button, checked) -> {
            Preferences.get(this).edit().putBoolean(key, checked).apply(); change.changed(checked);
        });
        content.addView(control);
        Ui.space(content, 6);
        content.addView(Ui.text(this, description, 13, Ui.MUTED));
    }
    private void settings(String action) {
        try { startActivity(new Intent(action)); }
        catch (android.content.ActivityNotFoundException e) { Toast.makeText(this, "Open Android Settings to manage notification access.", Toast.LENGTH_LONG).show(); }
    }
    private void addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(StatusBarManager.class).requestAddTileService(new ComponentName(this, ChronoTile.class),
                    "chrono-flow", Icon.createWithResource(this, R.drawable.ic_flow), getMainExecutor(),
                    result -> Toast.makeText(this, result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                            || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                            ? "Tile ready. Find chrono-flow in Quick Settings." : "You can also add chrono-flow using Edit in Quick Settings.", Toast.LENGTH_LONG).show());
        } else new android.app.AlertDialog.Builder(this).setTitle("Add your tile")
                .setMessage("Pull down Quick Settings twice, tap Edit (the pencil), and drag chrono-flow into your active tiles.")
                .setPositiveButton("Got it", null).show();
    }
}
