package com.simple.carlauncher;

/** Plain-Java checks: run with `sh test.sh`. */
public class DayPartsTest {
    private static int failures;

    private static void eq(String what, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            failures++;
            System.err.println("FAIL " + what + ": expected " + expected + " but got " + actual);
        }
    }

    public static void main(String[] args) {
        eq("00:00", DayParts.NIGHT, DayParts.part(0, 0));
        eq("05:59", DayParts.NIGHT, DayParts.part(5, 59));
        eq("06:00", DayParts.DAWN, DayParts.part(6, 0));
        eq("07:59", DayParts.DAWN, DayParts.part(7, 59));
        eq("08:00", DayParts.DAY, DayParts.part(8, 0));
        eq("16:29", DayParts.DAY, DayParts.part(16, 29));
        eq("16:30", DayParts.DUSK, DayParts.part(16, 30));
        eq("18:29", DayParts.DUSK, DayParts.part(18, 29));
        eq("18:30", DayParts.NIGHT, DayParts.part(18, 30));
        eq("23:59", DayParts.NIGHT, DayParts.part(23, 59));
        eq("isNight 18:29", false, DayParts.isNight(18, 29));
        eq("isNight 18:30", true, DayParts.isNight(18, 30));
        eq("isNight 06:00", false, DayParts.isNight(6, 0));
        if (failures > 0) {
            System.err.println(failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("DayParts: all checks passed");
    }
}
