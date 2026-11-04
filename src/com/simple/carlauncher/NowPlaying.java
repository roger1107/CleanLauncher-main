package com.simple.carlauncher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks what is playing from any source and routes controls back to it:
 * apps with a media session (Spotify, YouTube Music…), the factory USB/SD player
 * (com.android.music.* broadcasts) and Bluetooth audio (com.bt.* broadcasts).
 */
final class NowPlaying {

    enum Source { APP, LOCAL, BT }

    interface Listener {
        void onNowPlayingChanged();
    }

    private static final String LOCAL_PLAYER = "com.nwd.android.music.ui/.home_horizontalActivity";

    /** State reported by one source. */
    static final class Track {
        String title = "", artist = "";
        Bitmap art;
        boolean playing;
        long updated;
    }

    private static final String TAG = "NowPlaying";

    private static NowPlaying instance;

    static NowPlaying get(Context c) {
        if (instance == null) instance = new NowPlaying(c.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    private final List<Listener> listeners = new ArrayList<>();
    private final Track app = new Track(), local = new Track(), bt = new Track();
    final BtClient btClient = new BtClient();

    private final MediaSessionManager sessions;
    private final ComponentName notificationListener;
    private MediaController controller;

    private NowPlaying(Context c) {
        ctx = c;
        sessions = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
        notificationListener = new ComponentName(c, MediaListener.class);

        IntentFilter f = new IntentFilter();
        f.addAction("com.android.music.metachanged");
        f.addAction("com.android.music.playstatechanged");
        f.addAction("com.bt.ACTION_AVRCP_MUSIC_ID3");
        f.addAction("com.bt.ACTION_BT_MUSIC_PLAY");
        f.addAction("com.bt.ACTION_BT_MUSIC_PAUSE");
        f.addAction("com.bt.ACTION_A2DP_RELEASE");
        f.addAction("com.bt.ACTION_AVRCP_RELEASE");
        c.registerReceiver(receiver, f);

        watchSessions();
        refreshSessions();

        btClient.bind(c, () -> {
            bt.playing = btClient.isMusicPlaying();
            if (bt.playing) bt.updated = SystemClock.elapsedRealtime();
            btClient.queryId3();
            notifyListeners();
        });
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (a == null) return;
            i.setExtrasClassLoader(NowPlaying.class.getClassLoader());
            try {
                handle(a, i);
            } catch (RuntimeException e) {
                // Factory apps may attach extras we cannot unparcel; never crash the launcher for it.
                android.util.Log.w("NowPlaying", "Ignoring broadcast " + a, e);
            }
        }

        private void handle(String a, Intent i) {
            long now = SystemClock.elapsedRealtime();
            switch (a) {
                case "com.android.music.metachanged":
                case "com.android.music.playstatechanged":
                    String localTitle = i.hasExtra("track") ? nz(i.getStringExtra("track")) : local.title;
                    String localArtist = i.hasExtra("artist") ? nz(i.getStringExtra("artist")) : local.artist;
                    if (!localTitle.equals(local.title) || !localArtist.equals(local.artist)) local.art = null;
                    local.title = localTitle;
                    local.artist = localArtist;
                    local.playing = i.getBooleanExtra("playing", local.playing);
                    local.updated = now;
                    requestCover(Source.LOCAL);
                    break;
                case "com.bt.ACTION_AVRCP_MUSIC_ID3":
                    String btTitle = nz(i.getStringExtra("extra_avrcp_id3_title"));
                    String btArtist = nz(i.getStringExtra("extra_avrcp_id3_artist"));
                    if (!btTitle.equals(bt.title) || !btArtist.equals(bt.artist)) bt.art = null;
                    bt.title = btTitle;
                    bt.artist = btArtist;
                    if (!bt.title.isEmpty()) bt.updated = now;
                    requestCover(Source.BT);
                    break;
                case "com.bt.ACTION_BT_MUSIC_PLAY":
                    bt.playing = true;
                    bt.updated = now;
                    btClient.queryId3();
                    break;
                case "com.bt.ACTION_BT_MUSIC_PAUSE":
                    bt.playing = false;
                    break;
                default: // A2DP/AVRCP released: phone disconnected
                    bt.playing = false;
                    bt.title = "";
                    bt.artist = "";
                    bt.art = null;
                    bt.updated = 0;
                    break;
            }
            notifyListeners();
        }
    };

    // ---------- App media sessions ----------

    private final MediaController.Callback sessionCallback = new MediaController.Callback() {
        public void onMetadataChanged(MediaMetadata m) { readSession(); }
        public void onPlaybackStateChanged(PlaybackState s) { readSession(); }
        public void onSessionDestroyed() { refreshSessions(); }
    };

    private boolean watching;

    /**
     * Listens for apps starting or stopping playback. Needs notification access, which the user may
     * grant after the launcher started, so it is retried every time the home screen comes back.
     */
    private void watchSessions() {
        if (watching) return;
        try {
            sessions.addOnActiveSessionsChangedListener(list -> refreshSessions(), notificationListener);
            watching = true;
        } catch (SecurityException ignored) {
            // Notification access not granted yet: app sessions are not visible.
        }
    }

    private void refreshSessions() {
        List<MediaController> list = null;
        try {
            list = sessions.getActiveSessions(notificationListener);
        } catch (SecurityException ignored) { }
        MediaController pick = null;
        if (list != null) {
            for (MediaController c : list) {
                if ("com.android.server.telecom".equals(c.getPackageName())) continue;
                if (pick == null) pick = c;
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { pick = c; break; }
            }
        }
        if (controller != null) controller.unregisterCallback(sessionCallback);
        controller = pick;
        if (controller != null) controller.registerCallback(sessionCallback);
        readSession();
    }

    private void readSession() {
        if (controller == null) {
            app.title = "";
            app.artist = "";
            app.art = null;
            app.playing = false;
            app.updated = 0;
        } else {
            MediaMetadata m = controller.getMetadata();
            PlaybackState s = controller.getPlaybackState();
            app.title = m == null ? "" : nz(m.getString(MediaMetadata.METADATA_KEY_TITLE));
            app.artist = m == null ? "" : nz(m.getString(MediaMetadata.METADATA_KEY_ARTIST));
            Bitmap art = m == null ? null : m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null && m != null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
            app.art = art;
            boolean playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
            if (playing || !app.title.isEmpty()) app.updated = SystemClock.elapsedRealtime();
            app.playing = playing;
            if (app.art == null) requestCover(Source.APP);
        }
        notifyListeners();
    }

    // ---------- Public API ----------

    /** The source to show: whatever is playing (most recent first), else the last one used. */
    Source current() {
        Source best = null;
        long bestTime = -1;
        for (Source s : Source.values()) {
            Track t = track(s);
            if (t.playing && t.updated > bestTime) { best = s; bestTime = t.updated; }
        }
        if (best != null) return best;
        for (Source s : Source.values()) {
            Track t = track(s);
            if (t.updated > 0 && t.updated > bestTime) { best = s; bestTime = t.updated; }
        }
        return best;
    }

    Track track(Source s) {
        return s == Source.APP ? app : s == Source.LOCAL ? local : bt;
    }

    String label(Source s) {
        if (s == Source.BT) return "Bluetooth";
        if (s == Source.LOCAL) return "USB";
        if (controller != null) {
            try {
                PackageManager pm = ctx.getPackageManager();
                return pm.getApplicationLabel(pm.getApplicationInfo(controller.getPackageName(), 0)).toString();
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        return "Música";
    }

    void playPause() {
        Source s = current();
        Log.d(TAG, "playPause source=" + s + " appSession=" + (controller != null)
                + " btService=" + btClient.isBound());
        if (s == Source.BT && btClient.isBound()) {
            if (Ui.radioIsSource(ctx)) {
                // Radio has the speakers (the phone may even be playing muted): bring Bluetooth in.
                Ui.userChoseRadioAt = 0;
                Ui.selectBluetooth(ctx);
                btClient.playControl(BtClient.PLAY);
            } else {
                btClient.playControl(bt.playing ? BtClient.PAUSE : BtClient.PLAY);
            }
            return;
        }
        if (s == Source.APP && controller != null) {
            try {
                if (app.playing) controller.getTransportControls().pause();
                else controller.getTransportControls().play();
                return;
            } catch (RuntimeException e) {
                Log.w(TAG, "session refused the command, using the media key", e);
            }
        }
        // USB player, no session, or a radio without the factory Bluetooth service: standard media key.
        mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    void skip(boolean next) {
        Source s = current();
        Log.d(TAG, "skip next=" + next + " source=" + s + " appSession=" + (controller != null)
                + " btService=" + btClient.isBound());
        if (s == Source.BT && btClient.isBound()) {
            btClient.playControl(next ? BtClient.NEXT : BtClient.PREVIOUS);
            return;
        }
        if (s == Source.APP && controller != null) {
            try {
                if (next) controller.getTransportControls().skipToNext();
                else controller.getTransportControls().skipToPrevious();
                return;
            } catch (RuntimeException e) {
                Log.w(TAG, "session refused the command, using the media key", e);
            }
        }
        mediaKey(next ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    /**
     * True when the user enabled notification access for this launcher: without it Android hides the
     * media sessions of apps like Spotify or YouTube Music, so they can neither be shown nor controlled.
     */
    boolean hasNotificationAccess() {
        String l = android.provider.Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        return l != null && l.contains(ctx.getPackageName());
    }

    /** Opens the screen that belongs to the current source. */
    void open(Context c) {
        Source s = current();
        if (s == Source.BT) {
            c.startActivity(new Intent(c, BtActivity.class));
        } else if (s == Source.APP && controller != null) {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(controller.getPackageName());
            if (i != null) c.startActivity(i);
        } else {
            Ui.launch(c, LOCAL_PLAYER);
        }
    }

    void addListener(Listener l) {
        listeners.add(l);
        watchSessions();
        refreshSessions();
        if (btClient.isBound()) btClient.queryId3();
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Looks the cover up online for a track that came without one (Bluetooth, USB, some apps). */
    private void requestCover(Source s) {
        final Track t = track(s);
        if (t.art != null || t.title.isEmpty()) return;
        final String title = t.title, artist = t.artist;
        CoverArt.load(ctx, title, artist, cover -> {
            // The song may have changed while the lookup was running.
            if (t.art == null && t.title.equals(title) && t.artist.equals(artist)) {
                t.art = cover;
                notifyListeners();
            }
        });
    }

    private void notifyListeners() {
        for (Listener l : new ArrayList<>(listeners)) l.onNowPlayingChanged();
    }

    private void mediaKey(int keyCode) {
        Log.d(TAG, "media key " + KeyEvent.keyCodeToString(keyCode));
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        long now = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
