package com.simple.carlauncher;

/**
 * Parts of the day used for the dark theme, the car's headlights and the background tint.
 * Night starts at 18:30 (the stereo has no ILL wire to tell when the lights go on) and ends at 6:00.
 * Pure Java so it can be unit-tested with test.sh.
 */
final class DayParts {

    static final int NIGHT = 0, DAWN = 1, DAY = 2, DUSK = 3;

    static final int DAWN_FROM = 6 * 60;
    static final int DAY_FROM = 8 * 60;
    static final int DUSK_FROM = 16 * 60 + 30;
    static final int NIGHT_FROM = 18 * 60 + 30;

    private DayParts() { }

    static int part(int hour, int minute) {
        int m = hour * 60 + minute;
        if (m >= NIGHT_FROM || m < DAWN_FROM) return NIGHT;
        if (m < DAY_FROM) return DAWN;
        if (m < DUSK_FROM) return DAY;
        return DUSK;
    }

    static boolean isNight(int hour, int minute) {
        return part(hour, minute) == NIGHT;
    }
}
