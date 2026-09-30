package dev.chronoflow;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(16, 18, 23), CARD = Color.rgb(35, 37, 45);
    static final int TEXT = Color.rgb(242, 241, 247), MUTED = Color.rgb(159, 161, 177);
    static final int ACCENT = Color.rgb(197, 183, 245), LINE = Color.rgb(53, 55, 65);
    static int dp(Context context, float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    static LinearLayout column(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    static LinearLayout row(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    static TextView text(Context context, String value, float size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(context, 3), 1f);
        return view;
    }
    static void bold(TextView view) { view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); }
    static GradientDrawable shape(int color, float radius, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }
    static TextView button(Context context, String value, boolean accent, View.OnClickListener listener) {
        TextView view = text(context, value, 14, accent ? BG : TEXT);
        bold(view);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 16), dp(context, 13), dp(context, 16), dp(context, 13));
        view.setMinHeight(dp(context, 48));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ffffff),
                shape(accent ? ACCENT : CARD, 16, context), null));
        view.setOnClickListener(listener);
        view.setFocusable(true);
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        });
        return view;
    }
    static void space(LinearLayout parent, int height) {
        parent.addView(new View(parent.getContext()), new LinearLayout.LayoutParams(1, dp(parent.getContext(), height)));
    }
    static void padded(View view, int horizontal, int vertical) {
        view.setPadding(dp(view.getContext(), horizontal), dp(view.getContext(), vertical),
                dp(view.getContext(), horizontal), dp(view.getContext(), vertical));
    }
    static void insetWindow(Activity activity, View root, int horizontal, int top, int bottom) {
        if (Build.VERSION.SDK_INT >= 30) {
            activity.getWindow().setDecorFitsSystemWindows(false);
        } else {
            activity.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, right, insetTop, insetBottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                left = bars.left; right = bars.right; insetTop = bars.top; insetBottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft(); right = insets.getSystemWindowInsetRight();
                insetTop = insets.getSystemWindowInsetTop(); insetBottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(dp(activity, horizontal) + left, dp(activity, top) + insetTop,
                    dp(activity, horizontal) + right, dp(activity, bottom) + insetBottom);
            return insets;
        });
        root.requestApplyInsets();
    }
    private Ui() {}
}
