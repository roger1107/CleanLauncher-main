package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Finds the album cover of tracks that only arrive with a title and an artist (Bluetooth music
 * and the USB player): the radio's Bluetooth service does not pass covers on. The lookup uses
 * Deezer's public search (no key), so the song title and artist are sent to that service.
 * Covers are cached on disk, so a song is looked up only once.
 */
final class CoverArt {

    interface Callback {
        /** Called on the main thread. */
        void onCover(Bitmap cover);
    }

    private static final String SEARCH = "https://api.deezer.com/search?limit=1&q=";
    private static final String USER_AGENT = "CleanLauncher/1.0 (Android car launcher)";
    private static final int MAX_FILES = 300;
    private static final int KEEP_FILES = 200;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cover-art");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> memory = new LruCache<>(8);
    private static final Set<String> notFound = ConcurrentHashMap.newKeySet();

    private CoverArt() { }

    /**
     * Looks the cover up in the background and calls {@code done} on the main thread when it
     * finds one (straight away if it is already in memory). Call it from the main thread.
     */
    static void load(Context c, String title, String artist, Callback done) {
        String name = firstArtist(artist);
        String song = cleanTitle(title);
        if (song.isEmpty()) return;
        String key = (name + "|" + song).toLowerCase(Locale.ROOT);
        Bitmap hit = memory.get(key);
        if (hit != null) {
            done.onCover(hit);
            return;
        }
        if (notFound.contains(key)) return;
        File dir = new File(c.getCacheDir(), "covers");
        IO.execute(() -> {
            Bitmap b;
            try {
                b = fromDisk(dir, key);
                if (b == null) {
                    byte[] jpg = search(name, song);
                    if (jpg == null) {
                        notFound.add(key);
                        return;
                    }
                    b = BitmapFactory.decodeByteArray(jpg, 0, jpg.length);
                    if (b != null) save(dir, key, jpg);
                }
            } catch (Exception e) {
                return; // offline or server trouble: it is tried again with the next track
            }
            if (b == null) return;
            Bitmap cover = b;
            memory.put(key, cover);
            MAIN.post(() -> done.onCover(cover));
        });
    }

    // ---------- Text cleanup ----------

    /** "Camilo;Pedro Capó" or "A feat. B" -> the first artist. */
    private static String firstArtist(String artist) {
        if (artist == null) return "";
        return artist.split("(?i)[;/,&]|\\s+(?:feat\\.?|ft\\.?|x)\\s+", 2)[0].trim();
    }

    /** Drops "(Remastered)" or "[Official Video]" style suffixes. */
    private static String cleanTitle(String title) {
        if (title == null) return "";
        return title.replaceAll("\\s*[\\(\\[].*?[\\)\\]]", "").trim();
    }

    // ---------- Search ----------

    /** Cover bytes, or null when the service does not know the track. Throws when offline. */
    private static byte[] search(String artist, String song) throws IOException, JSONException {
        String url = null;
        if (!artist.isEmpty()) {
            url = firstCover(SEARCH + enc("artist:\"" + artist + "\" track:\"" + song + "\""));
        }
        if (url == null) url = firstCover(SEARCH + enc((artist + " " + song).trim()));
        return url == null ? null : get(url);
    }

    private static String firstCover(String searchUrl) throws IOException, JSONException {
        JSONObject root = new JSONObject(new String(get(searchUrl), "UTF-8"));
        JSONArray data = root.optJSONArray("data");
        if (data == null || data.length() == 0) return null;
        JSONObject album = data.getJSONObject(0).optJSONObject("album");
        if (album == null) return null;
        String u = album.optString("cover_big", "");
        if (u.isEmpty()) u = album.optString("cover_medium", "");
        return u.isEmpty() ? null : u;
    }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static byte[] get(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", USER_AGENT);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    // ---------- Disk cache ----------

    private static File file(File dir, String key) {
        String name;
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(key.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : h) sb.append(String.format(Locale.ROOT, "%02x", x));
            name = sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            name = Integer.toHexString(key.hashCode()) + "_" + key.length();
        }
        return new File(dir, name + ".jpg");
    }

    private static Bitmap fromDisk(File dir, String key) {
        File f = file(dir, key);
        return f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
    }

    private static void save(File dir, String key, byte[] jpg) {
        dir.mkdirs();
        try (FileOutputStream o = new FileOutputStream(file(dir, key))) {
            o.write(jpg);
        } catch (IOException ignored) {
            return;
        }
        File[] files = dir.listFiles();
        if (files != null && files.length > MAX_FILES) { // drop the oldest covers
            Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (int i = 0; i < files.length - KEEP_FILES; i++) files[i].delete();
        }
    }
}
