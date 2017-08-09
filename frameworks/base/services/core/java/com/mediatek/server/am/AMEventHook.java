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
 * MediaTek Inc. (C) 2016. All rights reserved.
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
package com.mediatek.server.am;

import android.content.Context;
import android.content.pm.PackageManagerInternal;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Slog;

import com.android.server.LocalServices;
import com.mediatek.aee.ExceptionLog;
import com.mediatek.alarm.PowerOffAlarmUtility;
import com.mediatek.am.AMEventHookAction;
import com.mediatek.am.AMEventHookData;
import com.mediatek.am.AMEventHookResult;

/// M : [process suppression] @{
import com.mediatek.apm.frc.FocusRelationshipChainPolicy;
import com.mediatek.apm.suppression.SuppressionAction;
/// M : [process suppression] @}

/// M: AWS feature
import com.mediatek.appworkingset.AWSManager;

import com.mediatek.ipomanager.ActivityManagerPlus;
import com.mediatek.perfservice.PerfServiceWrapper;

/// M: add for running booster feature
import com.mediatek.runningbooster.RunningBoosterService;

import com.mediatek.stk.IdleScreen;

import java.util.ArrayList;

/**
 * M: ActivityManager Event Hook.
 *
 * {@hide}
 */
public final class AMEventHook {
    private static final String TAG = "AMEventHook";
    private static boolean DEBUG = false;
    private static boolean DEBUG_FLOW = false;
    private static boolean DEBUG_EVENT_DETAIL = false;

    // log enhancement
    private static final boolean IS_USER_DEBUG_BUILD = "userdebug".equals(Build.TYPE);
    private static final boolean IS_USER_BUILD = "user".equals(Build.TYPE) || IS_USER_DEBUG_BUILD;

    /**
     * Create the instance of AMEventHookData.
     *
     * @return The instance of AMEventHookData
     */
    public static AMEventHook createInstance() {
        synchronized (AMEventHook.class) {
            return new AMEventHook();
        }
    }

    /**
      * Enable debug flags.
      *
      * @param on true for enabling debug flags
      */
    public static void setDebug(boolean on) {
        AMEventHook.DEBUG = on;
        AMEventHook.DEBUG_FLOW = on;
    }

    /**
     * Enable debug event flags.
     *
     * @param on true for enabling debug event flags
     */
    public static void setEventDetailDebug(boolean on) {
        AMEventHook.DEBUG = on;
        AMEventHook.DEBUG_FLOW = on;
        AMEventHook.DEBUG_EVENT_DETAIL = on;
    }

    /// Start showing event information
    private void showLogForBeforeActivitySwitch(AMEventHookData.BeforeActivitySwitch data) {
        String lastResumedActivityName = data.getString(
                AMEventHookData.BeforeActivitySwitch.Index.lastResumedActivityName);
        String nextResumedActivityName = data.getString(
                AMEventHookData.BeforeActivitySwitch.Index.nextResumedActivityName);
        String lastResumedPackageName = data.getString(
                AMEventHookData.BeforeActivitySwitch.Index.lastResumedPackageName);
        String nextResumedPackageName = data.getString(
                AMEventHookData.BeforeActivitySwitch.Index.nextResumedPackageName);
        int lastResumedActivityType = data.getInt(
                AMEventHookData.BeforeActivitySwitch.Index.lastResumedActivityType);
        int nextResumedActivityType = data.getInt(
                AMEventHookData.BeforeActivitySwitch.Index.nextResumedActivityType);
        boolean isNeedToPauseActivityFirst = data.getBoolean(
                AMEventHookData.BeforeActivitySwitch.Index.isNeedToPauseActivityFirst);
        ArrayList<String> nextTaskPackageList =
                (ArrayList<String>) data.get(
                        AMEventHookData.BeforeActivitySwitch.Index.nextTaskPackageList);

        Slog.v(TAG, "onBeforeActivitySwitch, from: (" + lastResumedPackageName + ", " +
                lastResumedActivityName + ", " + lastResumedActivityType +
                "), to: (" + nextResumedPackageName + ", " +
                nextResumedActivityName + ", " + nextResumedActivityType +
                "), isNeedToPauseActivityFirst: " + isNeedToPauseActivityFirst);
        if (nextTaskPackageList != null) {
            for (int i = 0; i < nextTaskPackageList.size(); i++) {
                Slog.v(TAG, "onBeforeActivitySwitch, nextTaskPackageList[" + i + "] = " +
                        nextTaskPackageList.get(i));
            }
        }
    }

