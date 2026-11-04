package com.simple.carlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Minimal client for the factory NWD radio service (com.nwd.radio.service).
 * Talks raw Binder using the transaction codes of its RadioFeature AIDL interface.
 */
public class RadioClient implements ServiceConnection {

    private static final String DESCRIPTOR = "com.nwd.radio.service.RadioFeature";
    private static final int TX_SET_CURRENT_FREQUENCY = 1;
    private static final int TX_GET_CURRENT_FREQUENCY = 2;
    private static final int TX_SEEK = 3;
    private static final int TX_CHANGE_BAND = 5;
    private static final int TX_GET_PREFAB_FREQUENCY = 21;
    private static final int TX_REGIST_CALLBACK = 24;
    private static final int TX_UNREGIST_CALLBACK = 25;
    private static final int TX_PREFEB = 26;

    /** Current station as reported by the service. */
    public static class Station {
        public final int band;      // 0-2 FM1-3, 3-5 AM1-3, 6-8 FML1-3
        public final int frequency; // FM: MHz * 100 (9610 = 96.10), AM: kHz
        public final String psName; // RDS station name, may be empty

        Station(int band, int frequency, String psName) {
            this.band = band;
            this.frequency = frequency;
            this.psName = psName;
        }

        public boolean isAm() {
            return band >= 3 && band <= 5;
        }

        public String bandLabel() {
            String[] names = {"FM1", "FM2", "FM3", "AM1", "AM2", "AM3", "FM1", "FM2", "FM3"};
            return band >= 0 && band < names.length ? names[band] : "FM";
        }

        public String frequencyLabel() {
            if (isAm()) return String.valueOf(frequency);
            String s = String.format(java.util.Locale.US, "%.2f", frequency / 100f);
            return s.endsWith("0") ? s.substring(0, s.length() - 1) : s;
        }
    }

    /** Result of {@link #snapshotAsync}: presets is null when they were not requested. */
    public static class Snapshot {
        public final Station current;
        public final List<Station> presets;

        Snapshot(Station current, List<Station> presets) {
            this.current = current;
            this.presets = presets;
        }
    }

    // Binder calls into the factory service block until it answers. Polling them on the UI thread
    // would freeze the screen (ANR) if that service ever hangs, so periodic reads go through here.
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "radio-binder");
        t.setDaemon(true);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean inFlight = new AtomicBoolean();

    private IBinder binder;

    /**
     * Reads the current station (and the presets when asked) off the UI thread and delivers the
     * result on it. If the previous read has not finished yet this call is skipped, so a slow
     * service never piles up requests.
     */
    public void snapshotAsync(boolean withPresets, Consumer<Snapshot> done) {
        if (!inFlight.compareAndSet(false, true)) return;
        IO.execute(() -> {
            Snapshot s = new Snapshot(current(), withPresets ? presets() : null);
            main.post(() -> {
                inFlight.set(false);
                done.accept(s);
            });
        });
    }

    public void currentAsync(Consumer<Station> done) {
        snapshotAsync(false, s -> done.accept(s.current));
    }

    public void bind(Context c) {
        Intent i = new Intent("com.nwd.radio.service.ACTION_RADIO_SERVICE");
        i.setPackage("com.nwd.radio.service");
        try {
            c.bindService(i, this, Context.BIND_AUTO_CREATE);
        } catch (SecurityException ignored) { }
    }

    public void unbind(Context c) {
        try {
            c.unbindService(this);
        } catch (IllegalArgumentException ignored) { }
        binder = null;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder = service;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        binder = null;
    }

    public boolean isConnected() {
        return binder != null && binder.isBinderAlive();
    }

    /** Returns the current station, or null if the service is unavailable. */
    public Station current() {
        if (!isConnected()) return null;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            binder.transact(TX_GET_CURRENT_FREQUENCY, data, reply, 0);
            reply.readException();
            if (reply.readInt() == 0) return null;
            int band = reply.readByte();
            String ps = reply.readString();
            int freq = reply.readInt();
            return new Station(band, freq, ps == null ? "" : ps.trim());
        } catch (Exception e) {
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    /** Saved stations of the current band. */
    public java.util.List<Station> presets() {
        java.util.List<Station> out = new java.util.ArrayList<>();
        if (!isConnected()) return out;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            binder.transact(TX_GET_PREFAB_FREQUENCY, data, reply, 0);
            reply.readException();
            int n = reply.readInt();
            for (int i = 0; i < n; i++) {
                if (reply.readInt() == 0) continue;
                int band = reply.readByte();
                String ps = reply.readString();
                int freq = reply.readInt();
                out.add(new Station(band, freq, ps == null ? "" : ps.trim()));
            }
        } catch (Exception ignored) {
        } finally {
            data.recycle();
            reply.recycle();
        }
        return out;
    }

    /** Tune directly to a station; index is its preset slot. */
    public void tune(Station s, int index) {
        if (!isConnected()) return;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(s.frequency);
            data.writeByte((byte) s.band);
            data.writeInt(index);
            binder.transact(TX_SET_CURRENT_FREQUENCY, data, reply, 0);
            reply.readException();
        } catch (Exception ignored) {
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    /** Cycle FM1 → FM2 → FM3 → AM… */
    public void changeBand() {
        if (!isConnected()) return;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            binder.transact(TX_CHANGE_BAND, data, reply, 0);
            reply.readException();
        } catch (Exception ignored) {
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    /**
     * Powers the FM chip back up. The service runs InitFM (power up, last band/frequency,
     * unmute) when a client registers a callback while the radio is the current source,
     * so register a no-op callback and drop it again.
     */
    public void wakeTuner(Context c) {
        // The full power-up + unmute runs on a "source changed to radio" notice. The kernel
        // does not resend it when the radio already is the current source, so send it to the
        // radio service only.
        Intent i = new Intent("com.nwd.action.ACTION_CHANGE_SOURCE");
        i.setPackage("com.nwd.radio.service");
        i.putExtra("extra_source_id", (byte) 4);
        c.sendBroadcast(i);
        // "Radio app came to front": the service mutes, then schedules the unmute of the FM audio.
        Intent in = new Intent("com.nwd.action.ACTION_APP_IN_OUT");
        in.setPackage("com.nwd.radio.service");
        in.putExtra("extra_app_id", 8);
        c.sendBroadcast(in);
        if (!isConnected()) return;
        android.os.Binder callback = new android.os.Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                return true; // ignore frequency/RDS notifications
            }
        };
        callback.attachInterface(null, "com.nwd.radio.service.RadioCallback");
        for (int code : new int[]{TX_REGIST_CALLBACK, TX_UNREGIST_CALLBACK}) {
            Parcel data = Parcel.obtain(), reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeStrongBinder(callback);
                binder.transact(code, data, reply, 0);
                reply.readException();
            } catch (Exception ignored) {
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
    }

    /** Jump to the next/previous saved station. */
    public void preset(boolean next) {
        call(TX_PREFEB, next);
    }

    /** Scan to the next/previous station with signal. */
    public void seek(boolean up) {
        call(TX_SEEK, up);
    }

    private void call(int code, boolean arg) {
        if (!isConnected()) return;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(arg ? 1 : 0);
            binder.transact(code, data, reply, 0);
            reply.readException();
        } catch (Exception ignored) {
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
