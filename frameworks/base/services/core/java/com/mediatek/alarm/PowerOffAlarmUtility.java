/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.mediatek.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.util.Log;
import android.os.SystemProperties;

import android.os.Build;
import android.os.Process;
import android.util.ArraySet;
import android.util.Slog;
import com.android.internal.os.InstallerConnection.InstallerException;
import com.android.server.am.ActivityManagerService;
import com.android.server.pm.Installer;
import com.mediatek.am.AMEventHookAction;
import com.mediatek.am.AMEventHookData.SystemReady;
import com.mediatek.am.AMEventHookData.AfterPostEnableScreenAfterBoot;
import com.mediatek.am.AMEventHookResult;
import com.mediatek.ipomanager.ActivityManagerPlus;
import dalvik.system.VMRuntime;

/**
 * add for power-off alarm
 *
 * @author mtk54296
 */
public class PowerOffAlarmUtility {
    private final static String         TAG           = "PowerOffAlarmUtility";
    private Context                     mContext;
    private final static String         REMOVE_IPOWIN = "alarm.boot.remove.ipowin";
    private final static String         ALARM_BOOT_DONE = "android.intent.action.normal.boot.done";
    private boolean                     mRollback     = false;
    public boolean                      mFirstBoot    = false;
    private ActivityManagerService      mService;
    private static PowerOffAlarmUtility mInstance;

    /**
     * return the singleton instance of this class called by systemReady() in
     * AMS to create a instance.
     *
     * @param ctx
     * @param aService
     * @return
     */
    public static PowerOffAlarmUtility getInstance(Context ctx,
            ActivityManagerService aService) {
        if (mInstance != null) {
            return mInstance;
        }
        if (ctx != null && aService != null) {
            mInstance = new PowerOffAlarmUtility(ctx, aService);
        }
        return mInstance;
    }

    /**
     * constructor
     *
     * @param ctx
     * @param aService
     */
    private PowerOffAlarmUtility(Context ctx, ActivityManagerService aService) {
        mContext = ctx;
        mService = aService;
        registerNormalBootReceiver(mContext);
        boolean recover = SystemProperties.getBoolean(
                "persist.sys.ams.recover", false);
        if (recover) {
            checkFlightMode(true, false);
        }
    }

    /**
     * launch power off alarm
     *
     * @param isAlarmBoot
     * @param recover
     * @param shutdown
     */

    public void launchPowerOffAlarm(Boolean recover, Boolean shutdown) {
        if (recover != null && shutdown != null) {
            checkFlightMode(recover, shutdown);
        }
        mContext.sendBroadcast(new Intent(
                "android.intent.action.LAUNCH_POWEROFF_ALARM"));
    }

    /**
     * to check if it is alarm boot
     *
     * @return
     */
    public static boolean isAlarmBoot() {
        String bootReason = SystemProperties.get("sys.boot.reason");
        boolean ret = (bootReason != null && bootReason.equals("1")) ? true
                : false;
        return ret;
    }

