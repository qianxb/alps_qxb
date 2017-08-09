/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

/*****************************************************************************
*  Copyright Statement:
*  --------------------
*  This software is protected by Copyright and the information contained
*  herein is confidential. The software may not be copied and the information
*  contained herein may not be used or disclosed except with the written
*  permission of MediaTek Inc. (C) 2005
*
*  BY OPENING THIS FILE, BUYER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
*  THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
*  RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO BUYER ON
*  AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
*  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
*  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
*  NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
*  SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
*  SUPPLIED WITH THE MEDIATEK SOFTWARE, AND BUYER AGREES TO LOOK ONLY TO SUCH
*  THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. MEDIATEK SHALL ALSO
*  NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE RELEASES MADE TO BUYER'S
*  SPECIFICATION OR TO CONFORM TO A PARTICULAR STANDARD OR OPEN FORUM.
*
*  BUYER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND CUMULATIVE
*  LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
*  AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
*  OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY BUYER TO
*  MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
*
*  THE TRANSACTION CONTEMPLATED HEREUNDER SHALL BE CONSTRUED IN ACCORDANCE
*  WITH THE LAWS OF THE STATE OF CALIFORNIA, USA, EXCLUDING ITS CONFLICT OF
*  LAWS PRINCIPLES.  ANY DISPUTES, CONTROVERSIES OR CLAIMS ARISING THEREOF AND
*  RELATED THERETO SHALL BE SETTLED BY ARBITRATION IN SAN FRANCISCO, CA, UNDER
*  THE RULES OF THE INTERNATIONAL CHAMBER OF COMMERCE (ICC).
*
*****************************************************************************/
/*******************************************************************************
 *
 * Filename:
 * ---------
 * BluetoothDun.java
 *
 * Project:
 * --------
 *   BT Project
 *
 * Description:
 * ------------
 *   This file is used to provide DUN service access api
 *
 * Author:
 * -------
 * Ting Zheng
 *
 *==============================================================================
 *             HISTORY
 * Below this line, this part is controlled by PVCS VM. DO NOT MODIFY!!
 *------------------------------------------------------------------------------
 * $Revision:
 * $Modtime:
 * $Log:
 *------------------------------------------------------------------------------
 * Upper this line, this part is controlled by PVCS VM. DO NOT MODIFY!!
 *==============================================================================
 *******************************************************************************/
package android.bluetooth;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * @hide
 */
public final class BluetoothDun {
    private static final String TAG = "BluetoothDun";
    private static final boolean DBG = true;
    private static final boolean VDBG = true;

    /**
     * Intent indicating timeout for user confirmation, which is sent to
     * BluetoothDunActivity
     */
    public static final String STATE_CHANGED_ACTION = "android.bluetooth.dun.intent.DUN_STATE";

    /**
     * Intent indicating timeout for user confirmation, which is sent to
     * BluetoothDunActivity
     */
    public static final String EXTRA_STATE = BluetoothProfile.EXTRA_STATE;

    public static final String EXTRA_PREVIOUS_STATE = BluetoothProfile.EXTRA_PREVIOUS_STATE;

    public static final int STATE_DISCONNECTED = BluetoothProfile.STATE_DISCONNECTED;
    public static final int STATE_CONNECTING = BluetoothProfile.STATE_CONNECTING;
    public static final int STATE_CONNECTED = BluetoothProfile.STATE_CONNECTED;
    public static final int STATE_DISCONNECTING = BluetoothProfile.STATE_DISCONNECTING;

    private IBluetoothDun mService = null;
    private Context mContext;
    private ServiceListener mServiceListener;
    private BluetoothAdapter mAdapter;

    private boolean mBindDunService = false;

    /**
     * An interface for notifying Bluetooth PCE IPC clients when they have been
     * connected to the BluetoothDun service.
     */
    public interface ServiceListener {
        /**
         * Called to notify the client when this proxy object has been connected
         * to the BluetoothDun service. Clients must wait for this callback
         * before making IPC calls on the BluetoothDun service.
         */
        void onServiceConnected(BluetoothDun proxy);

        /**
         * Called to notify the client that this proxy object has been
         * disconnected from the BluetoothDun service. Clients must not make IPC
         * calls on the BluetoothDun service after this callback. This callback
         * will currently only occur if the application hosting the BluetoothDun
         * service, but may be called more often in future.
         */
        void onServiceDisconnected();
    }

    private final IBluetoothStateChangeCallback mBluetoothStateChangeCallback =
            new IBluetoothStateChangeCallback.Stub() {
        public void onBluetoothStateChange(boolean up) {
            Log.d(TAG, "onBluetoothStateChange: up=" + up);

            if (!mBindDunService) {
                Log.d(TAG, "DUN is not enabled in project configuration. Ignore BT state change.");
                return;
            }

            if (!up) {
                Log.d(TAG, "Unbinding service...");
                synchronized (mConnection) {
                    try {
                        mService = null;
                        mContext.unbindService(mConnection);
                    } catch (Exception re) {
                        Log.e(TAG, re.toString());
                    }
                }
            } else {
                synchronized (mConnection) {
                    try {
                        if (mService == null) {
                            Log.d(TAG, "Binding service...");
                            doBind();
                        }
                    } catch (Exception re) {
                        Log.e(TAG, re.toString());
                    }
                }
            }
        }
    };