    private void showLogForWakefulnessChanged(AMEventHookData.WakefulnessChanged data) {
        int wakefulness = data.getInt(AMEventHookData.WakefulnessChanged.Index.wakefulness);

        Slog.v(TAG, "onWakefulnessChanged, wakefulness: " + wakefulness);
    }

    private void showLogForAfterActivityResumed(AMEventHookData.AfterActivityResumed data) {
        int pid = data.getInt(AMEventHookData.AfterActivityResumed.Index.pid);
        String activityName = data.getString(
                AMEventHookData.AfterActivityResumed.Index.activityName);
        String pkgName = data.getString(AMEventHookData.AfterActivityResumed.Index.packageName);
        int activityType = data.getInt(AMEventHookData.AfterActivityResumed.Index.activityType);

        Slog.v(TAG, "onAfterActivityResumed, Activity:(" + pid + ", " + pkgName +
                " " + activityName + ", " + activityType + ")");
    }

    private void showLogForActivityThreadResumedDone(
            AMEventHookData.ActivityThreadResumedDone data) {
        String pkgName = data.getString(
                AMEventHookData.ActivityThreadResumedDone.Index.packageName);

        Slog.v(TAG, "onActivityThreadResumedDone, Activity package: " + pkgName);
    }
    /// End showing event information

    boolean isDebuggableMessage(Event event) {
        switch (event) {
        case AM_WakefulnessChanged:
            return false;
        case AM_ReadyToStartService:
            return false;
        case AM_ReadyToGetProvider:
            return false;
        case AM_ReadyToStartDynamicReceiver:
            return false;
        case AM_ReadyToStartStaticReceiver:
            return false;
        case AM_AfterActivityResumed:
            return false;
        case AM_ReadyToStartComponent:
            return false;
        case AM_PackageStoppedStatusChanged:
            return false;
        case AM_ActivityThreadResumedDone:
            return false;
        default:
            return true;
        }
    }

    /**
     * The event hook when something happened in AMS.
     *
     * @param event The event. Defined in AMEventHook.Event.
     * @param data The data used for the event.
     *
     * @return The result of doing the event
     */
    public AMEventHookResult hook(Event event, Object data) {
        return onEvent(event, data);
    }

    /**
     * Hook events.
     *
     * Naming suggestion: AM_<WhatHappened>
     */
    public enum Event {
        AM_EndOfAMSCtor,
        AM_EndOfErrorDumpThread,
        AM_BeforeSendBootCompleted,
        AM_SystemReady,
        AM_AfterPostEnableScreenAfterBoot,
        AM_SkipStartActivity,
        AM_BeforeGoHomeWhenNoActivities,
        AM_EndOfActivityIdle,
        AM_BeforeShowAppErrorDialog,
        AM_BeforeSendBroadcast,
        AM_BeforeActivitySwitch,
        AM_AfterActivityResumed,
        AM_AfterActivityPaused,
        AM_AfterActivityStopped,
        AM_AfterActivityDestroyed,
        AM_WindowsVisible,
        AM_WakefulnessChanged,
        AM_ReadyToStartService,
        AM_ReadyToGetProvider,
        AM_ReadyToStartDynamicReceiver,
        AM_ReadyToStartStaticReceiver,
        AM_ReadyToStartComponent,
        AM_PackageStoppedStatusChanged,
        AM_ActivityThreadResumedDone,
    }

    /**
     * AMEventHook Constructor.
     */
    public AMEventHook() {
        if (DEBUG_FLOW) {
            Slog.d(TAG, "AMEventHook()", new Throwable());
        } else if (DEBUG) {
            Slog.d(TAG, "AMEventHook()");
        }
    }