    /**
     * Power off Alarm feature: When receiving the
     * android.intent.action.normal.boot intent, AMS will resume the boot
     * process (broadcast BOOT_COMPLETED intent)
     */
    private final void registerNormalBootReceiver(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.normal.boot");
        filter.addAction("android.intent.action.normal.shutdown");
        filter.addAction(ALARM_BOOT_DONE);
        filter.addAction(REMOVE_IPOWIN);
        filter.addAction("android.intent.action.ACTION_BOOT_IPO");
        mFirstBoot = true;

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    return;
                }
                String action = intent.getAction();
                if ("android.intent.action.normal.boot".equals(action)) {
                    Log.i(TAG, "DeskClock normally boots-up device");
                    if (mRollback) {
                        checkFlightMode(false, false);
                    }
                    if (mFirstBoot) {
                        // set mBooting
                        synchronized (mService) {
                            //mStack.mService.setBootingVal(true);   //no need with andorid n
                            //mStack.resumeTopActivityLocked(null); old
                            //new
                            mService.resumeTopActivityOnSystemReadyFocusedStackLocked();
                        }
                    } else {
                       ActivityManagerPlus.ipoBootCompleted();
                    }
                } else if ("android.intent.action.normal.shutdown"
                        .equals(action)) {
                    Log.v(TAG, "DeskClock normally shutdowns device");
                    ActivityManagerPlus.createIPOWin();
                    if (mRollback) {
                        checkFlightMode(false, true);
                    }
                } else if (ALARM_BOOT_DONE.equals(action)) {
                    Log.w(TAG, "ALARM_BOOT_DONE normally shutdowns device");
                    // do we need to remove the synchronized ?
                    synchronized (mService) {
                    //mStack.resumeTopActivityLocked(null);  //old
                    mService.resumeTopActivityOnSystemReadyFocusedStackLocked(); //new
                    }
                } else if (REMOVE_IPOWIN.equals(action)) {
                    ActivityManagerPlus.removeIPOWin();
                } else if ("android.intent.action.ACTION_BOOT_IPO".equals(action) &&
                        isAlarmBoot())
                {
                        Slog.v(TAG, "power off alarm enabled");
                        // M: move from ActivityManager to Poweroffalarmutility
                        // to call launchPowrOffAlarm directly
                        // in original design launchPowrOffAlarm will be delayed for 500ms
                        // but remove due to no Handler in PowerOffAlarmUtility
                        // TODO: check if we need Handler in PowerOffAlarmUtility
                        launchPowerOffAlarm(false, false);
                }
            }
        }, filter);
    }

    /**
     * Power Off Alarm feature: Update the flight mode status when Power Off
     * Alarm is triggered
     */
    private void checkFlightMode(boolean recover, boolean shutdown) {
        Log.v(TAG, "mRollback = " + mRollback + ", recover = " + recover);

        if (recover) {
            Log.v(TAG, "since system crash, switch flight mode to off");
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0);
            SystemProperties.set("persist.sys.ams.recover", "false");
            return;
        }

        if (mRollback) {
            mRollback = false;
            SystemProperties.set("persist.sys.ams.recover", "false");
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0);

            if (!shutdown) {
                Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
                intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                intent.putExtra("state", false);
                mContext.sendBroadcast(intent);
            }
        } else {
            boolean mode = Settings.Global.getInt(
                    mContext.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) != 0;

            if (!mode) {
                Log.v(TAG, "turn on flight mode");
                SystemProperties.set("persist.sys.ams.recover", "true");
                mRollback = true;
                Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.AIRPLANE_MODE_ON, 1);
                Intent intent_turn_on = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
                intent_turn_on.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                intent_turn_on.putExtra("state", true);
                mContext.sendBroadcast(intent_turn_on);
            }
        }
    }

    /// M: ALPS02042538 Avoid re-do odex after alarm snooze.
    private void markBootComplete(Installer installer) {
        ArraySet<String> completedIsas = new ArraySet<String>();
        for (String abi : Build.SUPPORTED_ABIS) {
            Process.establishZygoteConnectionForAbi(abi);
            final String instructionSet = VMRuntime.getInstructionSet(abi);
            if (!completedIsas.contains(instructionSet)) {
                try {
                    installer.markBootComplete(VMRuntime.getInstructionSet(abi));
                } catch (InstallerException e) {
                    Slog.e(TAG, "Unable to mark boot complete for abi: " + abi, e);
                }
                completedIsas.add(instructionSet);
            }
        }
    }
    /// @}

    /// M: Use AMEventHook @{
    public static PowerOffAlarmUtility getInstance(SystemReady data) {
        Context context = (Context) data.get(SystemReady.Index.context);
        ActivityManagerService ams = (ActivityManagerService) data.get(SystemReady.Index.ams);
        return PowerOffAlarmUtility.getInstance(context, ams);
    }

    public void onSystemReady(
        SystemReady data, AMEventHookResult result) {
        int phase = data.getInt(SystemReady.Index.phase);
        Context context = (Context) data.get(SystemReady.Index.context);
        ActivityManagerService ams = (ActivityManagerService) data.get(SystemReady.Index.ams);
        switch (phase) {
        case 200:
            mFirstBoot = true;
            break;
        case 300:
            if (!PowerOffAlarmUtility.isAlarmBoot()) {
                result.addAction(AMEventHookAction.AM_SkipHomeActivityLaunching);
            }
            break;
        case 400:
            if (PowerOffAlarmUtility.isAlarmBoot()) {
                result.addAction(AMEventHookAction.AM_PostEnableScreenAfterBoot);
            }
            break;
        default:
            break;
        }
    }

    public void onAfterPostEnableScreenAfterBoot(
        AfterPostEnableScreenAfterBoot data, AMEventHookResult result) {
        Installer installer = (Installer) data.get(AfterPostEnableScreenAfterBoot.Index.installer);

        /// M: ALPS02042538 Avoid re-do odex after alarm snooze.
        markBootComplete(installer);
        /// @}

        Slog.v(TAG, "power off alarm enabled");
        launchPowerOffAlarm(false, false);

        result.addAction(AMEventHookAction.AM_Interrupt);
    }
    /// M: Use AMEventHook @}
}
