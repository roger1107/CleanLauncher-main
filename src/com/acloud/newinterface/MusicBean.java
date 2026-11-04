package com.acloud.newinterface;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Wire-compatible copy of the factory music player's track object. Its
 * com.android.music.metachanged broadcasts carry one of these in the extras,
 * and without this class the whole extras bundle cannot be read.
 * Field order must match the factory writeToParcel exactly.
 */
public class MusicBean implements Parcelable {

    public String ID, DATA, SIZE, ARTIST, ALBUM_ID, DISPLAY_NAME, TITLE, DURATION;
    public int TYPE;
    public String ALBUM, PARENT_PATH, TEMP_ARTIST, TEMP_ALBUM;

    public static final Creator<MusicBean> CREATOR = new Creator<MusicBean>() {
        public MusicBean createFromParcel(Parcel p) {
            MusicBean b = new MusicBean();
            b.ID = p.readString();
            b.DATA = p.readString();
            b.SIZE = p.readString();
            b.ARTIST = p.readString();
            b.ALBUM_ID = p.readString();
            b.DISPLAY_NAME = p.readString();
            b.TITLE = p.readString();
            b.DURATION = p.readString();
            b.TYPE = p.readInt();
            b.ALBUM = p.readString();
            b.PARENT_PATH = p.readString();
            b.TEMP_ARTIST = p.readString();
            b.TEMP_ALBUM = p.readString();
            return b;
        }

        public MusicBean[] newArray(int n) {
            return new MusicBean[n];
        }
    };

    @Override
    public void writeToParcel(Parcel p, int flags) {
        p.writeString(ID);
        p.writeString(DATA);
        p.writeString(SIZE);
        p.writeString(ARTIST);
        p.writeString(ALBUM_ID);
        p.writeString(DISPLAY_NAME);
        p.writeString(TITLE);
        p.writeString(DURATION);
        p.writeInt(TYPE);
        p.writeString(ALBUM);
        p.writeString(PARENT_PATH);
        p.writeString(TEMP_ARTIST);
        p.writeString(TEMP_ALBUM);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
