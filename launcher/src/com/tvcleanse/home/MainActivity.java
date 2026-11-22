package com.tvcleanse.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The TV Cleanse home screen: a clock, a grid of the apps you chose, and your
 * HDMI inputs. No ads, no suggestions, no libraries.
 */
public class MainActivity extends Activity {

    private LinearLayout root;
    private ScrollView scroll;
    private View firstApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scroll = new ScrollView(this);
        scroll.setBackground(Ui.background(this));
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setPadding(dp(Ui.SIDE - 14), dp(32), dp(Ui.SIDE - 14), dp(28));
        scroll.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        HomeRedirect.homeShown(this);
        moving = -1;
        build();
    }

    @Override
    public void onBackPressed() {
        // This is the home screen. Back does nothing.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            event.startTracking(); // so a long press can be seen
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        // Hold Back: go to Google TV's own home screen (Google TV only).
        if (keyCode == KeyEvent.KEYCODE_BACK && HomeRedirect.openGoogleTv(this)) {
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        scroll.scrollTo(0, 0);
        focusFirstApp();
    }

    // ---------------------------------------------------------------- layout

    private void build() {
        root.removeAllViews();
        firstApp = null;

        root.addView(header());

        apps = Apps.shown(this);
        cards = new ArrayList<Ui.Card>();
        List<View> cells = new ArrayList<View>();
        for (int i = 0; i < apps.size(); i++) {
            Apps.App app = apps.get(i);
            Ui.Card card = Ui.appCard(this, app.banner, app.icon, app.label);
            card.tile.setOnClickListener(launcher(app.launch));
            final int index = i;
            card.tile.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    startMoving(index);
                    return true;
                }
            });
            cards.add(card);
            cells.add(card.holder);
            if (firstApp == null) {
                firstApp = card.tile;
            }
        }
        Ui.Card edit = Ui.glyphCard(this, Glyph.PLUS, "Edit apps");
        edit.tile.setOnClickListener(launcher(new Intent(this, ChooseAppsActivity.class)));
        cells.add(edit.holder);

        String title = "Apps";
        if (moving >= 0 && moving < apps.size()) {
            title = "Moving " + apps.get(moving).label + "  ·  use the arrows, then press OK";
        }
        root.addView(Ui.sectionTitle(this, title), marginTop(26));
        root.addView(Ui.grid(this, cells));

        List<View> inputs = inputChips();
        if (!inputs.isEmpty()) {
            root.addView(Ui.sectionTitle(this, "Inputs"), marginTop(6));
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setClipChildren(false);
            line.setPadding(dp(14), dp(12), dp(14), 0);
            for (View v : inputs) {
                line.addView(v);
            }
            root.addView(line);
        }

        if (moving >= 0 && moving < cards.size()) {
            Ui.Card m = cards.get(moving);
            m.tile.setForeground(movingRing());
            m.tile.requestFocus();
            m.name.setText("Moving…");
        } else {
            moving = -1;
            focusFirstApp();
        }
    }

    // ------------------------------------------------------------ move mode

    private List<Apps.App> apps = new ArrayList<Apps.App>();
    private List<Ui.Card> cards = new ArrayList<Ui.Card>();
    private int moving = -1;
    private boolean swallowUp;

    private void startMoving(int index) {
        moving = index;
        swallowUp = true; // the OK press that started the move
        build();
    }

    private void stopMoving() {
        int keep = moving;
        moving = -1;
        build();
        if (keep >= 0 && keep < cards.size()) {
            cards.get(keep).tile.requestFocus();
        }
    }

    /** Move the app in move mode by `step` places (left/right = 1, up/down = a row). */
    private void moveBy(int step) {
        int to = moving + step;
        if (to < 0 || to >= apps.size()) {
            return;
        }
        Apps.App a = apps.remove(moving);
        apps.add(to, a);
        Apps.saveOrder(this, apps);
        moving = to;
        build();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (moving < 0) {
            // OK held down on an app: start moving it.
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.isLongPress()
                    && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
                View f = getCurrentFocus();
                for (int i = 0; i < cards.size(); i++) {
                    if (cards.get(i).tile == f) {
                        startMoving(i);
                        return true;
                    }
                }
            }
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            swallowUp = false;
            return true;
        }
        if (swallowUp || event.getRepeatCount() > 0 && !isArrow(code)) {
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: moveBy(-1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: moveBy(1); return true;
            case KeyEvent.KEYCODE_DPAD_UP: moveBy(-Ui.COLUMNS); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: moveBy(Ui.COLUMNS); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
                stopMoving();
                return true;
            default:
                // Home and other system keys still work; anything else ends the move.
                moving = -1;
                return super.dispatchKeyEvent(event);
        }
    }

    private static boolean isArrow(int code) {
        return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    private android.graphics.drawable.Drawable movingRing() {
        android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
        ring.setColor(0x333D6BFF);
        ring.setStroke(dp(4), 0xFF5B8CFF);
        ring.setCornerRadius(dp(Ui.CARD_RADIUS));
        return ring;
    }

    private void focusFirstApp() {
        View target = firstApp != null ? firstApp : Ui.firstFocusable(root);
        if (target != null) {
            target.requestFocus();
        }
    }

    private View header() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.TOP);
        bar.setPadding(dp(14), 0, dp(14), 0);
        bar.setClipChildren(false);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        // Always 12-hour with AM/PM, whatever the TV's own setting.
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.HORIZONTAL);
        clock.setBaselineAligned(true);

        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("h:mm");
        time.setTextColor(Ui.TEXT);
        time.setTextSize(56);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setLetterSpacing(-0.02f);
        time.setIncludeFontPadding(false);
        clock.addView(time);

        TextClock ampm = new TextClock(this);
        ampm.setFormat12Hour("a");
        ampm.setFormat24Hour("a");
        ampm.setAllCaps(true);
        ampm.setTextColor(Ui.TEXT);
        ampm.setTextSize(20);
        ampm.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        ampm.setLetterSpacing(0.06f);
        ampm.setIncludeFontPadding(false);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = dp(10);
        clock.addView(ampm, alp);
        left.addView(clock);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, MMMM d");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextColor(Ui.MUTED);
        date.setTextSize(16);
        date.setPadding(dp(3), dp(6), 0, 0);
        left.addView(date);

        bar.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Intent settings = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        bar.addView(gearButton(settings));
        return bar;
    }

    // ---------------------------------------------------------------- inputs

    private List<View> inputChips() {
        List<View> chips = new ArrayList<View>();
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                List<TvInputInfo> list = new ArrayList<TvInputInfo>(tim.getTvInputList());
                final Context ctx = this;
                Collections.sort(list, new Comparator<TvInputInfo>() {
                    @Override
                    public int compare(TvInputInfo a, TvInputInfo b) {
                        return String.valueOf(a.loadLabel(ctx)).compareToIgnoreCase(String.valueOf(b.loadLabel(ctx)));
                    }
                });
                for (TvInputInfo info : list) {
                    if (info.getType() != TvInputInfo.TYPE_HDMI) {
                        continue;
                    }
                    Intent launch = new Intent(Intent.ACTION_VIEW,
                            TvContract.buildChannelUriForPassthroughInput(info.getId()));
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    CharSequence label = info.loadLabel(this);
                    if (label == null || label.length() == 0) {
                        label = "HDMI " + (chips.size() + 1);
                    }
                    chips.add(chip(label, launch));
                }
            }
        } catch (Exception ignored) {
            // No TV input service on this device: no inputs row.
        }
        return chips;
    }

    /** A pill-shaped button: an input icon and a name. Turns white when selected. */
    private View chip(CharSequence label, Intent launch) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(18), 0, dp(22), 0);
        c.setMinimumWidth(dp(132));
        c.setFocusable(true);
        c.setClickable(true);
        c.setBackground(Ui.states(Ui.rounded(this, Color.WHITE, 26), Ui.rounded(this, Ui.CARD, 26)));

        final Glyph glyph = new Glyph(Glyph.INPUT, Ui.TEXT);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(glyph);
        c.addView(iv, new LinearLayout.LayoutParams(dp(20), dp(20)));

        final TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(Ui.TEXT);
        name.setTextSize(14);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setSingleLine(true);
        name.setPadding(dp(10), 0, 0, 0);
        c.addView(name);

        c.setOnClickListener(launcher(launch));
        final Context ctx = this;
        c.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                Ui.lift(ctx, v, hasFocus, 1.06f);
                int color = hasFocus ? Ui.ON_LIGHT : Ui.TEXT;
                name.setTextColor(color);
                glyph.setColor(color);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(46));
        lp.rightMargin = dp(16);
        c.setLayoutParams(lp);
        return c;
    }

    /** The round Settings button in the top corner. */
    private View gearButton(Intent settings) {
        FrameLayout b = new FrameLayout(this);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(Ui.states(Ui.oval(Color.WHITE), Ui.oval(0x1FFFFFFF)));
        final Glyph gear = new Glyph(Glyph.GEAR, Ui.TEXT);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(gear);
        b.addView(iv, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        b.setOnClickListener(launcher(settings));
        final Context ctx = this;
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                Ui.lift(ctx, v, hasFocus, 1.1f);
                gear.setColor(hasFocus ? Ui.ON_LIGHT : Ui.TEXT);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    // ------------------------------------------------------------- helpers

    private View.OnClickListener launcher(final Intent launch) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(launch);
                } catch (Exception ignored) {
                    // App went away or cannot be opened. Stay on the home screen.
                }
            }
        };
    }

    private LinearLayout.LayoutParams marginTop(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