    /// M: AEE feature @{
    private ExceptionLog exceptionLog = null;
    /// M: AEE feature @}
    /// M: Power-off alarm feature @{
    private PowerOffAlarmUtility mPowerOffAlarmUtility = null;
    /// M: Power-off alarm feature @}
    /// M: STK IDLE SCREEN feature @{
    private IdleScreen mIdleScreen = null;
    /// M: STK IDLE SCREEN feature @}
    /// M: PerfService @{
    private PerfServiceWrapper mPerfService = null;
    /// M: PerfService @}

    /// M: IPO feature
    private ActivityManagerPlus mActivityManagerPlus = null;

    /// M: Running booster @{
    FocusRelationshipChainPolicy frcPolicy = null;
    /// M: Running booster @}

    /// M : [process suppression] @{
    SuppressionAction suppressionAction = null;
    /// M : [process suppression] @}

    /// M: add for running booster feature
    RunningBoosterService runningBoosterService = null;

    /// M: add for AWS feature
    AWSManager mAWSManager = null;

    /**
     * Handle each event.
     */
    private AMEventHookResult onEvent(Event event, Object data) {
        if (DEBUG_FLOW) {
            Slog.d(TAG, "onEvent: " + event, new Throwable());
        } else if (DEBUG || (!IS_USER_BUILD && isDebuggableMessage(event))) {
            Slog.d(TAG, "onEvent: " + event);
        }

        AMEventHookResult result = null;
        switch (event) {
        case AM_EndOfAMSCtor:
            result = onEndOfAMSCtor(
                (AMEventHookData.EndOfAMSCtor) data);
            break;
        case AM_EndOfErrorDumpThread:
            result = onEndOfErrorDumpThread(
                (AMEventHookData.EndOfErrorDumpThread) data);
            break;
        case AM_BeforeSendBootCompleted:
            result = onBeforeSendBootCompleted(
                (AMEventHookData.BeforeSendBootCompleted) data);
            break;
        case AM_SystemReady:
            result = onSystemReady(
                (AMEventHookData.SystemReady) data);
            break;
        case AM_AfterPostEnableScreenAfterBoot:
            result = onAfterPostEnableScreenAfterBoot(
                (AMEventHookData.AfterPostEnableScreenAfterBoot) data);
            break;
        case AM_SkipStartActivity:
            result = onSkipStartActivity(
                (AMEventHookData.SkipStartActivity) data);
            break;
        case AM_BeforeGoHomeWhenNoActivities:
            result = onBeforeGoHomeWhenNoActivities(
                (AMEventHookData.BeforeGoHomeWhenNoActivities) data);
            break;
        case AM_EndOfActivityIdle:
            result = onEndOfActivityIdle(
                (AMEventHookData.EndOfActivityIdle) data);
            break;
        case AM_BeforeShowAppErrorDialog:
            result = onBeforeShowAppErrorDialog(
                (AMEventHookData.BeforeShowAppErrorDialog) data);
            break;
        case AM_BeforeSendBroadcast:
            result = onBeforeSendBroadcast(
                (AMEventHookData.BeforeSendBroadcast) data);
            break;
        case AM_BeforeActivitySwitch:
            result = onBeforeActivitySwitch(
                (AMEventHookData.BeforeActivitySwitch) data);
            break;
        case AM_AfterActivityResumed:
            result = onAfterActivityResumed(
                (AMEventHookData.AfterActivityResumed) data);
            break;
        case AM_AfterActivityPaused:
            result = onAfterActivityPaused(
                (AMEventHookData.AfterActivityPaused) data);
            break;
        case AM_AfterActivityStopped:
            result = onAfterActivityStopped(
                (AMEventHookData.AfterActivityStopped) data);
            break;
        case AM_AfterActivityDestroyed:
            result = onAfterActivityDestroyed(
                (AMEventHookData.AfterActivityDestroyed) data);
            break;
        case AM_WindowsVisible:
            result = onWindowsVisible(
                (AMEventHookData.WindowsVisible) data);
            break;
        case AM_WakefulnessChanged:
            result = onWakefulnessChanged(
                (AMEventHookData.WakefulnessChanged) data);
            break;
        case AM_ReadyToStartService:
            result = onReadyToStartService(
                (AMEventHookData.ReadyToStartService) data);
            break;
        case AM_ReadyToGetProvider:
            result = onReadyToGetProvider(
                (AMEventHookData.ReadyToGetProvider) data);
            break;
        case AM_ReadyToStartDynamicReceiver:
            result = onReadyToStartDynamicReceiver(
                (AMEventHookData.ReadyToStartDynamicReceiver) data);
            break;
        case AM_ReadyToStartStaticReceiver:
            result = onReadyToStartStaticReceiver(
                (AMEventHookData.ReadyToStartStaticReceiver) data);
            break;
        case AM_ReadyToStartComponent:
            result = onReadyToStartComponent(
                (AMEventHookData.ReadyToStartComponent) data);
            break;
        case AM_PackageStoppedStatusChanged:
            result = onPackageStoppedStatusChanged(
                (AMEventHookData.PackageStoppedStatusChanged) data);
            break;
        case AM_ActivityThreadResumedDone:
            result = onActivityThreadResumedDone(
                (AMEventHookData.ActivityThreadResumedDone) data);
            break;
        default:
            Slog.w(TAG, "Unknown event: " + event);
        }

        if (DEBUG) {
            Slog.d(TAG, "onEvent result: " + result);
        }
        return result;
    }

