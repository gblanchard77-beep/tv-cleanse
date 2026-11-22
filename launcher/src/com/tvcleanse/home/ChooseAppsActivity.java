package com.tvcleanse.home;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * "Edit apps": every app on the TV in a grid. OK shows or hides an app on the
 * home screen; a tick means it's shown. Back goes home. Changes save straight away.
 */
public class ChooseAppsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.background(this));
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setPadding(dp(Ui.SIDE - 14), dp(36), dp(Ui.SIDE - 14), dp(28));

        TextView title = new TextView(this);
        title.setText("Choose your apps");
        title.setTextColor(Ui.TEXT);
        title.setTextSize(32);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setPadding(dp(14), 0, 0, 0);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Press OK to show or hide an app. Press Back when you're done. To move an app, hold OK on it on the home screen.");
        hint.setTextColor(Ui.MUTED);
        hint.setTextSize(15);
        hint.setPadding(dp(15), dp(6), 0, dp(18));
        root.addView(hint);

        Set<String> hidden = Apps.hidden(this);
        List<View> cells = new ArrayList<View>();
        View first = null;
        for (Apps.App app : Apps.ordered(this)) {
            cells.add(toggleCard(app, !hidden.contains(app.key)));
            if (first == null) {
                first = Ui.firstFocusable(cells.get(0));
            }
        }
        root.addView(Ui.grid(this, cells));

        scroll.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        if (first != null) {
            first.requestFocus();
        }
    }

    private View toggleCard(final Apps.App app, boolean shown) {
        final Ui.Card card = Ui.appCard(this, app.banner, app.icon, app.label);

        // A white tick badge in the corner while the app is shown.
        final FrameLayout badge = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable dot = Ui.oval(0xFF3D6BFF);
        dot.setStroke(dp(2), Color.WHITE);
        badge.setBackground(dot);
        badge.setElevation(dp(2));
        ImageView tick = new ImageView(this);
        tick.setImageDrawable(new Glyph(Glyph.CHECK, Color.WHITE));
        badge.addView(tick, new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.TOP | Gravity.END);
        blp.topMargin = dp(6);
        blp.rightMargin = dp(6);
        card.tile.addView(badge, blp);

        final boolean[] state = {shown};
        apply(card, badge, shown);
        card.tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                state[0] = !state[0];
                Apps.setHidden(ChooseAppsActivity.this, app.key, !state[0]);
                apply(card, badge, state[0]);
            }
        });
        return card.holder;
    }

    private static void apply(Ui.Card card, View badge, boolean shown) {
        badge.setVisibility(shown ? View.VISIBLE : View.GONE);
        float alpha = shown ? 1f : 0.35f;
        for (int i = 0; i < card.tile.getChildCount(); i++) {
            View child = card.tile.getChildAt(i);
            if (child != badge) {
                child.setAlpha(alpha);
            }
        }
        card.name.setAlpha(shown ? 1f : 0.5f);
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
