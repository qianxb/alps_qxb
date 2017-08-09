/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.server.connectivity;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.INetworkStatsService;
import android.net.InterfaceConfiguration;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.NetworkState;
import android.net.NetworkUtils;
import android.net.RouteInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.ResultReceiver;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.util.IState;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.MessageUtils;
import com.android.internal.util.Protocol;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.IoThread;
import com.android.server.net.BaseNetworkObserver;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** M: MediaTek imports @{ */
import android.content.res.Configuration;
import android.net.DhcpResults;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.widget.Toast;
import com.google.android.collect.Lists;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.net.SocketException;
/**
 * @hide
 *
 * Timeout
 *
 * TODO - look for parent classes and code sharing
 */
public class Tethering extends BaseNetworkObserver {

    private final Context mContext;
    private final static String TAG = "Tethering";
    private final static boolean DBG = true;
    private final static boolean VDBG = true;

    private static final Class[] messageClasses = {
            Tethering.class, TetherMasterSM.class, TetherInterfaceSM.class
    };
    private static final SparseArray<String> sMagicDecoderRing =
            MessageUtils.findMessageNames(messageClasses);

    // TODO - remove both of these - should be part of interface inspection/selection stuff
    private String[] mTetherableUsbRegexs;
    private String[] mTetherableWifiRegexs;
    private String[] mTetherableBluetoothRegexs;
    private Collection<Integer> mUpstreamIfaceTypes;

    // used to synchronize public access to members
    private final Object mPublicSync;

    private static final Integer MOBILE_TYPE = new Integer(ConnectivityManager.TYPE_MOBILE);
    private static final Integer HIPRI_TYPE = new Integer(ConnectivityManager.TYPE_MOBILE_HIPRI);
    private static final Integer DUN_TYPE = new Integer(ConnectivityManager.TYPE_MOBILE_DUN);

    // if we have to connect to mobile, what APN type should we use?  Calculated by examining the
    // upstream type list and the DUN_REQUIRED secure-setting
    private int mPreferredUpstreamMobileApn = ConnectivityManager.TYPE_NONE;

    private final INetworkManagementService mNMService;
    private final INetworkStatsService mStatsService;
    private final Looper mLooper;

    private HashMap<String, TetherInterfaceSM> mIfaces; // all tethered/tetherable ifaces

    private BroadcastReceiver mStateReceiver;

    // {@link ComponentName} of the Service used to run tether provisioning.
    private static final ComponentName TETHER_SERVICE = ComponentName.unflattenFromString(Resources
            .getSystem().getString(com.android.internal.R.string.config_wifi_tether_enable));

    private static final String USB_NEAR_IFACE_ADDR      = "192.168.42.129";
    private static final int USB_PREFIX_LENGTH        = 24;

    // USB is  192.168.42.1 and 255.255.255.0
    // Wifi is 192.168.43.1 and 255.255.255.0
    // BT is limited to max default of 5 connections. 192.168.44.1 to 192.168.48.1
    // with 255.255.255.0
    // P2P is 192.168.49.1 and 255.255.255.0

    private String[] mDhcpRange;
    private static final String[] DHCP_DEFAULT_RANGE = {
        "192.168.42.2", "192.168.42.254", "192.168.43.2", "192.168.43.254",
        "192.168.44.2", "192.168.44.254", "192.168.45.2", "192.168.45.254",
        "192.168.46.2", "192.168.46.254", "192.168.47.2", "192.168.47.254",
        "192.168.48.2", "192.168.48.254", "192.168.49.2", "192.168.49.254",
    };

    private String[] mDefaultDnsServers;
    private static final String DNS_DEFAULT_SERVER1 = "8.8.8.8";
    private static final String DNS_DEFAULT_SERVER2 = "8.8.4.4";

    private final StateMachine mTetherMasterSM;
    private final UpstreamNetworkMonitor mUpstreamNetworkMonitor;
    private String mCurrentUpstreamIface;

    /** M: for bug solving, ALPS00331223 */
    private boolean mUnTetherDone = true;
    private boolean mTetherDone = true;
    private boolean mTetheredFail = false;

    private Notification.Builder mTetheredNotificationBuilder;
    private int mLastNotificationId;

    private boolean mRndisEnabled;       // track the RNDIS function enabled state
    private boolean mUsbTetherRequested; // true if USB tethering should be started
                                         // when RNDIS is enabled
    /** M: ALPS00233672 track the UI USB Tethering State (record) */
    private boolean mUsbTetherEnabled = false;

    private String mWifiIface;
    private boolean mIsTetheringChangeDone = true;

    ///M: ALPS00433208 JE due to race condition
    private Object  mNotificationSync;

    /** M: ipv6 tethering @{ */
    private StateMachine mIpv6TetherMasterSM;
    private boolean mIpv6FeatureEnable;
    private static final String MASTERSM_IPV4 = "TetherMaster";
    private static final String MASTERSM_IPV6 = "Ipv6TetherMaster";
    /** @} */

    /// M: For automatic NS-IOT test
    public static final String ACTION_ENABLE_NSIOT_TESTING =
            "android.intent.action.ACTION_ENABLE_NSIOT_TESTING";
    public static final String EXTRA_NSIOT_ENABLED =
            "nsiot_enabled";
    public static final String EXTRA_NSIOT_IP_ADDR =
            "nsiot_ip_addr";
    public static final String SYSTEM_PROPERTY_NSIOT_PENDING =
            "net.nsiot_pending";

    private boolean mBspPackage;
    private boolean mMtkTetheringEemSupport;
    private boolean mTetheringIpv6Support;
    private boolean mIpv6TetherPdModeSupport;

    public Tethering(Context context, INetworkManagementService nmService,
            INetworkStatsService statsService) {
        mContext = context;
        mNMService = nmService;
        mStatsService = statsService;

        mPublicSync = new Object();

        mIfaces = new HashMap<String, TetherInterfaceSM>();

        // make our own thread so we don't anr the system
        mLooper = IoThread.get().getLooper();
        mTetherMasterSM = new TetherMasterSM("TetherMaster", mLooper);
        mTetherMasterSM.start();

        mUpstreamNetworkMonitor = new UpstreamNetworkMonitor();

        mBspPackage = SystemProperties.getBoolean("ro.mtk_bsp_package", false);
        Log.d(TAG, "mBspPackage: " + mBspPackage);
        mMtkTetheringEemSupport = SystemProperties.getBoolean("ro.mtk_tethering_eem_support", false);
        Log.d(TAG, "mMtkTetheringEemSupport: " + mMtkTetheringEemSupport);
        mTetheringIpv6Support = SystemProperties.getBoolean("ro.mtk_tetheringipv6_support", false);
        Log.d(TAG, "mTetheringIpv6Support: " + mTetheringIpv6Support);
        mIpv6TetherPdModeSupport = SystemProperties.getBoolean("ro.mtk_ipv6_tether_pd_mode", false);
        Log.d(TAG, "mIpv6TetherPdModeSupport: " + mIpv6TetherPdModeSupport);

        /** M: ipv6 tethering @{ */
        if (isTetheringIpv6Support()) {
              mIpv6TetherMasterSM = new TetherMasterSM(MASTERSM_IPV6, mLooper);
              mIpv6TetherMasterSM.start();
        }
        /** @} */

        mStateReceiver = new StateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_STATE);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        /// M: For automatic NS-IOT test
        filter.addAction(ACTION_ENABLE_NSIOT_TESTING);
        mContext.registerReceiver(mStateReceiver, filter);
        mNotificationSync = new Object();

        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_SHARED);
        filter.addAction(Intent.ACTION_MEDIA_UNSHARED);
        filter.addDataScheme("file");
        mContext.registerReceiver(mStateReceiver, filter);

        mDhcpRange = context.getResources().getStringArray(
                com.android.internal.R.array.config_tether_dhcp_range);
        if ((mDhcpRange.length == 0) || (mDhcpRange.length % 2 ==1)) {
            mDhcpRange = DHCP_DEFAULT_RANGE;
        }

        // load device config info
        updateConfiguration();

        // TODO - remove and rely on real notifications of the current iface
        mDefaultDnsServers = new String[2];
        mDefaultDnsServers[0] = DNS_DEFAULT_SERVER1;
        mDefaultDnsServers[1] = DNS_DEFAULT_SERVER2;

        mWifiIface = SystemProperties.get("wifi.interface", "wlan0");

        /// M: For automatic NS-IOT test
        SystemProperties.set(SYSTEM_PROPERTY_NSIOT_PENDING, "false");
    }

    // We can't do this once in the Tethering() constructor and cache the value, because the
    // CONNECTIVITY_SERVICE is registered only after the Tethering() constructor has completed.
    private ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    void updateConfiguration() {
        String[] tetherableUsbRegexs = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_tether_usb_regexs);
        String[] tetherableWifiRegexs = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_tether_wifi_regexs);
        String[] tetherableBluetoothRegexs = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_tether_bluetooth_regexs);

        int ifaceTypes[] = null;

        try {
            final TelephonyManager mTelephonyManager = TelephonyManager.getDefault();
            final int subId = SubscriptionManager.getDefaultDataSubscriptionId();
            String sMccMnc = null;
            String sMcc;
            String sMnc;
            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                sMccMnc = mTelephonyManager.getSimOperator(subId);
            }

            if (sMccMnc == null || sMccMnc.length() < 5) {
                Log.e(TAG, "updateConfiguration: wrong MCCMNC =" + sMccMnc);
            } else {
                Log.i(TAG, "updateConfiguration: MCCMNC =" + sMccMnc);
                sMcc = sMccMnc.substring(0, 3);
                sMnc = sMccMnc.substring(3, sMccMnc.length());
                int mcc = Integer.parseInt(sMcc);
                int mnc = Integer.parseInt(sMnc);

                Resources res = getResourcesUsingMccMnc(mContext, mcc, mnc);
                if (res != null) {
                    ifaceTypes = res.getIntArray(
                    com.android.internal.R.array.config_tether_upstream_types);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (ifaceTypes == null) {
            Log.i(TAG, "ifaceTypes = null, use default");
            ifaceTypes = mContext.getResources().getIntArray(
                com.android.internal.R.array.config_tether_upstream_types);
        }
        Collection<Integer> upstreamIfaceTypes = new ArrayList();
        for (int i : ifaceTypes) {
            if (VDBG) Log.i(TAG, "upstreamIfaceTypes.add:" + i);
            upstreamIfaceTypes.add(new Integer(i));
        }

        synchronized (mPublicSync) {
            mTetherableUsbRegexs = tetherableUsbRegexs;
            mTetherableWifiRegexs = tetherableWifiRegexs;
            mTetherableBluetoothRegexs = tetherableBluetoothRegexs;
            mUpstreamIfaceTypes = upstreamIfaceTypes;
        }

        // check if the upstream type list needs to be modified due to secure-settings
        checkDunRequired();

        /** M: ipv6 tethering @{ */
        if (isTetheringIpv6Support()) {
            mIpv6FeatureEnable = readIpv6FeatureEnable();
        }
        /** @} */
    }

    @Override
    public void interfaceStatusChanged(String iface, boolean up) {
        // Never called directly: only called from interfaceLinkStateChanged.
        // See NetlinkHandler.cpp:71.
        if (VDBG) Log.d(TAG, "interfaceStatusChanged " + iface + ", " + up);
        boolean found = false;
        boolean usb = false;
        synchronized (mPublicSync) {
            if (isWifi(iface)) {
                found = true;
            } else if (isUsb(iface)) {
                found = true;
                usb = true;
            } else if (isBluetooth(iface)) {
                found = true;
            }
            if (found == false) return;

            TetherInterfaceSM sm = mIfaces.get(iface);
            if (up) {
                if (sm == null) {
                    sm = new TetherInterfaceSM(iface, mLooper, usb);
                    mIfaces.put(iface, sm);
                    sm.start();
                }
            } else {
                // ignore usb0 down after enabling RNDIS
                // we will handle disconnect in interfaceRemoved instead
                /** M: ignore btn0 down event as well */
                if (isUsb(iface) || isBluetooth(iface)) {
                    Log.d(TAG, "ignore interface down for " + iface);
                } else if (sm != null) {
                    Log.d(TAG, "interfaceLinkStatusChanged, sm!=null, sendMessage:CMD_INTERFACE_DOWN");
                    sm.sendMessage(TetherInterfaceSM.CMD_INTERFACE_DOWN);
                    mIfaces.remove(iface);
                }
            }
        }
    }

    @Override
    public void interfaceLinkStateChanged(String iface, boolean up) {
        if (VDBG) Log.d(TAG, "interfaceLinkStateChanged " + iface + ", " + up);
        interfaceStatusChanged(iface, up);
    }

    private boolean isUsb(String iface) {
        synchronized (mPublicSync) {
            for (String regex : mTetherableUsbRegexs) {
                if (iface.matches(regex)) return true;
            }
            return false;
        }
    }

    public boolean isWifi(String iface) {
        synchronized (mPublicSync) {
            for (String regex : mTetherableWifiRegexs) {
                if (iface.matches(regex)) return true;
            }
            return false;
        }
    }

    public boolean isBluetooth(String iface) {
        synchronized (mPublicSync) {
            for (String regex : mTetherableBluetoothRegexs) {
                if (iface.matches(regex)) return true;
            }
            return false;
        }
    }

    @Override
    public void interfaceAdded(String iface) {
        if (VDBG) Log.d(TAG, "interfaceAdded " + iface);
        boolean found = false;
        boolean usb = false;
        synchronized (mPublicSync) {
            if (isWifi(iface)) {
                found = true;
            }
            if (isUsb(iface)) {
                found = true;
                usb = true;
            }
            if (isBluetooth(iface)) {
                found = true;
            }
            if (found == false) {
                if (VDBG) Log.d(TAG, iface + " is not a tetherable iface, ignoring");
                return;
            }

            TetherInterfaceSM sm = mIfaces.get(iface);
            if (sm != null) {
                if (VDBG) Log.d(TAG, "active iface (" + iface + ") reported as added, ignoring");
                return;
            }

            sm = new TetherInterfaceSM(iface, mLooper, usb);
            mIfaces.put(iface, sm);
            sm.start();
        }
        Log.d(TAG, "interfaceAdded :" + iface);
    }

    @Override
    public void interfaceRemoved(String iface) {
        if (VDBG) Log.d(TAG, "interfaceRemoved " + iface);
        synchronized (mPublicSync) {
            TetherInterfaceSM sm = mIfaces.get(iface);
            if (sm == null) {
                if (VDBG) {
                    Log.e(TAG, "attempting to remove unknown iface (" + iface + "), ignoring");
                }
                return;
            }
            Log.d(TAG, "interfaceRemoved, iface=" + iface + ", sendMessage:CMD_INTERFACE_DOWN");
            sm.sendMessage(TetherInterfaceSM.CMD_INTERFACE_DOWN);
            mIfaces.remove(iface);
        }
    }

    public void startTethering(int type, ResultReceiver receiver,
            boolean showProvisioningUi) {
        Log.d(TAG, "startTethering:" + type);
        if (!isTetherProvisioningRequired()) {
            Log.d(TAG, "Not TetherProvisioningRequired");
            enableTetheringInternal(type, true, receiver);
            return;
        }
        Log.d(TAG, "TetherProvisioningRequired");
        if (showProvisioningUi) {
            runUiTetherProvisioningAndEnable(type, receiver);
        } else {
            runSilentTetherProvisioningAndEnable(type, receiver);
        }
    }

    public void stopTethering(int type) {
        Log.d(TAG, "stopTethering:" + type);
        enableTetheringInternal(type, false, null);
        if (isTetherProvisioningRequired()) {
            cancelTetherProvisioningRechecks(type);
        }
    }
    /** M: ipv6 tethering. @{ */
    @Override
    public void addressUpdated(String iface, LinkAddress address) {
        if (VDBG) { Log.i(TAG, "addressUpdated " + iface + ", " + address); }
        if (address.getAddress() instanceof Inet6Address && address.isGlobalPreferred()  &&
            isIpv6MasterSmOn()) {
            mIpv6TetherMasterSM.sendMessage(TetherMasterSM.CMD_UPSTREAM_CHANGED);
        }
    }

    @Override
    public void addressRemoved(String iface, LinkAddress address) {
        if (VDBG) { Log.i(TAG, "addressRemoved " + iface + ", " + address); }
        if (address.getAddress() instanceof Inet6Address && address.isGlobalPreferred()  &&
            isIpv6MasterSmOn()) {
            mIpv6TetherMasterSM.sendMessage(TetherMasterSM.CMD_UPSTREAM_CHANGED);
        }
    }
    /** @} */


    /**
     * Check if the device requires a provisioning check in order to enable tethering.
     *
     * @return a boolean - {@code true} indicating tether provisioning is required by the carrier.
     */
    private boolean isTetherProvisioningRequired() {
        String[] provisionApp = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_mobile_hotspot_provision_app);
        if (SystemProperties.getBoolean("net.tethering.noprovisioning", false)
                || provisionApp == null) {
            return false;
        }

        // Check carrier config for entitlement checks
        final CarrierConfigManager configManager = (CarrierConfigManager) mContext
             .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        /// M: check the null config @{
        if (configManager == null || configManager.getConfig() == null) {
            return false;
        }
        /// @}
        boolean isEntitlementCheckRequired = configManager.getConfig().getBoolean(
             CarrierConfigManager.KEY_REQUIRE_ENTITLEMENT_CHECKS_BOOL);

        if (!isEntitlementCheckRequired) {
            return false;
        }
        return (provisionApp.length == 2);
    }

    /**
     * Enables or disables tethering for the given type. This should only be called once
     * provisioning has succeeded or is not necessary. It will also schedule provisioning rechecks
     * for the specified interface.
     */
    private void enableTetheringInternal(int type, boolean enable, ResultReceiver receiver) {
        boolean isProvisioningRequired = isTetherProvisioningRequired();
        Log.d(TAG, "enableTetheringInternal type:" + type + ", enable:" + enable);
        switch (type) {
            case ConnectivityManager.TETHERING_WIFI:
                final WifiManager wifiManager =
                        (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
                if (wifiManager.setWifiApEnabled(null, enable)) {
                    sendTetherResult(receiver, ConnectivityManager.TETHER_ERROR_NO_ERROR);
                    if (enable && isProvisioningRequired) {
                        scheduleProvisioningRechecks(type);
                    }
                } else{
                    sendTetherResult(receiver, ConnectivityManager.TETHER_ERROR_MASTER_ERROR);
                }
                break;
            case ConnectivityManager.TETHERING_USB:
                int result = setUsbTethering(enable);
                if (enable && isProvisioningRequired &&
                        result == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    scheduleProvisioningRechecks(type);
                }
                sendTetherResult(receiver, result);
                break;
            case ConnectivityManager.TETHERING_BLUETOOTH:
                setBluetoothTethering(enable, receiver);
                break;
            default:
                Log.w(TAG, "Invalid tether type.");
                sendTetherResult(receiver, ConnectivityManager.TETHER_ERROR_UNKNOWN_IFACE);
        }
    }

    private void sendTetherResult(ResultReceiver receiver, int result) {
        if (receiver != null) {
            receiver.send(result, null);
        }
    }

    private void setBluetoothTethering(final boolean enable, final ResultReceiver receiver) {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Log.w(TAG, "Tried to enable bluetooth tethering with null or disabled adapter. null: " +
                    (adapter == null));
            sendTetherResult(receiver, ConnectivityManager.TETHER_ERROR_SERVICE_UNAVAIL);
            return;
        }

        adapter.getProfileProxy(mContext, new ServiceListener() {
            @Override
            public void onServiceDisconnected(int profile) { }

            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                ((BluetoothPan) proxy).setBluetoothTethering(enable);
                // TODO: Enabling bluetooth tethering can fail asynchronously here.
                // We should figure out a way to bubble up that failure instead of sending success.
                int result = ((BluetoothPan) proxy).isTetheringOn() == enable ?
                        ConnectivityManager.TETHER_ERROR_NO_ERROR :
                        ConnectivityManager.TETHER_ERROR_MASTER_ERROR;
                sendTetherResult(receiver, result);
                if (enable && isTetherProvisioningRequired()) {
                    scheduleProvisioningRechecks(ConnectivityManager.TETHERING_BLUETOOTH);
                }
                adapter.closeProfileProxy(BluetoothProfile.PAN, proxy);
            }
        }, BluetoothProfile.PAN);
    }

    private void runUiTetherProvisioningAndEnable(int type, ResultReceiver receiver) {
        ResultReceiver proxyReceiver = getProxyReceiver(type, receiver);
        sendUiTetherProvisionIntent(type, proxyReceiver);
    }

    private void sendUiTetherProvisionIntent(int type, ResultReceiver receiver) {
        Intent intent = new Intent(Settings.ACTION_TETHER_PROVISIONING);
        intent.putExtra(ConnectivityManager.EXTRA_ADD_TETHER_TYPE, type);
        intent.putExtra(ConnectivityManager.EXTRA_PROVISION_CALLBACK, receiver);
        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.startActivityAsUser(intent, UserHandle.CURRENT);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /**
     * Creates a proxy {@link ResultReceiver} which enables tethering if the provsioning result is
     * successful before firing back up to the wrapped receiver.
     *
     * @param type The type of tethering being enabled.
     * @param receiver A ResultReceiver which will be called back with an int resultCode.
     * @return The proxy receiver.
     */
    private ResultReceiver getProxyReceiver(final int type, final ResultReceiver receiver) {
        ResultReceiver rr = new ResultReceiver(null) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                // If provisioning is successful, enable tethering, otherwise just send the error.
                if (resultCode == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    enableTetheringInternal(type, true, receiver);
                } else {
                    sendTetherResult(receiver, resultCode);
                }
            }
        };

        // The following is necessary to avoid unmarshalling issues when sending the receiver
        // across processes.
        Parcel parcel = Parcel.obtain();
        rr.writeToParcel(parcel,0);
        parcel.setDataPosition(0);
        ResultReceiver receiverForSending = ResultReceiver.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return receiverForSending;
    }

    private void scheduleProvisioningRechecks(int type) {
        Intent intent = new Intent();
        intent.putExtra(ConnectivityManager.EXTRA_ADD_TETHER_TYPE, type);
        intent.putExtra(ConnectivityManager.EXTRA_SET_ALARM, true);
        intent.setComponent(TETHER_SERVICE);
        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.startServiceAsUser(intent, UserHandle.CURRENT);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void runSilentTetherProvisioningAndEnable(int type, ResultReceiver receiver) {
        ResultReceiver proxyReceiver = getProxyReceiver(type, receiver);
        sendSilentTetherProvisionIntent(type, proxyReceiver);
    }

    private void sendSilentTetherProvisionIntent(int type, ResultReceiver receiver) {
        Intent intent = new Intent();
        intent.putExtra(ConnectivityManager.EXTRA_ADD_TETHER_TYPE, type);
        intent.putExtra(ConnectivityManager.EXTRA_RUN_PROVISION, true);
        intent.putExtra(ConnectivityManager.EXTRA_PROVISION_CALLBACK, receiver);
        intent.setComponent(TETHER_SERVICE);
        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.startServiceAsUser(intent, UserHandle.CURRENT);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void cancelTetherProvisioningRechecks(int type) {
        if (getConnectivityManager().isTetheringSupported()) {
            Intent intent = new Intent();
            intent.putExtra(ConnectivityManager.EXTRA_REM_TETHER_TYPE, type);
            intent.setComponent(TETHER_SERVICE);
            final long ident = Binder.clearCallingIdentity();
            try {
                mContext.startServiceAsUser(intent, UserHandle.CURRENT);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    public int tether(String iface) {
        if (DBG) Log.d(TAG, "Tethering " + iface);
        TetherInterfaceSM sm = null;
        synchronized (mPublicSync) {
            sm = mIfaces.get(iface);
        }
        if (sm == null) {
            Log.e(TAG, "Tried to Tether an unknown iface :" + iface + ", ignoring");
            return ConnectivityManager.TETHER_ERROR_UNKNOWN_IFACE;
        }
        if (!sm.isAvailable() && !sm.isErrored()) {
            Log.e(TAG, "Tried to Tether an unavailable iface :" + iface + ", ignoring");
            return ConnectivityManager.TETHER_ERROR_UNAVAIL_IFACE;
        }
        sm.sendMessage(TetherInterfaceSM.CMD_TETHER_REQUESTED);
        return ConnectivityManager.TETHER_ERROR_NO_ERROR;
    }

    public int untether(String iface) {
        if (DBG) Log.d(TAG, "Untethering " + iface);
        TetherInterfaceSM sm = null;
        synchronized (mPublicSync) {
            sm = mIfaces.get(iface);
        }
        if (sm == null) {
            Log.e(TAG, "Tried to Untether an unknown iface :" + iface + ", ignoring");
            return ConnectivityManager.TETHER_ERROR_UNKNOWN_IFACE;
        }
        if (sm.isErrored()) {
            Log.e(TAG, "Tried to Untethered an errored iface :" + iface + ", ignoring");
            return ConnectivityManager.TETHER_ERROR_UNAVAIL_IFACE;
        }
        sm.sendMessage(TetherInterfaceSM.CMD_TETHER_UNREQUESTED);
        return ConnectivityManager.TETHER_ERROR_NO_ERROR;
    }

    public void untetherAll() {
        if (DBG) Log.d(TAG, "Untethering " + mIfaces);
        for (String iface : mIfaces.keySet()) {
            untether(iface);
        }
    }

    public int getLastTetherError(String iface) {
        TetherInterfaceSM sm = null;
        synchronized (mPublicSync) {
            sm = mIfaces.get(iface);
            if (sm == null) {
                Log.e(TAG, "Tried to getLastTetherError on an unknown iface :" + iface +
                        ", ignoring");
                return ConnectivityManager.TETHER_ERROR_UNKNOWN_IFACE;
            }
            int error = sm.getLastError();
            if ((isBspPackage()) && (isTetheringIpv6Support()) &&
                ((error & 0xf0) == ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR)) {
                return (error & 0x0f);
            }
            else {
                return error;
            }
        }
    }

    // TODO - move all private methods used only by the state machine into the state machine
    // to clarify what needs synchronized protection.
    private void sendTetherStateChangedBroadcast() {
        if (!getConnectivityManager().isTetheringSupported()) return;

        Log.d(TAG, "sendTetherStateChangedBroadcast");

        ArrayList<String> availableList = new ArrayList<String>();
        ArrayList<String> activeList = new ArrayList<String>();
        ArrayList<String> erroredList = new ArrayList<String>();

        boolean wifiTethered = false;
        boolean usbTethered = false;
        boolean bluetoothTethered = false;

        synchronized (mPublicSync) {
            Set ifaces = mIfaces.keySet();
            for (Object iface : ifaces) {
                TetherInterfaceSM sm = mIfaces.get(iface);
                if (sm != null) {
                    if (sm.isErrored()) {
                        Log.d(TAG, "add err");
                        erroredList.add((String)iface);
                    } else if (sm.isAvailable()) {
                        Log.d(TAG, "add avai");
                        availableList.add((String)iface);
                    } else if (sm.isTethered()) {
                        if (isUsb((String)iface)) {
                             Log.d(TAG, "usb isTethered");
                            usbTethered = true;
                        } else if (isWifi((String)iface)) {
                            Log.d(TAG, "wifi isTethered");
                            wifiTethered = true;
                        } else if (isBluetooth((String) iface)) {
                            Log.d(TAG, "bt isTethered");
                            bluetoothTethered = true;
                        }
                        activeList.add((String)iface);
                    }
                }
            }
        }
        Intent broadcast = new Intent(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        broadcast.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING |
                Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        broadcast.putStringArrayListExtra(ConnectivityManager.EXTRA_AVAILABLE_TETHER,
                availableList);
        broadcast.putStringArrayListExtra(ConnectivityManager.EXTRA_ACTIVE_TETHER, activeList);
        broadcast.putStringArrayListExtra(ConnectivityManager.EXTRA_ERRORED_TETHER,
                erroredList);
        /** M: for bug solving, ALPS00331223 */
        broadcast.putExtra("UnTetherDone", mUnTetherDone);
        broadcast.putExtra("TetherDone", mTetherDone);
        broadcast.putExtra("TetherFail", mTetheredFail);

        mContext.sendStickyBroadcastAsUser(broadcast, UserHandle.ALL);
        if (DBG) {
            Log.d(TAG, String.format(
                    "sendTetherStateChangedBroadcast avail=[%s] active=[%s] error=[%s]",
                    TextUtils.join(",", availableList),
                    TextUtils.join(",", activeList),
                    TextUtils.join(",", erroredList)));
        }

        if (usbTethered) {
            if (wifiTethered || bluetoothTethered) {
                showTetheredNotification(com.android.internal.R.drawable.stat_sys_tether_general);
            } else {
                showTetheredNotification(com.android.internal.R.drawable.stat_sys_tether_usb);
            }
        } else if (wifiTethered) {
            if (bluetoothTethered) {
                showTetheredNotification(com.android.internal.R.drawable.stat_sys_tether_general);
            } else {
                /* We now have a status bar icon for WifiTethering, so drop the notification */
                clearTetheredNotification();
            }
        } else if (bluetoothTethered) {
            showTetheredNotification(com.android.internal.R.drawable.stat_sys_tether_bluetooth);
        } else {
            clearTetheredNotification();
        }
    }

    private void showTetheredNotification(int icon) {
        Log.i(TAG, "showTetheredNotification icon:" + icon);
        synchronized (Tethering.this.mNotificationSync) {
             NotificationManager notificationManager =
                (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
             if (notificationManager == null) {
                 return;
             }

             if (mLastNotificationId != 0) {
                 if (mLastNotificationId == icon) {
                     return;
                 }
                 notificationManager.cancelAsUser(null, mLastNotificationId,
                         UserHandle.ALL);
                 mLastNotificationId = 0;
             }

             Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
             intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        PendingIntent pi = PendingIntent.getActivityAsUser(mContext, 0, intent, 0,
                null, UserHandle.CURRENT);

        Resources r = Resources.getSystem();
        CharSequence title = r.getText(com.android.internal.R.string.tethered_notification_title);
        CharSequence message = r.getText(com.android.internal.R.string.
                tethered_notification_message);

        if (mTetheredNotificationBuilder == null) {
            mTetheredNotificationBuilder = new Notification.Builder(mContext);
            mTetheredNotificationBuilder.setWhen(0)
                    .setOngoing(true)
                    .setColor(mContext.getColor(
                            com.android.internal.R.color.system_notification_accent_color))
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setCategory(Notification.CATEGORY_STATUS);
        }
        mTetheredNotificationBuilder.setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pi);
        mLastNotificationId = icon;

        notificationManager.notifyAsUser(null, mLastNotificationId,
                mTetheredNotificationBuilder.build(), UserHandle.ALL);
        }
    }

    private void clearTetheredNotification() {
        Log.i(TAG, "clearTetheredNotification");
        synchronized (Tethering.this.mNotificationSync) {
            NotificationManager notificationManager =
                (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null && mLastNotificationId != 0) {
                notificationManager.cancelAsUser(null, mLastNotificationId,
                        UserHandle.ALL);
                mLastNotificationId = 0;
            }
        }
    }

    private class StateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            Log.i(TAG, "StateReceiver onReceive action:" + action);
            if (action == null) { return; }
            if (action.equals(UsbManager.ACTION_USB_STATE)) {
                synchronized (Tethering.this.mPublicSync) {
                    /** M: for bug solving, ALPS00331223 */
                    boolean usbConfigured = intent.getBooleanExtra(UsbManager.USB_CONFIGURED, false);
                    boolean usbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);

                    /** M: for bug solving, ALPS00233672 */
                    boolean oriRndisEnabled = mRndisEnabled;

                    /** EEM Support */
                    //mRndisEnabled = intent.getBooleanExtra(UsbManager.USB_FUNCTION_RNDIS, false);
                    /// M: @{
                    mRndisEnabled = intent.getBooleanExtra(UsbManager.USB_FUNCTION_RNDIS, false) ||
                        intent.getBooleanExtra(UsbManager.USB_FUNCTION_EEM, false) ;
                    /// @}

                    /** M: for bug solving, ALPS00233672 @{ */
                    Log.i(TAG, "StateReceiver onReceive action synchronized: usbConnected = "
                        + usbConnected +
                        " usbConfigured = " + usbConfigured +
                        ", mRndisEnabled = " + mRndisEnabled +
                        ", mUsbTetherRequested = " + mUsbTetherRequested);

                    Log.i(TAG, "StateReceiver onReceive action synchronized: mUsbTetherEnabled = " + mUsbTetherEnabled);
                    //check that if the UI state is sync with mRndisEnabled state
                    if (!mUsbTetherEnabled)
                    {
                        if (mRndisEnabled && (mRndisEnabled != oriRndisEnabled))
                        {
                            //The state of UI and USB is not synced
                            //The USB tethering is enabled without UI checked
                            //disable the rndis function
                            Log.i(TAG, "StateReceiver onReceive action synchronized: " +
                                "mUsbTetherEnabled = " + mUsbTetherEnabled +
                                ", mRndisEnabled = " + mRndisEnabled
                                + ", oriRndisEnabled = " + oriRndisEnabled);
                            tetherUsb(false);
                            UsbManager usbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
                            usbManager.setCurrentFunction(null);

                            mUsbTetherRequested = false;
                        }
                    } /** @} */
                    // start tethering if we have a request pending
                    /** M: for bug solving, ALPS00331223 */
                    if (usbConnected && mRndisEnabled && mUsbTetherRequested && usbConfigured) {
                        Log.i(TAG, "StateReceiver onReceive action synchronized: usbConnected && mRndisEnabled && mUsbTetherRequested, tetherUsb!! ");
                        tetherUsb(true);
                        /** M: for bug solving, ALPS00233672 */
                        mUsbTetherRequested = false;
                    }
                    /** M: for bug solving, ALPS00233672 */
                    //mUsbTetherRequested = false;
                }
            } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                NetworkInfo networkInfo = (NetworkInfo)intent.getParcelableExtra(
                        ConnectivityManager.EXTRA_NETWORK_INFO);
                if (VDBG) Log.i(TAG, "Tethering got CONNECTIVITY_ACTION, networkInfo:" + networkInfo);
                if (networkInfo != null &&
                        networkInfo.getDetailedState() != NetworkInfo.DetailedState.FAILED) {
                   if (VDBG) Log.d(TAG, "Tethering got CONNECTIVITY_ACTION");
                   mTetherMasterSM.sendMessage(TetherMasterSM.CMD_UPSTREAM_CHANGED);
                   /** M: ipv6 tethering @{ */
                   if (isIpv6MasterSmOn()) {
                       mIpv6TetherMasterSM.sendMessage(TetherMasterSM.CMD_UPSTREAM_CHANGED);
                   }
                } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                    updateConfiguration();
                }
                /** @} */
            } else if (action.equals(ACTION_ENABLE_NSIOT_TESTING)) {
                /// M: For automatic NS-IOT test
                boolean enabled = intent.getBooleanExtra(EXTRA_NSIOT_ENABLED, false);
                String ipAddr = intent.getStringExtra(EXTRA_NSIOT_IP_ADDR);
                Log.e(TAG, "[NS-IOT]Receieve ACTION_ENABLE_NSIOT_TESTING:" + EXTRA_NSIOT_ENABLED
                    + " = " + enabled
                    + "," + EXTRA_NSIOT_IP_ADDR + " = " + ipAddr);
                SystemProperties.set(SYSTEM_PROPERTY_NSIOT_PENDING, "true");
                enableUdpForwardingForUsb(enabled, ipAddr);
            }
            /** @} */
        }
    }

    private void tetherUsb(boolean enable) {
        if (VDBG) Log.d(TAG, "tetherUsb " + enable);

        String[] ifaces = new String[0];
        try {
            ifaces = mNMService.listInterfaces();
        } catch (Exception e) {
            Log.e(TAG, "Error listing Interfaces", e);
            return;
        }
        for (String iface : ifaces) {
           if (isUsb(iface)) {
                int result = (enable ? tether(iface) : untether(iface));
                if (result == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    return;
                }
            }
        }

        /** M: for bug solving, ALPS00331223 */
        mTetheredFail = true ;
        SystemClock.sleep(500);
        sendTetherStateChangedBroadcast() ;

        Log.e(TAG, "unable start or stop USB tethering");
    }

    // configured when we start tethering and unconfig'd on error or conclusion
    private boolean configureUsbIface(boolean enabled) {
        if (VDBG) Log.d(TAG, "configureUsbIface(" + enabled + ")");

        // toggle the USB interfaces
        String[] ifaces = new String[0];
        try {
            ifaces = mNMService.listInterfaces();
        } catch (Exception e) {
            Log.e(TAG, "Error listing Interfaces", e);
            return false;
        }
        for (String iface : ifaces) {
            if (isUsb(iface)) {
                InterfaceConfiguration ifcg = null;
                try {
                    ifcg = mNMService.getInterfaceConfig(iface);
                    if (ifcg != null) {
                        InetAddress addr = NetworkUtils.numericToInetAddress(USB_NEAR_IFACE_ADDR);
                        ifcg.setLinkAddress(new LinkAddress(addr, USB_PREFIX_LENGTH));
                        if (enabled) {
                            ifcg.setInterfaceUp();
                        } else {
                            ifcg.setInterfaceDown();
                        }
                        ifcg.clearFlag("running");
                        mNMService.setInterfaceConfig(iface, ifcg);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error configuring interface " + iface, e);
                    return false;
                }
            }
         }

        return true;
    }

    // TODO - return copies so people can't tamper
    public String[] getTetherableUsbRegexs() {
        return mTetherableUsbRegexs;
    }

    public String[] getTetherableWifiRegexs() {
        return mTetherableWifiRegexs;
    }

    public String[] getTetherableBluetoothRegexs() {
        return mTetherableBluetoothRegexs;
    }

    public int setUsbTethering(boolean enable) {
        if (VDBG) Log.d(TAG, "setUsbTethering(" + enable + ")");
         UsbManager usbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        int value ;

        synchronized (mPublicSync) {
            /** M: ALPS00233672 */
            mUsbTetherEnabled = enable;

            /** M: for bug solving, ALPS00331223 */
            mTetheredFail = false ;
            if (enable) {
                mTetherDone = false ;
                if (mRndisEnabled) {
                    final long ident = Binder.clearCallingIdentity();
                    try {
                        tetherUsb(true);
                    } finally {
                        Binder.restoreCallingIdentity(ident);
                    }
                } else {
                    mUsbTetherRequested = true;
                    /// M: @{
                    /** EEM Support */
                    //usbManager.setCurrentFunction(UsbManager.USB_FUNCTION_RNDIS);
                    value = Settings.System.getInt(mContext.getContentResolver()
                            , Settings.System.USB_TETHERING_TYPE
                            , Settings.System.USB_TETHERING_TYPE_DEFAULT);
                    if ((value == Settings.System.USB_TETHERING_TYPE_EEM) && isMtkTetheringEemSupport()) {
                            Log.d(TAG, "The MTK_TETHERING_EEM_SUPPORT is True");
                            usbManager.setCurrentFunction(UsbManager.USB_FUNCTION_EEM);
                    } else {
                            Log.d(TAG, "The MTK_TETHERING_RNDIS only");
                            usbManager.setCurrentFunction(UsbManager.USB_FUNCTION_RNDIS);
                    }
                    /// @}
                }
            } else {
                final long ident = Binder.clearCallingIdentity();
                try {
                    //for tear down request from ConnectivityService
                    mUnTetherDone = false ;
                    tetherUsb(false);
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
                if (mRndisEnabled) {
                    usbManager.setCurrentFunction(null);
                }
                mUsbTetherRequested = false;
            }
        }
        return ConnectivityManager.TETHER_ERROR_NO_ERROR;
    }

    public int[] getUpstreamIfaceTypes() {
        int values[];
        synchronized (mPublicSync) {
            updateConfiguration();  // TODO - remove?
            values = new int[mUpstreamIfaceTypes.size()];
            Iterator<Integer> iterator = mUpstreamIfaceTypes.iterator();
            for (int i=0; i < mUpstreamIfaceTypes.size(); i++) {
                values[i] = iterator.next();
            }
        }
        return values;
    }

    private void checkDunRequired() {
        int secureSetting = 2;
        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            secureSetting = tm.getTetherApnRequired();
        }
        synchronized (mPublicSync) {
            // 2 = not set, 0 = DUN not required, 1 = DUN required
            if (VDBG) Log.i(TAG, "checkDunRequired:" + secureSetting);
            if (secureSetting != 2) {
                int requiredApn = (secureSetting == 1 ?
                        ConnectivityManager.TYPE_MOBILE_DUN :
                        ConnectivityManager.TYPE_MOBILE_HIPRI);
                if (requiredApn == ConnectivityManager.TYPE_MOBILE_DUN) {
                    while (mUpstreamIfaceTypes.contains(MOBILE_TYPE)) {
                        mUpstreamIfaceTypes.remove(MOBILE_TYPE);
                    }
                    while (mUpstreamIfaceTypes.contains(HIPRI_TYPE)) {
                        mUpstreamIfaceTypes.remove(HIPRI_TYPE);
                    }
                    if (mUpstreamIfaceTypes.contains(DUN_TYPE) == false) {
                        mUpstreamIfaceTypes.add(DUN_TYPE);
                    }
                } else {
                    while (mUpstreamIfaceTypes.contains(DUN_TYPE)) {
                        mUpstreamIfaceTypes.remove(DUN_TYPE);
                    }
                    if (mUpstreamIfaceTypes.contains(MOBILE_TYPE) == false) {
                        mUpstreamIfaceTypes.add(MOBILE_TYPE);
                    }
                    if (mUpstreamIfaceTypes.contains(HIPRI_TYPE) == false) {
                        mUpstreamIfaceTypes.add(HIPRI_TYPE);
                    }
                }
            }
            if (mUpstreamIfaceTypes.contains(DUN_TYPE)) {
                mPreferredUpstreamMobileApn = ConnectivityManager.TYPE_MOBILE_DUN;
            } else {
                mPreferredUpstreamMobileApn = ConnectivityManager.TYPE_MOBILE_HIPRI;
            }
            Log.d(TAG, "mPreferredUpstreamMobileApn = " + mPreferredUpstreamMobileApn);
        }
    }

    // TODO review API - maybe return ArrayList<String> here and below?
    public String[] getTetheredIfaces() {
        ArrayList<String> list = new ArrayList<String>();
        synchronized (mPublicSync) {
            Set keys = mIfaces.keySet();
            for (Object key : keys) {
                TetherInterfaceSM sm = mIfaces.get(key);
                if (sm.isTethered()) {
                    list.add((String)key);
                }
            }
        }
        String[] retVal = new String[list.size()];
        for (int i=0; i < list.size(); i++) {
            retVal[i] = list.get(i);
        }
        return retVal;
    }

    /// M: For automatic NS-IOT test
    public String[] getTetheredIfacePairs() {
        final ArrayList<String> list = Lists.newArrayList();
        synchronized (mPublicSync) {
            for (TetherInterfaceSM sm : mIfaces.values()) {
                if (sm.isTethered()) {
                    list.add(sm.mMyUpstreamIfaceName);
                    list.add(sm.mIfaceName);
                    Log.i(TAG, "getTetheredIfacePairs:" + sm.mMyUpstreamIfaceName
                        + ", " + sm.mIfaceName);
                }
            }
        }
        return list.toArray(new String[list.size()]);
    }

    public String[] getTetherableIfaces() {
        ArrayList<String> list = new ArrayList<String>();
        synchronized (mPublicSync) {
            Set keys = mIfaces.keySet();
            for (Object key : keys) {
                TetherInterfaceSM sm = mIfaces.get(key);
                if (sm.isAvailable()) {
                    list.add((String)key);
                }
            }
        }
        String[] retVal = new String[list.size()];
        for (int i=0; i < list.size(); i++) {
            retVal[i] = list.get(i);
        }
        return retVal;
    }

    public String[] getTetheredDhcpRanges() {
        return mDhcpRange;
    }

    public String[] getErroredIfaces() {
        ArrayList<String> list = new ArrayList<String>();
        synchronized (mPublicSync) {
            Set keys = mIfaces.keySet();
            for (Object key : keys) {
                TetherInterfaceSM sm = mIfaces.get(key);
                if (sm.isErrored()) {
                    list.add((String)key);
                }
            }
        }
        String[] retVal = new String[list.size()];
        for (int i= 0; i< list.size(); i++) {
            retVal[i] = list.get(i);
        }
        return retVal;
    }

    private void maybeLogMessage(State state, int what) {
        if (DBG) {
            Log.d(TAG, state.getName() + " got " +
                    sMagicDecoderRing.get(what, Integer.toString(what)));
        }
    }

    //TODO: Temporary handling upstream change triggered without
    //      CONNECTIVITY_ACTION. Only to accomodate interface
    //      switch during HO.
    //      @see bug/4455071
    public void handleTetherIfaceChange() {
        mTetherMasterSM.sendMessage(TetherMasterSM.CMD_UPSTREAM_CHANGED);
        /** M: ipv6 tethering @{ */
        if (isIpv6MasterSmOn()) {
            mIpv6TetherMasterSM.sendMessage(TetherMasterSM.CMD_UPSTREAM_CHANGED); }
        /** @} */
    }

    /** M: ipv6 tethering @{ */
    private boolean isIpv6MasterSmOn() {
        return (isTetheringIpv6Support() && mIpv6FeatureEnable);
    }

    private boolean readIpv6FeatureEnable() {
        int value = Settings.System.getInt(mContext.getContentResolver()
            , Settings.System.TETHER_IPV6_FEATURE, 0);
        Log.d(TAG, "getIpv6FeatureEnable:" + value);
        return (value == 1);
    }

    public boolean getIpv6FeatureEnable() {
        return mIpv6FeatureEnable;
    }

    public void setIpv6FeatureEnable(boolean enable) {
        Log.d(TAG, "setIpv6FeatureEnable:" + enable + " old:" + mIpv6FeatureEnable);
        int value = (enable ? 1 : 0);
        if (mIpv6FeatureEnable != enable) {
            mIpv6FeatureEnable = enable;
            Settings.System.putInt(mContext.getContentResolver()
                , Settings.System.TETHER_IPV6_FEATURE, value);
        }
    }

    private boolean hasIpv6Address(int networkType) {
        if (ConnectivityManager.TYPE_NONE == networkType)
        {
            return false;
        }
        LinkProperties netProperties = getConnectivityManager().getLinkProperties(networkType);
        if (netProperties == null) {
            return false;
        }
        String iface = netProperties.getInterfaceName();
        return hasIpv6Address(iface);

    }

    private boolean hasIpv6Address(String iface) {
        if (iface == null || iface.isEmpty())
            return false;

            String propertyName = "net.ipv6." + iface + ".prefix";
            String value = SystemProperties.get(propertyName);
            if (value == null || value.length() == 0) {
                Log.d(TAG, "This is No IPv6 prefix!");
                return false;
            } else {
                Log.d(TAG, "This is IPv6 prefix: " + value);
                return true;
            }
    }

    private boolean hasIpv4Address(int networkType) {

        if (ConnectivityManager.TYPE_NONE == networkType)
        {
            return false;
        }

        LinkProperties netProperties = getConnectivityManager().getLinkProperties(networkType);
        if (netProperties == null) {
            return false;
        }
        for (LinkAddress l : netProperties.getLinkAddresses()) {
            if (l.getAddress() instanceof Inet4Address) {
                Log.i(TAG, "This is v4 address:" + l.getAddress());
                return true;
            } else {
                Log.i(TAG, "address:" + l.getAddress());
            }
        }
        return false;
    }

    private boolean hasDhcpv6PD(int networkType) {
        if (isIpv6TetherPdModeSupport())
        {
            if (ConnectivityManager.TYPE_NONE == networkType)
                return false;

            LinkProperties netProperties = getConnectivityManager().getLinkProperties(networkType);
            if (netProperties == null) {
                return false;
            }
            String iface = netProperties.getInterfaceName();
            return hasDhcpv6PD(iface);
        }
        else
        {
            Log.e(TAG, "[MSM_TetherModeAlive] bypass hasDhcpv6PD");
            return true;
        }
    }

    private boolean hasDhcpv6PD(String iface) {
        if (isIpv6TetherPdModeSupport())
        {
            if (iface == null || iface.isEmpty())
                return false;

            String propertyName = "net.pd." + iface + ".prefix";
            String value = SystemProperties.get(propertyName);
            if (value == null || value.length() == 0) {
                Log.i(TAG, "This is No Dhcpv6PD prefix!");
                return false;
            } else {
                Log.i(TAG, "This is Dhcpv6PD prefix: " + value);
                return true;
            }
        }
        else
        {
            Log.e(TAG, "[MSM_TetherModeAlive] bypass hasDhcpv6PD");
            return true;
        }
    }

    class TetherInterfaceSM extends StateMachine {
        private static final int BASE_IFACE              = Protocol.BASE_TETHERING + 100;
        // notification from the master SM that it's not in tether mode
        static final int CMD_TETHER_MODE_DEAD            = BASE_IFACE + 1;
        // request from the user that it wants to tether
        static final int CMD_TETHER_REQUESTED            = BASE_IFACE + 2;
        // request from the user that it wants to untether
        static final int CMD_TETHER_UNREQUESTED          = BASE_IFACE + 3;
        // notification that this interface is down
        static final int CMD_INTERFACE_DOWN              = BASE_IFACE + 4;
        // notification that this interface is up
        static final int CMD_INTERFACE_UP                = BASE_IFACE + 5;
        // notification from the master SM that it had an error turning on cellular dun
        static final int CMD_CELL_DUN_ERROR              = BASE_IFACE + 6;
        // notification from the master SM that it had trouble enabling IP Forwarding
        static final int CMD_IP_FORWARDING_ENABLE_ERROR  = BASE_IFACE + 7;
        // notification from the master SM that it had trouble disabling IP Forwarding
        static final int CMD_IP_FORWARDING_DISABLE_ERROR = BASE_IFACE + 8;
        // notification from the master SM that it had trouble starting tethering
        static final int CMD_START_TETHERING_ERROR       = BASE_IFACE + 9;
        // notification from the master SM that it had trouble stopping tethering
        static final int CMD_STOP_TETHERING_ERROR        = BASE_IFACE + 10;
        // notification from the master SM that it had trouble setting the DNS forwarders
        static final int CMD_SET_DNS_FORWARDERS_ERROR    = BASE_IFACE + 11;
        // the upstream connection has changed
        static final int CMD_TETHER_CONNECTION_CHANGED   = BASE_IFACE + 12;

        private State mDefaultState;

        private State mInitialState;
        private State mStartingState;
        private State mTetheredState;

        private State mUnavailableState;

        private boolean mAvailable;
        private boolean mTethered;
        int mLastError;

        String mIfaceName;
        String mMyUpstreamIfaceName;  // may change over time

        /** M: ipv6 tethering */
        String mMyUpstreamIfaceNameIpv6;
        List<InetAddress> mMyUpstreamLP = new ArrayList<InetAddress>();
        List<InetAddress> mMyUpstreamLPIpv6 = new ArrayList<InetAddress>();
        private boolean mDhcpv6Enabled;

        boolean mUsb;

        TetherInterfaceSM(String name, Looper looper, boolean usb) {
            super(name, looper);
            mIfaceName = name;
            mUsb = usb;
            setLastError(ConnectivityManager.TETHER_ERROR_NO_ERROR);

            /** M: ipv6 tethering @{ */
            if (isTetheringIpv6Support())
                setLastError(ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR);
            mDhcpv6Enabled = false;
            /** @} */
            mInitialState = new InitialState();
            addState(mInitialState);
            mStartingState = new StartingState();
            addState(mStartingState);
            mTetheredState = new TetheredState();
            addState(mTetheredState);
            mUnavailableState = new UnavailableState();
            addState(mUnavailableState);

            setInitialState(mInitialState);
        }

        public String toString() {
            String res = new String();
            res += mIfaceName + " - ";
            IState current = getCurrentState();
            if (current == mInitialState) res += "InitialState";
            if (current == mStartingState) res += "StartingState";
            if (current == mTetheredState) res += "TetheredState";
            if (current == mUnavailableState) res += "UnavailableState";
            if (mAvailable) res += " - Available";
            if (mTethered) res += " - Tethered";
            res += " - lastError =" + mLastError;
            return res;
        }

        public int getLastError() {
            synchronized (Tethering.this.mPublicSync) {
                Log.i(TAG, "getLastError:" + mLastError);
                return mLastError;
            }
        }

        private void setLastError(int error) {
            synchronized (Tethering.this.mPublicSync) {
                /** M: ipv6 tethering @{ */
                if (isTetheringIpv6Support()) {
                    if (error >= ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR) {
                        //set error for ipv6 status
                        mLastError &= 0x0f;
                        mLastError |= error;
                    } else {
                        //set error for ipv4 status
                        mLastError &= 0xf0;
                        mLastError |= error;
                    }
                } else {
                /** @} */
                    mLastError = error; }

                Log.i(TAG, "setLastError: " + mLastError);
                if (isErrored()) {
                    if (mUsb) {
                        // note everything's been unwound by this point so nothing to do on
                        // further error..
                        Tethering.this.configureUsbIface(false);
                    }
                }
            }
        }

        public boolean isAvailable() {
            synchronized (Tethering.this.mPublicSync) {
                return mAvailable;
            }
        }

        private void setAvailable(boolean available) {
            synchronized (Tethering.this.mPublicSync) {
                mAvailable = available;
            }
        }

        public boolean isTethered() {
            synchronized (Tethering.this.mPublicSync) {
                return mTethered;
            }
        }

        private void setTethered(boolean tethered) {
            synchronized (Tethering.this.mPublicSync) {
                mTethered = tethered;
            }
        }

        public boolean isErrored() {
            synchronized (Tethering.this.mPublicSync) {
                /** M: ipv6 tethering @{ */
                if (isTetheringIpv6Support()) {
                    boolean ret = ((mLastError & 0x0f) != ConnectivityManager.TETHER_ERROR_NO_ERROR) &&
                                  ((mLastError & 0xf0) != ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR);
                    return ret;
                }
                /** @} */

                return (mLastError != ConnectivityManager.TETHER_ERROR_NO_ERROR);
            }
        }

        class InitialState extends State {
            @Override
            public void enter() {
                Log.i(TAG, "[ISM_Initial] enter, sendTetherStateChangedBroadcast");
                setAvailable(true);
                setTethered(false);
                sendTetherStateChangedBroadcast();
            }

            @Override
            public boolean processMessage(Message message) {
                maybeLogMessage(this, message.what);
                if (DBG) Log.i(TAG, "[ISM_Initial] " + mIfaceName +
                    " processMessage what=" + message.what);
                boolean retValue = true;
                switch (message.what) {
                    case CMD_TETHER_REQUESTED:
                        setLastError(ConnectivityManager.TETHER_ERROR_NO_ERROR);
                        mTetherMasterSM.sendMessage(TetherMasterSM.CMD_TETHER_MODE_REQUESTED,
                                TetherInterfaceSM.this);
                        /** M: ipv6 tethering @{ */
                        if (isTetheringIpv6Support()) {
                            setLastError(ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR);
                            if (mIpv6FeatureEnable) {
                                mIpv6TetherMasterSM.sendMessage(
                                    TetherMasterSM.CMD_TETHER_MODE_REQUESTED,
                                    TetherInterfaceSM.this);
                             }
                        }
                        /** @} */
                        transitionTo(mStartingState);
                        break;
                    case CMD_INTERFACE_DOWN:
                        transitionTo(mUnavailableState);
                        break;
                    default:
                        retValue = false;
                        break;
                }
                return retValue;
            }
        }

        class StartingState extends State {
            @Override
            public void enter() {
                Log.i(TAG, "[ISM_Starting] enter");
                setAvailable(false);
                if (mUsb) {
                    if (!Tethering.this.configureUsbIface(true)) {
                        mTetherMasterSM.sendMessage(TetherMasterSM.CMD_TETHER_MODE_UNREQUESTED,
                                TetherInterfaceSM.this);
                        setLastError(ConnectivityManager.TETHER_ERROR_IFACE_CFG_ERROR);

                        /** M: ipv6 tethering @{ */
                        if (isIpv6MasterSmOn()) {
                            mIpv6TetherMasterSM.sendMessage(
                                TetherMasterSM.CMD_TETHER_MODE_UNREQUESTED,
                                TetherInterfaceSM.this);
                            setLastError(ConnectivityManager.TETHER_ERROR_IPV6_UNAVAIABLE);
                        }
                        /** @} */
                        transitionTo(mInitialState);
                        /** M: for bug solving, ALPS00331223 */
                        mTetherDone = true;
                        sendTetherStateChangedBroadcast();
                        return;
                    }
                }
                Log.i(TAG, "[ISM_Starting] sendTetherStateChangedBroadcast");
                sendTetherStateChangedBroadcast();

                // Skipping StartingState
                transitionTo(mTetheredState);
            }
            @Override
            public boolean processMessage(Message message) {
                maybeLogMessage(this, message.what);
                if (DBG) Log.i(TAG, "[ISM_Starting] " + mIfaceName + " processMessage what="
                    + message.what);
                boolean retValue = true;
                switch (message.what) {
                    // maybe a parent class?
                    case CMD_TETHER_UNREQUESTED:
                        mTetherMasterSM.sendMessage(TetherMasterSM.CMD_TETHER_MODE_UNREQUESTED,
                                TetherInterfaceSM.this);
                        /** M: ipv6 tethering @{ */
                        if (isIpv6MasterSmOn()) {
                            mIpv6TetherMasterSM.sendMessage(
                                TetherMasterSM.CMD_TETHER_MODE_UNREQUESTED,
                                TetherInterfaceSM.this);
                        }
                        /** @} */
                        if (mUsb) {
                            if (!Tethering.this.configureUsbIface(false)) {
                                setLastErrorAndTransitionToInitialState(
                                    ConnectivityManager.TETHER_ERROR_IFACE_CFG_ERROR);
                                /** M: ipv6 tethering @{ */
                                if (isIpv6MasterSmOn())
                                    setLastError(ConnectivityManager.TETHER_ERROR_IPV6_UNAVAIABLE);
                                /** @} */
                                break;
                            }
                        }
                        transitionTo(mInitialState);
                        break;
                    case CMD_CELL_DUN_ERROR:
                    case CMD_IP_FORWARDING_ENABLE_ERROR:
                    case CMD_IP_FORWARDING_DISABLE_ERROR:
                    case CMD_START_TETHERING_ERROR:
                    case CMD_STOP_TETHERING_ERROR:
                    case CMD_SET_DNS_FORWARDERS_ERROR:
                        setLastErrorAndTransitionToInitialState(
                                ConnectivityManager.TETHER_ERROR_MASTER_ERROR);
                        /** M: ipv6 tethering @{ */
                        if (isIpv6MasterSmOn())
                            setLastError(ConnectivityManager.TETHER_ERROR_IPV6_UNAVAIABLE);
                        /** @} */
                        break;
                    case CMD_INTERFACE_DOWN:
                        mTetherMasterSM.sendMessage(TetherMasterSM.CMD_TETHER_MODE_UNREQUESTED,
                                TetherInterfaceSM.this);
                        /** M: ipv6 tethering @{ */
                        if (isIpv6MasterSmOn()) {
                            mIpv6TetherMasterSM.sendMessage(
                                TetherMasterSM.CMD_TETHER_MODE_UNREQUESTED,
                                TetherInterfaceSM.this);
                        }
                        /** @} */
                        transitionTo(mUnavailableState);
                        break;
                    default:
                        retValue = false;
                }
                return retValue;
            }
        }

        class TetheredState extends State {
            @Override
            public void enter() {
                Log.i(TAG, "[ISM_Tethered] enter");
                try {
                    mNMService.tetherInterface(mIfaceName);
                } catch (Exception e) {
                    Log.e(TAG, "[ISM_Tethered] Error Tethering: " + e.toString());
                    setLastError(ConnectivityManager.TETHER_ERROR_TETHER_IFACE_ERROR);
                    try {
                        mNMService.untetherInterface(mIfaceName);
                    } catch (Exception ee) {
                        Log.e(TAG, "Error untethering after failure!" + ee.toString());
                    }
                    transitionTo(mInitialState);
                    return;
                }
                /** M: ipv6 tethering @{ */
                if (isIpv6MasterSmOn()) {
                    try {
                        mNMService.setDhcpv6Enabled(true, mIfaceName);
                        mDhcpv6Enabled = true;
                    } catch (Exception e) {
                        Log.e(TAG, "[ISM_Tethered] Error setDhcpv6Enabled: " + e.toString());
                        setLastError(ConnectivityManager.TETHER_ERROR_IPV6_UNAVAIABLE);
                        try {
                            mDhcpv6Enabled = false;
                            mNMService.untetherInterface(mIfaceName);
                            mNMService.setDhcpv6Enabled(false, mIfaceName);
                        } catch (Exception ee) {
                            Log.e(TAG, "[ISM_Tethered] untetherInterface failed, exception: " + ee);
                        }
                        transitionTo(mInitialState);
                        return;
                    }
                }
                /** @} */
                if (DBG) Log.i(TAG, "[ISM_Tethered] Tethered " + mIfaceName);
                setAvailable(false);
                setTethered(true);
                /** M: for bug solving, ALPS00331223 */
                mTetherDone = true ;
                Log.d(TAG, "[ISM_Tethered] sendTetherStateChangedBroadcast");
                sendTetherStateChangedBroadcast();
            }

            /** M: @{ */
            //ALPS0285766 : Interface down during ISM process CMD_TETHER_MODE_REQUESTED
            //to MSM_Initial got CMD_TETHER_MODE_REQUESTED.
            //When MSM_TetherModeAlive got next REQUESTED command, problem happens.
            @Override
            public void exit() {
                Log.i(TAG, "[ISM_Tethered] exit ,"+
                    "sendMessage CMD_TETHER_MODE_UNREQUESTED to TetherMasterSM");

                mTetherMasterSM.sendMessage(TetherMasterSM.CMD_TETHER_MODE_UNREQUESTED,
                        TetherInterfaceSM.this);
                /** M: ipv6 tethering @{ */
                if (isIpv6MasterSmOn()) {
                    Log.i(TAG, "[ISM_Tethered] exit ,"+
                        "sendMessage CMD_TETHER_MODE_UNREQUESTED to Ipv6TetherMaster");

                    mIpv6TetherMasterSM.sendMessage(
                        TetherMasterSM.CMD_TETHER_MODE_UNREQUESTED,
                        TetherInterfaceSM.this);
                }
                /** @} */
            }
            /** @} */

            private void cleanupUpstream() {
                if (mMyUpstreamIfaceName != null) {
                    // note that we don't care about errors here.
                    // sometimes interfaces are gone before we get
                    // to remove their rules, which generates errors.
                    // just do the best we can.
                    try {
                        // about to tear down NAT; gather remaining statistics
                        mStatsService.forceUpdate();
                    } catch (Exception e) {
                        if (VDBG) Log.e(TAG, "[ISM_Tethered] Exception in forceUpdate: "
                            + e.toString());
                    }
                    try {
                        mNMService.stopInterfaceForwarding(mIfaceName, mMyUpstreamIfaceName);
                    } catch (Exception e) {
                        if (VDBG) Log.e(
                                TAG, "Exception in removeInterfaceForward: " + e.toString());
                    }
                    try {
                        mNMService.disableNat(mIfaceName, mMyUpstreamIfaceName);
                        Log.d(TAG, "[ISM_Tethered] cleanupUpstream disableNat("
                            + mIfaceName + ", " + mMyUpstreamIfaceName + ")");

                        /// M: For automatic NS-IOT test
                        if (isUsb(mIfaceName)) {
                            mNMService.enableUdpForwarding(false, mIfaceName, mMyUpstreamIfaceName, "");
                        }
                    } catch (Exception e) {
                        if (VDBG) Log.e(TAG, "[ISM_Tethered] Exception in disableNat: " + e.toString());
                    }
                    mMyUpstreamIfaceName = null;
                    mMyUpstreamLP.clear();
                }
                return;
            }

            /** M: ipv6 tethering @{ */
            private void cleanupUpstreamIpv6() {
                if (mMyUpstreamIfaceNameIpv6 != null) {
                    try {
                        mNMService.clearRouteIpv6(mIfaceName, mMyUpstreamIfaceNameIpv6);
                        Log.i(TAG, "[ISM_Tethered] cleanupUpstream clearRouteIpv6(" + mIfaceName +
                            ", " + mMyUpstreamIfaceNameIpv6 + ")");

                                mNMService.clearSourceRouteIpv6(mIfaceName,
                                    mMyUpstreamIfaceNameIpv6);
                                Log.i(TAG, "[ISM_Tethered] clearSourceRouteIpv6(" + mIfaceName
                                    + ", " + mMyUpstreamIfaceNameIpv6 + ")");

                    } catch (Exception e) {
                        if (VDBG) Log.e(TAG, "[ISM_Tethered] Exception in clearRouteIpv6: " + e.toString());
                    }
                    mMyUpstreamIfaceNameIpv6 = null;
                    mMyUpstreamLPIpv6.clear();
                }
                return;
            }
            /** @} */

            @Override
            public boolean processMessage(Message message) {
                maybeLogMessage(this, message.what);
                if (DBG) Log.i(TAG, "[ISM_Tethered] " + mIfaceName
                    + " processMessage what=" + message.what);
                boolean retValue = true;
                boolean error = false;
                switch (message.what) {
                    case CMD_TETHER_UNREQUESTED:
                    case CMD_INTERFACE_DOWN:
                        Log.i(TAG, "[ISM_Tethered] mMyUpstreamIfaceName: " + mMyUpstreamIfaceName);
                        cleanupUpstream();
                        /** M: ipv6 tethering @{ */
                        if (isIpv6MasterSmOn())
                            cleanupUpstreamIpv6();
                        /** @} */
                        try {
                            mNMService.untetherInterface(mIfaceName);
                            /** M: disable dhcpv6 @{ */
                            if (isIpv6MasterSmOn() || mDhcpv6Enabled) {
                                mDhcpv6Enabled = false;
                                mNMService.setDhcpv6Enabled(false, mIfaceName);
                                mNMService.disableNatIpv6(mIfaceName, mMyUpstreamIfaceNameIpv6);
                            }
                            /** @} */
                        } catch (Exception e) {
                            setLastErrorAndTransitionToInitialState(
                                    ConnectivityManager.TETHER_ERROR_UNTETHER_IFACE_ERROR);
                            /** M: ipv6 tethering @{ */
                            if (isIpv6MasterSmOn())
                                setLastError(ConnectivityManager.TETHER_ERROR_IPV6_UNAVAIABLE);
                            /** @} */
                            break;
                        }
                        mTetherMasterSM.sendMessage(TetherMasterSM.CMD_TETHER_MODE_UNREQUESTED,
                                TetherInterfaceSM.this);
                        /** M: ipv6 tethering @{ */
                        if (isIpv6MasterSmOn()) {
                            mIpv6TetherMasterSM.sendMessage(TetherMasterSM.CMD_TETHER_MODE_UNREQUESTED,
                                TetherInterfaceSM.this);
                        }
                        /** @} */
                        if (message.what == CMD_TETHER_UNREQUESTED) {
                            if (mUsb) {
                                if (!Tethering.this.configureUsbIface(false)) {
                                    setLastError(
                                            ConnectivityManager.TETHER_ERROR_IFACE_CFG_ERROR);
                                    /** M: ipv6 tethering @{ */
                                    if (isTetheringIpv6Support())
                                        setLastError(ConnectivityManager.TETHER_ERROR_IPV6_UNAVAIABLE);
                                    /** @} */
                                }
                            }
                            transitionTo(mInitialState);
                        } else if (message.what == CMD_INTERFACE_DOWN) {
                            transitionTo(mUnavailableState);
                        }
                        if (DBG) Log.i(TAG, "[ISM_Tethered] Untethered " + mIfaceName);
                        break;
                    case CMD_TETHER_CONNECTION_CHANGED:
                        /** M: ipv6 tethering @{ */
                        String s = (String) (message.obj);
                        String newUpstreamIfaceName = null;
                        List<InetAddress> newUpstreamLP = new ArrayList<InetAddress>();;
                        String smName = null;
                        if (isIpv6MasterSmOn()) {
                            if (s != null) {
                                Log.i(TAG, "[ISM_Tethered] CMD_TETHER_CONNECTION_CHANGED s:" + s);
                                String [] IfaceNameSmNames = s.split(",");
                                if (IfaceNameSmNames.length > 1) {
                                    Log.i(TAG, "[ISM_Tethered] IfaceNameSmNames[0]:"
                                        + IfaceNameSmNames[0] + " IfaceNameSmNames[1]:"
                                        + IfaceNameSmNames[1]);
                                    newUpstreamIfaceName = IfaceNameSmNames[0];
                                    smName = IfaceNameSmNames[1];
                                    if ("empty".equals(newUpstreamIfaceName))
                                        newUpstreamIfaceName = null;
                                }
                            }
                        } else {
                            newUpstreamIfaceName = s;
                        }
                        Log.i(TAG, "[ISM_Tethered:" + smName
                            + "] CMD_TETHER_CONNECTION_CHANGED mMyUpstreamIfaceName: "
                            + mMyUpstreamIfaceName +
                            ", mMyUpstreamIfaceNameIpv6:" + mMyUpstreamIfaceNameIpv6 +
                            ", newUpstreamIfaceName: " + newUpstreamIfaceName);

                        if (newUpstreamIfaceName == null &&
                            isIpv6MasterSmOn() &&
                            MASTERSM_IPV6.equals(smName)) {
                                setLastError(ConnectivityManager.TETHER_ERROR_IPV6_UNAVAIABLE);
                        }

                        if (mPreferredUpstreamMobileApn == ConnectivityManager.TYPE_MOBILE_DUN) {
                            NetworkInterface ni = null;
                            try {
                                if (newUpstreamIfaceName != null)
                                {
                                    ni = NetworkInterface.getByName(newUpstreamIfaceName);
                                }
                            } catch (SocketException e) {
                                Log.e(TAG, "Error NetworkInterface.getByName:", e);
                            } catch (NullPointerException e) {
                                Log.e(TAG, "Error NetworkInterface.getByName:", e);
                            }
                            if (ni != null)
                            {
                                Enumeration<InetAddress> inet_enum = ni.getInetAddresses();
                                List<InetAddress> list = Collections.list(inet_enum);
                                Log.i(TAG, "getInetAddresses newUpstreamLP list: " + list);
                                newUpstreamLP = list;
                                Log.i(TAG, "[ISM_Tethered:" + smName + "] mMyUpstreamLP: " + mMyUpstreamLP);
                                Log.i(TAG, "[ISM_Tethered:" + smName + "] mMyUpstreamLPIpv6: " + mMyUpstreamLPIpv6);
                                Log.i(TAG, "[ISM_Tethered:" + smName + "] newUpstreamLP: " + newUpstreamLP);
                            }
                        }

                        boolean isSameLinkproperty = true;
                        if (smName == null || MASTERSM_IPV4.equals(smName)) {
                            if (mPreferredUpstreamMobileApn == ConnectivityManager.TYPE_MOBILE_DUN) {
                                isSameLinkproperty = (mMyUpstreamLP.size() == newUpstreamLP.size()) ? mMyUpstreamLP.containsAll(newUpstreamLP) : false;
                            }

                            if ((mMyUpstreamIfaceName == null && newUpstreamIfaceName == null) ||
                                    (mMyUpstreamIfaceName != null &&
                                    mMyUpstreamIfaceName.equals(newUpstreamIfaceName) &&
                                    isSameLinkproperty)) {
                                if (VDBG) Log.i(TAG, "[ISM_Tethered] Connection changed noop - dropping");
                                break;
                            }
                        } else if (MASTERSM_IPV6.equals(smName)) {
                            if (mPreferredUpstreamMobileApn == ConnectivityManager.TYPE_MOBILE_DUN) {
                                isSameLinkproperty = (mMyUpstreamLPIpv6.size() == newUpstreamLP.size()) ? mMyUpstreamLPIpv6.containsAll(newUpstreamLP) : false;
                            }
                            if ((mMyUpstreamIfaceNameIpv6 == null && newUpstreamIfaceName == null) ||
                                    (mMyUpstreamIfaceNameIpv6 != null &&
                                    mMyUpstreamIfaceNameIpv6.equals(newUpstreamIfaceName) &&
                                    isSameLinkproperty)) {
                                if (VDBG) Log.i(TAG, "[ISM_Tethered] Connection changed noop - dropping ipv6");
                                break;
                            }
                        }
                        /** @} */
                        /** M: dedicate apn feature for OP03APNSettingExt*/
                        mIsTetheringChangeDone = false;
                        /** M: ipv6 tethering @{ */
                        if (isTetheringIpv6Support()) {
                            if (smName == null || MASTERSM_IPV4.equals(smName))
                                cleanupUpstream();
                            else if (MASTERSM_IPV6.equals(smName))
                                cleanupUpstreamIpv6();
                        } else {
                            cleanupUpstream();
                        }
                        /** @} */

                        /** M: dedicate apn feature @{ */
                        if (mPreferredUpstreamMobileApn == ConnectivityManager.TYPE_MOBILE_DUN) {
                            try {
                                InterfaceConfiguration ifcg = mNMService.getInterfaceConfig(newUpstreamIfaceName);
                                if (ifcg != null && (ifcg.isActive() ||  (ifcg.hasFlag("up") && hasIpv6Address(newUpstreamIfaceName)))) {
                                    Log.i(TAG, "[ISM_Tethered] " + newUpstreamIfaceName + " is up!");
                                } else {
                                    Log.i(TAG, "[ISM_Tethered] " + newUpstreamIfaceName + " is down!");
                                    newUpstreamIfaceName = null;
                                    newUpstreamLP.clear();;
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "[ISM_Tethered] Exception getInterfaceConfig: " + e.toString());
                                newUpstreamIfaceName = null;
                                newUpstreamLP.clear();;
                            }
                        }
                        /** @} */

                        if (newUpstreamIfaceName != null) {
                            try {
                                /** M: ipv6 tethering @{ */
                                if (isTetheringIpv6Support()) {
                                    if (smName == null || MASTERSM_IPV4.equals(smName)) {
                                        mNMService.enableNat(mIfaceName, newUpstreamIfaceName);
                                        mNMService.startInterfaceForwarding(mIfaceName,
                                                newUpstreamIfaceName);
                                        mMyUpstreamIfaceName = newUpstreamIfaceName;
                                        mMyUpstreamLP = newUpstreamLP;
                                        mNMService.setIpForwardingEnabled(true);
                                        Log.i(TAG, "[ISM_Tethered] CMD_TETHER_CONNECTION_CHANGED enableNat for:" + smName + "(" + mIfaceName + ", " + newUpstreamIfaceName + ")");
                                        if (SystemProperties.getBoolean(SYSTEM_PROPERTY_NSIOT_PENDING, false) && isUsb(mIfaceName)) {
                                            enableUdpForwardingForUsb(true, null);
                                        }
                                    } else if (mIpv6FeatureEnable && MASTERSM_IPV6.equals(smName)) {
                                        mNMService.setRouteIpv6(mIfaceName, newUpstreamIfaceName);
                                        mNMService.enableNatIpv6(mIfaceName, newUpstreamIfaceName);
                                        mMyUpstreamIfaceNameIpv6 = newUpstreamIfaceName;
                                        mMyUpstreamLPIpv6 = newUpstreamLP;

                                            mNMService.setSourceRouteIpv6(mIfaceName, newUpstreamIfaceName);

                                        mNMService.setIpv6ForwardingEnabled(true);
                                        Log.i(TAG, "[ISM_Tethered] CMD_TETHER_CONNECTION_CHANGED enableNat for:" + smName + "(" + mIfaceName + ", " + newUpstreamIfaceName + ")");
                                        if (SystemProperties.getBoolean(SYSTEM_PROPERTY_NSIOT_PENDING, false) && isUsb(mIfaceName)) {
                                            enableUdpForwardingForUsb(true, null);
                                        }
                                    }
                                } else {
                                    mNMService.enableNat(mIfaceName, newUpstreamIfaceName);
                                    mNMService.startInterfaceForwarding(mIfaceName,
                                            newUpstreamIfaceName);
                                    mMyUpstreamIfaceName = newUpstreamIfaceName;
                                    mMyUpstreamLP = newUpstreamLP;
                                    mNMService.setIpForwardingEnabled(true);
                                    Log.i(TAG, "[ISM_Tethered] CMD_TETHER_CONNECTION_CHANGED enableNat("
                                        + mIfaceName + ", " + newUpstreamIfaceName + ")");
                                } /** @} */
                            } catch (Exception e) {
                                Log.e(TAG, "[ISM_Tethered] Exception enabling Nat: " + e.toString());
                                try {
                                    mNMService.disableNat(mIfaceName, newUpstreamIfaceName);
                                } catch (Exception ee) {}
                                try {
                                    mNMService.untetherInterface(mIfaceName);
                                    /** M: disabke dhcpv6 @{ */
                                    if (isIpv6MasterSmOn() || mDhcpv6Enabled) {
                                        mDhcpv6Enabled = false;
                                        mNMService.setDhcpv6Enabled(false, mIfaceName);
                                        mNMService.disableNatIpv6(mIfaceName, mMyUpstreamIfaceNameIpv6);
                                    }
                                    /** @} */
                                } catch (Exception ee) {
                                    Log.e(TAG, "[ISM_Tethered] untetherInterface failed, exception: " + ee);
                                }
                                /** M: ipv6 tethering @{ */
                                if (isIpv6MasterSmOn()) {
                                    setLastError(ConnectivityManager.TETHER_ERROR_IPV6_UNAVAIABLE);
                                }
                                /// M: fix bug, enableNat ok but enableNatIpv6 fail case (vice versa)
                                cleanupUpstream();
                                cleanupUpstreamIpv6();
                                /** @} */
                                setLastError(ConnectivityManager.TETHER_ERROR_ENABLE_NAT_ERROR);
                                transitionTo(mInitialState);
                                /** M: dedicate apn feature for OP03APNSettingExt*/
                                if (mPreferredUpstreamMobileApn == ConnectivityManager.TYPE_MOBILE_DUN) {
                                    mContext.sendBroadcastAsUser(
                                        new Intent(ConnectivityManager.TETHER_CHANGED_DONE_ACTION),
                                        UserHandle.ALL);
                                }
                                mIsTetheringChangeDone = true;
                                return true;
                            }
                        } else {
                            try {
                                /** M: ipv6 tethering @{ */
                                if (isIpv6MasterSmOn()) {
                                    if (MASTERSM_IPV4.equals(smName)) {
                                        mNMService.setIpForwardingEnabled(false); }
                                    if (MASTERSM_IPV6.equals(smName)) {
                                        mNMService.setIpv6ForwardingEnabled(false); }
                                } else {
                                /** @} */
                                mNMService.setIpForwardingEnabled(false);
                                }
                            } catch (Exception eee) {
                                Log.e(TAG, "[ISM_Tethered] untetherInterface failed, exception: " + eee);
                            }
                        }
                        /** M: ipv6 tethering @{ */
                        if (isIpv6MasterSmOn()) {
                            if (smName == null || MASTERSM_IPV4.equals(smName)) {
                            if (newUpstreamIfaceName == null) {
                                    Log.i(TAG, "[ISM_Tethered] CMD_TETHER_CONNECTION_CHANGED mMyUpstreamIfaceName = null");
                                mMyUpstreamIfaceName = null;
                                    mMyUpstreamLP.clear();
                                } else {
                                mMyUpstreamIfaceName = newUpstreamIfaceName;
                                    mMyUpstreamLP = newUpstreamLP;
                                }
                            } else if (MASTERSM_IPV6.equals(smName)) {
                                if (newUpstreamIfaceName == null) {
                                    Log.i(TAG, "[ISM_Tethered] CMD_TETHER_CONNECTION_CHANGED mMyUpstreamIfaceNameIpv6 = null");
                                    mMyUpstreamIfaceNameIpv6 = null;
                                    mMyUpstreamLPIpv6.clear();
                                } else {
                                mMyUpstreamIfaceNameIpv6 = newUpstreamIfaceName;
                                    mMyUpstreamLPIpv6 = newUpstreamLP;
                                }
                            }
                        } else { /** @} */
                            mMyUpstreamIfaceName = newUpstreamIfaceName;
                            mMyUpstreamLP = newUpstreamLP;
                        }
                        Log.i(TAG, "[ISM_Tethered] CMD_TETHER_CONNECTION_CHANGED finished!"
                            + smName);
                        /** M: dedicate apn feature for OP03APNSettingExt*/
                        if (mPreferredUpstreamMobileApn == ConnectivityManager.TYPE_MOBILE_DUN) {
                            mContext.sendBroadcastAsUser(
                                new Intent(ConnectivityManager.TETHER_CHANGED_DONE_ACTION),
                                UserHandle.ALL);
                        }
                        mIsTetheringChangeDone = true;

                        /** M: ipv6 tethering @{ */
                        if (isIpv6MasterSmOn() && MASTERSM_IPV6.equals(smName)) {
                            if (mMyUpstreamIfaceNameIpv6 != null) {
                                setLastError(ConnectivityManager.TETHER_ERROR_IPV6_AVAIABLE);
                            } else {
                                setLastError(ConnectivityManager.TETHER_ERROR_IPV6_UNAVAIABLE);
                            }
                                sendTetherStateChangedBroadcast();
                        }
                        /** @} */
                        break;
                    case CMD_CELL_DUN_ERROR:
                    case CMD_IP_FORWARDING_ENABLE_ERROR:
                    case CMD_IP_FORWARDING_DISABLE_ERROR:
                    case CMD_START_TETHERING_ERROR:
                    case CMD_STOP_TETHERING_ERROR:
                    case CMD_SET_DNS_FORWARDERS_ERROR:
                        error = true;
                        // fall through
                    case CMD_TETHER_MODE_DEAD:
                        Log.i(TAG, "[ISM_Tethered] CMD_TETHER_MODE_DEAD, mMyUpstreamIfaceName: "
                            + mMyUpstreamIfaceName);
                        Log.i(TAG, "[ISM_Tethered] CMD_TETHER_MODE_DEAD, mMyUpstreamIfaceNameIpv6: "
                            + mMyUpstreamIfaceNameIpv6);
                        cleanupUpstream();
                        /** M: ipv6 tethering @{ */
                        if (isIpv6MasterSmOn())
                            cleanupUpstreamIpv6();
                        /** @} */
                        try {
                            mNMService.untetherInterface(mIfaceName);
                            /** M: disable dhcpv6 @{ */
                            if (isIpv6MasterSmOn() || mDhcpv6Enabled) {
                                mDhcpv6Enabled = false;
                                mNMService.setDhcpv6Enabled(false, mIfaceName);
                                mNMService.disableNatIpv6(mIfaceName, mMyUpstreamIfaceNameIpv6);
                            }
                            /** @} */
                        } catch (Exception e) {
                            /** M: ipv6 tethering @{ */
                            if (isIpv6MasterSmOn())
                                setLastError(ConnectivityManager.TETHER_ERROR_IPV6_UNAVAIABLE);
                            /** @} */
                            setLastErrorAndTransitionToInitialState(
                                    ConnectivityManager.TETHER_ERROR_UNTETHER_IFACE_ERROR);
                            break;
                        }
                        if (error) {
                            /** M: ipv6 tethering @{ */
                            if (isIpv6MasterSmOn())
                                setLastError(ConnectivityManager.TETHER_ERROR_IPV6_UNAVAIABLE);
                            /** @} */
                            setLastErrorAndTransitionToInitialState(
                                    ConnectivityManager.TETHER_ERROR_MASTER_ERROR);
                            break;
                        }
                        if (DBG) Log.i(TAG, "[ISM_Tethered] Tether lost upstream connection "
                            + mIfaceName);
                        Log.i(TAG, "[ISM_Tethered] sendTetherStateChangedBroadcast in CMD_TETHER_MODE_DEAD of TetheredState");
                        sendTetherStateChangedBroadcast();

                        if (mUsb) {
                            if (!Tethering.this.configureUsbIface(false)) {
                                setLastError(ConnectivityManager.TETHER_ERROR_IFACE_CFG_ERROR);
                                /** M: ipv6 tethering @{ */
                                if (isIpv6MasterSmOn())
                                    setLastError(ConnectivityManager.TETHER_ERROR_IPV6_UNAVAIABLE);
                                /** @} */
                            }
                        }
                        transitionTo(mInitialState);
                        break;
                    default:
                        retValue = false;
                        break;
                }
                return retValue;
            }
        }

        class UnavailableState extends State {
            @Override
            public void enter() {
                Log.i(TAG, "[ISM_Unavailable] enter, sendTetherStateChangedBroadcast");
                setAvailable(false);
                setLastError(ConnectivityManager.TETHER_ERROR_NO_ERROR);
                /** M: ipv6 tethering @{ */
                if (isIpv6MasterSmOn())
                    setLastError(ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR);
                /** @} */
                setTethered(false);
                /** M: for bug solving, ALPS00331223,ALPS00361177 */
                mTetherDone = true ;
                mTetheredFail = true;
                sendTetherStateChangedBroadcast();
                /** @} */
            }
            @Override
            public boolean processMessage(Message message) {
                Log.i(TAG, "[ISM_Unavailable] " + mIfaceName
                    + " processMessage what=" + message.what);
                boolean retValue = true;
                switch (message.what) {
                    case CMD_INTERFACE_UP:
                        transitionTo(mInitialState);
                        break;
                    default:
                        retValue = false;
                        break;
                }
                return retValue;
            }
        }

        void setLastErrorAndTransitionToInitialState(int error) {
            setLastError(error);
            transitionTo(mInitialState);
        }

    }

    /**
     * A NetworkCallback class that relays information of interest to the
     * tethering master state machine thread for subsequent processing.
     */
    class UpstreamNetworkCallback extends NetworkCallback {
        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties newLp) {
            mTetherMasterSM.sendMessage(
                    TetherMasterSM.EVENT_UPSTREAM_LINKPROPERTIES_CHANGED,
                    new NetworkState(null, newLp, null, network, null, null));
        }

        @Override
        public void onLost(Network network) {
            mTetherMasterSM.sendMessage(TetherMasterSM.EVENT_UPSTREAM_LOST, network);
        }
    }

    /**
     * A class to centralize all the network and link properties information
     * pertaining to the current and any potential upstream network.
     *
     * Calling #start() registers two callbacks: one to track the system default
     * network and a second to specifically observe TYPE_MOBILE_DUN networks.
     *
     * The methods and data members of this class are only to be accessed and
     * modified from the tethering master state machine thread. Any other
     * access semantics would necessitate the addition of locking.
     *
     * TODO: Investigate whether more "upstream-specific" logic/functionality
     * could/should be moved here.
     */
    class UpstreamNetworkMonitor {
        final HashMap<Network, NetworkState> mNetworkMap = new HashMap();
        NetworkCallback mDefaultNetworkCallback;
        NetworkCallback mDunTetheringCallback;

        void start() {
            stop();

            mDefaultNetworkCallback = new UpstreamNetworkCallback();
            getConnectivityManager().registerDefaultNetworkCallback(mDefaultNetworkCallback);

            final NetworkRequest dunTetheringRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_DUN)
                    .build();
            mDunTetheringCallback = new UpstreamNetworkCallback();
            getConnectivityManager().registerNetworkCallback(
                    dunTetheringRequest, mDunTetheringCallback);
        }

        void stop() {
            if (mDefaultNetworkCallback != null) {
                getConnectivityManager().unregisterNetworkCallback(mDefaultNetworkCallback);
                mDefaultNetworkCallback = null;
            }

            if (mDunTetheringCallback != null) {
                getConnectivityManager().unregisterNetworkCallback(mDunTetheringCallback);
                mDunTetheringCallback = null;
            }

            mNetworkMap.clear();
        }

        // Returns true if these updated LinkProperties pertain to the current
        // upstream network interface, false otherwise (or if there is not
        // currently any upstream tethering interface).
        boolean processLinkPropertiesChanged(NetworkState networkState) {
            if (networkState == null ||
                    networkState.network == null ||
                    networkState.linkProperties == null) {
                return false;
            }

            mNetworkMap.put(networkState.network, networkState);

            if (mCurrentUpstreamIface != null) {
                for (String ifname : networkState.linkProperties.getAllInterfaceNames()) {
                    if (mCurrentUpstreamIface.equals(ifname)) {
                        return true;
                    }
                }
            }
            return false;
        }

        void processNetworkLost(Network network) {
            if (network != null) {
                mNetworkMap.remove(network);
            }
        }
    }

    class TetherMasterSM extends StateMachine {
        private static final int BASE_MASTER                    = Protocol.BASE_TETHERING;
        // an interface SM has requested Tethering
        static final int CMD_TETHER_MODE_REQUESTED              = BASE_MASTER + 1;
        // an interface SM has unrequested Tethering
        static final int CMD_TETHER_MODE_UNREQUESTED            = BASE_MASTER + 2;
        // upstream connection change - do the right thing
        static final int CMD_UPSTREAM_CHANGED                   = BASE_MASTER + 3;
        // we don't have a valid upstream conn, check again after a delay
        static final int CMD_RETRY_UPSTREAM                     = BASE_MASTER + 4;
        // Events from NetworkCallbacks that we process on the master state
        // machine thread on behalf of the UpstreamNetworkMonitor.
        static final int EVENT_UPSTREAM_LINKPROPERTIES_CHANGED  = BASE_MASTER + 5;
        static final int EVENT_UPSTREAM_LOST                    = BASE_MASTER + 6;

        // This indicates what a timeout event relates to.  A state that
        // sends itself a delayed timeout event and handles incoming timeout events
        // should inc this when it is entered and whenever it sends a new timeout event.
        // We do not flush the old ones.
        private int mSequenceNumber;

        private State mInitialState;
        private State mTetherModeAliveState;

        private State mSetIpForwardingEnabledErrorState;
        private State mSetIpForwardingDisabledErrorState;
        private State mStartTetheringErrorState;
        private State mStopTetheringErrorState;
        private State mSetDnsForwardersErrorState;

        private ArrayList<TetherInterfaceSM> mNotifyList;

        private int mMobileApnReserved = ConnectivityManager.TYPE_NONE;
        private NetworkCallback mMobileUpstreamCallback;

        private static final int UPSTREAM_SETTLE_TIME_MS     = 10000;
        /** M: ipv6 tethering */
        private String mName;

        /** M: MTK_IPV6_TETHER_PD_MODE, Ipv6 Dhcp PD feature enhancement */
        private Thread mDhcpv6PDThread;
        private String mPreviousDhcpv6PDIface;  //have run runDhcpv6PDSequence before

        TetherMasterSM(String name, Looper looper) {
            super(name, looper);
            /** M: ipv6 tethering */
            mName = name;

            //Add states
            mInitialState = new InitialState();
            addState(mInitialState);
            mTetherModeAliveState = new TetherModeAliveState();
            addState(mTetherModeAliveState);

            mSetIpForwardingEnabledErrorState = new SetIpForwardingEnabledErrorState();
            addState(mSetIpForwardingEnabledErrorState);
            mSetIpForwardingDisabledErrorState = new SetIpForwardingDisabledErrorState();
            addState(mSetIpForwardingDisabledErrorState);
            mStartTetheringErrorState = new StartTetheringErrorState();
            addState(mStartTetheringErrorState);
            mStopTetheringErrorState = new StopTetheringErrorState();
            addState(mStopTetheringErrorState);
            mSetDnsForwardersErrorState = new SetDnsForwardersErrorState();
            addState(mSetDnsForwardersErrorState);

            mNotifyList = new ArrayList<TetherInterfaceSM>();
            setInitialState(mInitialState);

            /** M: MTK_IPV6_TETHER_PD_MODE @{ */
            mDhcpv6PDThread = null;
            mPreviousDhcpv6PDIface = null;
        }

        class TetherMasterUtilState extends State {
            @Override
            public boolean processMessage(Message m) {
                return false;
            }

            protected boolean turnOnUpstreamMobileConnection(int apnType) {
                if (apnType == ConnectivityManager.TYPE_NONE) { return false; }

                if (apnType != mMobileApnReserved) {
                    // Unregister any previous mobile upstream callback because
                    // this request, if any, will be different.
                    turnOffUpstreamMobileConnection();
                }

                if (mMobileUpstreamCallback != null) {
                    // Looks like we already filed a request for this apnType.
                    return true;
                }

                switch (apnType) {
                    case ConnectivityManager.TYPE_MOBILE_DUN:
                    case ConnectivityManager.TYPE_MOBILE:
                    case ConnectivityManager.TYPE_MOBILE_HIPRI:
                        boolean isCcpMode = SystemProperties.getBoolean(
                            "persist.op12.ccp.mode", false);
                        if (isCcpMode) {
                            Log.i(TAG ,"isCcpMode enabled, don't enable mobile");
                            return false;
                        }
                        mMobileApnReserved = apnType;
                        break;
                    default:
                        return false;
                }
                Log.i(TAG, "[MSM_TetherModeAlive][" + mName + "] mMobileApnReserved:" + mMobileApnReserved);
                final NetworkRequest.Builder builder = new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
                if (apnType == ConnectivityManager.TYPE_MOBILE_DUN) {
                    builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                           .addCapability(NetworkCapabilities.NET_CAPABILITY_DUN);
                } else {
                    builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                }
                final NetworkRequest mobileUpstreamRequest = builder.build();

                // The UpstreamNetworkMonitor's callback will be notified.
                // Therefore, to avoid duplicate notifications, we only register a no-op.
                mMobileUpstreamCallback = new NetworkCallback();

                // TODO: Change the timeout from 0 (no onUnavailable callback) to use some
                // moderate callback time (once timeout callbacks are implemented). This might
                // be useful for updating some UI. Additionally, we should definitely log a
                // message to aid in any subsequent debugging
                if (DBG) Log.d(TAG, "requesting mobile upstream network: " + mobileUpstreamRequest);
                getConnectivityManager().requestNetwork(
                        mobileUpstreamRequest, mMobileUpstreamCallback, 0, apnType);
                return true;
            }

            protected void turnOffUpstreamMobileConnection() {
                if (mMobileUpstreamCallback != null) {
                    getConnectivityManager().unregisterNetworkCallback(mMobileUpstreamCallback);
                    mMobileUpstreamCallback = null;
                }
                mMobileApnReserved = ConnectivityManager.TYPE_NONE;
            }

            protected boolean turnOnMasterTetherSettings() {
                try {
                    /** M: ipv6 tethering @{ */
                    if (isIpv6MasterSmOn()) {
                        if (MASTERSM_IPV4.equals(mName)) {
                            ///M: Fix for CR ALPS00382764
                            //mNMService.setIpForwardingEnabled(true);
                        }
                        else if (MASTERSM_IPV6.equals(mName)) {
                            mNMService.setIpv6ForwardingEnabled(true); }
                    } else {
                    /** @} */
                        ///M: Fix for CR ALPS00382764
                        //mNMService.setIpForwardingEnabled(true);
                    }
                } catch (Exception e) {
                    transitionTo(mSetIpForwardingEnabledErrorState);
                    return false;
                }
                try {
                    mNMService.startTethering(mDhcpRange);
                } catch (Exception e) {
                    try {
                        mNMService.stopTethering();
                        mNMService.startTethering(mDhcpRange);
                    } catch (Exception ee) {
                        transitionTo(mStartTetheringErrorState);
                        return false;
                    }
                }
                return true;
            }

            protected boolean turnOffMasterTetherSettings() {
                try {
                    mNMService.stopTethering();
                } catch (Exception e) {
                    transitionTo(mStopTetheringErrorState);
                    return false;
                }
                try {
                    /** M: ipv6 tethering @{ */
                    if (isIpv6MasterSmOn()) {
                        if (MASTERSM_IPV4.equals(mName)) {
                            mNMService.setIpForwardingEnabled(false); }
                        else if (MASTERSM_IPV6.equals(mName)) {
                            mNMService.setIpv6ForwardingEnabled(false); }
                    } else {
                    /** @} */
                        mNMService.setIpForwardingEnabled(false);
                    }
                } catch (Exception e) {
                    transitionTo(mSetIpForwardingDisabledErrorState);
                    return false;
                }

                transitionTo(mInitialState);
                return true;
            }

            /** M: dedicated apn feature @{ */
            private boolean checkDataEnabled(int networkType) {
                TelephonyManager tm = TelephonyManager.getDefault();
                boolean dataEnabled = false;

                dataEnabled = tm.getDataEnabled();
                Log.i(TAG, "checkDataEnabled:" + dataEnabled);

                return dataEnabled;
            }
            /** @} */

            protected void chooseUpstreamType(boolean tryCell) {
                int upType = ConnectivityManager.TYPE_NONE;
                String iface = null;
                String ifacePD = null;  //need run runDhcpv6PDSequence()

                updateConfiguration(); // TODO - remove?

                synchronized (mPublicSync) {
                    if (VDBG) {
                        Log.d(TAG, "[" + mName + "]chooseUpstreamType has upstream iface types:");
                        for (Integer netType : mUpstreamIfaceTypes) {
                            Log.d(TAG, " " + netType);
                        }
                    }

                    for (Integer netType : mUpstreamIfaceTypes) {
                        NetworkInfo info =
                            getConnectivityManager().getNetworkInfo(netType.intValue());
                        if ((info != null) && info.isConnected()) {
                            upType = netType.intValue();
                            break;
                        }
                    }
                }

                if (DBG) {
                    Log.d(TAG, "[" + mName + "]chooseUpstreamType(" + tryCell + "),"
                            + " preferredApn="
                            + ConnectivityManager.getNetworkTypeName(mPreferredUpstreamMobileApn)
                            + ", got type="
                            + ConnectivityManager.getNetworkTypeName(upType));
                }
                Log.d(TAG, "pre-checkDataEnabled + " + checkDataEnabled(upType) );
                switch (upType) {
                    case ConnectivityManager.TYPE_MOBILE_DUN:
                    case ConnectivityManager.TYPE_MOBILE_HIPRI:
                        // If we're on DUN, put our own grab on it.
                        if (checkDataEnabled(upType)) {
                            turnOnUpstreamMobileConnection(upType);
                        }
                        break;
                    case ConnectivityManager.TYPE_NONE:
                        if (tryCell &&
                                turnOnUpstreamMobileConnection(mPreferredUpstreamMobileApn)) {
                            // We think mobile should be coming up; don't set a retry.
                        } else {
                            sendMessageDelayed(CMD_RETRY_UPSTREAM, UPSTREAM_SETTLE_TIME_MS);
                        }
                        break;
                    default:
                        /* If we've found an active upstream connection that's not DUN/HIPRI
                         * we should stop any outstanding DUN/HIPRI start requests.
                         *
                         * If we found NONE we don't want to do this as we want any previous
                         * requests to keep trying to bring up something we can use.
                         */
                        turnOffUpstreamMobileConnection();
                        break;
                }


                /** M: ipv6 tethering @{ */
                if (isTetheringIpv6Support() && mName.equals(MASTERSM_IPV6)) {
                    if (mIpv6FeatureEnable) {
                        if (!hasIpv6Address(upType)) {
                            Log.i(TAG, "we have no ipv6 address, upType:" + upType);
                            upType = ConnectivityManager.TYPE_NONE;
                        } else if (isIpv6TetherPdModeSupport()) {
                            LinkProperties linkProperties = getConnectivityManager().getLinkProperties(upType);
                            if (linkProperties != null) {
                                ifacePD = linkProperties.getInterfaceName();
                            }

                            if (ifacePD != null && !hasDhcpv6PD(ifacePD)) {
                                Log.i(TAG, "we have no dhcp ipv6 PD address, iface:" + ifacePD);
                                upType = ConnectivityManager.TYPE_NONE;
                            } else {
                                ifacePD = null;
                            }
                        }
                    }
                }
                /** @} */
                if (mName.equals(MASTERSM_IPV6) && isIpv6TetherPdModeSupport() &&
                    mIpv6FeatureEnable)
                {
                    Log.i(TAG, "mPreviousDhcpv6PDIface:" + mPreviousDhcpv6PDIface
                        + ",ifacePD:" + ifacePD + ",upType:" + upType);
                    //Handle Upstream change or disconnect
                    if (mPreviousDhcpv6PDIface != null &&
                        ((ifacePD != null && ifacePD != mPreviousDhcpv6PDIface)
                        || (ifacePD == null && upType == ConnectivityManager.TYPE_NONE)))
                    {
                        stopDhcpv6PDSequence();
                    }

                    if (ifacePD != null)
                    {
                        runDhcpv6PDSequence(ifacePD);
                        ifacePD = null;
                    }
                }

                if (upType == ConnectivityManager.TYPE_NONE) {
                    //For v4, have done everyting
                    //For v6, do not call extra startusingNetwork!
                    //        Do nothing and will got refresh while upstream changed
                } else {
                    LinkProperties linkProperties =
                            getConnectivityManager().getLinkProperties(upType);
                    if (linkProperties != null) {
                        if (isTetheringIpv6Support() && mName.equals(MASTERSM_IPV6)) {
                            iface = linkProperties.getInterfaceName();
                        } else {
                            // Find the interface with the default IPv4 route. It may be the
                            // interface described by linkProperties, or one of the interfaces
                            // stacked on top of it.
                            Log.i(TAG, "Finding IPv4 upstream interface on: " + linkProperties);
                            RouteInfo ipv4Default = RouteInfo.selectBestRoute(
                                linkProperties.getAllRoutes(), Inet4Address.ANY);
                            if (ipv4Default != null) {
                                iface = ipv4Default.getInterface();
                                Log.i(TAG, "Found interface " + ipv4Default.getInterface());
                            } else {
                                Log.i(TAG, "No IPv4 upstream interface, giving up.");
                            }
                        }
                    }
                    if (iface != null) {
                        Network network = getConnectivityManager().getNetworkForType(upType);
                        if (network == null) {
                            Log.e(TAG, "No Network for upstream type " + upType + "!");
                        }
                        setDnsForwarders(network, linkProperties);
                    }
                }
                notifyTetheredOfNewUpstreamIface(iface);
            }

            protected void setDnsForwarders(final Network network, final LinkProperties lp) {
                String[] dnsServers = mDefaultDnsServers;
                Collection<InetAddress> dnses = lp.getDnsServers();
                // TODO: Properly support the absence of DNS servers.
                if (dnses != null && !dnses.isEmpty()) {
                    //M: support tethering over clatd
                    //M: sort v6 dns first for tethering over clatd
                    Collection<InetAddress> sortedDnses = new ArrayList<InetAddress>();
                    for (InetAddress ia : dnses) {
                        if (ia instanceof Inet6Address) {
                            sortedDnses.add(ia);
                        }
                    }
                    for (InetAddress ia : dnses) {
                        if (ia instanceof Inet4Address) {
                            sortedDnses.add(ia);
                        }
                    }
                    dnses = sortedDnses;
                    // TODO: remove this invocation of NetworkUtils.makeStrings().
                    dnsServers = NetworkUtils.makeStrings(dnses);
                }
                if (VDBG) {
                    Log.d(TAG, "Setting DNS forwarders: Network=" + network +
                           ", dnsServers=" + Arrays.toString(dnsServers));
                }
                try {
                    mNMService.setDnsForwarders(network, dnsServers);
                } catch (Exception e) {
                    // TODO: Investigate how this can fail and what exactly
                    // happens if/when such failures occur.
                    Log.e(TAG, "Setting DNS forwarders failed!");
                    transitionTo(mSetDnsForwardersErrorState);
                }
            }

            protected void notifyTetheredOfNewUpstreamIface(String ifaceName) {
                if (DBG) Log.i(TAG, "[MSM_TetherModeAlive][" + mName +
                    "] Notifying tethered with upstream =" + ifaceName);
                mCurrentUpstreamIface = ifaceName;

                /** M: ipv6 tethering @{ */
                if (isIpv6MasterSmOn()) {
                    if (ifaceName != null) {
                        ifaceName = ifaceName + "," + mName;
                    } else {
                        ifaceName = "empty," + mName;
                    }
                    Log.i(TAG, "notifying tethered with change iface =" + ifaceName);
                }
                /** @} */
                for (TetherInterfaceSM sm : mNotifyList) {
                    sm.sendMessage(TetherInterfaceSM.CMD_TETHER_CONNECTION_CHANGED,
                            ifaceName);
                }
            }

            /** M: MTK_IPV6_TETHER_PD_MODE @{ */
            protected void runDhcpv6PDSequence(String iface) {
                Log.i(TAG, "runDhcpv6PDSequence:" + iface);
                if (mDhcpv6PDThread == null)
                {
                    Log.i(TAG, "mDhcpv6PDThread is null, creating thread");
                    mPreviousDhcpv6PDIface = iface;
                    mDhcpv6PDThread = new Thread(new MyRunDhcpv6PDSequence(iface));
                    mDhcpv6PDThread.start();
                }
                else
                {
                    Log.i(TAG, "mDhcpv6PDThread is not null");
                }
            }

            private class MyRunDhcpv6PDSequence implements Runnable {
                private String mIface = "";

                public MyRunDhcpv6PDSequence(String iface) {
                    mIface = iface;
                }

                public void run() {
                        DhcpResults dhcpResults = new DhcpResults();
                        Log.i(TAG, "runDhcpv6PD:" + mIface);
                        if (!NetworkUtils.runDhcpv6PD(mIface, dhcpResults)) {
                            Log.e(TAG, "Finish runDhcpv6PD request error:"
                                + NetworkUtils.getDhcpv6PDError());
                            stopDhcpv6PDSequence();
                            mDhcpv6PDThread = null;
                            mPreviousDhcpv6PDIface = null;
                            return;
                        }
                       /** M: ipv6 tethering @{ */
                       if (isIpv6MasterSmOn()) {
                           mIpv6TetherMasterSM.sendMessage(TetherMasterSM.CMD_UPSTREAM_CHANGED);
                       }
                       mDhcpv6PDThread = null;
                       Log.i(TAG, "Finish runDhcpv6PD:" + mIface);
                    }
            }
            /** @} */
        }

        private final AtomicInteger mSimBcastGenerationNumber = new AtomicInteger(0);
        private SimChangeBroadcastReceiver mBroadcastReceiver = null;

        private void startListeningForSimChanges() {
            if (DBG) Log.d(TAG, "startListeningForSimChanges");
            if (mBroadcastReceiver == null) {
                mBroadcastReceiver = new SimChangeBroadcastReceiver(
                        mSimBcastGenerationNumber.incrementAndGet());
                final IntentFilter filter = new IntentFilter();
                filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);

                mContext.registerReceiver(mBroadcastReceiver, filter);
            }
        }

        private void stopListeningForSimChanges() {
            if (DBG) Log.d(TAG, "stopListeningForSimChanges");
            if (mBroadcastReceiver != null) {
                mSimBcastGenerationNumber.incrementAndGet();
                mContext.unregisterReceiver(mBroadcastReceiver);
                mBroadcastReceiver = null;
            }
        }

        class SimChangeBroadcastReceiver extends BroadcastReceiver {
            // used to verify this receiver is still current
            final private int mGenerationNumber;

            // we're interested in edge-triggered LOADED notifications, so
            // ignore LOADED unless we saw an ABSENT state first
            private boolean mSimAbsentSeen = false;

            public SimChangeBroadcastReceiver(int generationNumber) {
                super();
                mGenerationNumber = generationNumber;
            }

            @Override
            public void onReceive(Context context, Intent intent) {
                if (DBG) {
                    Log.d(TAG, "simchange mGenerationNumber=" + mGenerationNumber +
                            ", current generationNumber=" + mSimBcastGenerationNumber.get());
                }
                if (mGenerationNumber != mSimBcastGenerationNumber.get()) return;

                final String state =
                        intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);

                Log.d(TAG, "got Sim changed to state " + state + ", mSimAbsentSeen=" +
                        mSimAbsentSeen);
                if (!mSimAbsentSeen && IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(state)) {
                    mSimAbsentSeen = true;
                }

                if (mSimAbsentSeen && IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(state)) {
                    mSimAbsentSeen = false;
                    try {
                        if (mContext.getResources().getString(com.android.internal.R.string.
                                config_mobile_hotspot_provision_app_no_ui).isEmpty() == false) {
                            ArrayList<Integer> tethered = new ArrayList<Integer>();
                            synchronized (mPublicSync) {
                                Set ifaces = mIfaces.keySet();
                                for (Object iface : ifaces) {
                                    TetherInterfaceSM sm = mIfaces.get(iface);
                                    if (sm != null && sm.isTethered()) {
                                        if (isUsb((String)iface)) {
                                            tethered.add(new Integer(
                                                    ConnectivityManager.TETHERING_USB));
                                        } else if (isWifi((String)iface)) {
                                            tethered.add(new Integer(
                                                    ConnectivityManager.TETHERING_WIFI));
                                        } else if (isBluetooth((String)iface)) {
                                            tethered.add(new Integer(
                                                    ConnectivityManager.TETHERING_BLUETOOTH));
                                        }
                                    }
                                }
                            }
                            for (int tetherType : tethered) {
                                Intent startProvIntent = new Intent();
                                startProvIntent.putExtra(
                                        ConnectivityManager.EXTRA_ADD_TETHER_TYPE, tetherType);
                                startProvIntent.putExtra(
                                        ConnectivityManager.EXTRA_RUN_PROVISION, true);
                                startProvIntent.setComponent(TETHER_SERVICE);
                                mContext.startServiceAsUser(startProvIntent, UserHandle.CURRENT);
                            }
                            Log.d(TAG, "re-evaluate provisioning");
                        } else {
                            Log.d(TAG, "no prov-check needed for new SIM");
                        }
                    } catch (Resources.NotFoundException e) {
                        Log.d(TAG, "no prov-check needed for new SIM");
                        // not defined, do nothing
                    }
                }
            }
        }

        class InitialState extends TetherMasterUtilState {
            @Override
            public void enter() {
                Log.i(TAG, "[MSM_Initial][" + mName + "] enter");
            }
            @Override
            public boolean processMessage(Message message) {
                maybeLogMessage(this, message.what);
                if (DBG) Log.d(TAG, "[MSM_Initial][" + mName
                    + "] processMessage what=" + message.what);
                boolean retValue = true;
                switch (message.what) {
                    case CMD_TETHER_MODE_REQUESTED:
                        TetherInterfaceSM who = (TetherInterfaceSM)message.obj;
                        if (VDBG) Log.d(TAG, "[MSM_Initial][" + mName
                            + "] Tether Mode requested by " + who);
                        mNotifyList.add(who);
                        transitionTo(mTetherModeAliveState);
                        break;
                    case CMD_TETHER_MODE_UNREQUESTED:
                        who = (TetherInterfaceSM)message.obj;
                        Log.d(TAG, "[MSM_Initial] CMD_TETHER_MODE_UNREQUESTED ===========>");
                        if (VDBG) Log.d(TAG, "[MSM_Initial][" + mName
                            + "] Tether Mode unrequested by " + who);
                        int index = mNotifyList.indexOf(who);
                        while (index != -1) {
                            mNotifyList.remove(who);
                            index = mNotifyList.indexOf(who);
                        }
                        /** M: for bug solving, ALPS00331223 */
                        if (who.mUsb) {
                            mUnTetherDone = true;
                            Log.i(TAG, "[MSM_Initial] sendTetherStateChangedBroadcast");
                            sendTetherStateChangedBroadcast();
                        }
                        Log.i(TAG, "[MSM_Initial] CMD_TETHER_MODE_UNREQUESTED <===========");
                        break;
                    default:
                        retValue = false;
                        break;
                }
                return retValue;
            }
        }

        class TetherModeAliveState extends TetherMasterUtilState {
            boolean mTryCell = true;
            @Override
            public void enter() {
                Log.i(TAG, "[MSM_TetherModeAlive][" + mName + "] enter");
                // TODO: examine if we should check the return value.
                turnOnMasterTetherSettings(); // may transition us out
                startListeningForSimChanges();
                if (!(isIpv6MasterSmOn() && mName.equals(MASTERSM_IPV4))) {
                    mUpstreamNetworkMonitor.start();
                }
                mTryCell = true;  // better try something first pass or crazy tests cases will fail
                chooseUpstreamType(mTryCell);
                mTryCell = !mTryCell;
            }
            @Override
            public void exit() {
                /** M: ipv6 tethering @{ */
                // TODO: examine if we should check the return value.
                Log.i(TAG, "[MSM_TetherModeAlive][" + mName + "] exit");
                if (isIpv6MasterSmOn()) {
                    if (mName.equals(MASTERSM_IPV4)) {
                        turnOffUpstreamMobileConnection();
                        mUpstreamNetworkMonitor.stop();
                        stopListeningForSimChanges();
                        notifyTetheredOfNewUpstreamIface(null);
                    } else {
                        Log.i(TAG, "[MSM_TetherModeAlive][" + mName + "] skip actions when exit");
                    }
                } else {
                /** @} */
                    turnOffUpstreamMobileConnection();
                    mUpstreamNetworkMonitor.stop();
                    stopListeningForSimChanges();
                    notifyTetheredOfNewUpstreamIface(null);
                }
            }
            @Override
            public boolean processMessage(Message message) {
                maybeLogMessage(this, message.what);
                if (DBG) Log.d(TAG, "[MSM_TetherModeAlive][" + mName + "] processMessage what="
                    + message.what);
                boolean retValue = true;
                switch (message.what) {
                    case CMD_TETHER_MODE_REQUESTED:
                        TetherInterfaceSM who = (TetherInterfaceSM)message.obj;
                        if (VDBG) Log.d(TAG, "Tether Mode requested by " + who);
                        mNotifyList.add(who);
                        /** M: ipv6 tethering @{ */
                        String ifaceName = mCurrentUpstreamIface;
                        if (isIpv6MasterSmOn()) {
                            if (ifaceName != null) {
                                ifaceName = ifaceName + "," + mName;
                            } else {
                                ifaceName = "empty," + mName;
                            }
                            Log.i(TAG, "CMD_TETHER_MODE_REQUESTED with change iface =" + ifaceName);
                        }
                        who.sendMessage(TetherInterfaceSM.CMD_TETHER_CONNECTION_CHANGED, ifaceName);
                        /** @} */
                        break;
                    case CMD_TETHER_MODE_UNREQUESTED:
                        who = (TetherInterfaceSM)message.obj;
                        if (VDBG) Log.d(TAG, "Tether Mode unrequested by " + who);
                        int index = mNotifyList.indexOf(who);
                        if (index != -1) {
                            while (index != -1) {
                                mNotifyList.remove(who);
                                index = mNotifyList.indexOf(who);
                            }
                            if (DBG) Log.d(TAG, "TetherModeAlive removing notifyee " + who);
                            if (mNotifyList.isEmpty()) {
                                if (isIpv6TetherPdModeSupport() && mName.equals(MASTERSM_IPV6))
                                {
                                    stopDhcpv6PDSequence();
                                }
                                Log.i(TAG, "[MSM_TetherModeAlive] CMD_TETHER_MODE_UNREQUESTED is empty");
                                turnOffMasterTetherSettings(); // transitions appropriately
                            } else {
                                if (DBG) {
                                    Log.d(TAG, "TetherModeAlive still has " + mNotifyList.size() +
                                            " live requests:");
                                    for (Object o : mNotifyList) {
                                        Log.d(TAG, "  " + o);
                                    }
                                }
                            }
                        } else {
                           Log.e(TAG, "TetherModeAliveState UNREQUESTED has unknown who: " + who);
                        }
                        /** M: for bug solving, ALPS00331223 */
                        if (who.mUsb) {
                            mUnTetherDone = true;
                            Log.i(TAG, "[MSM_TetherModeAliveState] sendTetherStateChangedBroadcast");
                            sendTetherStateChangedBroadcast();
                        }

                        Log.i(TAG, "[MSM_TetherModeAlive] CMD_TETHER_MODE_UNREQUESTED <==========");
                        break;
                    case CMD_UPSTREAM_CHANGED:
                        // need to try DUN immediately if Wifi goes down
                        mTryCell = true;
                        chooseUpstreamType(mTryCell);
                        mTryCell = !mTryCell;
                        break;
                    case CMD_RETRY_UPSTREAM:
                        chooseUpstreamType(mTryCell);
                        mTryCell = !mTryCell;
                        break;
                    case EVENT_UPSTREAM_LINKPROPERTIES_CHANGED:
                        NetworkState state = (NetworkState) message.obj;
                        if (mUpstreamNetworkMonitor.processLinkPropertiesChanged(state)) {
                            setDnsForwarders(state.network, state.linkProperties);
                        } else if (mCurrentUpstreamIface == null) {
                            // If we have no upstream interface, try to run through upstream
                            // selection again.  If, for example, IPv4 connectivity has shown up
                            // after IPv6 (e.g., 464xlat became available) we want the chance to
                            // notice and act accordingly.
                            chooseUpstreamType(false);
                        }
                        break;
                    case EVENT_UPSTREAM_LOST:
                        // TODO: Re-evaluate possible upstreams. Currently upstream reevaluation
                        // is triggered via received CONNECTIVITY_ACTION broadcasts that result
                        // in being passed a TetherMasterSM.CMD_UPSTREAM_CHANGED.
                        mUpstreamNetworkMonitor.processNetworkLost((Network) message.obj);
                        break;
                    default:
                        retValue = false;
                        break;
                }
                return retValue;
            }
        }

        class ErrorState extends State {
            int mErrorNotification;
            @Override
            public boolean processMessage(Message message) {
                Log.i(TAG, "[MSM_Error][" + mName + "] processMessage what=" + message.what);
                boolean retValue = true;
                switch (message.what) {
                    case CMD_TETHER_MODE_REQUESTED:
                        TetherInterfaceSM who = (TetherInterfaceSM)message.obj;
                        who.sendMessage(mErrorNotification);
                        break;
                    default:
                       retValue = false;
                }
                return retValue;
            }
            void notify(int msgType) {
                mErrorNotification = msgType;
                for (Object o : mNotifyList) {
                    TetherInterfaceSM sm = (TetherInterfaceSM)o;
                    sm.sendMessage(msgType);
                }
            }

        }
        class SetIpForwardingEnabledErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "[MSM_Error][" + mName + "] setIpForwardingEnabled");
                notify(TetherInterfaceSM.CMD_IP_FORWARDING_ENABLE_ERROR);
                /** M: for bug solving */
                transitionTo(mInitialState);
            }
        }

        class SetIpForwardingDisabledErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "[MSM_Error][" + mName + "] setIpForwardingDisabled");
                notify(TetherInterfaceSM.CMD_IP_FORWARDING_DISABLE_ERROR);
                /** M: for bug solving */
                transitionTo(mInitialState);
            }
        }

        class StartTetheringErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "[MSM_Error][" + mName + "] startTethering");
                notify(TetherInterfaceSM.CMD_START_TETHERING_ERROR);
                try {
                    /** M: ipv6 tethering @{ */
                    if (isIpv6MasterSmOn()) {
                        if (MASTERSM_IPV4.equals(mName)) {
                            mNMService.setIpForwardingEnabled(false); }
                        else if (MASTERSM_IPV6.equals(mName)) {
                            mNMService.setIpv6ForwardingEnabled(false);
                            if (isIpv6TetherPdModeSupport() && mName.equals(MASTERSM_IPV6))
                            {
                                stopDhcpv6PDSequence();
                            }
                        }
                    } else {
                    /** @} */
                        mNMService.setIpForwardingEnabled(false);
                    }
                } catch (Exception e) {}
                /** M: for bug solving */
                transitionTo(mInitialState);
            }
        }

        class StopTetheringErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "[MSM_Error][" + mName + "] stopTethering");
                notify(TetherInterfaceSM.CMD_STOP_TETHERING_ERROR);
                try {
                    /** M: ipv6 tethering @{ */
                    if (isIpv6MasterSmOn()) {
                        if (MASTERSM_IPV4.equals(mName)) {
                            mNMService.setIpForwardingEnabled(false); }
                        else if (MASTERSM_IPV6.equals(mName)) {
                            mNMService.setIpv6ForwardingEnabled(false);
                            if (isIpv6TetherPdModeSupport() && mName.equals(MASTERSM_IPV6))
                            {
                                stopDhcpv6PDSequence();
                            }
                        }
                    } else {
                    /** @} */
                        mNMService.setIpForwardingEnabled(false);
                    }
                } catch (Exception e) {}
                /** M: for bug solving */
                transitionTo(mInitialState);
            }
        }

        class SetDnsForwardersErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "[MSM_Error][" + mName + "] setDnsForwarders");
                notify(TetherInterfaceSM.CMD_SET_DNS_FORWARDERS_ERROR);
                try {
                    mNMService.stopTethering();
                } catch (Exception e) {}
                try {
                    /** M: ipv6 tethering @{ */
                    if (isIpv6MasterSmOn()) {
                        if (MASTERSM_IPV4.equals(mName)) {
                            mNMService.setIpForwardingEnabled(false); }
                        if (MASTERSM_IPV6.equals(mName)) {
                            mNMService.setIpv6ForwardingEnabled(false);
                            if (isIpv6TetherPdModeSupport() && mName.equals(MASTERSM_IPV6))
                            {
                                stopDhcpv6PDSequence();
                            }
                        }
                    } else {
                    /** @} */
                        mNMService.setIpForwardingEnabled(false);
                    }
                } catch (Exception e) {}
                /** M: for bug solving */
                transitionTo(mInitialState);
            }
        }

        /** M: MTK_IPV6_TETHER_PD_MODE @{ */
        protected void stopDhcpv6PDSequence() {
            Log.i(TAG, "stopDhcpv6PD:" + mPreviousDhcpv6PDIface);
            if (mPreviousDhcpv6PDIface != null)
            {
                NetworkUtils.stopDhcpv6PD(mPreviousDhcpv6PDIface);
            }
            mPreviousDhcpv6PDIface = null;
            mDhcpv6PDThread = null;
        }
    }

    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");

        if (mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.DUMP) != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump ConnectivityService.Tether " +
                    "from from pid=" + Binder.getCallingPid() + ", uid=" +
                    Binder.getCallingUid());
                    return;
        }

        pw.println("Tethering:");
        pw.increaseIndent();
        pw.print("mUpstreamIfaceTypes:");
        synchronized (mPublicSync) {
            for (Integer netType : mUpstreamIfaceTypes) {
                pw.print(" " + ConnectivityManager.getNetworkTypeName(netType));
            }
            pw.println();

            pw.println("Tether state:");
            pw.increaseIndent();
            //M: Modified for quick debug using dumpsys
            for (TetherInterfaceSM o : mIfaces.values()) {
                pw.println(" " + o);
                //M: Modified for quick debug using dumpsys
                pw.println("  mMyUpstreamIfaceName: " + o.mMyUpstreamIfaceName);
                pw.println("  mMyUpstreamIfaceNameIpv6: " + o.mMyUpstreamIfaceNameIpv6);
            }
            pw.decreaseIndent();
        }
        pw.decreaseIndent();
        return;
    }

    /** M: dedicated apn feature for OP03APNSettingExt
     * @hide
     */
    public boolean isTetheringChangeDone() {
        return mIsTetheringChangeDone;
    }

    /// M: For automatic NS-IOT test
    private boolean enableUdpForwardingForUsb(boolean enabled, String ipAddr) {
        Toast mToast;
        mToast = Toast.makeText(mContext, null, Toast.LENGTH_SHORT);

        String[] tetherInterfaces = getTetheredIfacePairs();
        if (tetherInterfaces.length != 2) {
           Log.e(TAG, "[NS-IOT]Wrong tethering state:" + tetherInterfaces.length);
           mToast.setText("Please only enable one tethering, now:" + tetherInterfaces.length / 2);
           mToast.show();
           return false;
        } else if (tetherInterfaces[0] == null) {
           Log.e(TAG, "[NS-IOT]Upstream is null");
           mToast.setText("[NS-IOT]Upstream is null" + tetherInterfaces.length / 2);
           mToast.show();
           return false;
        }

        String extInterface = tetherInterfaces[0];
        String inInterface = tetherInterfaces[1];

        if (ipAddr == null || ipAddr.length() == 0 || "unknown".equals(ipAddr)) {
            try {
                Log.e(TAG, "[NS-IOT]getUsbClient(" + inInterface);
                mNMService.getUsbClient(inInterface);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "[NS-IOT]getUsbClient failed!");
            }
            String propertyName = "net.rndis.client";
            ipAddr = SystemProperties.get(propertyName);
            if (enabled && (ipAddr == null || ipAddr.length() == 0)) {
                Log.d(TAG, "[NS-IOT]There is no HostPC address!");
                mToast.setText("There is no HostPC address");
                mToast.show();
                return false;
            } else {
                Log.d(TAG, "[NS-IOT]Disable or There is HostPC prefix: " + ipAddr);
            }
        }

        mToast.setText("enableUdpForwarding(" + enabled + "," + inInterface + ","
            + extInterface + "," + ipAddr);
        mToast.show();
        try {
            Log.e(TAG, "[NS-IOT]enableUdpForwarding(" + enabled + "," + inInterface
                + "," + extInterface + "," + ipAddr);
            mNMService.enableUdpForwarding(enabled, inInterface, extInterface, ipAddr);
            mNMService.setMtu(extInterface, 1500);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "[NS-IOT]enableUdpForwarding failed!");
            mToast.setText("enableUdpForwarding failed!");
            mToast.show();
            return false;
        }
    }

    private Resources getResourcesUsingMccMnc(Context context, int mcc, int mnc) {
        try {
            Log.i(TAG, "getResourcesUsingMccMnc: mcc = " + mcc + ", mnc = " + mnc);
            Configuration configuration = new Configuration();
            configuration.mcc = mcc;
            configuration.mnc = mnc;
            Context resc = context.createConfigurationContext(configuration);
            return resc.getResources();

        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.i(TAG, "getResourcesUsingMccMnc fail, return null");
        return null;
    }

    private boolean isMtkTetheringEemSupport() {
        Log.d(TAG, "isMtkTetheringEemSupport: " + mMtkTetheringEemSupport);

        return mMtkTetheringEemSupport;
    }

    private boolean isBspPackage() {
        Log.d(TAG, "isBspPackage: " + mBspPackage);

        return mBspPackage;
    }

    private boolean isTetheringIpv6Support() {
        Log.d(TAG, "isTetheringIpv6Support: " + mTetheringIpv6Support);

        return mTetheringIpv6Support;
    }

    private boolean isIpv6TetherPdModeSupport() {
        Log.d(TAG, "isIpv6TetherPdModeSupport: " + mIpv6TetherPdModeSupport);

        return isTetheringIpv6Support() && mIpv6TetherPdModeSupport;
    }
}
