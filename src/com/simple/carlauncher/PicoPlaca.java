package com.simple.carlauncher;

import java.util.Calendar;

/**
 * Quito "pico y placa": Monday to Friday, by the last digit of the plate
 * (1-2 Mon, 3-4 Tue, 5-6 Wed, 7-8 Thu, 9-0 Fri). Restricted 06:00-09:30 and 16:00-20:00.
 * Pure Java (no Android classes) so it can be unit-tested with test.sh.
 */
final class PicoPlaca {

    private PicoPlaca() { }

    /** Calendar.MONDAY..FRIDAY on which a plate ending in {@code digit} (0-9) is restricted, else -1. */
    static int dayFor(int digit) {
        switch (digit) {
            case 1: case 2: return Calendar.MONDAY;
            case 3: case 4: return Calendar.TUESDAY;
            case 5: case 6: return Calendar.WEDNESDAY;
            case 7: case 8: return Calendar.THURSDAY;
            case 9: case 0: return Calendar.FRIDAY;
            default: return -1;
        }
    }

    /** Spanish name of a Calendar.MONDAY..FRIDAY constant ("" for anything else). */
    static String dayName(int dayOfWeek) {
        switch (dayOfWeek) {
            case Calendar.MONDAY: return "lunes";
            case Calendar.TUESDAY: return "martes";
            case Calendar.WEDNESDAY: return "miércoles";
            case Calendar.THURSDAY: return "jueves";
            case Calendar.FRIDAY: return "viernes";
            default: return "";
        }
    }
}
