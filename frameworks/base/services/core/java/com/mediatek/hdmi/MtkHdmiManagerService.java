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

package com.mediatek.hdmi;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioSystem;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.mediatek.internal.R;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * HDMI manager service, used to provide API for APPs to manage HDMI
 */
public final class MtkHdmiManagerService extends IMtkHdmiManager.Stub {
    private static final String TAG = "MtkHdmiService";
    private final Context mContext;
    private final ContentResolver mContentResolver;
    private static final int MSG_INIT = 0;
    private static final int MSG_DEINIT = MSG_INIT + 1;
    private static final int MSG_CABLE_STATE = MSG_INIT + 2;
    private static final int MSG_USER_SWITCH = MSG_INIT + 3;
    // This LID is only used for 8389 talbet project to read hdcp key from
    // nvram, ignore on other projects
    private static final int AP_CFG_RDCL_FILE_HDCP_KEY_LID = 45;
    private static final String ACTION_IPO_BOOT = "android.intent.action.ACTION_BOOT_IPO";
    private static final String ACTION_IPO_SHUTDOWN = "android.intent.action.ACTION_SHUTDOWN_IPO";
    private static final String ACTION_CLEARMOTION_DIMMED =
            "com.mediatek.clearmotion.DIMMED_UPDATE";
    private static final String KEY_CLEARMOTION_DIMMED = "sys.display.clearMotion.dimmed";
    private static final int HDMI_ENABLE_STATUS_DEFAULT = 1;
    private static final int HDMI_VIDEO_RESOLUTION_DEFAULT = 100;
    private static final int HDMI_VIDEO_SCALE_DEFAULT = 0;
    private static final int HDMI_COLOR_SPACE_DEFAULT = 0;
    private static final int HDMI_DEEP_COLOR_DEFAULT = 1;
    private static String sHdmi = "HDMI";
    private static String sMhl = "MHL";
    private static String sSlimPort = "SLIMPORT";
    private PowerManager.WakeLock mWakeLock = null;
    private HdmiHandler mHandler;
    private HandlerThread mHandlerThread;
    private HdmiObserver mHdmiObserver;
    private boolean mHdmiEnabled;
    private int mHdmiVideoResolution;
    private int mHdmiVideoScale;
    private int mHdmiColorSpace;
    private int mHdmiDeepColor;
    private int mCapabilities;
    private boolean mCablePlugged;
    private int[] mEdid;
    private int[] mPreEdid;
    private boolean mInitialized = false;
    private boolean mIsSmartBookPluggedIn = false;

    private boolean mIsHdVideoPlaying = false;
    private boolean mHdVideoRestore = false;
    private boolean mCallComing = false;
    private boolean mCallRestore = false;

