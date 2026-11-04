package com.simple.carlauncher;

import java.util.Locale;

/**
 * Trip counters (distance, moving time, top speed) and the "time to rest" reminder, fed with one
 * GPS fix at a time. Pure Java so it can be unit-tested with test.sh.
 */
final class TripStats {

    static final float MOVING_KMH = 5f;
    static final long MAX_GAP_MS = 5000;               // a longer silence between fixes adds no time
    static final long BREAK_RESETS_MS = 15 * 60_000L;  // a stop this long starts a new driving session
    static final long REST_FIRST_MS = 2 * 3_600_000L;  // first reminder after 2 h of driving
    static final long REST_AGAIN_MS = 30 * 60_000L;    // and every 30 min after that

    private double meters;
    private long movingMs;
    private float maxKmh;

    private long lastFixMs;
    private long lastMovingMs;
    private long sessionMs;
    private long nextRestMs = REST_FIRST_MS;

    /** Feeds one fix. {@code deltaMeters} is the distance since the previous good fix. Returns true when a rest is due. */
    boolean onFix(long nowMs, float speedKmh, float deltaMeters) {
        long dt = lastFixMs == 0 ? 0 : Math.min(Math.max(0, nowMs - lastFixMs), MAX_GAP_MS);
        lastFixMs = nowMs;
        if (speedKmh < MOVING_KMH) return false;

        if (lastMovingMs != 0 && nowMs - lastMovingMs > BREAK_RESETS_MS) { // came back after a real break
            sessionMs = 0;
            nextRestMs = REST_FIRST_MS;
            dt = 0; // the break itself is not driving time
        }
        lastMovingMs = nowMs;
        meters += deltaMeters;
        movingMs += dt;
        sessionMs += dt;
        if (speedKmh > maxKmh) maxKmh = speedKmh;
        if (sessionMs >= nextRestMs) {
            nextRestMs += REST_AGAIN_MS;
            return true;
        }
        return false;
    }

    void reset() {
        meters = 0;
        movingMs = 0;
        maxKmh = 0;
    }

    void restore(long meters, long movingMs, float maxKmh) {
        this.meters = meters;
        this.movingMs = movingMs;
        this.maxKmh = maxKmh;
    }

    double meters() { return meters; }
    long movingMs() { return movingMs; }
    float maxKmh() { return maxKmh; }
    long sessionMs() { return sessionMs; }

    /** Average speed while moving, km/h. */
    float avgKmh() {
        if (movingMs <= 0) return 0;
        return (float) (meters / 1000.0 / (movingMs / 3_600_000.0));
    }

    // ---------- Text ----------

    /** 12.4 -> "12,4 km". */
    static String formatKm(double meters) {
        return String.format(Locale.US, "%.1f", meters / 1000.0).replace('.', ',') + " km";
    }

    /** "H:MM". */
    static String formatDuration(long ms) {
        long minutes = ms / 60_000L;
        return String.format(Locale.US, "%d:%02d", minutes / 60, minutes % 60);
    }
}
