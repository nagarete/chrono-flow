package dev.chronoflow;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import dev.chronoflow.core.LockPolicy;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The daily surface, launched from the tile or edge. Never unlocks or opens itself automatically. */
public final class PanelActivity extends Activity implements NotificationRepository.Observer {
    private NotificationRepository repository;
    private ListView list;
    private TextView subtitle, footer, clock, date;
    private final PanelAdapter adapter = new PanelAdapter();
    private final List<Object> rows = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();
    private final Map<String, Long> observed = new HashMap<>();
    private final Map<String, Dwell> dwell = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable sample = this::sampleVisible;
    private final Runnable rerender = this::render;
    private boolean resumed, locked, earlierOpen;
    private AlertDialog dialog;
    private static final String NEW = "NEW", EARLIER = "EARLIER", EMPTY = "EMPTY", STACK = "STACK";
    private int earlierCount;
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                // Remove private views before the device transitions to its locked state.
                dwell.clear(); rows.clear(); adapter.notifyDataSetChanged(); finish();
            } else render();
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = ChronoApp.repository(this);
        if (savedInstanceState != null) earlierOpen = savedInstanceState.getBoolean("earlier", false);
        setShowWhenLocked(true);
        LinearLayout root = Ui.column(this);
        root.setBackground(Ui.shape(Ui.BG, 30, this));
        LinearLayout top = Ui.row(this);
        TextView brand = Ui.text(this, "≡  chrono-flow", 15, Ui.ACCENT);
        Ui.bold(brand);
        top.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = Ui.button(this, "×", false, view -> finish());
        close.setTextSize(25); close.setContentDescription("Close notification panel"); top.addView(close);
        root.addView(top);
        Ui.space(root, 22);
        clock = Ui.text(this, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), 44, Ui.TEXT);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)); root.addView(clock);
        Ui.space(root, 7);
        date = Ui.text(this, new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()), 14, Ui.MUTED);
        root.addView(date);
        Ui.space(root, 26);
        TextView title = Ui.text(this, "A clear view of now.", 25, Ui.TEXT);
        Ui.bold(title); root.addView(title);
        Ui.space(root, 8);
        subtitle = Ui.text(this, "", 13, Ui.MUTED); root.addView(subtitle);
        Ui.space(root, 20);
        list = new ListView(this);
        list.setDivider(null); list.setSelector(android.R.color.transparent);
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false); list.setPadding(0, 0, 0, Ui.dp(this, 12));
        list.setAdapter(adapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int state) { scheduleSample(); }
            @Override public void onScroll(AbsListView view, int first, int visible, int total) { scheduleSample(); }
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        Ui.space(root, 10);
        LinearLayout bottom = Ui.row(this);
        footer = Ui.text(this, "", 11, Ui.MUTED);
        bottom.addView(footer, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bottom.addView(Ui.button(this, "Done", true, view -> finish())); root.addView(bottom);
        setContentView(root);
        Ui.secureWindow(this, root, 20, 14, 16);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_TIME_TICK); filter.addAction(Intent.ACTION_TIME_CHANGED); filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, filter);
        render();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("earlier", earlierOpen);
        super.onSaveInstanceState(state);
    }
    @Override protected void onResume() {
        super.onResume();
        ChronoListener.refreshActive();
        resumed = true; repository.observe(this); render();
    }
    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacksAndMessages(null);
        repository.unobserve(this);
        // A lock-screen glance is not a meaningful check.
        if (!observed.isEmpty()) {
            List<String> shown = new ArrayList<>();
            for (Map.Entry<String, Long> item : observed.entrySet()) {
                NotificationEntry entry = repository.find(item.getKey());
                if (entry != null && entry.attention.revision == item.getValue()) shown.add(entry.sbn.getKey());
            }
            repository.checked(observed);
            ChronoListener.shown(shown.toArray(new String[0]));
        }
        observed.clear(); dwell.clear();
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        super.onPause();
    }
    @Override protected void onDestroy() { unregisterReceiver(screen); super.onDestroy(); }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && repository != null) render();
    }
    @Override public void onChanged() {
        // Coalesce bursts of listener callbacks without retaining activity observers while closed.
        handler.removeCallbacks(rerender); handler.post(rerender);
    }
    private LockPolicy.Presentation presentation(NotificationEntry entry) {
        return LockPolicy.presentation(locked, Preferences.systemAllowsLockNotifications(this),
                Preferences.get(this).getBoolean("public_on_lock", false), entry.notification().visibility,
                entry.lockVisibility);
    }

    private void render() {
        locked = Preferences.locked(this);
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        date.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()));
        rows.clear();
        List<NotificationEntry> fresh = new ArrayList<>(), earlier = new ArrayList<>();
        for (NotificationEntry entry : repository.snapshot()) {
            if (presentation(entry) == LockPolicy.Presentation.HIDDEN) continue;
            (entry.attention.isNew() ? fresh : earlier).add(entry);
        }
        earlierCount = earlier.size();
        subtitle.setText(locked ? "Locked · private content stays private"
                : "What’s new, with room for what came before.");
        footer.setText(locked ? "Unlock to interact" : "On your device. Only on your device.");
        rows.add(NEW);
        if (!repository.connected()) rows.add("DISCONNECTED");
        else if (fresh.isEmpty()) rows.add(EMPTY);
        else rows.addAll(fresh);
        if (earlierCount > 0) {
            rows.add(EARLIER);
            if (earlierOpen) rows.addAll(earlier); else rows.add(STACK);
        }
        adapter.notifyDataSetChanged();
        scheduleSample();
    }

    private void scheduleSample() {
        handler.removeCallbacks(sample);
        if (resumed && !Preferences.locked(this)) handler.post(sample);
    }

    private void sampleVisible() {
        if (!resumed || Preferences.locked(this)) { dwell.clear(); return; }
        long now = SystemClock.elapsedRealtime();
        Set<String> visible = new HashSet<>();
        boolean pending = false;
        Rect viewport = new Rect(); list.getGlobalVisibleRect(viewport);
        for (int i = 0; i < list.getChildCount(); i++) {
            View view = list.getChildAt(i);
            Object tag = view.getTag();
            if (!(tag instanceof NotificationEntry)) continue;
            NotificationEntry entry = (NotificationEntry) tag;
            Rect bounds = new Rect();
            if (!view.getGlobalVisibleRect(bounds) || bounds.height() < Math.min(view.getHeight() / 2, Ui.dp(this, 80))) continue;
            if (!bounds.intersect(viewport) || bounds.height() < Math.min(view.getHeight() / 2, Ui.dp(this, 80))) continue;
            visible.add(entry.attention.key);
            Dwell item = dwell.get(entry.attention.key);
            if (item == null || item.revision != entry.attention.revision) {
                item = new Dwell(entry.attention.revision, now); dwell.put(entry.attention.key, item);
            }
            if (now - item.since >= 1200) observed.put(entry.attention.key, item.revision);
            else pending = true;
        }
        dwell.keySet().retainAll(visible);
        if (pending) handler.postDelayed(sample, 1200);
    }

    private final class PanelAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean isEnabled(int position) { return false; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            Object row = rows.get(position);
            if (row instanceof NotificationEntry) return card((NotificationEntry) row);
            String kind = row.toString();
            if (NEW.equals(kind) || EARLIER.equals(kind)) {
                LinearLayout header = Ui.row(PanelActivity.this);
                header.setPadding(0, Ui.dp(PanelActivity.this, NEW.equals(kind) ? 0 : 24), 0, Ui.dp(PanelActivity.this, 12));
                int count = 0;
                if (NEW.equals(kind)) for (Object value : rows) if (value instanceof NotificationEntry
                        && ((NotificationEntry) value).attention.isNew()) count++;
                TextView label = Ui.text(PanelActivity.this, NEW.equals(kind) ? "NEW  ·  " + count : "EARLIER  ·  " + earlierCount,
                        11, NEW.equals(kind) ? Ui.ACCENT : Ui.MUTED);
                label.setLetterSpacing(0.14f); Ui.bold(label);
                header.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                if (EARLIER.equals(kind)) header.addView(Ui.button(PanelActivity.this,
                        earlierOpen ? "Collapse ↑" : "Show ↓", false, view -> { earlierOpen = !earlierOpen; render(); }));
                return header;
            }
            LinearLayout container = Ui.column(PanelActivity.this);
            Ui.padded(container, 20, 26);
            container.setBackground(Ui.shape(Ui.CARD, 22, PanelActivity.this));
            if (STACK.equals(kind)) {
                TextView stackTitle = Ui.text(PanelActivity.this, "Still here. Just a little quieter.", 16, Ui.MUTED);
                Ui.bold(stackTitle); container.addView(stackTitle); Ui.space(container, 8);
                container.addView(Ui.text(PanelActivity.this, earlierCount + (earlierCount == 1 ? " seen notification" : " seen notifications") + " · tap to look back", 13, Ui.MUTED));
                container.setAlpha(0.65f); container.setOnClickListener(view -> { earlierOpen = true; render(); });
                container.setFocusable(true); container.setContentDescription("Show " + earlierCount + " earlier notifications");
            } else if (EMPTY.equals(kind)) {
                container.addView(Ui.text(PanelActivity.this, "✓", 30, Ui.ACCENT)); Ui.space(container, 15);
                TextView emptyTitle = Ui.text(PanelActivity.this, locked ? "A private moment." : "You’re all caught up.", 19, Ui.TEXT);
                Ui.bold(emptyTitle); container.addView(emptyTitle); Ui.space(container, 8);
                container.addView(Ui.text(PanelActivity.this, locked ? "Unlock to see notifications hidden by your privacy settings."
                        : "New notifications will find their place here.", 14, Ui.MUTED));
            } else {
                container.addView(Ui.text(PanelActivity.this, "Connect your notifications", 18, Ui.TEXT)); Ui.space(container, 10);
                container.addView(Ui.text(PanelActivity.this, Preferences.accessGranted(PanelActivity.this)
                        ? "Waiting for Android to connect. If this persists, toggle notification access off and on."
                        : "Enable notification access once, then come here from your tile or edge handle.", 14, Ui.MUTED));
                Ui.space(container, 16);
                container.addView(Ui.button(PanelActivity.this, locked ? "Unlock to set up" : "Set up chrono-flow", true,
                        view -> unlocked(() -> startActivity(new Intent(PanelActivity.this, SetupActivity.class)))));
            }
            return container;
        }
    }

    private View card(NotificationEntry entry) {
        LinearLayout outer = Ui.column(this);
        outer.setPadding(0, 0, 0, Ui.dp(this, 10)); outer.setTag(entry);
        LinearLayout card = Ui.column(this); Ui.padded(card, 16, 15);
        android.graphics.drawable.GradientDrawable background = Ui.shape(Ui.CARD, 22, this);
        if (entry.attention.isNew()) background.setStroke(Ui.dp(this, 1), 0x30c5b7f5);
        card.setBackground(background); outer.addView(card);
        if (!entry.attention.isNew()) card.setAlpha(0.65f);
        LinearLayout top = Ui.row(this);
        ImageView icon = new ImageView(this);
        try {
            Drawable drawable = entry.notification().getSmallIcon() == null ? null
                    : entry.notification().getSmallIcon().loadDrawable(this);
            icon.setImageDrawable(drawable); icon.setColorFilter(Ui.ACCENT);
        } catch (RuntimeException ignored) { icon.setImageResource(R.drawable.ic_flow); }
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        top.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18)));
        TextView name = Ui.text(this, entry.appLabel(this), 11, Ui.MUTED);
        Ui.bold(name); name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams appSize = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        appSize.setMarginStart(Ui.dp(this, 8)); top.addView(name, appSize);
        top.addView(Ui.text(this, age(entry.attention.receivedAt), 10, Ui.MUTED));
        boolean open = expanded.contains(entry.attention.key);
        TextView expand = Ui.button(this, open ? "⌃" : "⌄", false, view -> {
            if (open) expanded.remove(entry.attention.key); else expanded.add(entry.attention.key);
            render();
        });
        expand.setContentDescription((open ? "Collapse " : "Expand ") + entry.appLabel(this) + " notification");
        expand.setPadding(Ui.dp(this, 10), 0, 0, 0); expand.setMinWidth(Ui.dp(this, 48));
        top.addView(expand); card.addView(top);
        boolean redacted = presentation(entry) != LockPolicy.Presentation.PUBLIC_CONTENT;
        String heading = redacted ? "Notification" : entry.title();
        if (heading.isEmpty()) heading = entry.appLabel(this);
        TextView title = Ui.text(this, heading, 16, Ui.TEXT); Ui.bold(title);
        title.setMaxLines(open ? Integer.MAX_VALUE : 2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END); card.addView(title);
        String body = redacted ? "Unlock to view this notification." : entry.body(open);
        if (!body.isEmpty()) {
            Ui.space(card, 6);
            TextView text = Ui.text(this, body, 14, Ui.MUTED);
            text.setMaxLines(open ? Integer.MAX_VALUE : 2); text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(text);
        }
        if (open) {
            Ui.space(card, 14);
            HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout actions = Ui.row(this);
            actions.addView(Ui.button(this, locked ? "Unlock" : "Open", false, view -> open(entry)));
            if (!locked && entry.notification().actions != null) {
                for (int i = 0; i < entry.notification().actions.length; i++) {
                    Notification.Action action = entry.notification().actions[i];
                    if (action.actionIntent == null) continue;
                    final int index = i;
                    TextView actionView = Ui.button(this, action.title == null ? "Action" : action.title.toString(), false,
                            view -> unlocked(() -> action(entry, index, action.actionIntent)));
                    LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    margin.setMarginStart(Ui.dp(this, 8)); actions.addView(actionView, margin);
                }
            }
            if (entry.dismissible()) {
                TextView dismiss = Ui.button(this, "Dismiss", false, view -> dismiss(entry));
                LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                margin.setMarginStart(Ui.dp(this, 8)); actions.addView(dismiss, margin);
            }
            scroll.addView(actions); card.addView(scroll);
        }
        card.setOnClickListener(view -> open(entry));
        card.setOnLongClickListener(view -> { expanded.add(entry.attention.key); render(); return true; });
        card.setFocusable(true);
        attachSwipe(card, entry);
        return outer;
    }

    private void attachSwipe(View card, NotificationEntry entry) {
        card.setOnTouchListener(new View.OnTouchListener() {
            float x, y; boolean dragging;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { x = event.getX(); y = event.getY(); dragging = false; return true; }
                float delta = event.getX() - x;
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    if (Math.abs(delta) > Ui.dp(PanelActivity.this, 24) && Math.abs(delta) > Math.abs(event.getY() - y) * 1.5) {
                        dragging = true; view.getParent().requestDisallowInterceptTouchEvent(true);
                        view.setTranslationX(delta * 0.45f);
                    }
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    view.setTranslationX(0); view.getParent().requestDisallowInterceptTouchEvent(false);
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        if (dragging && Math.abs(delta) > Ui.dp(PanelActivity.this, 90)) dismiss(entry);
                        else if (!dragging) view.performClick();
                    }
                    return true;
                }
                return true;
            }
        });
    }

    private String age(long time) {
        long minutes = Math.max(0, (System.currentTimeMillis() - time) / 60000);
        if (minutes == 0) return "now";
        if (minutes < 60) return minutes + "m";
        if (minutes < 1440) return minutes / 60 + "h";
        return minutes / 1440 + "d";
    }

    private void unlocked(Runnable action) {
        if (!Preferences.locked(this)) { action.run(); return; }
        getSystemService(KeyguardManager.class).requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
            @Override public void onDismissSucceeded() {
                if (isFinishing() || isDestroyed() || Preferences.locked(PanelActivity.this)) return;
                render(); action.run();
            }
            @Override public void onDismissError() { message("Unlock your device to continue."); }
        });
    }

    private NotificationEntry live(NotificationEntry old) {
        NotificationEntry entry = repository.find(old.attention.key);
        if (entry == null || entry.attention.revision != old.attention.revision) {
            message("This notification changed or is no longer available."); return null;
        }
        return entry;
    }
    private void open(NotificationEntry old) {
        unlocked(() -> {
            NotificationEntry entry = live(old); if (entry == null) return;
            PendingIntent intent = entry.notification().contentIntent;
            if (intent == null) { expanded.add(entry.attention.key); render(); message("This notification has no open action."); return; }
            if (send(intent, null)) {
                observed.put(entry.attention.key, entry.attention.revision);
                if ((entry.notification().flags & Notification.FLAG_AUTO_CANCEL) != 0) ChronoListener.dismiss(entry.sbn.getKey());
                finish();
            }
        });
    }
    private void dismiss(NotificationEntry old) {
        unlocked(() -> {
            NotificationEntry entry = live(old); if (entry == null) return;
            if (!entry.dismissible()) { message("Android keeps this ongoing notification active."); return; }
            if (!ChronoListener.dismiss(entry.sbn.getKey())) message("Notification access is disconnected.");
        });
    }
    private void action(NotificationEntry old, int index, PendingIntent expectedIntent) {
        NotificationEntry entry = live(old); if (entry == null) return;
        Notification.Action[] actions = entry.notification().actions;
        if (actions == null || index >= actions.length || !expectedIntent.equals(actions[index].actionIntent)) {
            message("This action changed. Expand the notification again."); return;
        }
        Notification.Action action = actions[index];
        RemoteInput[] inputs = action.getRemoteInputs();
        if (inputs == null || inputs.length == 0) {
            if (send(action.actionIntent, null)) observed.put(entry.attention.key, entry.attention.revision);
            return;
        }
        if (Build.VERSION.SDK_INT >= 31 && action.actionIntent.isImmutable()) {
            message("This app’s reply action cannot accept input. Open the app to reply."); return;
        }
        reply(entry, index, action);
    }

    private void reply(NotificationEntry entry, int index, Notification.Action action) {
        LinearLayout form = Ui.column(this); Ui.padded(form, 24, 12);
        Map<String, EditText> fields = new HashMap<>();
        Map<String, CharSequence[]> choices = new HashMap<>();
        for (RemoteInput input : action.getRemoteInputs()) {
            EditText edit = new EditText(this);
            edit.setTextColor(Ui.TEXT); edit.setHintTextColor(Ui.MUTED);
            edit.setHint(input.getLabel() == null ? "Reply" : input.getLabel());
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            edit.setMaxLines(4); edit.setMinHeight(Ui.dp(this, 48));
            if (!input.getAllowFreeFormInput()) {
                CharSequence[] options = input.getChoices();
                if (options == null || options.length == 0) { message("This action needs input this panel cannot provide. Open the app to continue."); return; }
                choices.put(input.getResultKey(), options);
                edit.setFocusable(false); edit.setOnClickListener(view -> {
                    AlertDialog choice = new AlertDialog.Builder(this).setTitle(input.getLabel()).setItems(options,
                            (d, which) -> edit.setText(options[which])).create();
                    choice.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); choice.show();
                });
            }
            fields.put(input.getResultKey(), edit); form.addView(edit);
        }
        dialog = new AlertDialog.Builder(this).setTitle(action.title).setView(form)
                .setNegativeButton("Cancel", null).setPositiveButton("Send", null).create();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (Preferences.locked(this)) { dialog.dismiss(); render(); return; }
            NotificationEntry current = live(entry); if (current == null) { dialog.dismiss(); return; }
            Notification.Action[] currentActions = current.notification().actions;
            if (currentActions == null || index >= currentActions.length || !action.actionIntent.equals(currentActions[index].actionIntent)) {
                dialog.dismiss(); message("This action is no longer available."); return;
            }
            Bundle results = new Bundle();
            for (Map.Entry<String, EditText> field : fields.entrySet()) {
                String value = field.getValue().getText().toString().trim();
                if (value.isEmpty()) { field.getValue().setError("Enter a reply"); return; }
                results.putCharSequence(field.getKey(), value);
            }
            Intent fillIn = new Intent();
            RemoteInput.addResultsToIntent(action.getRemoteInputs(), fillIn, results);
            if (Build.VERSION.SDK_INT >= 28) RemoteInput.setResultsSource(fillIn, choices.isEmpty()
                    ? RemoteInput.SOURCE_FREE_FORM_INPUT : RemoteInput.SOURCE_CHOICE);
            if (send(action.actionIntent, fillIn)) {
                observed.put(entry.attention.key, entry.attention.revision);
                dialog.dismiss(); message("Reply sent to " + entry.appLabel(this));
            }
        }));
        dialog.show();
    }

    private boolean send(PendingIntent pendingIntent, Intent fillIn) {
        try {
            Bundle options = null;
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions activityOptions = ActivityOptions.makeBasic();
                // Explicit opt-in is limited to this user-triggered, unlocked, foreground send.
                activityOptions.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                options = activityOptions.toBundle();
            }
            pendingIntent.send(this, 0, fillIn, null, null, null, options); return true;
        } catch (PendingIntent.CanceledException | SecurityException e) {
            message("The app no longer supports this action."); return false;
        }
    }
    private void message(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    private static final class Dwell {
        final long revision, since;
        Dwell(long revision, long since) { this.revision = revision; this.since = since; }
    }
}