    /**
     * Handle AM_EndOfAMSCtor event.
     */
    private AMEventHookResult onEndOfAMSCtor(
        AMEventHookData.EndOfAMSCtor data) {
        AMEventHookResult result = null;

        /// M: AEE feature @{
        if (exceptionLog == null) {
            if (SystemProperties.get("ro.have_aee_feature").equals("1")) {
                exceptionLog = new ExceptionLog();
            }
        }
        /// M: AEE feature @}

        return result;
    }

    /**
     * Handle AM_EndOfErrorDumpThread event.
     */
    private AMEventHookResult onEndOfErrorDumpThread(
        AMEventHookData.EndOfErrorDumpThread data) {
        AMEventHookResult result = null;

        /// M: AEE feature @{
        if (exceptionLog != null) {
            exceptionLog.onEndOfErrorDumpThread(data);
        }
        /// M: AEE feature @}

        return result;
    }

    /**
     * Handle AM_BeforeSendBootCompleted event.
     */
    private AMEventHookResult onBeforeSendBootCompleted(
        AMEventHookData.BeforeSendBootCompleted data) {
        AMEventHookResult result = new AMEventHookResult();

        /// M: Power-off alarm feature @{
        if (PowerOffAlarmUtility.isAlarmBoot()) {
            result.addAction(AMEventHookAction.AM_Interrupt);
        }
        /// M: Power-off alarm feature @}

        return result;
    }

    /**
     * Handle AM_SystemReady event.
     *
     * phase   0: On start of AMS systemReady(final Runnable goingCallback) method.
     * phase 200: After printing out "System now ready" log.
     * phase 300: Before calling startHomeActivityLocked to launch home activity.
     * phase 400: Before calling resumeFocusedStackTopActivityLocked to resume top activity.
     */
    private AMEventHookResult onSystemReady(
        AMEventHookData.SystemReady data) {
        AMEventHookResult result = new AMEventHookResult();

        /// M: Power-off alarm feature @{
        if (mPowerOffAlarmUtility == null) {
            mPowerOffAlarmUtility = PowerOffAlarmUtility.getInstance(data);
        }
        mPowerOffAlarmUtility.onSystemReady(data, result);
        /// M: Power-off alarm feature @}

        /// M: IPO feature
        if (null == mActivityManagerPlus) {
            mActivityManagerPlus = ActivityManagerPlus.getInstance(data);
        }

        /// M: STK IDLE SCREEN feature @{
        if (mIdleScreen == null) {
            mIdleScreen = new IdleScreen();
        }
        mIdleScreen.onSystemReady(data);
        /// M: STK IDLE SCREEN feature @}

        /// M: PerfService @{
        if(mPerfService == null) {
            mPerfService = new PerfServiceWrapper(null);
        }
        /// M: PerfService @}

        if ("1".equals(SystemProperties.get("persist.runningbooster.support")) ||
                "1".equals(SystemProperties.get("ro.mtk_aws_support"))) {
            /// M: Running booster @{
            if (frcPolicy == null) {
                frcPolicy = FocusRelationshipChainPolicy.getInstance();
            }
            /// M: Running booster @}

            /// M : [process suppression] @{
            if (suppressionAction == null) {
                suppressionAction = SuppressionAction.getInstance
                        ((Context) data.get(AMEventHookData.SystemReady.Index.context));
            }
            /// M : [process suppression] @}
        }
        /// M: add for running booster feature @{
        if ("1".equals(SystemProperties.get("persist.runningbooster.support"))) {
            runningBoosterService = RunningBoosterService.getInstance(data);
        }
        /// M: add for running booster feature @}

        /// M: add for AWS feature @{
        if (SystemProperties.get("ro.mtk_aws_support").equals("1")) {
            if (mAWSManager == null) {
                mAWSManager = AWSManager.getInstance(data);
            }
        }
        /// M: add for AWS feature @}

        return result;
    }

