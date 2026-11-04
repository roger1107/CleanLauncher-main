package com.simple.carlauncher;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

/**
 * GPS odometer for the oil-change reminder (the car's CAN box does not report mileage).
 *
 * The user sets the dashboard reading once; from then on distance between good GPS fixes
 * is added. Fixes while parked or with poor accuracy are ignored so drift does not count.
 */
public class Odometer extends Service implements LocationListener {

    private static final String TAG = "Odometer";
    static final int DEFAULT_INTERVAL_KM = 5000;
    static final int WARN_KM = 500;

    /** Last GPS speed in km/h and when it arrived (SystemClock.elapsedRealtime); 0 = no fix yet. */
    static volatile float speedKmh;
    static volatile long speedAtMs;

    /** Last usable GPS fix for the map card: position, bearing (NaN if unknown), when it arrived. */
    static volatile double lat, lon;
    static volatile float bearing = Float.NaN;
    static volatile long fixAtMs;

    private LocationManager lm;
    private Location last;
    private float pendingMeters;
    private boolean tracking;
    private int overCount;
    private long lastAlertMs;

    // ---------- Trip (shared with the launcher; both run on the main thread) ----------

    private static TripStats trip;

    static TripStats trip(Context c) {
        if (trip == null) {
            trip = new TripStats();
            SharedPreferences p = prefs(c);
            trip.restore(p.getLong("trip_m", 0), p.getLong("trip_ms", 0), p.getFloat("trip_max", 0));
        }
        return trip;
    }

    private static void saveTrip(Context c) {
        TripStats t = trip(c);
        prefs(c).edit().putLong("trip_m", Math.round(t.meters())).putLong("trip_ms", t.movingMs())
                .putFloat("trip_max", t.maxKmh()).apply();
    }

    static void resetTrip(Context c) {
        trip(c).reset();
        saveTrip(c);
    }

    // ---------- Speed alert limit ----------

    static final int[] LIMITS = {0, 60, 80, 90, 100, 120};

    static int speedLimit(Context c) {
        return prefs(c).getInt("speed_limit", 0);
    }

    /** Next limit in the cycle off -> 60 -> 80 -> 90 -> 100 -> 120 -> off; returns it. */
    static int nextSpeedLimit(Context c) {
        int cur = speedLimit(c);
        int next = LIMITS[0];
        for (int i = 0; i < LIMITS.length; i++) {
            if (LIMITS[i] == cur) next = LIMITS[(i + 1) % LIMITS.length];
        }
        prefs(c).edit().putInt("speed_limit", next).apply();
        return next;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        startGps();
    }

    /** Safe to call again: the permission may be granted after the service is already running. */
    private void startGps() {
        if (tracking
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this); // 1 s: also feeds the speedometer
            tracking = true;
        } catch (Exception e) {
            Log.w(TAG, "no GPS", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startGps(); // the launcher/setup start the service again once the permission is granted
        return START_STICKY;
    }

    @Override
    public void onLocationChanged(Location l) {
        if (l.hasSpeed()) {
            speedKmh = l.getSpeed() * 3.6f;
            speedAtMs = SystemClock.elapsedRealtime();
            checkSpeedAlert(speedKmh);
        }
        if (l.hasAccuracy() && l.getAccuracy() <= 60) {
            lat = l.getLatitude();
            lon = l.getLongitude();
            bearing = l.hasBearing() ? l.getBearing() : Float.NaN;
            fixAtMs = SystemClock.elapsedRealtime();
        }
        if (!l.hasAccuracy() || l.getAccuracy() > 25) return;
        float counted = 0;
        float speed = l.hasSpeed() ? l.getSpeed() * 3.6f : 0f;
        if (last != null) {
            float d = last.distanceTo(l);
            float secs = Math.max(1, (l.getTime() - last.getTime()) / 1000f);
            float kmh = d / secs * 3.6f;
            if (!l.hasSpeed()) speed = kmh;
            // Count only real driving; drop parked drift and impossible jumps.
            if (speed > 4 && kmh < 200) {
                pendingMeters += d;
                counted = d;
            }
        }
        last = l;
        if (trip(this).onFix(SystemClock.elapsedRealtime(), speed, counted)) {
            long minutes = trip(this).sessionMs() / 60_000L;
            GreeterService.say(this, Speech.restReminder((int) minutes));
        }
        if (pendingMeters >= 100) { // persist every 100 m
            SharedPreferences p = prefs(this);
            p.edit().putFloat("gps_km", p.getFloat("gps_km", 0) + pendingMeters / 1000f).apply();
            pendingMeters = 0;
            saveTrip(this);
        }
    }

    /** Voice warning when the user's own limit is exceeded for a few seconds (at most once a minute). */
    private void checkSpeedAlert(float kmh) {
        int limit = speedLimit(this);
        if (limit <= 0) {
            overCount = 0;
            return;
        }
        overCount = kmh > limit + 3 ? overCount + 1 : 0;
        long now = SystemClock.elapsedRealtime();
        if (overCount >= 3 && now - lastAlertMs > 60_000) {
            lastAlertMs = now;
            GreeterService.say(this, Speech.overLimit(limit));
        }
    }

    public void onStatusChanged(String p, int s, Bundle b) { }
    public void onProviderEnabled(String p) { }
    public void onProviderDisabled(String p) { }

    @Override
    public void onDestroy() {
        super.onDestroy();
        speedAtMs = 0;
        fixAtMs = 0;
        saveTrip(this);
        lm.removeUpdates(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- Shared state ----------

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("odometer", Context.MODE_PRIVATE);
    }

    static boolean isConfigured(Context c) {
        return prefs(c).contains("base_km");
    }

    /** Estimated dashboard reading: the value the user entered plus GPS distance since. */
    static int currentKm(Context c) {
        SharedPreferences p = prefs(c);
        return Math.round(p.getInt("base_km", 0) + p.getFloat("gps_km", 0));
    }

    static void setCurrentKm(Context c, int km) {
        prefs(c).edit().putInt("base_km", km).putFloat("gps_km", 0).apply();
    }

    static int lastChangeKm(Context c) {
        return prefs(c).getInt("last_change_km", currentKm(c));
    }

    static void setLastChangeKm(Context c, int km) {
        prefs(c).edit().putInt("last_change_km", km).apply();
    }

    static int intervalKm(Context c) {
        return prefs(c).getInt("interval_km", DEFAULT_INTERVAL_KM);
    }

    static void setIntervalKm(Context c, int km) {
        prefs(c).edit().putInt("interval_km", km).apply();
    }

    /** Km left until the next oil change (negative when overdue). */
    static int remainingKm(Context c) {
        return lastChangeKm(c) + intervalKm(c) - currentKm(c);
    }
}
