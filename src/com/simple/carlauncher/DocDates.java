package com.simple.carlauncher;

import java.util.Calendar;
import java.util.GregorianCalendar;

/** Expiry dates of the car's papers ("yyyy-MM-dd") and the texts that go with them. Pure Java. */
final class DocDates {

    static final String[] KEYS = {"seguro", "matricula", "revision"};
    static final String[] NAMES = {"Seguro", "Matrícula", "Revisión técnica"};
    static final int INVALID = Integer.MIN_VALUE;

    private DocDates() { }

    static String iso(int year, int month, int day) {
        return String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month, day);
    }

    /** Days from today to the date (negative when it has passed), or INVALID for an empty or bad date. */
    static int daysLeft(String iso, Calendar today) {
        if (iso == null) return INVALID;
        String[] p = iso.trim().split("-");
        if (p.length != 3) return INVALID;
        try {
            Calendar target = new GregorianCalendar(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            Calendar midnight = new GregorianCalendar(today.get(Calendar.YEAR), today.get(Calendar.MONTH),
                    today.get(Calendar.DAY_OF_MONTH));
            return (int) Math.round((target.getTimeInMillis() - midnight.getTimeInMillis()) / 86_400_000.0);
        } catch (NumberFormatException e) {
            return INVALID;
        }
    }

    private static String days(int n) {
        return n == 1 ? "1 día" : n + " días";
    }

    /** "Seguro vence en 12 días", "Seguro vence hoy", "Seguro vencido hace 3 días". */
    static String describe(String name, int daysLeft) {
        if (daysLeft == 0) return name + " vence hoy";
        if (daysLeft == 1) return name + " vence mañana";
        if (daysLeft > 1) return name + " vence en " + days(daysLeft);
        return name + " vencido hace " + days(-daysLeft);
    }

    /** Sentence for the voice greeting. */
    static String spoken(String name, int daysLeft) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (daysLeft < 0) return "Recuerda: la " + n + " de tu carro está vencida desde hace " + days(-daysLeft) + ".";
        if (daysLeft == 0) return "Recuerda: hoy vence la " + n + " de tu carro.";
        if (daysLeft == 1) return "Recuerda: mañana vence la " + n + " de tu carro.";
        return "Recuerda: la " + n + " de tu carro vence en " + days(daysLeft) + ".";
    }
}