    /**
     * Handle AM_AfterPostEnableScreenAfterBoot event.
     */
    private AMEventHookResult onAfterPostEnableScreenAfterBoot(
        AMEventHookData.AfterPostEnableScreenAfterBoot data) {
        AMEventHookResult result = new AMEventHookResult();

        /// M: Power-off alarm feature @{
        if (mPowerOffAlarmUtility != null) {
            mPowerOffAlarmUtility.onAfterPostEnableScreenAfterBoot(data, result);
        }
        /// M: Power-off alarm feature @}

        return result;
    }

    /**
     * Handle AM_SkipStartActivity event.
     */
    private AMEventHookResult onSkipStartActivity(
        AMEventHookData.SkipStartActivity data) {
        AMEventHookResult result = new AMEventHookResult();

        /// M: Power-off alarm feature @{
        if (PowerOffAlarmUtility.isAlarmBoot()) {
            Slog.d(TAG, "Skip by alarm boot");
            result.addAction(AMEventHookAction.AM_SkipStartActivity);
        }
        /// M: Power-off alarm feature @}

        return result;
    }

    /**
     * Handle AM_BeforeGoHomeWhenNoActivities event.
     */
    private AMEventHookResult onBeforeGoHomeWhenNoActivities(
        AMEventHookData.BeforeGoHomeWhenNoActivities data) {
        AMEventHookResult result = new AMEventHookResult();

        /// M: Power-off alarm feature @{
        if (PowerOffAlarmUtility.isAlarmBoot()) {
            Slog.v(TAG, "Skip to resume home activity!!");
            result.addAction(AMEventHookAction.AM_SkipHomeActivityLaunching);
        }
        /// M: Power-off alarm feature @}

        return result;
    }

    /**
     * Handle AM_EndOfActivityIdle event.
     */
    private AMEventHookResult onEndOfActivityIdle(
        AMEventHookData.EndOfActivityIdle data) {
        AMEventHookResult result = null;

        /// M: STK IDLE SCREEN feature @{
        if (mIdleScreen != null) {
            mIdleScreen.onEndOfActivityIdle(data);
        }
        /// M: STK IDLE SCREEN feature @}

        return result;
    }

    /**
     * Handle AM_BeforeShowAppErrorDialog event.
     */
    private AMEventHookResult onBeforeShowAppErrorDialog(
        AMEventHookData.BeforeShowAppErrorDialog data) {
        AMEventHookResult result = new AMEventHookResult();
        /// M: CTA requirement - Enhanced exception dialog by runtime permission @{
        PackageManagerInternal pkgMgrInternal =
                LocalServices.getService(PackageManagerInternal.class);
        pkgMgrInternal.initMtkPermErrorDialog(data, result);
        ///@}
        return result;
    }

