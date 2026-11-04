package com.simple.carlauncher;

import android.content.Context;

import java.util.List;

/**
 * Turns the greeting clip list into one sentence for text-to-speech (shared builds), so the
 * same reminders are spoken with the user's own name.
 */
final class Speech {

    private Speech() { }

    static String text(Context c, List<String> clips) {
        String name = Profile.name(c);
        String who = name.isEmpty() ? "" : ", " + name;
        StringBuilder sb = new StringBuilder();
        for (String clip : clips) {
            String part = phrase(clip, who);
            if (part != null) sb.append(part).append(' ');
        }
        return sb.toString().trim();
    }

    private static String phrase(String clip, String who) {
        switch (clip) {
            case "v_greet_morning": return "¡Buenos días" + who + "! Bienvenido.";
            case "v_greet_afternoon": return "¡Buenas tardes" + who + "! Bienvenido.";
            case "v_greet_night": return "¡Buenas noches" + who + "! Bienvenido.";
            case "v_greet_monday": return "¡Hola" + who + "! Arrancó la semana: hoy es lunes.";
            case "v_maintenance": return "Revisa el aceite, el agua del radiador y el líquido de frenos.";
            case "v_seatbelt": return "Ponte el cinturón de seguridad.";
            case "v_careful": return "Maneja con cuidado. ¡Buen viaje!";
            case "v_oil_soon": return "Se acerca el cambio de aceite: quedan menos de quinientos kilómetros.";
            case "v_oil_due": return "Ya toca el cambio de aceite.";
            default: break;
        }
        if (clip.startsWith("tts:")) return clip.substring(4); // sentence with no recorded clip
        if (clip.startsWith("v_cond_")) return condition(clip.substring(7));
        if (clip.startsWith("v_temp_")) {
            String t = clip.substring(7).replace("m", "menos ");
            return "y hace " + t + " grados.";
        }
        return null;
    }

    /** "Llevas 2 horas y media manejando..." for minutes of continuous driving. */
    static String restReminder(int minutes) {
        int h = minutes / 60;
        String time = (h == 1 ? "1 hora" : h + " horas") + (minutes % 60 >= 30 ? " y media" : "");
        return "Llevas " + time + " manejando. Es buen momento para parar y descansar un rato.";
    }

    static String overLimit(int limit) {
        return "Estás por encima del límite de " + limit + " kilómetros por hora.";
    }

    /** "Ojo: hoy llueve a las 5 de la tarde." for a "HH:mm" time. */
    static String rainSoon(String hhmm) {
        int h = Integer.parseInt(hhmm.substring(0, 2));
        int m = Integer.parseInt(hhmm.substring(3, 5));
        int h12 = h % 12 == 0 ? 12 : h % 12;
        String period = h < 12 ? "de la mañana" : h < 19 ? "de la tarde" : "de la noche";
        String minutes = m == 0 ? "" : m == 30 ? " y media" : " y " + m;
        return "Ojo: hoy llueve a las " + h12 + minutes + " " + period + ".";
    }

    /** "Hoy es jueves: recuerda la restricción vehicular." for a java.util.Calendar weekday. */
    static String restriccion(int dayOfWeek) {
        return "Hoy es " + PicoPlaca.dayName(dayOfWeek) + ": recuerda la restricción vehicular.";
    }

    private static String condition(String c) {
        switch (c) {
            case "clear": return "Afuera está despejado,";
            case "mostly": return "Afuera está casi despejado,";
            case "partly": return "Afuera está parcialmente nublado,";
            case "cloudy": return "Afuera está nublado,";
            case "fog": return "Afuera hay neblina,";
            case "drizzle": return "Afuera está lloviznando,";
            case "rain": return "Afuera está lloviendo,";
            case "snow": return "Afuera está nevando,";
            case "showers": return "Afuera hay aguaceros,";
            case "storm": return "Afuera hay tormenta,";
            default: return "";
        }
    }
}
