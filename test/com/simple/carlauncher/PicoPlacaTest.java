package com.simple.carlauncher;

import java.util.Calendar;

/** Plain-Java checks (no JUnit, no Android SDK): run with `sh test.sh`. */
public class PicoPlacaTest {

    private static int failures;

    private static void eq(String what, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            failures++;
            System.err.println("FAIL " + what + ": expected " + expected + " but got " + actual);
        }
    }

    public static void main(String[] args) {
        int[][] schedule = {
                {1, Calendar.MONDAY}, {2, Calendar.MONDAY},
                {3, Calendar.TUESDAY}, {4, Calendar.TUESDAY},
                {5, Calendar.WEDNESDAY}, {6, Calendar.WEDNESDAY},
                {7, Calendar.THURSDAY}, {8, Calendar.THURSDAY},
                {9, Calendar.FRIDAY}, {0, Calendar.FRIDAY},
        };
        for (int[] s : schedule) eq("digit " + s[0], s[1], PicoPlaca.dayFor(s[0]));
        eq("digit -1", -1, PicoPlaca.dayFor(-1));
        eq("digit 10", -1, PicoPlaca.dayFor(10));

        // Every weekday restricts exactly two digits.
        for (int day = Calendar.MONDAY; day <= Calendar.FRIDAY; day++) {
            int n = 0;
            for (int d = 0; d <= 9; d++) if (PicoPlaca.dayFor(d) == day) n++;
            eq("digits on " + PicoPlaca.dayName(day), 2, n);
        }

        eq("monday", "lunes", PicoPlaca.dayName(Calendar.MONDAY));
        eq("wednesday", "miércoles", PicoPlaca.dayName(Calendar.WEDNESDAY));
        eq("sunday", "", PicoPlaca.dayName(Calendar.SUNDAY));

        if (failures > 0) {
            System.err.println(failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("PicoPlaca: all checks passed");
    }
}
