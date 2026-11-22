package com.tvcleanse.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A plain TV home screen: a clock, one row of your apps, and one row with
 * HDMI inputs, the Play Store and Settings. No ads, no suggestions, no libraries.
 */
public class MainActivity extends Activity {

    private static final int BG = Color.rgb(16, 20, 24);

    // Packages shown in the second row instead of the apps row.
    private static final String PLAY_STORE = "com.android.vending";

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(56), dp(32), dp(56), dp(24));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    @Override
    public void onBackPressed() {
        // This is the home screen. Back does nothing.
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already here: go back to the first tile.
        View first = firstFocusable(root);
        if (first != null) {
            first.requestFocus();
        }
    }

    // ---------------------------------------------------------------- layout

    private void build() {
        root.removeAllViews();

        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm a");
        time.setFormat24Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(64);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, MMMM d");
        date.setFormat24Hour("EEEE, MMMM d");
        date.setTextColor(Color.rgb(170, 178, 189));
        date.setTextSize(22);
        root.addView(date);

        root.addView(spacer(dp(40)));
        root.addView(sectionTitle("Apps"));
        root.addView(row(appTiles()));

        root.addView(spacer(dp(28)));
        root.addView(sectionTitle("Inputs and settings"));
        root.addView(row(systemTiles()));

        View first = firstFocusable(root);
        if (first != null) {
            first.requestFocus();
        }
    }

    private List<View> appTiles() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
        if (found.isEmpty()) {
            query = new Intent(Intent.ACTION_MAIN);
            query.addCategory(Intent.CATEGORY_LAUNCHER);
            found = pm.queryIntentActivities(query, 0);
        }

        final PackageManager pmf = pm;
        List<ResolveInfo> apps = new ArrayList<ResolveInfo>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || pkg.equals(PLAY_STORE)
                    || pkg.endsWith(".settings")) {
                continue;
            }
            apps.add(ri);
        }
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pmf).toString().compareToIgnoreCase(b.loadLabel(pmf).toString());
            }
        });

        List<View> tiles = new ArrayList<View>();
        for (ResolveInfo ri : apps) {
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            tiles.add(tile(ri.loadIcon(pm), ri.loadLabel(pm), launch));
        }
        return tiles;
    }

    private List<View> systemTiles() {
        List<View> tiles = new ArrayList<View>();

        // HDMI inputs
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                for (TvInputInfo info : tim.getTvInputList()) {
                    if (info.getType() != TvInputInfo.TYPE_HDMI) {
                        continue;
                    }
                    Intent launch = new Intent(Intent.ACTION_VIEW,
                            TvContract.buildChannelUriForPassthroughInput(info.getId()));
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    CharSequence label = info.loadLabel(this);
                    Drawable icon = info.loadIcon(this);
                    if (icon == null) {
                        icon = plainIcon();
                    }
                    tiles.add(tile(icon, label == null ? "HDMI" : label, launch));
                }
            }
        } catch (Exception ignored) {
            // No TV input service on this device: skip the HDMI tiles.
        }

        // Play Store
        PackageManager pm = getPackageManager();
        Intent store = pm.getLeanbackLaunchIntentForPackage(PLAY_STORE);
        if (store == null) {
            store = pm.getLaunchIntentForPackage(PLAY_STORE);
        }
        if (store != null) {
            store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                tiles.add(tile(pm.getApplicationIcon(PLAY_STORE), "Play Store", store));
            } catch (PackageManager.NameNotFoundException ignored) {
                // Not installed.
            }
        }

        // Settings
        Intent settings = new Intent(Settings.ACTION_SETTINGS);
        settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        tiles.add(tile(plainIcon(), "Settings", settings));

        return tiles;
    }

    // ----------------------------------------------------------------- views

    private View row(List<View> tiles) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        for (View t : tiles) {
            line.addView(t);
        }
        scroll.addView(line, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private View tile(Drawable icon, CharSequence label, final Intent launch) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(dp(16), dp(16), dp(16), dp(12));
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(focusBackground());

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        t.addView(iv, new LinearLayout.LayoutParams(dp(96), dp(96)));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(Color.WHITE);
        name.setTextSize(16);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        name.setMaxWidth(dp(128));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(8);
        t.addView(name, nameLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(12);
        t.setLayoutParams(lp);

        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(launch);
                } catch (Exception ignored) {
                    // App went away or cannot be opened. Stay on the home screen.
                }
            }
        });
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.08f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(100).start();
            }
        });
        return t;
    }

    private Drawable focusBackground() {
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.argb(60, 255, 255, 255));
        focused.setStroke(dp(3), Color.WHITE);
        focused.setCornerRadius(dp(12));

        GradientDrawable idle = new GradientDrawable();
        idle.setColor(Color.TRANSPARENT);
        idle.setCornerRadius(dp(12));

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, focused);
        states.addState(new int[] {}, idle);
        return states;
    }

    private Drawable plainIcon() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.rgb(48, 56, 68));
        d.setCornerRadius(dp(16));
        return d;
    }

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.rgb(170, 178, 189));
        tv.setTextSize(18);
        tv.setPadding(dp(16), 0, 0, dp(8));
        return tv;
    }

    private View spacer(int height) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, height));
        return v;
    }

    private View firstFocusable(View v) {
        if (v.isClickable() && v.isFocusable()) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = firstFocusable(g.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