    /**
     * Handle AM_BeforeSendBroadcast event.
     */
    private AMEventHookResult onBeforeSendBroadcast(
        AMEventHookData.BeforeSendBroadcast data) {
        AMEventHookResult result = new AMEventHookResult();

        // M: IPO feature
        if (mActivityManagerPlus != null) {
            result = mActivityManagerPlus.filterBroadcast(data, result);
        }

        /// M : [process suppression] @{
        if ("1".equals(SystemProperties.get("persist.runningbooster.support")) ||
                "1".equals(SystemProperties.get("ro.mtk_aws_support"))) {
            if (suppressionAction != null) {
                result = suppressionAction.onBeforeSendBroadcast(data, result);
            }
        }
        /// M : [process suppression] @}

        return result;
    }

    /**
     * Handle AM_BeforeActivitySwitch event.
     * If Activity A wants to start Activity B, the event occurs two times
     * 1. The event occurs before A is paused and isNeedToPauseActivityFirst = true
     * 2. The event occurs after A is paused and isNeedToPauseActivityFirst = false
     */
    private AMEventHookResult onBeforeActivitySwitch(
        AMEventHookData.BeforeActivitySwitch data) {
        if (AMEventHook.DEBUG_EVENT_DETAIL) {
            showLogForBeforeActivitySwitch(data);
        }
        AMEventHookResult result = null;

        /// M: PerfService @{
        if(mPerfService != null) {
            mPerfService.amsBoostResume(data);
        }
        /// M: PerfService @}

        /// M: add for running booster feature @{
        if (null != runningBoosterService) {
            runningBoosterService.onBeforeActivitySwitch(data);
        }
        /// M: add for running booster feature @}

        /// M: add for AWS feature @{
        if (mAWSManager != null) {
            mAWSManager.onBeforeActivitySwitch(data);
        }
        /// M: add for AWS feature @}

        /// M: Running booster @{
        if (frcPolicy != null) {
            frcPolicy.onStartActivity(data);
        }
        /// M: Running booster @}

        return result;
    }

    /**
     * Handle AM_AfterActivityResumed event.
     * It is the activity status that AMS records,
     * maybe resuming ActivityThread is not completed.
     */
    private AMEventHookResult onAfterActivityResumed(
        AMEventHookData.AfterActivityResumed data) {
        if (AMEventHook.DEBUG_EVENT_DETAIL) {
            showLogForAfterActivityResumed(data);
        }
        AMEventHookResult result = null;

        /// M: PerfService @{
        if(mPerfService != null) {
            mPerfService.onAfterActivityResumed(data);
        }
        /// M: PerfService @}

        /// M: add for running booster feature @{
        if (null != runningBoosterService) {
            runningBoosterService.onAfterActivityResumed(data);
        }
        /// M: add for running booster feature @}
        return result;
    }

    /**
     * Handle AM_AfterActivityPaused event.
     * It is the activity status that AMS records,
     * maybe pausing ActivityThread is not completed.
     */
    private AMEventHookResult onAfterActivityPaused(
        AMEventHookData.AfterActivityPaused data) {
        AMEventHookResult result = null;

        /// M: PerfService @{
        if(mPerfService != null) {
            mPerfService.onAfterActivityPaused(data);
        }
        /// M: PerfService @}
        return result;
    }

    /**
     * Handle AM_AfterActivityStopped event.
     * It is the activity status that AMS records,
     * maybe stopping ActivityThread is not completed.
     */
    private AMEventHookResult onAfterActivityStopped(
        AMEventHookData.AfterActivityStopped data) {
        AMEventHookResult result = null;

        /// M: PerfService @{
        if(mPerfService != null) {
            mPerfService.onAfterActivityStopped(data);
        }
        /// M: PerfService @}
        return result;
    }

    /**
     * Handle AM_AfterActivityDestroyed event.
     * It is the activity status that AMS records,
     * maybe destroying ActivityThread is not completed.
     */
    private AMEventHookResult onAfterActivityDestroyed(
        AMEventHookData.AfterActivityDestroyed data) {
        AMEventHookResult result = null;

        /// M: PerfService @{
        if(mPerfService != null) {
            mPerfService.onAfterActivityDestroyed(data);
        }
        /// M: PerfService @}
        return result;
    }

