package com.simple.carlauncher;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

/** Current weather from Open-Meteo (free, no API key), located by GPS or, failing that, by IP. */
public class Weather {

    public interface Listener {
        void onWeather(Weather w);
    }

    public final int temp, feelsLike, max, min, humidity, wind, code;
    public final boolean isDay;
    public final String city;
    /** "17:00" when rain is likely within the next hours (empty when not), its chance in %, and when this was fetched. */
    public final String rainAt;
    public final int rainProb;
    public final long fetchedAt;

    private Weather(JSONObject j) {
        temp = j.optInt("temp");
        feelsLike = j.optInt("feels");
        max = j.optInt("max");
        min = j.optInt("min");
        humidity = j.optInt("hum");
        wind = j.optInt("wind");
        code = j.optInt("code");
        isDay = j.optBoolean("day", true);
        city = j.optString("city", "");
        rainAt = j.optString("rainAt", "");
        rainProb = j.optInt("rainProb");
        fetchedAt = j.optLong("at", 0);
    }

    /** "Lluvia a las 17:00 (70%)", or "" when no rain is expected (or the forecast is old). */
    public String rainNote() {
        if (rainAt.isEmpty() || System.currentTimeMillis() - fetchedAt > 3 * 3600 * 1000L) return "";
        return "Lluvia a las " + rainAt + " (" + rainProb + "%)";
    }

    public String description() {
        if (code == 0) return isDay ? "Despejado" : "Noche despejada";
        if (code == 1) return "Mayormente despejado";
        if (code == 2) return "Parcialmente nublado";
        if (code == 3) return "Nublado";
        if (code == 45 || code == 48) return "Niebla";
        if (code >= 51 && code <= 57) return "Llovizna";
        if (code >= 61 && code <= 67) return "Lluvia";
        if (code >= 71 && code <= 77) return "Nieve";
        if (code >= 80 && code <= 82) return "Chubascos";
        if (code == 85 || code == 86) return "Nevadas";
        if (code >= 95) return "Tormenta";
        return "—";
    }

    public int icon() {
        if (code <= 1) return isDay ? R.drawable.ic_w_sun : R.drawable.ic_w_moon;
        if (code <= 3) return R.drawable.ic_w_cloud;
        if (code == 45 || code == 48) return R.drawable.ic_w_fog;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.ic_w_snow;
        if (code >= 95) return R.drawable.ic_w_storm;
        return R.drawable.ic_w_rain;
    }

    /** Last saved weather, so the card is filled instantly on start. */
    public static Weather cached(Context c) {
        try {
            String s = prefs(c).getString("json", null);
            return s == null ? null : new Weather(new JSONObject(s));
        } catch (Exception e) {
            return null;
        }
    }

    public static void fetch(Context ctx, Listener l) {
        Context c = ctx.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                double lat, lon;
                String city = "";
                Location loc = lastLocation(c);
                if (loc != null) {
                    lat = loc.getLatitude();
                    lon = loc.getLongitude();
                    city = cityName(c, lat, lon);
                } else {
                    JSONObject ip = new JSONObject(get("https://ipapi.co/json/"));
                    lat = ip.getDouble("latitude");
                    lon = ip.getDouble("longitude");
                    city = ip.optString("city", "");
                }
                JSONObject r = new JSONObject(get(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,"
                        + "weather_code,wind_speed_10m,is_day"
                        + "&hourly=precipitation_probability"
                        + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=2&timezone=auto",
                        lat, lon)));
                JSONObject cur = r.getJSONObject("current");
                JSONObject day = r.getJSONObject("daily");
                JSONObject j = new JSONObject();
                j.put("temp", Math.round(cur.getDouble("temperature_2m")));
                j.put("feels", Math.round(cur.getDouble("apparent_temperature")));
                j.put("hum", cur.getInt("relative_humidity_2m"));
                j.put("wind", Math.round(cur.getDouble("wind_speed_10m")));
                j.put("code", cur.getInt("weather_code"));
                j.put("day", cur.getInt("is_day") == 1);
                j.put("max", Math.round(day.getJSONArray("temperature_2m_max").getDouble(0)));
                j.put("min", Math.round(day.getJSONArray("temperature_2m_min").getDouble(0)));
                j.put("city", city);
                putRain(j, cur, r.optJSONObject("hourly"));
                j.put("at", System.currentTimeMillis());
                prefs(c).edit().putString("json", j.toString()).apply();
                Weather w = new Weather(j);
                main.post(() -> l.onWeather(w));
            } catch (Exception ignored) {
                // No internet: keep showing the cached weather.
            }
        }).start();
    }

    /** First hour within the next six with at least 60% chance of rain (unless it is raining already). */
    private static void putRain(JSONObject j, JSONObject cur, JSONObject hourly) {
        try {
            j.put("rainAt", "");
            j.put("rainProb", 0);
            if (hourly == null || cur.getInt("weather_code") >= 51) return;
            JSONArray times = hourly.getJSONArray("time");
            JSONArray probs = hourly.getJSONArray("precipitation_probability");
            String nowHour = cur.getString("time").substring(0, 13); // yyyy-MM-ddTHH
            for (int i = 0; i < times.length(); i++) {
                if (!times.getString(i).startsWith(nowHour)) continue;
                for (int k = i + 1; k <= i + 6 && k < times.length(); k++) {
                    if (!probs.isNull(k) && probs.getInt(k) >= 60) {
                        j.put("rainAt", times.getString(k).substring(11, 16));
                        j.put("rainProb", probs.getInt(k));
                        return;
                    }
                }
                return;
            }
        } catch (Exception ignored) {
            // the rest of the weather is still good
        }
    }

    private static Location lastLocation(Context c) {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        Location best = null;
        for (String p : lm.getAllProviders()) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (SecurityException ignored) { }
        }
        return best;
    }

    private static String cityName(Context c, double lat, double lon) {
        try {
            List<Address> a = new Geocoder(c, Locale.getDefault()).getFromLocation(lat, lon, 1);
            if (a != null && !a.isEmpty()) {
                String city = a.get(0).getLocality();
                return city != null ? city : a.get(0).getSubAdminArea();
            }
        } catch (Exception ignored) { }
        return "";
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(10000);
        con.setRequestProperty("User-Agent", "CarLauncher");
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("weather", Context.MODE_PRIVATE);
    }
}
