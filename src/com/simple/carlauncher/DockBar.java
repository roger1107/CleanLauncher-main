package com.simple.carlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Row of app shortcuts along the bottom of the home screen. Each app shows its real icon and is
 * left out when it is not installed on the head unit, so the row never has dead buttons.
 * Maps and Radio are always there (the launcher opens them itself); "Aplicaciones" ends the row.
 */
final class DockBar {

    private DockBar() { }

    /** At most this many shortcuts before the final "Aplicaciones" button. */
    private static final int MAX_ITEMS = 7;
    private static final String MAPS_PACKAGE = "com.google.android.apps.maps";

    /** {label, package}; a null package marks the entries the launcher handles by itself. */
    private static final String[][] ENTRIES = {
            {"YouTube", "com.google.android.youtube"},
            {"Netflix", "com.netflix.mediaclient"},
            {"Disney+", "com.disney.disneyplus"},
            {"Play Store", "com.android.vending"},
            {"Maps", null},
            {"Radio", null},
            {"Chrome", "com.android.chrome"},
            {"Spotify", "com.spotify.music"},
            {"Waze", "com.waze"},
    };

    static View build(Activity a, Runnable openMaps, Runnable openRadio, Runnable openApps) {
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        PackageManager pm = a.getPackageManager();
        int shown = 0;
        for (String[] e : ENTRIES) {
            if (shown >= MAX_ITEMS) break;
            String label = e[0];
            String pkg = e[1];
            View item;
            if (pkg == null) {
                boolean maps = label.equals("Maps");
                item = item(a, label, maps ? appIcon(pm, MAPS_PACKAGE) : null,
                        maps ? R.drawable.ic_nav : R.drawable.ic_radio,
                        maps ? 0xFF4F9BFF : 0xFFFF9F5A, maps ? openMaps : openRadio);
            } else {
                Intent launch = pm.getLaunchIntentForPackage(pkg);
                if (launch == null) continue;
                item = item(a, label, appIcon(pm, pkg), R.drawable.ic_apps, Color.WHITE,
                        () -> start(a, launch));
            }
            bar.addView(item, slot(a, 1f));
            shown++;
        }
        bar.addView(item(a, "Aplicaciones", null, R.drawable.ic_apps, Color.WHITE, openApps), slot(a, 1.4f));
        return bar;
    }

    private static LinearLayout.LayoutParams slot(Activity a, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, weight);
        lp.setMargins(Ui.dp(a, 4), 0, Ui.dp(a, 4), 0);
        return lp;
    }

    private static View item(Activity a, String label, Drawable icon, int fallbackRes, int tint, Runnable action) {
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.card(a));
        ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (icon != null) {
            iv.setImageDrawable(icon);
        } else {
            iv.setImageResource(fallbackRes);
            iv.setColorFilter(tint);
        }
        t.addView(iv, new LinearLayout.LayoutParams(Ui.dp(a, 34), Ui.dp(a, 34)));
        TextView tv = Ui.text(a, label, 12, Color.WHITE);
        tv.setPadding(Ui.dp(a, 4), Ui.dp(a, 3), Ui.dp(a, 4), 0);
        t.addView(tv, new LinearLayout.LayoutParams(-2, -2));
        Ui.pressable(t, 0.95f);
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private static Drawable appIcon(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static void start(Activity a, Intent i) {
        try {
            a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            // the app was removed or disabled after the row was built; nothing to open
        }
    }
}
