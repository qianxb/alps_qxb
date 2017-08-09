package com.mediatek.settingslib.bluetooth;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDun;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import com.android.settingslib.bluetooth.LocalBluetoothProfile;
import com.android.settingslib.R;

/**
 * DunServer Profile
 */
public final class DunServerProfile implements LocalBluetoothProfile {
    private static final String TAG = "DunServerProfile";
    private static final boolean V = true;

    private BluetoothDun mService;
    private boolean mIsProfileReady;

    public static final String NAME = "DUN Server";

    // Order of this profile in device profiles list
    private static final int ORDINAL = 12;

    // The UUIDs indicate that remote device might access dun server
    static final ParcelUuid[] DUN_CLIENT_UUIDS = {
        BluetoothUuid.DUN
    };

    // These callbacks run on the main thread.
    private final class DunServiceListener
            implements BluetoothDun.ServiceListener {

        public void onServiceConnected(BluetoothDun proxy) {
            if (V) Log.d(TAG, "Bluetooth Dun service connected");
            mService = (BluetoothDun) proxy;
            mIsProfileReady = true;
        }

        public void onServiceDisconnected() {
            if (V) Log.d(TAG, "Bluetooth Dun service disconnected");
            mIsProfileReady = false;
        }
    }

    @Override
    public boolean isProfileReady() {
        return mIsProfileReady;
    }

    public DunServerProfile(Context context) {
        BluetoothDun dun = new BluetoothDun(context, new DunServiceListener());
    }

    @Override
    public boolean isConnectable() {
        return true;
    }

    @Override
    public boolean isAutoConnectable() {
        return false;
    }

    @Override
    public boolean connect(BluetoothDevice device) {
        /*Can't connect from server */
        return false;
    }

    @Override
    public boolean disconnect(BluetoothDevice device) {
        if (mService == null) return false;
        return mService.disconnect(device);
    }

    @Override
    public int getConnectionStatus(BluetoothDevice device) {
        if (mService == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        return mService.getState(device);
    }

    @Override
    public boolean isPreferred(BluetoothDevice device) {
        return false;
    }

    @Override
    public int getPreferred(BluetoothDevice device) {
        return -1;
    }

    @Override
    public void setPreferred(BluetoothDevice device, boolean preferred) {
        // ignore: isPreferred is always true for DUN
    }

    public String toString() {
        return NAME;
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }

    @Override
    public int getNameResource(BluetoothDevice device) {
        return R.string.bluetooth_profile_dun;
    }

    @Override
    public int getSummaryResourceForDevice(BluetoothDevice device) {
        return R.string.bluetooth_profile_dun_summary;
    }

    @Override
    public int getDrawableResource(BluetoothClass btClass) {
        return R.drawable.ic_bt_cellphone;
    }

    protected void finalize() {
        if (V) Log.d(TAG, "finalize()");
        if (mService != null) {
            try {
                mService.close();
                mService = null;
            } catch (Throwable t) {
                Log.w(TAG, "Error cleaning up PBAP proxy", t);
            }
        }
    }
}
