package com.simple.carlauncher;

import java.util.Calendar;
import java.util.GregorianCalendar;

/** Plain-Java checks: run with `sh test.sh`. */
public class DocDatesTest {
    private static int failures;

    private static void eq(String what, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            failures++;
            System.err.println("FAIL " + what + ": expected " + expected + " but got " + actual);
        }
    }

    public static void main(String[] args) {
        Calendar today = new GregorianCalendar(2026, Calendar.NOVEMBER, 1, 15, 30);
        eq("same day", 0, DocDates.daysLeft("2026-11-01", today));
        eq("tomorrow", 1, DocDates.daysLeft("2026-11-02", today));
        eq("in 12 days", 12, DocDates.daysLeft("2026-11-13", today));
        eq("yesterday", -1, DocDates.daysLeft("2026-10-31", today));
        eq("across a year", 61, DocDates.daysLeft("2027-01-01", today));
        eq("empty", DocDates.INVALID, DocDates.daysLeft("", today));
        eq("null", DocDates.INVALID, DocDates.daysLeft(null, today));
        eq("garbage", DocDates.INVALID, DocDates.daysLeft("abc", today));
        eq("iso", "2026-03-07", DocDates.iso(2026, 3, 7));

        eq("describe soon", "Seguro vence en 12 días", DocDates.describe("Seguro", 12));
        eq("describe 1", "Seguro vence mañana", DocDates.describe("Seguro", 1));
        eq("describe 0", "Seguro vence hoy", DocDates.describe("Seguro", 0));
        eq("describe past", "Matrícula vencido hace 3 días", DocDates.describe("Matrícula", -3));
        eq("spoken", "Recuerda: la revisión técnica de tu carro vence en 5 días.", DocDates.spoken("Revisión técnica", 5));

        if (failures > 0) {
            System.err.println(failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("DocDates: all checks passed");
    }
}
