package com.bt;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Wire-compatible copy of the factory Bluetooth module's device record (com.bt.bc03).
 * Received in com.bt.ACTION_GET_PAIR_DEVICE_RECORD broadcasts and passed back to
 * connectDevice_BC. Field order must match the factory writeToParcel exactly.
 */
public class BTDevice implements Parcelable {

    public String mAddress, mName;
    public int mDeviceClass, mService;
    public boolean isPair;
    public int mPairRecordIndex;

    public BTDevice() { }

    public BTDevice(String address, String name) {
        mAddress = address;
        mName = name;
    }

    private BTDevice(Parcel p) {
        mAddress = p.readString();
        mName = p.readString();
        mDeviceClass = p.readInt();
        mService = p.readInt();
        isPair = p.readInt() != 0;
        mPairRecordIndex = p.readInt();
    }

    public static final Creator<BTDevice> CREATOR = new Creator<BTDevice>() {
        public BTDevice createFromParcel(Parcel p) {
            return new BTDevice(p);
        }

        public BTDevice[] newArray(int n) {
            return new BTDevice[n];
        }
    };

    @Override
    public void writeToParcel(Parcel p, int flags) {
        p.writeString(mAddress);
        p.writeString(mName);
        p.writeInt(mDeviceClass);
        p.writeInt(mService);
        p.writeInt(isPair ? 1 : 0);
        p.writeInt(mPairRecordIndex);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
