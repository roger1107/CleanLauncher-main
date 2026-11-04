package com.simple.carlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.util.Calendar;

/**
 * Per-user settings so the launcher can be shared: driver name, own car photo and which
 * reminders to speak. Also detects whether the head unit is an NWD one (factory radio,
 * Bluetooth and sound services available) so NWD-only features can be hidden elsewhere.
 */
final class Profile {

    private Profile() { }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("profile", Context.MODE_PRIVATE);
    }

    /** Name used by the personal build (the one with the bundled car photo) until Setup changes it. */
    private static final String OWNER_NAME = "Roger";

    /** The recorded greeting clips (res/raw/v_greet_*) say this name. */
    static final String RECORDED_VOICE_NAME = "Jhon";

    static String name(Context c) {
        String n = prefs(c).getString("name", null);
        if (n == null) return bundledCar(c, false) != 0 ? OWNER_NAME : "";
        return n.trim();
    }

    /**
     * Recorded clips only while the name is the one they were recorded with; for any other name the
     * device's TTS says the greeting, so it never greets somebody else by the wrong name.
     */
    static boolean useRecordedVoice(Context c) {
        return c.getResources().getIdentifier("v_careful", "raw", c.getPackageName()) != 0
                && name(c).equalsIgnoreCase(RECORDED_VOICE_NAME);
    }

    static boolean voiceEnabled(Context c) {
        return prefs(c).getBoolean("voice", true);
    }

    static boolean picoPlacaEnabled(Context c) {
        return prefs(c).getBoolean("picoplaca", false);
    }

    static final int NO_DIGIT = -1;

    /** Big speed panel while driving (on by default). */
    static boolean drivingModeEnabled(Context c) {
        return prefs(c).getBoolean("driving_mode", true);
    }

    static final int FAVORITES = 3;

    /** {name, number} of favourite contact i (0-2); either may be empty. */
    static String[] favorite(Context c, int i) {
        return new String[]{prefs(c).getString("fav" + i + "_name", "").trim(),
                prefs(c).getString("fav" + i + "_number", "").trim()};
    }

    /** Last digit of the plate (0-9) chosen in the setup screen, or NO_DIGIT. */
    static int plateDigit(Context c) {
        return prefs(c).getInt("plate_digit", NO_DIGIT);
    }

    /**
     * Weekday (Calendar.MONDAY..FRIDAY) on which the pico y placa reminder applies, or -1 when it is
     * off. Installs from before the plate digit existed always meant Wednesday, so keep that until
     * the user picks a digit.
     */
    static int picoPlacaDay(Context c) {
        if (!picoPlacaEnabled(c)) return -1;
        int digit = plateDigit(c);
        return digit == NO_DIGIT ? Calendar.WEDNESDAY : PicoPlaca.dayFor(digit);
    }

    static boolean isSetupDone(Context c) {
        return prefs(c).getBoolean("setup_done", false);
    }

    /** "Hola, Ana" or just "Hola". */
    static String hello(Context c) {
        String n = name(c);
        return n.isEmpty() ? "Hola" : "Hola, " + n;
    }

    // ---------- Car picture ----------

    static File carPhotoFile(Context c) {
        return new File(c.getFilesDir(), "car.png");
    }

    /** The user's own car photo, if they picked one. */
    static Bitmap carPhoto(Context c) {
        File f = carPhotoFile(c);
        return f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
    }

    /** Car pictures bundled only in the owner's personal build (0 when absent). */
    static int bundledCar(Context c, boolean lights) {
        return c.getResources().getIdentifier(lights ? "duster_lights" : "duster", "drawable", c.getPackageName());
    }

    // ---------- Device ----------

    private static Boolean nwd;

    /** NWD head unit: factory radio, Bluetooth module and sound services are installed. */
    static boolean isNwd(Context c) {
        if (nwd == null) {
            PackageManager pm = c.getPackageManager();
            nwd = installed(pm, "com.nwd.radio.service") && installed(pm, "com.bt.bc03")
                    && installed(pm, "com.nwd.setting.service");
        }
        return nwd;
    }

    private static boolean installed(PackageManager pm, String pkg) {
        try {
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
