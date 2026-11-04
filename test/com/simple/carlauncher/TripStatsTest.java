package com.simple.carlauncher;

/** Plain-Java checks: run with `sh test.sh`. */
public class TripStatsTest {
    private static int failures;

    private static void eq(String what, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            failures++;
            System.err.println("FAIL " + what + ": expected " + expected + " but got " + actual);
        }
    }

    public static void main(String[] args) {
        // 10 minutes at 36 km/h (10 m/s), one fix per second
        TripStats t = new TripStats();
        long now = 1_000_000;
        for (int i = 0; i < 600; i++) {
            t.onFix(now, 36f, 10f);
            now += 1000;
        }
        eq("distance m", 6000L, Math.round(t.meters()));   // 600 fixes x 10 m
        eq("moving s", 599L, t.movingMs() / 1000);
        eq("avg km/h", 36L, (long) Math.round(t.avgKmh())); // the first fix adds no time
        eq("max", 36f, t.maxKmh());

        // standing still adds nothing
        TripStats s = new TripStats();
        s.onFix(1000, 0f, 0f);
        s.onFix(2000, 2f, 3f);
        eq("still meters", 0.0, s.meters());
        eq("still time", 0L, s.movingMs());

        // a long silence between fixes does not count as driving time
        TripStats g = new TripStats();
        g.onFix(1000, 50f, 0f);
        g.onFix(601_000, 50f, 0f);
        eq("gap time", (long) TripStats.MAX_GAP_MS, g.movingMs());

        // rest reminder: first at 2 h of driving, then every 30 min
        TripStats r = new TripStats();
        int reminders = 0;
        long at = 5_000_000;
        r.onFix(at, 60f, 0f);
        for (int i = 0; i < 3 * 3600; i++) {   // 3 hours, one fix per second
            at += 1000;
            if (r.onFix(at, 60f, 16f)) reminders++;
        }
        eq("reminders in 3 h", 3, reminders); // at 2:00, 2:30 and 3:00

        // a 20 minute stop starts a new session
        long after = at + 20 * 60_000L;
        r.onFix(after, 60f, 0f);
        eq("session after break", 0L, r.sessionMs());

        // a 5 minute stop does not
        TripStats b = new TripStats();
        long x = 1_000_000;
        b.onFix(x, 60f, 0f);
        for (int i = 0; i < 600; i++) { x += 1000; b.onFix(x, 60f, 16f); }
        long before = b.sessionMs();
        b.onFix(x + 5 * 60_000L, 60f, 0f);
        eq("short stop keeps session", true, b.sessionMs() >= before);

        // reset and restore
        t.reset();
        eq("reset meters", 0.0, t.meters());
        t.restore(12_400L, 1_500_000L, 71f);
        eq("restore km text", "12,4 km", TripStats.formatKm(t.meters()));
        eq("restore time text", "0:25", TripStats.formatDuration(t.movingMs()));
        eq("duration 1h05", "1:05", TripStats.formatDuration(65 * 60_000L));
        eq("restore max", 71f, t.maxKmh());

        if (failures > 0) {
            System.err.println(failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("TripStats: all checks passed");
    }
}
