package com.tvcleanse.home;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Shared look: colours, sizes, cards and the grid. */
final class Ui {

    static final int BG_TOP = 0xFF101722;
    static final int BG_BOTTOM = 0xFF06080C;
    static final int GLOW = 0x403A6DFF;
    static final int CARD = 0xFF19202B;
    static final int TEXT = 0xFFF3F5F8;
    static final int MUTED = 0xFF8A94A6;
    static final int ON_LIGHT = 0xFF0B0F15;

    // Sizes in dp. 5 cards across a 1080p TV.
    static final int SIDE = 56;
    static final int COLUMNS = 5;
    static final int CARD_W = 150;
    static final int CARD_H = 84;
    static final int CARD_RADIUS = 12;
    static final int GAP = 20;

    private Ui() {
    }

    /** A card in the grid: the tile itself, and a name underneath. */
    static final class Card {
        final LinearLayout holder;
        final FrameLayout tile;
        final TextView name;

        Card(LinearLayout holder, FrameLayout tile, TextView name) {
            this.holder = holder;
            this.tile = tile;
            this.name = name;
        }
    }

    /** An app card: its banner if it has one, otherwise its icon on a dark tile. */
    static Card appCard(Context c, Drawable banner, Drawable icon, CharSequence label) {
        Card card = blankCard(c, label);
        ImageView art = new ImageView(c);
        if (banner != null) {
            art.setImageDrawable(banner);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.tile.addView(art, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            card.tile.setBackground(iconTile(c));
            art.setImageDrawable(icon);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.tile.addView(art, 0, new FrameLayout.LayoutParams(dp(c, 44), dp(c, 44), Gravity.CENTER));
        }
        return card;
    }

    /** A card with a line icon in the middle, like "Edit apps". */
    static Card glyphCard(Context c, int glyph, CharSequence label) {
        Card card = blankCard(c, label);
        card.tile.setBackground(iconTile(c));
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(new Glyph(glyph, TEXT));
        card.tile.addView(iv, 0, new FrameLayout.LayoutParams(dp(c, 30), dp(c, 30), Gravity.CENTER));
        return card;
    }

    private static Card blankCard(Context c, CharSequence label) {
        LinearLayout holder = new LinearLayout(c);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setClipChildren(false);

        final FrameLayout tile = new FrameLayout(c);
        tile.setBackground(rounded(c, CARD, CARD_RADIUS));
        tile.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        tile.setClipToOutline(true);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setForeground(focusRing(c, CARD_RADIUS));
        holder.addView(tile, new LinearLayout.LayoutParams(dp(c, CARD_W), dp(c, CARD_H)));

        final TextView name = new TextView(c);
        name.setText(label);
        name.setTextColor(MUTED);
        name.setTextSize(13);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(dp(c, 2), dp(c, 9), dp(c, 2), 0);
        holder.addView(name, new LinearLayout.LayoutParams(dp(c, CARD_W), ViewGroup.LayoutParams.WRAP_CONTENT));

        final Context ctx = c;
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                lift(ctx, v, hasFocus, 1.1f);
                name.setTextColor(hasFocus ? TEXT : MUTED);
                name.animate().translationY(hasFocus ? dp(ctx, 5) : 0).setDuration(160).start();
            }
        });
        return new Card(holder, tile, name);
    }

    /** Lay cards out in rows of COLUMNS. */
    static View grid(Context c, List<View> cells) {
        LinearLayout grid = new LinearLayout(c);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), 0);
        LinearLayout row = null;
        for (int i = 0; i < cells.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(c, 16);
                grid.addView(row, lp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % COLUMNS != COLUMNS - 1) {
                lp.rightMargin = dp(c, GAP);
            }
            row.addView(cells.get(i), lp);
        }
        return grid;
    }

    /** Zoom a focused item up and give it a shadow; settle it back when focus leaves. */
    static void lift(Context c, View v, boolean up, float scale) {
        float s = up ? scale : 1f;
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(v, View.SCALE_X, s),
                ObjectAnimator.ofFloat(v, View.SCALE_Y, s),
                ObjectAnimator.ofFloat(v, View.TRANSLATION_Z, up ? dp(c, 12) : 0));
        set.setDuration(170);
        set.setInterpolator(new DecelerateInterpolator(1.6f));
        set.start();
    }

    static TextView sectionTitle(Context c, String text) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setAllCaps(true);
        tv.setLetterSpacing(0.14f);
        tv.setTextColor(MUTED);
        tv.setTextSize(12);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setPadding(dp(c, 16), 0, 0, 0);
        return tv;
    }

    static Drawable background(Context c) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {BG_TOP, BG_BOTTOM});
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setColors(new int[] {GLOW, Color.TRANSPARENT});
        glow.setGradientCenter(0.12f, 0.0f);
        glow.setGradientRadius(dp(c, 560));
        return new LayerDrawable(new Drawable[] {base, glow});
    }

    static Drawable iconTile(Context c) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF243044, 0xFF161C26});
        d.setCornerRadius(dp(c, CARD_RADIUS));
        return d;
    }

    static Drawable focusRing(Context c, int radiusDp) {
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(c, 3), Color.WHITE);
        ring.setCornerRadius(dp(c, radiusDp));
        return states(ring, new ColorDrawable(Color.TRANSPARENT));
    }

    static Drawable states(Drawable focused, Drawable idle) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, focused);
        s.addState(new int[] {}, idle);
        return s;
    }

    static GradientDrawable rounded(Context c, int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    static View firstFocusable(View v) {
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

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
