package com.simple.carlauncher;

import android.content.Context;
import android.provider.Settings;

/**
 * "Automatic equalizer": picks a 10-band curve for what is playing and turns loudness on at
 * low volume. Radio gets a curve that brings voices forward; Bluetooth/USB get a music curve.
 * Band values are 0..6 with 3 = flat.
 */
final class EqAuto {

    static final int[] VOICE = {2, 2, 3, 3, 4, 5, 5, 4, 3, 3};
    static final int[] MUSIC = {5, 5, 4, 3, 3, 3, 4, 4, 5, 5};
    private static final int LOUDNESS_BELOW_VOLUME = 10;

    private static String lastCurve;
    private static int lastLoudness = -1;

    private EqAuto() { }

    static boolean isEnabled(Context c) {
        return c.getSharedPreferences("eq", Context.MODE_PRIVATE).getBoolean("auto", false);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("eq", Context.MODE_PRIVATE).edit().putBoolean("auto", on).apply();
        lastCurve = null;
        lastLoudness = -1;
    }

    /** Source changed (or auto just turned on): apply the matching curve once. */
    static void apply(Context c, SoundClient s) {
        if (!isEnabled(c) || !s.isConnected()) return;
        String curve = Ui.radioIsSource(c) ? "voice" : "music";
        if (!curve.equals(lastCurve)) {
            s.applyCurve("voice".equals(curve) ? VOICE : MUSIC);
            lastCurve = curve;
        }
        applyLoudness(c, s);
    }

    /** Volume changed: loudness on when quiet, off when loud. */
    static void applyLoudness(Context c, SoundClient s) {
        if (!isEnabled(c) || !s.isConnected()) return;
        int vol = Settings.System.getInt(c.getContentResolver(), "mcu_system_volume", 15);
        int want = vol <= LOUDNESS_BELOW_VOLUME ? 1 : 0;
        if (want != lastLoudness) {
            s.setAudio(SoundClient.LOUDNESS, want);
            lastLoudness = want;
        }
    }

    static String currentLabel(Context c) {
        return Ui.radioIsSource(c) ? "Voz clara (radio)" : "Música (Bluetooth / USB)";
    }
}
