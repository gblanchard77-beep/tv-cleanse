package com.tvcleanse.home;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The TV's apps, and which ones you've hidden from the home screen. */
final class Apps {

    private static final String PREFS = "home_apps";
    private static final String HIDDEN = "hidden";
    private static final String ORDER = "order";

    private Apps() {
    }

    static final class App {
        final String key; // package/activity
        final CharSequence label;
        final Drawable banner;
        final Drawable icon;
        final Intent launch;

        App(String key, CharSequence label, Drawable banner, Drawable icon, Intent launch) {
            this.key = key;
            this.label = label;
            this.banner = banner;
            this.icon = icon;
            this.launch = launch;
        }
    }

    /** Every app that can be opened from a TV home screen, sorted by name. */
    static List<App> all(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
        if (found.isEmpty()) {
            query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            found = pm.queryIntentActivities(query, 0);
        }
        List<App> apps = new ArrayList<App>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || pkg.endsWith(".settings")) {
                continue;
            }
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.setClassName(pkg, ri.activityInfo.name);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            Drawable banner = null;
            try {
                banner = ri.activityInfo.loadBanner(pm);
            } catch (Exception ignored) {
                // No banner: the card shows the icon instead.
            }
            apps.add(new App(pkg + "/" + ri.activityInfo.name, ri.loadLabel(pm), banner,
                    ri.loadIcon(pm), launch));
        }
        Collections.sort(apps, new Comparator<App>() {
            @Override
            public int compare(App a, App b) {
                return a.label.toString().compareToIgnoreCase(b.label.toString());
            }
        });
        return apps;
    }

    /** Every app, in the order you arranged them. Apps you haven't placed go last, by name. */
    static List<App> ordered(Context c) {
        List<App> apps = all(c);
        final List<String> order = order(c);
        Collections.sort(apps, new Comparator<App>() { // stable: unplaced apps stay A-Z
            @Override
            public int compare(App a, App b) {
                return rank(order, a.key) - rank(order, b.key);
            }
        });
        return apps;
    }

    private static int rank(List<String> order, String key) {
        int i = order.indexOf(key);
        return i < 0 ? Integer.MAX_VALUE / 2 : i;
    }

    static List<String> order(Context c) {
        String saved = prefs(c).getString(ORDER, "");
        List<String> out = new ArrayList<String>();
        for (String k : saved.split("\n")) {
            if (k.length() > 0) {
                out.add(k);
            }
        }
        return out;
    }

    /** Save the order of the apps on screen. Hidden apps keep their old places after them. */
    static void saveOrder(Context c, List<App> onScreen) {
        StringBuilder sb = new StringBuilder();
        Set<String> seen = new HashSet<String>();
        for (App a : onScreen) {
            sb.append(a.key).append('\n');
            seen.add(a.key);
        }
        for (String k : order(c)) {
            if (!seen.contains(k)) {
                sb.append(k).append('\n');
            }
        }
        prefs(c).edit().putString(ORDER, sb.toString()).apply();
    }

    /** The apps shown on the home screen: everything you haven't hidden, in your order. */
    static List<App> shown(Context c) {
        Set<String> hidden = hidden(c);
        List<App> out = new ArrayList<App>();
        for (App a : ordered(c)) {
            if (!hidden.contains(a.key)) {
                out.add(a);
            }
        }
        return out;
    }

    static Set<String> hidden(Context c) {
        return new HashSet<String>(prefs(c).getStringSet(HIDDEN, new HashSet<String>()));
    }

    static void setHidden(Context c, String key, boolean hide) {
        Set<String> hidden = hidden(c);
        if (hide) {
            hidden.add(key);
        } else {
            hidden.remove(key);
        }
        prefs(c).edit().putStringSet(HIDDEN, hidden).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
