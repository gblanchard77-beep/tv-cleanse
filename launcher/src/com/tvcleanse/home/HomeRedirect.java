package com.tvcleanse.home;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional helper for Google TV, where the TV's own home screen cannot be replaced
 * without switching it off. Nothing is switched off: when Google TV's main home page
 * comes to the front, this brings the TV Cleanse home screen up over it.
 *
 * It only receives "window changed" events from Google TV's home app (see
 * res/xml/home_redirect.xml). It cannot read the screen or see key presses.
 *
 * Ways out, so a broken home screen can never trap anyone:
 *  - Hold Back on the TV Cleanse home screen: Google TV's home opens and stays
 *    until the TV restarts or you open TV Cleanse Home from the apps row.
 *  - If TV Cleanse Home is launched 3 times in a row without ever appearing,
 *    the helper stops until it next appears.
 *  - Turn off "TV Cleanse Home button" in Settings > Accessibility, or run home-remove.
 */
public class HomeRedirect extends AccessibilityService {

    static final String GOOGLE_TV_PKG = "com.google.android.apps.tv.launcherx";
    static final String GOOGLE_TV_HOME = GOOGLE_TV_PKG + ".home.HomeActivity";

    private static final String PREFS = "home_redirect";
    private static final int MAX_MISSES = 3;
    private static final long DEBOUNCE_MS = 2000;

    private long lastLaunch = -DEBOUNCE_MS;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        if (!GOOGLE_TV_PKG.equals(String.valueOf(event.getPackageName()))
                || !GOOGLE_TV_HOME.equals(String.valueOf(event.getClassName()))) {
            return;
        }
        tryOpenHome();
    }

    /**
     * Just after the TV starts, Google TV's home can open before this helper is running,
     * so it would never see it. If the helper starts in the first two minutes after boot,
     * open TV Cleanse once.
     */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (SystemClock.uptimeMillis() < 2 * 60 * 1000) {
            tryOpenHome();
        }
    }

    private void tryOpenHome() {
        // Leave the TV alone while it is still being set up.
        if (Settings.Secure.getInt(getContentResolver(), "user_setup_complete", 1) != 1) {
            return;
        }
        SharedPreferences p = prefs(this);
        if (pausedThisBoot(this, p)) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunch < DEBOUNCE_MS) {
            return; // Google TV can send several events for one Home press.
        }
        int misses = p.getInt("misses", 0);
        if (p.contains("sent")) {
            misses++; // the last launch never reached the screen
        }
        if (misses >= MAX_MISSES) {
            p.edit().putInt("misses", misses).remove("sent").commit();
            return;
        }
        // Save first, in case the launch takes this process down.
        if (!p.edit().putLong("sent", now).putInt("misses", misses).commit()) {
            return;
        }
        lastLaunch = now;
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
            // Counted as a miss next time.
        }
    }

    @Override
    public void onInterrupt() {
    }

    /** Called when the TV Cleanse home screen is on screen: all is well, clear the counters. */
    static void homeShown(Context c) {
        prefs(c).edit().remove("sent").remove("misses").remove("paused_boot").commit();
    }

    /** Open Google TV's own home and stop redirecting until the TV restarts. */
    static boolean openGoogleTv(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.edit().putString("paused_boot", bootId(c)).commit()) {
            return false;
        }
        try {
            c.startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setComponent(new ComponentName(GOOGLE_TV_PKG, GOOGLE_TV_HOME))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            p.edit().remove("paused_boot").commit();
            return false; // not a Google TV
        }
    }

    private static boolean pausedThisBoot(Context c, SharedPreferences p) {
        String saved = p.getString("paused_boot", null);
        return saved != null && saved.equals(bootId(c));
    }

    /** Something that changes every time the TV restarts. */
    private static String bootId(Context c) {
        int count = Settings.Global.getInt(c.getContentResolver(), "boot_count", -1);
        if (count >= 0) {
            return "count:" + count;
        }
        // Older Android: the boot time, rounded to the minute.
        long boot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        return "time:" + (boot / 60000);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