    /**
     * Handle AM_WindowsVisible event.
     */
    private AMEventHookResult onWindowsVisible(
        AMEventHookData.WindowsVisible data) {
        AMEventHookResult result = null;

        /// M: PerfService @{
        if(mPerfService != null) {
            mPerfService.amsBoostStop();
        }
        /// M: PerfService @}
        return result;
    }

    /**
     * Handle AM_WakefulnessChanged event.
     */
    private AMEventHookResult onWakefulnessChanged(
        AMEventHookData.WakefulnessChanged data) {
        if (AMEventHook.DEBUG_EVENT_DETAIL) {
            showLogForWakefulnessChanged(data);
        }
        AMEventHookResult result = null;

        /// M: add for running booster feature @{
        if (null != runningBoosterService) {
            runningBoosterService.onWakefulnessChanged(data);
        }
        /// M: add for running booster feature @}
        return result;
    }

    /**
     * Handle AM_ReadyToStartService event.
     */
    private AMEventHookResult onReadyToStartService(
        AMEventHookData.ReadyToStartService data) {
        AMEventHookResult result = null;

        /// M: Running booster @{
        if (frcPolicy != null) {
            frcPolicy.onStartService(data);
        }
        /// M: Running booster @}

        return result;
    }

    /**
     * Handle AM_ReadyToGetProvider event.
     */
    private AMEventHookResult onReadyToGetProvider(
        AMEventHookData.ReadyToGetProvider data) {
        AMEventHookResult result = null;

        /// M: Running booster @{
        if (frcPolicy != null) {
            frcPolicy.onStartProvider(data);
        }
        /// M: Running booster @}

        return result;
    }

    /**
     * Handle AM_ReadyToStartDynamicReceiver event.
     */
    private AMEventHookResult onReadyToStartDynamicReceiver(
        AMEventHookData.ReadyToStartDynamicReceiver data) {
        AMEventHookResult result = null;

        /// M: Running booster @{
        if (frcPolicy != null) {
            frcPolicy.onStartDynamicReceiver(data);
        }
        /// M: Running booster @}

        return result;
    }

    /**
     * Handle AM_onReadyToStartStaticReceiver event.
     */
    private AMEventHookResult onReadyToStartStaticReceiver(
        AMEventHookData.ReadyToStartStaticReceiver data) {
        AMEventHookResult result = null;

        /// M: Running booster @{
        if (frcPolicy != null) {
            frcPolicy.onStartStaticReceiver(data);
        }
        /// M: Running booster @}

        return result;
    }

    /**
      * Handle AM_ReadyToStartProcess event.
      */
    private AMEventHookResult onReadyToStartComponent(
        AMEventHookData.ReadyToStartComponent data) {
        AMEventHookResult result = null;

        /// M : [process suppression] @{
        if ("1".equals(SystemProperties.get("persist.runningbooster.support")) ||
                "1".equals(SystemProperties.get("ro.mtk_aws_support"))) {
            if (suppressionAction != null) {
                suppressionAction.onReadyToStartComponent(data);
            }
        }
        /// M : [process suppression] @}

        return result;
    }

    /**
     * Handle AM_PackageStoppedStatusChanged event.
     */
    private AMEventHookResult onPackageStoppedStatusChanged(
        AMEventHookData.PackageStoppedStatusChanged data) {
        AMEventHookResult result = null;

        /// M : [process suppression] @{
        if ("1".equals(SystemProperties.get("persist.runningbooster.support")) ||
                "1".equals(SystemProperties.get("ro.mtk_aws_support"))) {
            if (suppressionAction != null) {
                suppressionAction.onPackageStoppedStatusChanged(data);
            }
        }
        /// M : [process suppression] @}

        return result;
    }

    /**
     * Handle AM_ActivityThreadResumedDone event.
     */
    private AMEventHookResult onActivityThreadResumedDone(
        AMEventHookData.ActivityThreadResumedDone data) {
        if (AMEventHook.DEBUG_EVENT_DETAIL) {
            showLogForActivityThreadResumedDone(data);
        }
        AMEventHookResult result = null;

        /// M: add for running booster feature @{
        if (null != runningBoosterService) {
            runningBoosterService.onActivityThreadResumedDone(data);
        }
        /// M: add for running booster feature @}
        return result;
    }
}