    private TelephonyManager mTelephonyManager = null;

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        public void onCallStateChanged(int state, String incomingNumber) {
            log(TAG, " Phone state changed, new state= " + state);
            handleCallStateChanged(state);
        }
    };

    private void handleCallStateChanged(int state) {
        log(TAG, "mCallComing: " + mCallComing + " mCallRestore: " + mCallRestore);
        if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
            mCallComing = true;
            if (isSignalOutputting()) {
                String contentStr = mContext.getResources().getString(
                        R.string.hdmi_mutex_call_content);
                int type = getDisplayType();
                if (type == HdmiDef.DISPLAY_TYPE_MHL) {
                    contentStr = contentStr.replaceAll(sHdmi, sMhl);
                } else if (type == HdmiDef.DISPLAY_TYPE_SLIMPORT) {
                    contentStr = contentStr.replaceAll(sHdmi, sSlimPort);
                }
                Toast.makeText(mContext, contentStr, Toast.LENGTH_LONG).show();
                mCallRestore = true;
                enableHdmi(false);
            }
        } else {
            mCallComing = false;
            if (mCallRestore) {
                mCallRestore = false;
                enableHdmi(true);
            }
        }
    }

    private class HdmiHandler extends Handler {
        public HdmiHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            log(TAG, "handleMessage: " + msg.what);
            if (null == mHandlerThread || !mHandlerThread.isAlive()
                    || mHandlerThread.isInterrupted()) {
                log(TAG, "handler thread is error");
                return;
            }
            switch (msg.what) {
            case MSG_INIT:
                initHdmi(false);
                if (isRealyBootComplete()) {
                    mInitialized = true;
                    // After boot complete, handle HDMI cable plug in event
                    hdmiCableStateChanged(mCablePlugged ? 1 : 0);
                } else {
                    deinitHdmi();
                }
                break;
            case MSG_DEINIT:
                mInitialized = false;
                deinitHdmi();
                break;
            case MSG_CABLE_STATE:
                int state = (Integer) msg.obj;
                hdmiCableStateChanged(state);
                break;
            case MSG_USER_SWITCH:
                deinitHdmi();
                initHdmi(true);
                break;
            default:
                super.handleMessage(msg);
            }
        }

        private boolean isRealyBootComplete() {
            boolean bRet = false;
            String state = SystemProperties.get("ro.crypto.state");
            String decrypt = SystemProperties.get("vold.decrypt");
            String type = SystemProperties.get("ro.crypto.type");
            if ("unencrypted".equals(state)) {
                if ("".equals(decrypt)) {
                    bRet = true;
                }
            } else if ("unsupported".equals(state)) {
                if ("".equals(decrypt)) {
                    bRet = true;
                }
            } else if ("".equals(state)) {
                // Always do nothing
            } else if ("encrypted".equals(state)) {
                if ("block".equals(type)) {
                    if ("trigger_restart_framework".equals(decrypt)) {
                        bRet = true;
                    }
                } else if ("file".equals(type)) {
                    bRet = true;
                }
            } else {
                // Unexpected state
            }
            log(TAG, "ro.crypto.state=" + state + " vold.decrypt=" + decrypt
                    + " realBoot=" + bRet);
            return bRet;
        }

        private void deinitHdmi() {
            unregisterCallListener();
            enableHdmiImpl(false);
            if (isSignalOutputting()) {
                mCablePlugged = false;
                handleCablePlugged(false);
            }
        }

        private void initHdmi(boolean bSwitchUser) {
            loadHdmiSettings();
            enableHdmiImpl(mHdmiEnabled);
            if (bSwitchUser && mInitialized) {
                handleCablePlugged(mCablePlugged);
                Settings.System.putIntForUser(mContentResolver,
                        Settings.System.HDMI_CABLE_PLUGGED, mCablePlugged ? 1
                                : 0, UserHandle.USER_CURRENT);
            }
            registerCallListener();
        }

    }

    private void hdmiCableStateChanged(int state) {
        mCablePlugged = state == 1;
        if (mInitialized) {
            int type = getDisplayType();
            if (mIsHdVideoPlaying && mCablePlugged) {
                if (type != HdmiDef.DISPLAY_TYPE_SMB) {
                    String contentStr = mContext.getResources().getString(
                            R.string.hdmi_hdvideo_toast);
                    if (type == HdmiDef.DISPLAY_TYPE_MHL) {
                        contentStr = contentStr.replaceAll(sHdmi, sMhl);
                    } else if (type == HdmiDef.DISPLAY_TYPE_SLIMPORT) {
                        contentStr = contentStr.replaceAll(sHdmi, sSlimPort);
                    }
                    log(TAG, "disable hdmi when play HD video");
                    Toast.makeText(mContext, contentStr, Toast.LENGTH_LONG)
                            .show();
                    mHdVideoRestore = true;
                    log(TAG, "mIsHdVideoPlaying: " + mIsHdVideoPlaying
                            + " mHdVideoRestore: " + mHdVideoRestore);
                    enableHdmi(false);
                    return;
                }
            } else if (mCallComing && mCablePlugged) {
                String contentStr = mContext.getResources().getString(
                        R.string.hdmi_mutex_call_content);
                if (type == HdmiDef.DISPLAY_TYPE_MHL) {
                    contentStr = contentStr.replaceAll(sHdmi, sMhl);
                } else if (type == HdmiDef.DISPLAY_TYPE_SLIMPORT) {
                    contentStr = contentStr.replaceAll(sHdmi, sSlimPort);
                }
                log(TAG, "disable hdmi when call coming");
                Toast.makeText(mContext, contentStr, Toast.LENGTH_LONG).show();
                mCallRestore = true;
                log(TAG, "mCallComing: " + mCallComing + " mCallRestore: " + mCallRestore);
                enableHdmi(false);
                return;
            }
            getCapabilities(); // Add for multi channel, when state changed,capability maybe change.
            handleCablePlugged(mCablePlugged);
            // Update settings provider will trigger HDMI settings UI get
            // resolution, so need behind getEdid
            Settings.System.putIntForUser(mContentResolver,
                    Settings.System.HDMI_CABLE_PLUGGED, state,
                    UserHandle.USER_CURRENT);
        }
    }

    private void unregisterCallListener() {
        if (hasCapability(HdmiDef.CAPABILITY_MUTEX_CALL) && mTelephonyManager != null) {
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    private void registerCallListener() {
        if (hasCapability(HdmiDef.CAPABILITY_MUTEX_CALL)) {
            if (mTelephonyManager == null) {
                mTelephonyManager = (TelephonyManager) mContext
                        .getSystemService(Context.TELEPHONY_SERVICE);
            }
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            log(TAG, "register phone state change listener...");
        }
    }

    private void handleCablePlugged(boolean plugged) {
        updateClearMotionDimmed(plugged);
        if (plugged) {
            refreshEdid(plugged);
            if (FeatureOption.MTK_MT8193_HDMI_SUPPORT
                    || FeatureOption.MTK_INTERNAL_HDMI_SUPPORT
                    || FeatureOption.MTK_INTERNAL_MHL_SUPPORT) {
                setColorAndDeepImpl(mHdmiColorSpace, mHdmiDeepColor);
            }
            initVideoResolution(mHdmiVideoResolution, mHdmiVideoScale);
        } else {
            refreshEdid(plugged);
        }
        // if smart book plug in, don't show hdmi settings notification
        boolean isShowNotification = plugged && !mIsSmartBookPluggedIn;
        handleNotification(isShowNotification);
        updateWakeLock(plugged, mHdmiEnabled);
        if (FeatureOption.MTK_ENABLE_HDMI_MULTI_CHANNEL && plugged) {
            handleMultiChannel();
        }
    }

    private boolean isSupportMultiChannel() {
        return getAudioParameter(HdmiDef.HDMI_MAX_CHANNEL, HdmiDef.HDMI_MAX_CHANNEL_OFFSETS) > 2;
    }

    private int mAudioOutputMode = 0;
    private AlertDialog mAudioOutputDialog;

    private void handleMultiChannel() {
        if (isSupportMultiChannel()) {
            mAudioOutputMode = Settings.System.getIntForUser(mContentResolver,
                    Settings.System.HDMI_AUDIO_OUTPUT_MODE, 0,
                    UserHandle.USER_CURRENT);
            log(TAG, "current mode from setting provider : " + mAudioOutputMode);
            if (mAudioOutputDialog == null) {
                String title = mContext.getResources().getString(
                        R.string.hdmi_audio_output);
                String stereo = mContext.getResources().getString(
                        R.string.hdmi_audio_output_stereo);
                String multiChannel = mContext.getResources().getString(
                        R.string.hdmi_audio_output_multi_channel);
                mAudioOutputDialog = new AlertDialog.Builder(mContext)
                        .setTitle(title).setSingleChoiceItems(
                                new String[] { stereo, multiChannel },
                                mAudioOutputMode,
                                new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        mAudioOutputMode = which;
                                        log(TAG,
                                                "mAudioOutputDialog clicked.. which: "
                                                        + which);
                                        setAudioParameters(which == 0);
                                        dialog.dismiss();
                                        mAudioOutputDialog = null;
                                    }
                                }).setNegativeButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        setAudioParameters(mAudioOutputMode == 0);
                                        dialog.dismiss();
                                        mAudioOutputDialog = null;
                                    }
                                }).create();
                mAudioOutputDialog.setCancelable(false);
                Window win = mAudioOutputDialog.getWindow();
                win.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            }
            mAudioOutputDialog.show();
        } else {
            setAudioParameters(false);
        }
    }

    private void setAudioParameters(boolean isStereoChecked) {
        int maxChannel = getAudioParameter(HdmiDef.HDMI_MAX_CHANNEL,
                HdmiDef.HDMI_MAX_CHANNEL_OFFSETS);
        if (isStereoChecked) { // stereo is checked.
            maxChannel = HdmiDef.AUDIO_OUTPUT_STEREO;
        }
        int maxSampleate = getAudioParameter(HdmiDef.HDMI_MAX_SAMPLERATE,
                HdmiDef.HDMI_MAX_SAMPLERATE_OFFSETS);
        int maxBitwidth = getAudioParameter(HdmiDef.HDMI_MAX_BITWIDTH,
                HdmiDef.HDMI_MAX_BITWIDTH_OFFSETS);
        AudioSystem.setParameters("HDMI_channel=" + maxChannel);
        AudioSystem.setParameters("HDMI_maxsamplingrate=" + maxSampleate);
        AudioSystem.setParameters("HDMI_bitwidth=" + maxBitwidth);
        Settings.System.putIntForUser(mContentResolver,
                Settings.System.HDMI_AUDIO_OUTPUT_MODE, mAudioOutputMode,
                UserHandle.USER_CURRENT);
        log(TAG, "setAudioParameters mAudioOutputMode: " + mAudioOutputMode
                + " ,maxChannel: " + maxChannel + " ,maxSampleate: "
                + maxSampleate + " ,maxBitwidth: " + maxBitwidth);
    }

    @Override
    public int getAudioParameter(int mask, int offsets) {
        int param = (mCapabilities & mask) >> offsets;
        log(TAG, "getAudioParameter() mask: " + mask + " ,offsets: " + offsets
                + " ,param: " + param + " ,mCapabilities: 0x"
                + Integer.toHexString(mCapabilities));
        return param;
    }

    private void updateClearMotionDimmed(boolean plugged) {
        if (FeatureOption.MTK_CLEARMOTION_SUPPORT) {
            SystemProperties.set(KEY_CLEARMOTION_DIMMED, plugged ? "1" : "0");
            mContext.sendBroadcastAsUser(new Intent(ACTION_CLEARMOTION_DIMMED),
                    UserHandle.ALL);
        }
    }

    private void refreshEdid(boolean plugged) {
        if (plugged) {
            mEdid = getResolutionMask();
            if (mEdid != null) {
                for (int i = 0; i < mEdid.length; i++) {
                    log(TAG, String.format("mEdid[%d] = %d", i, mEdid[i]));
                }
            } else {
                log(TAG, "mEdid is null!");
            }

            if (mPreEdid != null) {
                for (int i = 0; i < mPreEdid.length; i++) {
                    log(TAG, String.format("mPreEdid[%d] = %d", i, mPreEdid[i]));
                }
            } else {
                log(TAG, "mPreEdid is null!");
            }
        } else {
            mPreEdid = mEdid;
            // mEdid = null;
        }
    }

    private void handleNotification(boolean showNoti) {
        NotificationManager notificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            Log.w(TAG, "Fail to get NotificationManager");
            return;
        }
        if (showNoti) {
            log(TAG, "Show notification now");
            Notification notification = new Notification();
            String titleStr = mContext.getResources().getString(
                    R.string.hdmi_notification_title);
            String contentStr = mContext.getResources().getString(
                    R.string.hdmi_notification_content);
            notification.icon = R.drawable.ic_hdmi_notification;
            int type = getDisplayType();
            if (type == HdmiDef.DISPLAY_TYPE_MHL) {
                titleStr = titleStr.replaceAll(sHdmi, sMhl);
                contentStr = contentStr.replaceAll(sHdmi, sMhl);
                notification.icon = R.drawable.ic_mhl_notification;
            } else if(type == HdmiDef.DISPLAY_TYPE_SLIMPORT) {
                titleStr = titleStr.replaceAll(sHdmi, sSlimPort);
                contentStr = contentStr.replaceAll(sHdmi, sSlimPort);
                notification.icon = R.drawable.ic_sp_notification;
            }
            notification.tickerText = titleStr;
            notification.flags = Notification.FLAG_ONGOING_EVENT
                    | Notification.FLAG_NO_CLEAR
                    | Notification.FLAG_SHOW_LIGHTS;
            Intent intent = Intent
                    .makeRestartActivityTask(new ComponentName(
                            "com.android.settings",
                            "com.android.settings.HDMISettings"));
            PendingIntent pendingIntent = PendingIntent.getActivityAsUser(
                    mContext, 0, intent, 0, null, UserHandle.CURRENT);
            notification.setLatestEventInfo(mContext, titleStr, contentStr,
                    pendingIntent);
            notificationManager.notifyAsUser(null,
                    R.drawable.ic_hdmi_notification, notification,
                    UserHandle.CURRENT);
        } else {
            log(TAG, "Clear notification now");
            notificationManager.cancelAsUser(null,
                    R.drawable.ic_hdmi_notification, UserHandle.CURRENT);
        }
    }

    private BroadcastReceiver mActionReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            log(TAG, "receive: " + action);
            if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                    || ACTION_IPO_BOOT.equals(action)) {
                sendMsg(MSG_INIT);
            } else if (Intent.ACTION_SHUTDOWN.equals(action)) {
                Log.d(TAG, "intent.getExtra_mode" + intent.getExtra("_mode"));
                if (intent.getExtra("_mode") == null) {
                    Log.d(TAG, "SHUTDOWN_REQUESTED=" + FeatureOption.SHUTDOWN_REQUESTED);
                    if (FeatureOption.SHUTDOWN_REQUESTED) {
                        sendMsg(MSG_DEINIT);
                    }
                } else {
                    sendMsg(MSG_DEINIT);
                }
            } else if (ACTION_IPO_SHUTDOWN.equals(action)) {
                sendMsg(MSG_DEINIT);
            } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                sendMsg(MSG_USER_SWITCH);
            // { @ Smart book hdmi settings
            } else if (Intent.ACTION_SMARTBOOK_PLUG.equals(action)) {
                mIsSmartBookPluggedIn = intent.getBooleanExtra(
                        Intent.EXTRA_SMARTBOOK_PLUG_STATE, false);
                Log.d(TAG, "smartbook plug:" + mIsSmartBookPluggedIn);
                // if smart book plug in or out, don't show hdmi settings
                // notification
                handleNotification(false);
            }
            // @ }
        }

        private void sendMsg(int msgInit) {
            if (!mHandler.hasMessages(msgInit)) {
                mHandler.sendEmptyMessage(msgInit);
                log(TAG, "send msg: " + msgInit);
            }
        }

    };

    private ContentObserver mHdmiSettingsObserver = new ContentObserver(
            mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            Log.d(TAG, "hdmiSettingsObserver onChanged: " + selfChange);
            mHdmiEnabled = Settings.System.getIntForUser(mContentResolver,
                    Settings.System.HDMI_ENABLE_STATUS,
                    HDMI_ENABLE_STATUS_DEFAULT, UserHandle.USER_CURRENT) == 1;
            updateWakeLock(mCablePlugged, mHdmiEnabled);
        }
    };

    public MtkHdmiManagerService(Context context) {
        log(TAG, "MtkHdmiManagerService constructor");
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        initial();
    }

    private void initial() {
        if (null == mHandlerThread || !mHandlerThread.isAlive()) {
            mHandlerThread = new HandlerThread("HdmiService");
            mHandlerThread.start();
            mHandler = new HdmiHandler(mHandlerThread.getLooper());
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SHUTDOWN);
            filter.addAction(Intent.ACTION_LOCKED_BOOT_COMPLETED);
            filter.addAction(Intent.ACTION_USER_SWITCHED);
            filter.addAction(ACTION_IPO_SHUTDOWN);
            filter.addAction(ACTION_IPO_BOOT);
            // { @ Smart book hdmi settings
            if (FeatureOption.MTK_SMARTBOOK_SUPPORT) {
                filter.addAction(Intent.ACTION_SMARTBOOK_PLUG);
            }
            // @ }
            mContext.registerReceiverAsUser(mActionReceiver, UserHandle.ALL,
                    filter, null, mHandler);
        }
        if (null == mWakeLock) {
            PowerManager mPowerManager = (PowerManager) mContext
                    .getSystemService(Context.POWER_SERVICE);
             mWakeLock = mPowerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                    PowerManager.ON_AFTER_RELEASE, "HDMI");
            mWakeLock.setReferenceCounted(false);
        }
        if (null == mHdmiObserver) {
            mHdmiObserver = new HdmiObserver(mContext);
            mHdmiObserver.startObserve();
        }
        if ((FeatureOption.MTK_MT8193_HDCP_SUPPORT)
                || (FeatureOption.MTK_HDMI_HDCP_SUPPORT)) {
            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    if (FeatureOption.MTK_DRM_KEY_MNG_SUPPORT) {
                        log(TAG, "setDrmKey: " + setDrmKey());
                    } else {
                        log(TAG, "setHdcpKey: " + setHdcpKey());
                    }
                }
            });
        }
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                getCapabilities();
                sHdmi = mContext.getResources().getString(
                        R.string.hdmi_replace_hdmi);
                sMhl = mContext.getResources().getString(
                        R.string.hdmi_replace_mhl);
            }

        });
        observeSettings();
    }

    private void updateWakeLock(boolean plugged, boolean hdmiEnabled) {
        if (plugged && hdmiEnabled && nativeIsHdmiForceAwake()) {
            mWakeLock.acquire();
        } else {
            mWakeLock.release();
        }
    }

    private boolean setHdcpKey() {
        byte[] key = null;
        IBinder binder = ServiceManager.getService("NvRAMAgent");
        NvRAMAgent agent = NvRAMAgent.Stub.asInterface(binder);
        if (agent != null) {
            try {
                log(TAG, "Read HDCP key from nvram");
                key = agent.readFile(AP_CFG_RDCL_FILE_HDCP_KEY_LID);
                for (int i = 0; i < 287; i++) {
                    log(TAG, String.format("HDCP key[%d] = %d", i, key[i]));
                }
                if (null != key) {
                    return nativeSetHdcpKey(key);
                }
            } catch (RemoteException e) {
                Log.w(TAG, "NvRAMAgent read file fail");
            }
        }
        return false;
    }

    private boolean setDrmKey() {
        synchronized (MtkHdmiManagerService.this) {
            return nativeSetHdmiDrmKey();
        }
    }

    private void loadHdmiSettings() {
        mHdmiEnabled = Settings.System.getIntForUser(mContentResolver,
                Settings.System.HDMI_ENABLE_STATUS, HDMI_ENABLE_STATUS_DEFAULT,
                UserHandle.USER_CURRENT) == 1;
        mHdmiVideoResolution = Settings.System.getIntForUser(mContentResolver,
                Settings.System.HDMI_VIDEO_RESOLUTION,
                HDMI_VIDEO_RESOLUTION_DEFAULT, UserHandle.USER_CURRENT);
        mHdmiVideoScale = Settings.System.getIntForUser(mContentResolver,
                Settings.System.HDMI_VIDEO_SCALE,
                HDMI_VIDEO_SCALE_DEFAULT, UserHandle.USER_CURRENT);
        mHdmiColorSpace = Settings.System.getIntForUser(mContentResolver,
                Settings.System.HDMI_COLOR_SPACE, HDMI_COLOR_SPACE_DEFAULT,
                UserHandle.USER_CURRENT);
        mHdmiDeepColor = Settings.System.getIntForUser(mContentResolver,
                Settings.System.HDMI_DEEP_COLOR, HDMI_DEEP_COLOR_DEFAULT,
                UserHandle.USER_CURRENT);
        mIsHdVideoPlaying = false;
        mHdVideoRestore = false;
        mCallComing = false;
        mCallRestore = false;
    }

    private void observeSettings() {
        mContentResolver.registerContentObserver(Settings.System
                .getUriFor(Settings.System.HDMI_ENABLE_STATUS), false,
                mHdmiSettingsObserver, UserHandle.USER_ALL);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.DUMP, TAG);
        pw.println("MTK HDMI MANAGER (dumpsys HDMI)");
        pw.println("HDMI mHdmiEnabled: " + mHdmiEnabled);
        pw.println("HDMI mHdmiVideoResolution: " + mHdmiVideoResolution);
        pw.println("HDMI mHdmiVideoScale: " + mHdmiVideoScale);
        pw.println("HDMI mHdmiColorSpace: " + mHdmiColorSpace);
        pw.println("HDMI mHdmiDeepColor: " + mHdmiDeepColor);
        pw.println("HDMI mCapabilities: " + mCapabilities);
        pw.println("HDMI mCablePlugged: " + mCablePlugged);
        pw.println("HDMI mEdid: " + Arrays.toString(mEdid));
        pw.println("HDMI mPreEdid: " + Arrays.toString(mPreEdid));
        pw.println("HDMI mInitialized: " + mInitialized);
        pw.println();
    }

    @Override
    public boolean enableHdmi(boolean enabled) {
        log(TAG, "enableHdmi: " + enabled);
        boolean ret = false;
        if (enabled == mHdmiEnabled) {
            log(TAG, "mHdmiEnabled is the same: " + enabled);
        } else {
            ret = enableHdmiImpl(enabled);
            if (ret) {
                final long ident = Binder.clearCallingIdentity();
                try {
                    mHdmiEnabled = enabled;
                    Settings.System.putIntForUser(mContentResolver,
                            Settings.System.HDMI_ENABLE_STATUS,
                            mHdmiEnabled ? 1 : 0, UserHandle.USER_CURRENT);
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }
        return ret;
    }

    private boolean enableHdmiImpl(boolean enabled) {
        synchronized (MtkHdmiManagerService.this) {
            return nativeEnableHdmi(enabled);
        }
    }

    @Override
    public int[] getResolutionMask() {
        log(TAG, "getResolutionMask");
        synchronized (MtkHdmiManagerService.this) {
            return nativeGetEdid();
        }
    }

    @Override
    public boolean isSignalOutputting() {
        log(TAG, "isSignalOutputting");
        return mCablePlugged && mHdmiEnabled;
    }

    @Override
    public boolean setColorAndDeep(int color, int deep) {
        log(TAG, "setColorAndDeep: " + color + ", " + deep);
        boolean ret = setColorAndDeepImpl(color, deep);
        if (ret) {
            final long ident = Binder.clearCallingIdentity();
            try {
                mHdmiColorSpace = color;
                mHdmiDeepColor = deep;
                Settings.System.putIntForUser(mContentResolver,
                        Settings.System.HDMI_COLOR_SPACE, color,
                        UserHandle.USER_CURRENT);
                Settings.System.putIntForUser(mContentResolver,
                        Settings.System.HDMI_DEEP_COLOR, deep,
                        UserHandle.USER_CURRENT);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
        return ret;
    }

    private boolean setColorAndDeepImpl(int color, int deep) {
        synchronized (MtkHdmiManagerService.this) {
            return nativeSetDeepColor(color, deep);
        }
    }

    @Override
    public boolean setVideoResolution(int resolution) {
        log(TAG, "setVideoResolution: " + resolution);
        boolean ret = false;
        int suitableResolution = resolution;
        if (resolution >= HdmiDef.AUTO) {
            suitableResolution = getSuitableResolution(resolution);
        }
        if (suitableResolution == mHdmiVideoResolution) {
            log(TAG, "setVideoResolution is the same");
        }
        int finalResolution = suitableResolution >= HdmiDef.AUTO ?
                (suitableResolution - HdmiDef.AUTO) : suitableResolution;
        log(TAG, "final video resolution: " + finalResolution + " scale: " + mHdmiVideoScale);
        ret = setVideoResolutionImpl(finalResolution, mHdmiVideoScale);
        if (ret) {
            final long ident = Binder.clearCallingIdentity();
            try {
                mHdmiVideoResolution = suitableResolution;
                Settings.System.putIntForUser(mContentResolver,
                        Settings.System.HDMI_VIDEO_RESOLUTION,
                        mHdmiVideoResolution, UserHandle.USER_CURRENT);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
        return ret;
    }

    private void initVideoResolution(int resolution, int scale) {
        log(TAG, "initVideoResolution: " + resolution + " scale: " + scale);
        if (isResolutionSupported(resolution)) {
            setVideoResolutionImpl(resolution, scale);
        } else {
            int suitableResolution = getSuitableResolution(resolution);
            int finalResolution = suitableResolution >= HdmiDef.AUTO ?
                    (suitableResolution - HdmiDef.AUTO) : suitableResolution;
            log(TAG, "initVideoResolution final video resolution: "
                    + finalResolution);
            if (setVideoResolutionImpl(finalResolution, scale)) {
                mHdmiVideoResolution = suitableResolution;
                Settings.System.putIntForUser(mContentResolver,
                        Settings.System.HDMI_VIDEO_RESOLUTION,
                        mHdmiVideoResolution, UserHandle.USER_CURRENT);
            }
        }
    }

    private boolean isResolutionSupported(int resolution) {
        log(TAG, "isResolutionSupported: " + resolution);
        if (resolution >= HdmiDef.AUTO) {
            return false;
        }
        int[] supportedResolutions = getSupportedResolutions();
        for (int res : supportedResolutions) {
            if (res == resolution) {
                log(TAG, "resolution is supported");
                return true;
            }
        }
        return false;
    }

    private boolean setVideoResolutionImpl(int resolution, int scale) {
        int type = getDisplayType();
        if (type == HdmiDef.DISPLAY_TYPE_SMB) {
            log(TAG, "revise resolution for SMB to " + HdmiDef.RESOLUTION_1280X720P_60HZ);
            resolution = HdmiDef.RESOLUTION_1280X720P_60HZ;
        }
        int param = (resolution & 0xff) | ((scale & 0xff) << 8);
        log(TAG, "set video resolution&scale: 0x" + Integer.toHexString(param));
        synchronized (MtkHdmiManagerService.this) {
            return nativeSetVideoConfig(param);
        }
    }

    private int getSuitableResolution(int videoResolution) {
        int[] supportedResolutions = getSupportedResolutions();
        ArrayList<Integer> resolutionList = new ArrayList<Integer>();
        for (int res : supportedResolutions) {
            resolutionList.add(res);
        }
        if (needUpdate(videoResolution)) {
            log(TAG, "upate resolution");
            if (mEdid != null) {
                int edidTemp = mEdid[0] | mEdid[1];
                int index = 0;
                if (FeatureOption.MTK_INTERNAL_HDMI_SUPPORT
                        || FeatureOption.MTK_INTERNAL_MHL_SUPPORT) {
                    index = 1;
                } else if (FeatureOption.MTK_MT8193_HDMI_SUPPORT) {
                    index = 0;
                } else if (FeatureOption.MTK_TB6582_HDMI_SUPPORT) {
                    index = 2;
                } else {
                    index = 3;
                }
                int[] prefered = HdmiDef.getPreferedResolutions(index);
                for (int res : prefered) {
                    int act = res;
                    if (res >= HdmiDef.AUTO) {
                        act = res - HdmiDef.AUTO;
                    }
                    if (0 != (edidTemp & HdmiDef.sResolutionMask[act])
                            && resolutionList.contains(act)) {
                        videoResolution = res;
                        break;
                    }
                }
            }
        }
        log(TAG, "suiteable video resolution: " + videoResolution);
        return videoResolution;
    }

    private boolean needUpdate(int videoResolution) {
        log(TAG, "needUpdate: " + videoResolution);
        boolean needUpdate = true;
        if (mPreEdid != null && Arrays.equals(mEdid, mPreEdid)) {
            needUpdate = false;
        }
        if (videoResolution >= HdmiDef.AUTO) {
            needUpdate = true;
        }
        return needUpdate;
    }


    @Override
    public boolean setVideoScale(int scale) {
        log(TAG, "setVideoScale: " + scale);
        boolean ret = false;
        if (scale >= 0 && scale <= 10) {
            ret = true;
        }
        if (ret) {
            mHdmiVideoScale = scale;
            int finalResolution = mHdmiVideoResolution >= HdmiDef.AUTO ?
                    (mHdmiVideoResolution - HdmiDef.AUTO) : mHdmiVideoResolution;
            log(TAG, "set video resolution: " + finalResolution + " scale: "
                    + mHdmiVideoScale);
            ret = setVideoResolutionImpl(finalResolution, mHdmiVideoScale);
            if (ret) {
                final long ident = Binder.clearCallingIdentity();
                try {
                    Settings.System.putIntForUser(mContentResolver,
                            Settings.System.HDMI_VIDEO_SCALE, mHdmiVideoScale,
                            UserHandle.USER_CURRENT);
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }
        return ret;
    }

    @Override
    public int[] getSupportedResolutions() {
        log(TAG, "getSupportedResolutions");
        return getSupportedResolutionsImpl();
    }

    private int[] getSupportedResolutionsImpl() {
        if (null == mEdid) {
            if (FeatureOption.MTK_TB6582_HDMI_SUPPORT) {
                return HdmiDef.getDefaultResolutions(2);
            } else {
                return HdmiDef.getDefaultResolutions(3);
            }
        }
        int[] resolutions;
        if (FeatureOption.MTK_INTERNAL_HDMI_SUPPORT
                || FeatureOption.MTK_INTERNAL_MHL_SUPPORT) {
            if(FeatureOption.MTK_HDMI_4K_SUPPORT){
                return HdmiDef.getDefaultResolutions(1);
            } else {
                return HdmiDef.getDefaultResolutions(4);
            }
        } else if (FeatureOption.MTK_MT8193_HDMI_SUPPORT) {
            resolutions = HdmiDef.getDefaultResolutions(0);
        } else {
            resolutions = HdmiDef.getAllResolutions();
        }
        int edidTemp = mEdid[0] | mEdid[1];
        ArrayList<Integer> list = new ArrayList<Integer>();
        for (int res : resolutions) {
            try {
                int mask = HdmiDef.sResolutionMask[res];
                if ((edidTemp & mask) != 0) {
                    if (!list.contains(res)) {
                        list.add(res);
                    }
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                Log.w(TAG, e.getMessage());
            }
        }
        resolutions = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            resolutions[i] = list.get(i);
        }
        log(TAG, "getSupportedResolutionsImpl: " + Arrays.toString(resolutions));
        return resolutions;
    }

    @Override
    public int getDisplayType() {
        log(TAG, "getDisplayType");
        int ret = 0;
        synchronized (MtkHdmiManagerService.this) {
            ret = nativeGetDisplayType();
        }
        return ret;
    }

    @Override
    public void notifyHdVideoState(boolean playing) {
        log(TAG, "notifyHdVideoState: " + playing);
        synchronized (MtkHdmiManagerService.this) {
            if (mIsHdVideoPlaying == playing) {
                return;
            } else {
                log(TAG, "mIsHdVideoPlaying: " + mIsHdVideoPlaying
                        + " mNeedRestore: " + mHdVideoRestore);
                mIsHdVideoPlaying = playing;
                if (!mIsHdVideoPlaying) {
                    if (mHdVideoRestore) {
                        mHdVideoRestore = false;
                        enableHdmi(true);
                    }
                }
            }
        }
    }

    @Override
    public boolean enableHdmiPower(boolean enabled) {
        log(TAG, "enableHdmiPower");
        boolean ret = false;
        synchronized (MtkHdmiManagerService.this) {
            ret = nativeHdmiPowerEnable(enabled);
        }
        return ret;
    }

    @Override
    public boolean needSwDrmProtect() {
        log(TAG, "needSwDrmProtect");
        boolean ret = false;
        synchronized (MtkHdmiManagerService.this) {
            ret = nativeNeedSwDrmProtect();
        }
        return ret;
    }

    @Override
    public boolean hasCapability(int mask) {
        log(TAG, "hasCapability: " + mask);
        return (mCapabilities & mask) != 0;
    }

    private void getCapabilities() {
        synchronized (MtkHdmiManagerService.this) {
            mCapabilities = nativeGetCapabilities();
        }
        log(TAG, "getCapabilities: 0x"
            + Integer.toHexString(mCapabilities));
    }

    private static void log(String tag, Object obj) {
        if (Log.isLoggable(tag, Log.INFO)) {
            Log.i(tag, obj.toString());
        }
    }

    private class HdmiObserver extends UEventObserver {
        private static final String TAG = "HdmiObserver";

        private static final String HDMI_UEVENT_MATCH = "DEVPATH=/devices/virtual/switch/hdmi";
        private static final String HDMI_STATE_PATH = "/sys/class/switch/hdmi/state";
        private static final String HDMI_NAME_PATH = "/sys/class/switch/hdmi/name";

        // Monitor OTG and notify HDMI
        private static final int MSG_HDMI = 10;
        private static final int MSG_OTG = 11;
        private static final String OTG_UEVENT_MATCH = "DEVPATH=/devices/virtual/switch/otg_state";
        private static final String OTG_STATE_PATH = "/sys/class/switch/otg_state/state";
        private static final String OTG_NAME_PATH = "/sys/class/switch/otg_state/name";
        private String mOtgName;

        private int mHdmiState;
        private int mPrevHdmiState;
        private String mHdmiName;

        private final Context mContext;
        private final WakeLock mWakeLock;

        public HdmiObserver(Context context) {
            mContext = context;
            PowerManager pm = (PowerManager) context
                    .getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "HdmiObserver");
            mWakeLock.setReferenceCounted(false);
            init();
        }

        public void startObserve() {
            startObserving(HDMI_UEVENT_MATCH);
            // Monitor OTG
            startObserving(OTG_UEVENT_MATCH);
        }

        public void stopObserve() {
            stopObserving();
        }

        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            log(TAG, "HdmiObserver: onUEvent: " + event.toString());
            String name = event.get("SWITCH_NAME");
            int state = 0;
            try {
                state = Integer.parseInt(event.get("SWITCH_STATE"));
            } catch (NumberFormatException e) {
                Log.w(TAG,
                        "HdmiObserver: Could not parse switch state from event "
                                + event);
            }
            log(TAG, "HdmiObserver.onUEvent(), name=" + name + ", state="
                    + state);
            if (name.equals(mOtgName)) {
                updateOtgState(state);
            } else {
                update(name, state);
            }
        }

        private synchronized void init() {
            String newName = mHdmiName;
            int newState = mHdmiState;
            mPrevHdmiState = mHdmiState;
            newName = getContentFromFile(HDMI_NAME_PATH);
            try {
                newState = Integer
                        .parseInt(getContentFromFile(HDMI_STATE_PATH));
            } catch (NumberFormatException e) {
                Log.w(TAG, "HDMI state fail");
                return;
            }
            update(newName, newState);
            initOtgState();
        }

        private String getContentFromFile(String filePath) {
            char[] buffer = new char[1024];
            FileReader reader = null;
            String content = null;
            try {
                reader = new FileReader(filePath);
                int len = reader.read(buffer, 0, buffer.length);
                content = String.valueOf(buffer, 0, len).trim();
                log(TAG, filePath + " content is " + content);
            } catch (FileNotFoundException e) {
                Log.w(TAG, "can't find file " + filePath);
            } catch (IOException e) {
                Log.w(TAG, "IO exception when read file " + filePath);
            } catch (IndexOutOfBoundsException e) {
                Log.w(TAG, "index exception: " + e.getMessage());
            } finally {
                if (null != reader) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.w(TAG, "close reader fail: " + e.getMessage());
                    }
                }
            }
            return content;
        }

        private synchronized void update(String newName, int newState) {
            log(TAG, "HDMIOberver.update(), oldState=" + mHdmiState
                    + ", newState=" + newState);
            // Retain only relevant bits
            int hdmiState = newState;
            int newOrOld = hdmiState | mHdmiState;
            int delay = 0;
            // reject all suspect transitions: only accept state changes from:
            // - a: 0 HDMI to 1 HDMI
            // - b: 1 HDMI to 0 HDMI

            /**
             * HDMI states HDMI_STATE_NO_DEVICE HDMI_STATE_ACTIVE
             *
             * Following are for MT8193
             *
             * HDMI_STATE_PLUGIN_ONLY HDMI_STATE_EDID_UPDATE
             * HDMI_STATE_CEC_UPDATE
             */
            if (FeatureOption.MTK_MT8193_HDMI_SUPPORT) {
                if ((mHdmiState == hdmiState) && (3 != mHdmiState)) {
                    return;
                }
            } else {
                if (mHdmiState == hdmiState
                        || ((newOrOld & (newOrOld - 1)) != 0)) {
                    return;
                }
            }
            mHdmiName = newName;
            mPrevHdmiState = mHdmiState;
            mHdmiState = hdmiState;
            mWakeLock.acquire();
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_HDMI,
                    mHdmiState, mPrevHdmiState, mHdmiName), delay);
        }

        private synchronized void sendIntents(int hdmiState, int prevHdmiState,
                String hdmiName) {
            int curHdmi = 1;
            // int curHDMI = 3;
            sendIntent(curHdmi, hdmiState, prevHdmiState, hdmiName);
        }

        private void sendIntent(int hdmi, int hdmiState, int prevHdmiState,
                String hdmiName) {
            if ((hdmiState & hdmi) != (prevHdmiState & hdmi)) {
                Intent intent = new Intent(Intent.ACTION_HDMI_PLUG);
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
                int state = 0;
                if ((hdmiState & hdmi) != 0) {
                    state = 1;
                }
                intent.putExtra("state", state);
                intent.putExtra("name", hdmiName);
                log(TAG, "HdmiObserver: Broadcast HDMI event, state: " + state
                        + " name: " + hdmiName);
                mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
                MtkHdmiManagerService.this.mHandler.obtainMessage(
                        MSG_CABLE_STATE, state).sendToTarget();
            }
        }

        private final Handler mHandler = new Handler(
                MtkHdmiManagerService.this.mHandler.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case MSG_HDMI:
                    sendIntents(msg.arg1, msg.arg2, (String) msg.obj);
                    break;
                case MSG_OTG:
                    handleOtgStateChanged(msg.arg1);
                    break;
                default:
                    super.handleMessage(msg);
                }
                mWakeLock.release();
            }
        };

        private void initOtgState() {
            mOtgName = getContentFromFile(OTG_NAME_PATH);
            int otgState = 0;
            try {
                otgState = Integer.parseInt(getContentFromFile(OTG_STATE_PATH));
            } catch (NumberFormatException e) {
                Log.w(TAG, "OTG state fail");
                return;
            }
            Log.i(TAG, "HDMIObserver.initOtgState(), state=" + otgState
                    + ", name=" + mOtgName);
            updateOtgState(otgState);
        }

        private void updateOtgState(int otgState) {
            Log.i(TAG, "HDMIObserver.updateOtgState(), otgState=" + otgState);
            mWakeLock.acquire();
            Message msg = mHandler.obtainMessage(MSG_OTG);
            msg.arg1 = otgState;
            mHandler.sendMessage(msg);
        }

        private void handleOtgStateChanged(int otgState) {
            Log.i(TAG, "HDMIObserver.handleOtgStateChanged(), otgState="
                    + otgState);
            boolean ret = nativeNotifyOtgState(otgState);
            Log.i(TAG, "notifyOtgState: " + ret);
        }
    }

    private static class FeatureOption {

        public static final boolean MTK_DRM_KEY_MNG_SUPPORT =
                getValue("ro.mtk_key_manager_support");
        public static final boolean MTK_HDMI_HDCP_SUPPORT =
                getValue("ro.mtk_hdmi_hdcp_support");
        public static final boolean MTK_MT8193_HDCP_SUPPORT =
                getValue("ro.mtk_mt8193_hdcp_support");
        public static final boolean MTK_SMARTBOOK_SUPPORT =
                getValue("ro.mtk_smartbook_support");
        public static final boolean MTK_CLEARMOTION_SUPPORT =
                getValue("ro.mtk_clearmotion_support");
        public static final boolean MTK_INTERNAL_MHL_SUPPORT =
                getValue("ro.mtk_internal_mhl_support");
        public static final boolean MTK_INTERNAL_HDMI_SUPPORT =
                getValue("ro.mtk_internal_hdmi_support");
        public static final boolean MTK_MT8193_HDMI_SUPPORT =
                getValue("ro.mtk_mt8193_hdmi_support");
        public static final boolean MTK_ENABLE_HDMI_MULTI_CHANNEL = true;
        public static final boolean MTK_TB6582_HDMI_SUPPORT =
                getValue("ro.hdmi.1080p60.disable");
        public static final boolean MTK_HDMI_4K_SUPPORT =
                getValue("ro.mtk_hdmi_4k_support");

        public static final boolean SHUTDOWN_REQUESTED =
                getValue("sys.shutdown.requested");
        private static boolean getValue(String key) {
            return SystemProperties.get(key).equals("1");
        }

    }

    public native boolean nativeEnableHdmi(boolean enabled);

    public native boolean nativeEnableHdmiIpo(boolean enabled);

    public native boolean nativeEnableVideo(boolean enabled);

    public native boolean nativeEnableAudio(boolean enabled);

    public native boolean nativeEnableCec(boolean enbaled);

    public native boolean nativeEnableHdcp(boolean enabled);

    public native boolean nativeSetVideoConfig(int newValue);

    public native boolean nativeSetAudioConfig(int newValue);

    public native boolean nativeSetDeepColor(int colorSpace, int deepColor);

    public native boolean nativeSetHdcpKey(byte[] key);

    public native boolean nativeSetHdmiDrmKey();

    public native boolean nativeSetCecAddr(byte laNum, byte[] la, char pa,
            char svc);

    public native boolean nativeSetCecCmd(byte initAddr, byte destAddr,
            char opCode, byte[] operand, int size, byte enqueueOk);

    public native boolean nativeHdmiPowerEnable(boolean enabled);

    public native boolean nativeHdmiPortraitEnable(boolean enabled);

    public native boolean nativeIsHdmiForceAwake();

    public native int[] nativeGetEdid();

    public native char[] nativeGetCecAddr();

    public native int[] nativeGetCecCmd();

    public native boolean nativeNotifyOtgState(int otgState);

    public native int nativeGetDisplayType();

    public native boolean nativeNeedSwDrmProtect();

    public native int nativeGetCapabilities();
}
