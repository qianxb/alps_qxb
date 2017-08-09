/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.util.Log;

import com.android.settingslib.BaseTest;
import com.android.settingslib.bluetooth.BluetoothEventManager;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothAdapter;
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

// TODO: Add some coverage
public class BluetoothEventManagerTest extends BaseTest {

    private static final String TAG = "BluetoothEventManagerTest";

    private static final int BT_STATE_ON = BluetoothAdapter.STATE_ON;
    private static final int BT_STATE_OFF = BluetoothAdapter.STATE_OFF;
    private static final int BT_STATE_TURNING_ON = BluetoothAdapter.STATE_TURNING_ON;
    private static final int BT_STATE_TURNING_OFF = BluetoothAdapter.STATE_TURNING_OFF;
    private static final int BT_PROFILE_CONNECTED_STATE = BluetoothProfile.STATE_CONNECTED;
    private static final int BT_PROFILE_DISCONNECTED_STATE = BluetoothProfile.STATE_DISCONNECTED;
    private static final int BT_PROFILE_CONNECTING_STATE = BluetoothProfile.STATE_CONNECTING;
    private static final int BT_PROFILE_DISCONNECTING_STATE = BluetoothProfile.STATE_DISCONNECTING;
    private static final Boolean BT_SCANNING_START_STATE = true;
    private static final Boolean BT_SCANNING_END_STATE = false;

    private BluetoothCallback mCallback;
    private BluetoothEventManager mBluetoothEventManager;

    private LocalBluetoothAdapter mAdapter;
    private CachedBluetoothDeviceManager mCachedBluetoothDeviceManager;
    private LocalBluetoothProfileManager mLocalBluetoothProfileManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mCallback = Mockito.mock(BluetoothCallback.class);
        mAdapter = Mockito.mock(LocalBluetoothAdapter.class);
        mCachedBluetoothDeviceManager = Mockito.mock(CachedBluetoothDeviceManager.class);

        mBluetoothEventManager = new BluetoothEventManager(mAdapter,
            mCachedBluetoothDeviceManager, mContext);

        mBluetoothEventManager.registerCallback(mCallback);
    }

    @Override
    protected void tearDown() throws Exception {
        Log.d(TAG, "tearDown");
        super.tearDown();
    }

    public void testAdapterStateChangedCallback() {
        Intent i = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);

        testFourAdapterState(i, BT_STATE_ON);
        testFourAdapterState(i, BT_STATE_OFF);
        testFourAdapterState(i, BT_STATE_TURNING_ON);
        testFourAdapterState(i, BT_STATE_TURNING_OFF);
    }

    private void testFourAdapterState(Intent intent, int state) {
        intent.putExtra(BluetoothAdapter.EXTRA_STATE, state);
        mBluetoothEventManager.mBroadcastReceiver.onReceive(mContext, intent);

        ArgumentCaptor<Integer> btState = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(mCallback, Mockito.atLeastOnce())
                .onBluetoothStateChanged(btState.capture());
        assertEquals(state, (int) btState.getValue());
    }

    public void testConnectionStateCallback() {
        final CachedBluetoothDevice cachedBluetoothDevice = Mockito.mock(
            CachedBluetoothDevice.class);;

        Intent i = new Intent(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);

        testFourConnectionState(i, BT_PROFILE_CONNECTED_STATE, cachedBluetoothDevice);
        testFourConnectionState(i, BT_PROFILE_DISCONNECTED_STATE, cachedBluetoothDevice);
        testFourConnectionState(i, BT_PROFILE_CONNECTING_STATE, cachedBluetoothDevice);
        testFourConnectionState(i, BT_PROFILE_DISCONNECTING_STATE, cachedBluetoothDevice);
    }

    private void testFourConnectionState(Intent i, int state,
        CachedBluetoothDevice cachedBluetoothDevice) {
        i.putExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, state);
        mBluetoothEventManager.mBroadcastReceiver.onReceive(mContext, i);

        ArgumentCaptor<Integer> btState = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<CachedBluetoothDevice> btState1 = ArgumentCaptor.forClass(
            CachedBluetoothDevice.class);
        Mockito.verify(mCallback, Mockito.atLeastOnce())
                .onConnectionStateChanged(btState1.capture(), btState.capture());

        assertEquals(TAG, state, (int) btState.getValue());
        assertEquals(TAG, null, (CachedBluetoothDevice) btState1.getValue());
    }

    public void testScanningStateStartedCallback() {
        Intent i = new Intent(BluetoothAdapter.ACTION_DISCOVERY_STARTED);

        mBluetoothEventManager.mBroadcastReceiver.onReceive(mContext, i);

        ArgumentCaptor<Boolean> btState = ArgumentCaptor.forClass(Boolean.class);
        Mockito.verify(mCallback, Mockito.atLeastOnce())
                .onScanningStateChanged(btState.capture());
        assertEquals(BT_SCANNING_START_STATE, (Boolean) btState.getValue());
    }

    public void testScanningStateEndedCallback() {
        Intent i = new Intent(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        mBluetoothEventManager.mBroadcastReceiver.onReceive(mContext, i);

        ArgumentCaptor<Boolean> btState = ArgumentCaptor.forClass(Boolean.class);
        Mockito.verify(mCallback, Mockito.atLeastOnce())
                .onScanningStateChanged(btState.capture());
        assertEquals(BT_SCANNING_END_STATE, (Boolean) btState.getValue());
    }

    public void testNameChangedCallback() {
        final BluetoothDevice bluetoothDevice = null;

        Intent i = new Intent(BluetoothDevice.ACTION_NAME_CHANGED);

        mBluetoothEventManager.mBroadcastReceiver.onReceive(mContext, i);

        Mockito.verify(mCachedBluetoothDeviceManager, Mockito.atLeastOnce())
                .onDeviceNameUpdated(bluetoothDevice);
    }
}
