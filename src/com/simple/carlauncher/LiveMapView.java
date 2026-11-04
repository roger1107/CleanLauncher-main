package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.LruCache;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Live street map that follows the GPS position: OpenStreetMap raster tiles, cached on disk,
 * drawn "heading up" (the map turns so the direction of travel points up). It is only a map of
 * where the car is, with no routing: tapping the card opens the real navigation app.
 *
 * Tiles come from tile.openstreetmap.org (personal use; needs INTERNET). The OSM tile policy asks
 * for an identifying User-Agent, caching for at least 7 days and visible attribution, which the
 * code below does. For a widely shared build use a tile provider of your own instead.
 */
public class LiveMapView extends View {

    private static final int TILE = 256;
    private static final String TILE_URL = "https://tile.openstreetmap.org/%d/%d/%d.png";
    private static final String USER_AGENT = "CleanLauncher/1.0 (Android car launcher)";
    private static final long FRESH_MS = 7L * 24 * 3600 * 1000;
    private static final long RETRY_MS = 30 * 1000;
    private static final long MAX_CACHE_BYTES = 60L * 1024 * 1024;
    private static final long GLIDE_MS = 1000; // position/rotation glide between 1 s GPS fixes

    private static final ExecutorService IO = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "map-tiles");
        t.setDaemon(true);
        return t;
    });

    private final float d;
    private final File cacheDir;
    private final LruCache<String, Bitmap> memory = new LruCache<>(48);
    private final Set<String> loading = Collections.synchronizedSet(new HashSet<String>());
    private final Map<String, Long> failedAt = new ConcurrentHashMap<>();

    private final Paint tilePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();
    private final RectF dst = new RectF();
    private final int background;

    private boolean hasPos;
    private int zoom = 16;
    // Where the map is gliding from and to: latitude, longitude and map rotation (degrees).
    private double fromLat, fromLon, toLat, toLon;
    private float fromRot, toRot;
    private long glideStart;

    public LiveMapView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        cacheDir = new File(c.getCacheDir(), "tiles");
        boolean dark = Ui.isDark(c);
        background = dark ? 0xFF0B1220 : 0xFF0E2350;
        // Tone the (light) OSM tiles down so they sit well in the neon theme.
        ColorMatrix cm = new ColorMatrix();
        if (dark) cm.setScale(0.5f, 0.55f, 0.75f, 1f);
        else cm.setScale(0.92f, 0.95f, 1f, 1f);
        tilePaint.setColorFilter(new ColorMatrixColorFilter(cm));
        text.setColor(0xB3FFFFFF);
        text.setTextSize(12 * d);
        text.setShadowLayer(2 * d, 0, 0, 0xFF000000);
        IO.execute(() -> prune(cacheDir));
    }

    /**
     * New GPS fix. bearing is NaN when unknown; speedKmh is negative when unknown. The map only
     * turns while the car is really moving (the GPS bearing is noise at a standstill).
     */
    public void setPosition(double lat, double lon, float bearing, float speedKmh) {
        double[] cur = current();
        fromLat = cur[0];
        fromLon = cur[1];
        fromRot = norm((float) cur[2]);
        toLat = lat;
        toLon = lon;
        if (!hasPos) { // first fix: no glide from nowhere
            fromLat = lat;
            fromLon = lon;
        }
        if (!Float.isNaN(bearing) && speedKmh >= 5f) toRot = -bearing;
        if (!hasPos) fromRot = toRot;
        if (speedKmh > 70f) zoom = 15;       // zoom out a little at highway speed
        else if (speedKmh >= 0f && speedKmh < 55f) zoom = 16;
        hasPos = true;
        glideStart = SystemClock.elapsedRealtime();
        invalidate();
    }

    /** {lat, lon, rotation} at this moment of the glide. */
    private double[] current() {
        float t = Math.min(1f, (SystemClock.elapsedRealtime() - glideStart) / (float) GLIDE_MS);
        double rotDiff = norm(toRot - fromRot); // always turn the short way round
        return new double[]{
                fromLat + (toLat - fromLat) * t,
                fromLon + (toLon - fromLon) * t,
                fromRot + rotDiff * t};
    }

    /** Angle in degrees brought into -180..180. */
    private static float norm(float a) {
        a %= 360f;
        if (a > 180f) a -= 360f;
        else if (a < -180f) a += 360f;
        return a;
    }

    // ---------- Drawing ----------

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        c.drawColor(background);
        if (!hasPos) {
            text.setTextAlign(Paint.Align.CENTER);
            c.drawText("Buscando GPS…", w / 2f, h * 0.4f, text);
            if (hint != null) {
                text.setTextSize(10 * d);
                c.drawText(hint, w / 2f, h * 0.4f + 16 * d, text);
                text.setTextSize(12 * d);
            }
            return;
        }

        double[] cur = current();
        float mx = w / 2f, my = h * 0.62f; // the car sits a bit below the middle: more road ahead
        int z = zoom;
        double n = 1L << z;
        double cxw = worldX(cur[1], z), cyw = worldY(cur[0], z);
        float scale = d;
        // Rotation brings the corners into view: cover the farthest one.
        double reach = Math.hypot(Math.max(mx, w - mx), Math.max(my, h - my)) / scale;

        int drawn = 0;
        c.save();
        c.rotate((float) cur[2], mx, my);
        int x0 = (int) Math.floor((cxw - reach) / TILE), x1 = (int) Math.floor((cxw + reach) / TILE);
        int y0 = Math.max(0, (int) Math.floor((cyw - reach) / TILE));
        int y1 = Math.min((int) n - 1, (int) Math.floor((cyw + reach) / TILE));
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                int wx = (int) (((tx % n) + n) % n); // wrap around the antimeridian
                String key = z + "/" + wx + "/" + ty;
                Bitmap b = memory.get(key);
                if (b == null) {
                    request(z, wx, ty, key);
                    continue;
                }
                float left = mx + (float) ((tx * (double) TILE - cxw) * scale);
                float top = my + (float) ((ty * (double) TILE - cyw) * scale);
                dst.set(left, top, left + TILE * scale + 0.5f, top + TILE * scale + 0.5f); // overlap hides seams
                c.drawBitmap(b, null, dst, tilePaint);
                drawn++;
            }
        }
        c.restore();

        drawArrow(c, mx, my);

        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(9 * d);
        c.drawText("© OpenStreetMap", w - 8 * d, h - 6 * d, text);
        text.setTextSize(12 * d);
        if (drawn == 0 && !failedAt.isEmpty()) {
            text.setTextAlign(Paint.Align.CENTER);
            c.drawText("Mapa sin conexión", w / 2f, h * 0.4f, text);
            if (!lastError.isEmpty()) { // the real reason, so a failing network can be diagnosed
                text.setTextSize(10 * d);
                c.drawText(lastError, w / 2f, h * 0.4f + 16 * d, text);
                text.setTextSize(12 * d);
            }
        }

        // Keep gliding between fixes (about 20 frames per second is plenty for a map).
        if (SystemClock.elapsedRealtime() - glideStart < GLIDE_MS) postInvalidateDelayed(50);
    }

    private void drawArrow(Canvas c, float mx, float my) {
        float s = 11 * d;
        arrow.setStyle(Paint.Style.FILL);
        arrow.setColor(0x334F7BFF);
        c.drawCircle(mx, my, 20 * d, arrow);
        arrowPath.reset();
        arrowPath.moveTo(0, -1.35f * s);
        arrowPath.lineTo(s, s);
        arrowPath.lineTo(0, 0.45f * s);
        arrowPath.lineTo(-s, s);
        arrowPath.close();
        c.save();
        c.translate(mx, my);
        arrow.setColor(0xFF4F7BFF);
        c.drawPath(arrowPath, arrow);
        arrow.setStyle(Paint.Style.STROKE);
        arrow.setStrokeWidth(2 * d);
        arrow.setStrokeJoin(Paint.Join.ROUND);
        arrow.setColor(0xFFFFFFFF);
        c.drawPath(arrowPath, arrow);
        c.restore();
    }

    // ---------- Web Mercator ----------

    private static double worldX(double lon, int z) {
        return (lon + 180.0) / 360.0 * TILE * (double) (1L << z);
    }

    private static double worldY(double lat, int z) {
        double s = Math.sin(Math.toRadians(Math.max(-85.05, Math.min(85.05, lat))));
        return (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * TILE * (double) (1L << z);
    }

    // ---------- Tiles: memory, disk cache, network ----------

    /** Last tile download error, shown under "Mapa sin conexión". */
    private volatile String lastError = "";
    /** Extra line under "Buscando GPS…" (for example a missing location permission). */
    private String hint;

    void setHint(String h) {
        hint = h;
        postInvalidate();
    }

    private static String describe(Exception e) {
        String m = e.getMessage();
        String s = e.getClass().getSimpleName() + (m == null || m.isEmpty() ? "" : ": " + m);
        return s.length() > 44 ? s.substring(0, 44) : s;
    }

    private void request(int z, int x, int y, String key) {
        Long failed = failedAt.get(key);
        if (failed != null && SystemClock.elapsedRealtime() - failed < RETRY_MS) return;
        if (!loading.add(key)) return;
        IO.execute(() -> {
            Bitmap b = null;
            try {
                b = loadTile(z, x, y);
            } catch (Exception e) {
                // offline or server trouble: retry later
                lastError = describe(e);
                android.util.Log.w("LiveMap", "tile " + key + " failed", e);
            }
            loading.remove(key);
            if (b != null) {
                memory.put(key, b);
                lastError = "";
                failedAt.remove(key);
            } else {
                failedAt.put(key, SystemClock.elapsedRealtime());
            }
            postInvalidate();
        });
    }

    private Bitmap loadTile(int z, int x, int y) throws IOException {
        File f = new File(cacheDir, z + "/" + x + "/" + y + ".png");
        boolean fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < FRESH_MS;
        if (!fresh) {
            try {
                download(z, x, y, f);
            } catch (IOException e) {
                if (!f.exists()) throw e; // offline: an old copy is better than a hole
            }
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b == null) {
            f.delete(); // corrupt download
            throw new IOException("bad tile");
        }
        return b;
    }

    private void download(int z, int x, int y, File f) throws IOException {
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        HttpURLConnection c = (HttpURLConnection) new URL(String.format(Locale.US, TILE_URL, z, x, y)).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", USER_AGENT);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            File tmp = new File(f.getPath() + ".tmp");
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            }
            if (!tmp.renameTo(f)) {
                f.delete();
                if (!tmp.renameTo(f)) throw new IOException("cannot store tile");
            }
        } finally {
            c.disconnect();
        }
    }

    /** Keeps the tile cache under MAX_CACHE_BYTES by dropping the oldest tiles. */
    private static void prune(File dir) {
        List<File> files = new ArrayList<>();
        collect(dir, files);
        long total = 0;
        for (File f : files) total += f.length();
        if (total <= MAX_CACHE_BYTES) return;
        Collections.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
        });
        for (File f : files) {
            if (total <= MAX_CACHE_BYTES * 0.8) break;
            total -= f.length();
            f.delete();
        }
    }

    private static void collect(File dir, List<File> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) collect(f, out);
            else out.add(f);
        }
    }
}