    public BluetoothDun(Context context, ServiceListener l) {
        mContext = context;
        mServiceListener = l;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        IBluetoothManager mgr = mAdapter.getBluetoothManager();
        if (mgr != null) {
            try {
                mgr.registerStateChangeCallback(mBluetoothStateChangeCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "", e);
            }
        }

        String dunEnabled = SystemProperties.get("bt.profiles.dun.enabled");
        if (dunEnabled.isEmpty() || (Integer.parseInt(dunEnabled) == 0)) {
            Log.w(TAG, "bt.profiles.dun.enabled is empty or 0");
            // DUN is not enabled in project configuration. Do not bind
            // DunService
            mBindDunService = false;
        } else {
            mBindDunService = true;
            doBind();
        }
    }

    public BluetoothDun(Context context) {
        mContext = context;
        mServiceListener = null;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        IBluetoothManager mgr = mAdapter.getBluetoothManager();
        if (mgr != null) {
            try {
                mgr.registerStateChangeCallback(mBluetoothStateChangeCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "", e);
            }
        }

        String dunEnabled = SystemProperties.get("bt.profiles.dun.enabled");
        if (dunEnabled.isEmpty() || (Integer.parseInt(dunEnabled) == 0)) {
            Log.w(TAG, "bt.profiles.dun.enabled is empty or 0");
            // DUN is not enabled in project configuration. Do not bind
            // DunService
            mBindDunService = false;
        } else {
            mBindDunService = true;
            doBind();
        }
    }

    boolean doBind() {
        Intent intent = new Intent(IBluetoothDun.class.getName());
        ComponentName comp = intent.resolveSystemService(mContext.getPackageManager(), 0);
        intent.setComponent(comp);
        if (comp == null || !mContext.bindServiceAsUser(intent, mConnection, 0,
                android.os.Process.myUserHandle())) {
            Log.e(TAG, "Could not bind to Bluetooth DUN Service with " + intent);
            return false;
        }
        return true;
    }

    protected void finalize() {
        close();
    }

    /**
     * Close the connection to the backing service. Other public functions of
     * BluetoothDun will return default error results once close() has been
     * called. Multiple invocations of close() are ok.
     */
    public synchronized void close() {
        IBluetoothManager mgr = mAdapter.getBluetoothManager();
        if (mgr != null) {
            try {
                mgr.unregisterStateChangeCallback(mBluetoothStateChangeCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "", e);
            }
        }

        if (mBindDunService) {
            synchronized (mConnection) {
                if (mService != null) {
                    try {
                        mService = null;
                        mContext.unbindService(mConnection);
                    } catch (Exception re) {
                        Log.e(TAG, "", re);
                    }
                }
            }
        }
        mContext = null;
        mServiceListener = null;
    }

    public Set<BluetoothDevice> getConnectedDevices() {
        Log.d(TAG, "getConnectedDevices()");
        HashSet<BluetoothDevice> devices = new HashSet<BluetoothDevice>();
        if (mService != null) {
            BluetoothDevice connDev = null;
            try {
                connDev = mService.dunGetConnectedDevice();
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
            if (connDev != null) {
                devices.add(connDev);
            }
        } else {
            Log.w(TAG, "getConnectedDevices error: not attached to DUN service");
        }
        return devices;
    }

    public boolean connect(BluetoothDevice device) {
        return false;
    }

    public boolean disconnect(BluetoothDevice device) {
        if (mService != null) {
            try {
                mService.dunDisconnect();
                return true;
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        } else {
            Log.w(TAG, "disconnect error: not attached to DUN service");
        }
        return false;
    }

    public int getState(BluetoothDevice device) {
        if (mService != null) {
            try {
                Set<BluetoothDevice> remoteDevices = null;

                remoteDevices = getConnectedDevices();

                if (device == null || remoteDevices == null || !remoteDevices.contains(device)) {
                    return BluetoothProfile.STATE_DISCONNECTED;
                }
                return mService.dunGetState();
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            }
        } else {
            Log.w(TAG, "get state error: not attached to DUN service");
        }
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    public void setBluetoothTethering(boolean value) {
        Log.d(TAG, "setBluetoothTethering(" + value + ")");
        if (mService == null) {
            Log.d(TAG, "Service is not ready");
            return;
        }
        try {
            mService.setBluetoothTethering(value);
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(e));
        }
    }

    public boolean isTetheringOn() {
        if (mService == null) {
            Log.d(TAG, "Service is not ready");
            return false;
        }
        try {
            return mService.isTetheringOn();
        } catch (RemoteException e) {
            Log.e(TAG, "Stack:" + Log.getStackTraceString(e));
            return false;
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Proxy object connected");
            mService = IBluetoothDun.Stub.asInterface(service);

            if (mServiceListener != null) {
                mServiceListener.onServiceConnected(BluetoothDun.this);
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "Proxy object disconnected");
            mService = null;
            if (mServiceListener != null) {
                mServiceListener.onServiceDisconnected();
            }
        }
    };
}
