package com.simple.carlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;

/**
 * Client for the factory NWD setting service (com.nwd.setting.service), the same API the
 * factory equalizer uses. The service validates values and forwards them to the audio DSP,
 * so no raw MCU packets are built here.
 */
public class SoundClient implements ServiceConnection {

    private static final String DESCRIPTOR = "com.nwd.setting.service.SettingFeature";
    private static final int TX_SET_AUDIO_PARAM = 6;
    private static final int TX_GET_AUDIO_PARAM = 7;
    private static final int TX_SET_DSP_AUDIO = 31;
    private static final int TX_GET_DSP_AUDIO = 32;

    // SettingConstant.AudioType (setAudioParam / getAudioParam)
    public static final int TREBLE = 0, MIDDLE = 1, BASS = 2, EQ_PRESET = 5, FADER = 12, BALANCE = 13,
            LOUDNESS = 17;
    public static final int TONE_MAX = 14, BALANCE_MAX = 14; // centre 7

    // EQ presets (AUDIO_PARA_EQ values)
    public static final int EQ_CUSTOM = 0;
    public static final String[] PRESET_NAMES = {"Personalizado", "Estándar", "Pop", "Rock", "Clásica",
            "Jazz", "Dance", "Película", "Sala"};

    // SettingConstant.DspAudioType: 10 bands, 32 Hz .. 16 kHz
    public static final int BAND_FIRST = 11, BANDS = 10, BAND_MAX = 6; // centre 3
    public static final String[] BAND_LABELS = {"32", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"};

    private IBinder binder;
    private Runnable onConnected;

    public void bind(Context c, Runnable onConnected) {
        this.onConnected = onConnected;
        Intent i = new Intent("com.nwd.setting.service.ACTION_SETTING_SERVICE");
        i.setPackage("com.nwd.setting.service");
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
        if (onConnected != null) onConnected.run();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        binder = null;
    }

    public boolean isConnected() {
        return binder != null && binder.isBinderAlive();
    }

    public int getAudio(int type) {
        return callGet(TX_GET_AUDIO_PARAM, type, false);
    }

    public void setAudio(int type, int value) {
        callSet(TX_SET_AUDIO_PARAM, type, value);
    }

    public int getBand(int band) {
        return callGet(TX_GET_DSP_AUDIO, BAND_FIRST + band, true);
    }

    public void setBand(int band, int value) {
        callSet(TX_SET_DSP_AUDIO, BAND_FIRST + band, Math.max(0, Math.min(BAND_MAX, value)));
    }

    /** Applies a whole 10-band curve as the custom preset. */
    public void applyCurve(int[] curve) {
        setAudio(EQ_PRESET, EQ_CUSTOM);
        for (int b = 0; b < BANDS && b < curve.length; b++) setBand(b, curve[b]);
    }

    private int callGet(int code, int arg, boolean asByte) {
        if (!isConnected()) return -1;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (asByte) data.writeByte((byte) arg);
            else data.writeInt(arg);
            binder.transact(code, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } catch (Exception e) {
            return -1;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void callSet(int code, int a, int b) {
        if (!isConnected()) return;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (code == TX_SET_DSP_AUDIO) {
                data.writeByte((byte) a);
                data.writeByte((byte) b);
            } else {
                data.writeInt(a);
                data.writeInt(b);
            }
            binder.transact(code, data, reply, 0);
            reply.readException();
        } catch (Exception ignored) {
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
