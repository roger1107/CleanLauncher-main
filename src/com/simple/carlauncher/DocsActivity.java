package com.simple.carlauncher;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;

/** Expiry dates of the car's papers (insurance, registration, technical inspection). */
public class DocsActivity extends Activity {

    private final TextView[] status = new TextView[DocDates.KEYS.length];

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("docs", MODE_PRIVATE);
    }

    /** {index, days left} of the paper that expires first, if that is within 30 days (or past); else null. */
    static int[] soonest(Context c) {
        int bestIndex = -1, bestDays = Integer.MAX_VALUE;
        Calendar today = Calendar.getInstance();
        for (int i = 0; i < DocDates.KEYS.length; i++) {
            int left = DocDates.daysLeft(prefs(c).getString(DocDates.KEYS[i], ""), today);
            if (left != DocDates.INVALID && left < bestDays) {
                bestDays = left;
                bestIndex = i;
            }
        }
        return bestIndex >= 0 && bestDays <= 30 ? new int[]{bestIndex, bestDays} : null;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.background(this));
        root.setPadding(dp(24), dp(16), dp(24), dp(16));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(Ui.iconButton(this, R.drawable.ic_back, 48, false, this::finish),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = Ui.text(this, "Documentos del carro", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(16), 0, 0, 0);
        bar.addView(title);
        root.addView(bar);

        TextView hint = Ui.text(this, "Elige la fecha de vencimiento. La pantalla principal avisa 30 días antes y la voz, 7 días antes.",
                14, 0xB3FFFFFF);
        hint.setGravity(Gravity.START);
        hint.setPadding(0, dp(12), 0, dp(8));
        root.addView(hint);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < DocDates.KEYS.length; i++) list.addView(row(i));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private View row(int i) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(14), dp(14), dp(14));
        row.setBackground(Ui.card(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        row.setLayoutParams(lp);

        TextView name = Ui.text(this, DocDates.NAMES[i], 19, Color.WHITE);
        name.setGravity(Gravity.START);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        status[i] = Ui.text(this, "", 15, 0xCCFFFFFF);
        status[i].setGravity(Gravity.START);
        row.addView(status[i], new LinearLayout.LayoutParams(0, -2, 1.4f));

        row.addView(button("Fecha", 0xFF4F7BFF, () -> pickDate(i)));
        LinearLayout.LayoutParams clear = new LinearLayout.LayoutParams(-2, -2);
        clear.leftMargin = dp(8);
        row.addView(button("Quitar", 0x33FFFFFF, () -> {
            prefs(this).edit().remove(DocDates.KEYS[i]).apply();
            refresh();
        }), clear);
        return row;
    }

    private TextView button(String s, int color, Runnable r) {
        TextView t = Ui.text(this, s, 15, Color.WHITE);
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        t.setBackground(bg);
        Ui.pressable(t, 0.95f);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private void pickDate(int i) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) -> {
            prefs(this).edit().putString(DocDates.KEYS[i], DocDates.iso(y, m + 1, d)).apply();
            refresh();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        Calendar today = Calendar.getInstance();
        for (int i = 0; i < DocDates.KEYS.length; i++) {
            String iso = prefs(this).getString(DocDates.KEYS[i], "");
            int left = DocDates.daysLeft(iso, today);
            if (left == DocDates.INVALID) {
                status[i].setText("Sin fecha");
                status[i].setTextColor(0x88FFFFFF);
            } else {
                status[i].setText(iso + " · " + (left < 0 ? "vencido hace " + (-left) + " d" : left == 0 ? "vence hoy" : "faltan " + left + " d"));
                status[i].setTextColor(left <= 7 ? 0xFFFF6B81 : left <= 30 ? 0xFFFFC94D : 0xCCFFFFFF);
            }
        }
    }
}
