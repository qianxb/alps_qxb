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

package com.mediatek.perfservice;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.UEventObserver;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.util.Log;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;

import dalvik.system.VMRuntime;

public class PerfServiceManager implements IPerfServiceManager {
    private static final String TAG = "PerfServiceManager";
    private HandlerThread mHandlerThread;
    private PerfServiceThreadHandler mHandler;
    private Context mContext;
    final List<Integer> mTimeList;


    private boolean bDuringTouch;
    private boolean bRenderAwareValid; // render aware is only valid within 3 sec.
    private static final int RENDER_AWARE_DURATION_MS = 3000;
    private static final int UI_UPDATE_DURATION_MS = 300;  // 2 frame should be within this value
    private static final int RENDER_BIT = 0x800000;
    private static final float HEAP_UTILIZATION_DURING_FRAME_UPDATE = 0.5f;
    private static final int GAME_LAUNCH_DURATION = 10;
    private static final int APP_LAUNCH_DURATION = 3; // begin to detect application

    private int mDisplayType;
    private VMRuntime mRuntime;
    private float mDefaultUtilization;
    private PackageManager mPm;
    private String mCurrPack;
    private SmartObserver mSmartObserver;

    public static native int nativePerfBoostEnable(int scenario);
    public static native int nativePerfBoostDisable(int scenario);
    public static native int nativePerfNotifyAppState(String packName, String className,
                                                      int state, int pid);
    public static native int nativePerfUserScnReg(int scn_core, int scn_freq, int pid, int tid);
    public static native int nativePerfUserScnRegBigLittle(int scn_core_big, int scn_freq_big, int scn_core_little, int scn_freq_little, int pid, int tid);
    public static native int nativePerfUserScnUnreg(int handle);
    public static native int nativePerfUserGetCapability(int cmd);
    public static native int nativePerfUserRegScn(int pid, int tid);
    public static native int nativePerfUserRegScnConfig(int handle, int cmd, int param_1, int param_2, int param_3, int param_4);
    public static native int nativePerfUserUnregScn(int handle);
    public static native int nativePerfUserScnEnable(int handle);
    public static native int nativePerfUserScnDisable(int handle);
    public static native int nativePerfUserScnResetAll();
    public static native int nativePerfUserScnDisableAll();
    public static native int nativePerfUserScnRestoreAll();
    public static native int nativePerfDumpAll();
    public static native int nativePerfSetFavorPid(int pid);
    public static native int nativePerfRestorePolicy(int pid);
    public static native int nativePerfNotifyUserStatus(int type, int status);
    public static native int nativePerfLevelBoost(int level);
    public static native int nativePerfNotifyDisplayType(int type);
    public static native int nativePerfGetLastBoostPid();
    public static native int nativePerfGetClusterInfo(int cmd, int id);
    public static native int nativePerfSetPackAttr(int isSystem, int eabiNum);
    public static native int nativePerfSetUeventIndex(int index);
    public static native int nativePerfGetPackAttr(String packName, int cmd);

    public class PerfServiceAppState {
        private String mPackName;
        private String mClassName;
        private int mState;
        private int mPid;

        PerfServiceAppState(String packName, String className, int state, int pid) {
            mPackName = packName;
            mClassName = className;
            mState = state;
            mPid = pid;
        }
    }

    //static
    //{
    //    Log.w(TAG, "load libperfservice_jni.so");
    //    System.loadLibrary("perfservice_jni");
    //}

    public PerfServiceManager(Context context) {
        super();
        mContext = context;
        mHandlerThread = new HandlerThread("PerfServiceManager", Process.THREAD_PRIORITY_FOREGROUND);
        mHandlerThread.start();
        Looper looper = mHandlerThread.getLooper();
        if (looper != null) {
            mHandler = new PerfServiceThreadHandler(looper);
        }
        mTimeList = new ArrayList<Integer>();
        bDuringTouch = false;
        bRenderAwareValid = false;
        mDisplayType = IPerfServiceWrapper.DISPLAY_TYPE_OTHERS;
        mRuntime = VMRuntime.getRuntime();
        mDefaultUtilization = mRuntime.getTargetHeapUtilization();
        mPm = mContext.getPackageManager();

        mSmartObserver = new SmartObserver();
        mSmartObserver.startObserve();
        log("Created and started PerfService thread");
    }

    public void systemReady() {
        log("systemReady, register ACTION_BOOT_COMPLETED");
    }

    public void boostEnable(int scenario) {
        //log("boostEnable:" + scenario);
        if (IPerfServiceWrapper.SCN_APP_TOUCH == scenario) {
            if (touchEnable() == false)
                return;
        }
        mHandler.stopCheckTimer(scenario);

        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_BOOST_ENABLE;
        msg.arg1 = scenario;
        msg.sendToTarget();
    }

    public void boostDisable(int scenario) {
        //log("boostDisable:" + scenario);
        if (IPerfServiceWrapper.SCN_APP_TOUCH == scenario) {
            if(touchDisable() == false)
                return;
        }
        mHandler.stopCheckTimer(scenario);

        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_BOOST_DISABLE;
        msg.arg1 = scenario;
        msg.sendToTarget();
    }

    public void boostEnableTimeout(int scenario, int timeout) {
        //log("boostEnableTimeout");
        if (IPerfServiceWrapper.SCN_APP_TOUCH == scenario) {
            if (touchEnable() == false)
                return;
        }
        mHandler.stopCheckTimer(scenario);

        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_BOOST_ENABLE_TIMEOUT;
        msg.arg1 = scenario;
        msg.arg2 = timeout;
        msg.sendToTarget();
    }

    public void boostEnableTimeoutMs(int scenario, int timeout_ms) {
        //log("boostEnableTimeoutMs");
        if (IPerfServiceWrapper.SCN_APP_TOUCH == scenario) {
            if (touchEnable() == false)
                return;
        }
        mHandler.stopCheckTimer(scenario);

        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_BOOST_ENABLE_TIMEOUT_MS;
        msg.arg1 = scenario;
        msg.arg2 = timeout_ms;
        msg.sendToTarget();
    }

    public void notifyAppState(String packName, String className, int state, int pid) {
        //log("notifyAppState");
        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_NOTIFY_APP_STATE;
        msg.obj = new PerfServiceAppState(packName, className, state, pid);
        msg.sendToTarget();
    }

    public int userReg(int scn_core, int scn_freq, int pid, int tid) {
        return nativePerfUserScnReg(scn_core, scn_freq, pid, tid);
    }

    public int userRegBigLittle(int scn_core_big, int scn_freq_big, int scn_core_little, int scn_freq_little, int pid, int tid) {
        return nativePerfUserScnRegBigLittle(scn_core_big, scn_freq_big, scn_core_little, scn_freq_little, pid, tid);
    }

    public void userUnreg(int handle) {
        //nativePerfUserScnUnreg(handle);
        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_USER_UNREG;
        msg.arg1 = handle;
        msg.sendToTarget();
    }

    public int userGetCapability(int cmd) {
        return nativePerfUserGetCapability(cmd);
    }

    public int userRegScn(int pid, int tid) {
        return nativePerfUserRegScn(pid, tid);
    }

    public void userRegScnConfig(int handle, int cmd, int param_1, int param_2, int param_3, int param_4) {
        nativePerfUserRegScnConfig(handle, cmd, param_1, param_2, param_3, param_4);
    }

    public void userUnregScn(int handle) {
        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_USER_UNREG_SCN;
        msg.arg1 = handle;
        msg.sendToTarget();
    }

    public void userEnable(int handle) {
        mHandler.stopCheckUserTimer(handle);
        nativePerfUserScnEnable(handle);
        //log("userEnable: " + handle);
    }

    public void userEnableTimeout(int handle, int timeout) {
        mHandler.stopCheckUserTimer(handle);
        nativePerfUserScnEnable(handle);
        mHandler.startCheckUserTimer(handle, timeout);
        //log("userEnableTimeout: " + handle + ", " + timeout);
    }

    public void userEnableTimeoutMs(int handle, int timeout_ms) {
        mHandler.stopCheckUserTimer(handle);
        nativePerfUserScnEnable(handle);
        mHandler.startCheckUserTimerMs(handle, timeout_ms);
        //log("userEnableTimeoutMs: " + handle + ", " + timeout_ms);
    }

    public void userEnableAsync(int handle) {
        mHandler.stopCheckUserTimer(handle);

        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_USER_ENABLE;
        msg.arg1 = handle;
        msg.sendToTarget();
    }

    public void userEnableTimeoutAsync(int handle, int timeout) {
        mHandler.stopCheckUserTimer(handle);

        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_USER_ENABLE_TIMEOUT;
        msg.arg1 = handle;
        msg.arg2 = timeout;
        msg.sendToTarget();
    }

    public void userEnableTimeoutMsAsync(int handle, int timeout_ms) {
        mHandler.stopCheckUserTimer(handle);

        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_USER_ENABLE_TIMEOUT_MS;
        msg.arg1 = handle;
        msg.arg2 = timeout_ms;
        msg.sendToTarget();
    }

    public void userDisable(int handle) {
        mHandler.stopCheckUserTimer(handle);

        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_USER_DISABLE;
        msg.arg1 = handle;
        msg.sendToTarget();
    }

    public void userResetAll() {
        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_USER_RESET_ALL;
        msg.sendToTarget();
    }

    public void userDisableAll() {
        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_USER_DISABLE_ALL;
        msg.sendToTarget();
    }

    public void userRestoreAll() {
        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_USER_RESTORE_ALL;
        msg.sendToTarget();
    }

    public void dumpAll() {
        nativePerfDumpAll();
    }

    public void setFavorPid(int pid) {
        //nativePerfSetFavorPid(pid);
        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_SET_FAVOR_PID;
        msg.arg1 = pid;
        msg.sendToTarget();
    }

    public void restorePolicy(int pid) {
        nativePerfRestorePolicy(pid);
    }

    public void notifyFrameUpdate(int level) {
        if (level != 0)
            nativePerfSetFavorPid(level | RENDER_BIT);

        if(bRenderAwareValid == false)
            return;

        //log("notifyFrameUpdate - bRenderAwareValid:" + bRenderAwareValid);

        mHandler.stopCheckTimer(IPerfServiceWrapper.SCN_SW_FRAME_UPDATE);

        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_BOOST_ENABLE_TIMEOUT_MS;
        msg.arg1 = IPerfServiceWrapper.SCN_SW_FRAME_UPDATE;
        msg.arg2 = UI_UPDATE_DURATION_MS;
        msg.sendToTarget();
    }

    public void notifyDisplayType(int type) {
        log("notifyDisplayType:" + type);
        mDisplayType = type;
        nativePerfNotifyDisplayType(type);
    }

    public int getLastBoostPid() {
        //log("getLastBoostPid " + nativePerfGetLastBoostPid());
        //mDisplayType = type;
        //return "fuck";
        return nativePerfGetLastBoostPid();
    }

    public void notifyUserStatus(int type, int status) {
        //log("notifyUserStatus - type:" + type + " status:" + status);
        nativePerfNotifyUserStatus(type, status);
    }

    public int getClusterInfo(int cmd, int id) {
        return nativePerfGetClusterInfo(cmd, id);
    }

    public boolean touchEnable() {
        nativePerfNotifyUserStatus(IPerfServiceWrapper.NOTIFY_USER_TYPE_SCENARIO_ON,
                                   IPerfServiceWrapper.SCN_APP_TOUCH);
        if (mDisplayType == IPerfServiceWrapper.DISPLAY_TYPE_GAME ||
            mDisplayType == IPerfServiceWrapper.DISPLAY_TYPE_NO_TOUCH_BOOST) {
            bRenderAwareValid = false;
            return false; // disable touch boost
        }
        bDuringTouch = true;
        bRenderAwareValid = true;
        mHandler.stopCheckRenderAwareTimer();
        return true;
    }

    public int getPackAttr(String packName, int cmd) {
        return nativePerfGetPackAttr(packName, cmd);
    }

    public boolean touchDisable() {
        nativePerfNotifyUserStatus(IPerfServiceWrapper.NOTIFY_USER_TYPE_SCENARIO_OFF,
                                   IPerfServiceWrapper.SCN_APP_TOUCH);
        bDuringTouch = false;

        if (mDisplayType == IPerfServiceWrapper.DISPLAY_TYPE_GAME ||
            mDisplayType == IPerfServiceWrapper.DISPLAY_TYPE_NO_TOUCH_BOOST) {
            mHandler.stopCheckTimer(IPerfServiceWrapper.SCN_APP_TOUCH);
            bRenderAwareValid = false;
            return false;
        }
        mHandler.startCheckRenderAwareTimerMs(RENDER_AWARE_DURATION_MS);
        return true;
    }

    public void setUeventIndex(int index) {
        Message msg = mHandler.obtainMessage();
        msg.what = PerfServiceThreadHandler.MESSAGE_SET_UEVENT_INDEX;
        msg.arg1 = index;
        if(index == 6) {
            nativePerfNotifyUserStatus(IPerfServiceWrapper.NOTIFY_USER_TYPE_CORE_ONLINE, 8);
            mHandler.sendMessageDelayed(msg, 100); // 100ms for TLP
        }
        else {
            msg.sendToTarget();
        }
    }

    public void levelBoost(int level) {
        nativePerfLevelBoost(level);
    }

    private class PerfServiceThreadHandler extends Handler {
        private static final int MESSAGE_BOOST_ENABLE            = 0;
        private static final int MESSAGE_BOOST_DISABLE           = 1;
        private static final int MESSAGE_BOOST_ENABLE_TIMEOUT    = 2;
        private static final int MESSAGE_BOOST_ENABLE_TIMEOUT_MS = 3;
        private static final int MESSAGE_NOTIFY_APP_STATE        = 4;

        private static final int MESSAGE_USER_REG                = 10;
        private static final int MESSAGE_USER_REG_BIG_LITTLE     = 11;
        private static final int MESSAGE_USER_UNREG              = 12;
        private static final int MESSAGE_USER_GET_CAPABILITY     = 13;
        private static final int MESSAGE_USER_REG_SCN            = 14;
        private static final int MESSAGE_USER_REG_SCN_CONFIG     = 15;
        private static final int MESSAGE_USER_UNREG_SCN          = 16;
        private static final int MESSAGE_USER_ENABLE             = 17;
        private static final int MESSAGE_USER_ENABLE_TIMEOUT     = 18;
        private static final int MESSAGE_USER_ENABLE_TIMEOUT_MS  = 19;
        private static final int MESSAGE_USER_DISABLE            = 20;
        private static final int MESSAGE_USER_RESET_ALL          = 21;
        private static final int MESSAGE_USER_DISABLE_ALL        = 22;
        private static final int MESSAGE_USER_RESTORE_ALL        = 23;
        private static final int MESSAGE_DUMP_ALL                = 24;
        private static final int MESSAGE_SET_FAVOR_PID           = 25;
        private static final int MESSAGE_NOTIFY_FRAME_UPDATE     = 26;
        private static final int MESSAGE_SW_FRAME_UPDATE_TIMEOUT = 27;
        private static final int MESSAGE_TOUCH_BOOST_DURATION    = 28;
        private static final int MESSAGE_GET_PACK_NAME           = 29;
        private static final int MESSAGE_SET_UEVENT_INDEX        = 30;
        private static final int MESSAGE_START_DETECT            = 31;

        private static final int MESSAGE_TIMER_RENDER_AWARE_DURATION = 40;

        private static final int MESSAGE_TIMER_SCN_BASE          = 100;
        private static final int MESSAGE_TIMER_SCN_USER_BASE     = 200; // last message

        public PerfServiceThreadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            try {
                switch (msg.what) {
                    case MESSAGE_BOOST_ENABLE:
                    {
                        //log("MESSAGE_BOOST_ENABLE:" + msg.arg1);
                         nativePerfBoostEnable(msg.arg1);
                        break;
                    }

                    case MESSAGE_BOOST_DISABLE:
                    {
                        //log("MESSAGE_BOOST_DISABLE");
                        nativePerfBoostDisable(msg.arg1);
                        break;
                    }

                    case MESSAGE_BOOST_ENABLE_TIMEOUT:
                    {
                        //log("MESSAGE_BOOST_ENABLE_TIMEOUT");
                        nativePerfBoostEnable(msg.arg1);
                        startCheckTimer(msg.arg1, msg.arg2);
                        break;
                    }

                    case MESSAGE_BOOST_ENABLE_TIMEOUT_MS:
                    {
                        if (msg.arg1 == IPerfServiceWrapper.SCN_SW_FRAME_UPDATE) {
                            //log("MESSAGE_BOOST_ENABLE_TIMEOUT_MS [SCN_SW_FRAME_UPDATE] bDuringTouchBoost:" + bDuringTouchBoost);
                            if(bRenderAwareValid == false)
                                break;

                            // adjust heap utilization
                            mRuntime.setTargetHeapUtilization(HEAP_UTILIZATION_DURING_FRAME_UPDATE);
                            //float utilization = mRuntime.getTargetHeapUtilization();
                            //log("set utilization:" + utilization);
                        }

                        nativePerfBoostEnable(msg.arg1);
                        startCheckTimerMs(msg.arg1, msg.arg2);
                        break;
                    }

                    case MESSAGE_NOTIFY_APP_STATE:
                    {
                        PerfServiceAppState passedObject = (PerfServiceAppState) msg.obj;
                        //log("MESSAGE_NOTIFY_APP_STATE");
                        nativePerfNotifyAppState(passedObject.mPackName, passedObject.mClassName,
                                                 passedObject.mState, passedObject.mPid);

                        if(passedObject.mState == 1 && mCurrPack != passedObject.mPackName) {
                            try {
                                mCurrPack = passedObject.mPackName;
                                ApplicationInfo info = mPm.getApplicationInfo(mCurrPack, 0);
                                //int abi_num = info.supportAbiNum;
                                int isSystem = (info.isSystemApp()) ? 1 : 0;
                                nativePerfSetPackAttr(isSystem, 0);

                                boostDisable(IPerfServiceWrapper.SCN_GAME_LAUNCH);
                                int cmd = IPerfServiceWrapper.CMD_GET_FOREGROUND_TYPE;
                                int type = nativePerfUserGetCapability(cmd);
                                if(type == 1) {
                                    boostEnableTimeout(IPerfServiceWrapper.SCN_GAME_LAUNCH,
                                                       GAME_LAUNCH_DURATION);
                                }

                                // time to start detect
                                Message msg1 = this.obtainMessage();
                                msg1.what = PerfServiceThreadHandler.MESSAGE_START_DETECT;
                                this.removeMessages(msg1.what);
                                this.sendMessageDelayed(msg1, APP_LAUNCH_DURATION * 1000);

                            } catch (PackageManager.NameNotFoundException e) {
                                log("PackageManager exp:" + e);
                            }
                        }

                        passedObject = null;
                        msg.obj = null;
                        break;
                    }

                    case MESSAGE_TIMER_RENDER_AWARE_DURATION:
                    {
                        log("MESSAGE_TIMER_RENDER_AWARE_DURATION timeout");
                        bRenderAwareValid = false;
                        nativePerfBoostDisable(IPerfServiceWrapper.SCN_SW_FRAME_UPDATE);
                        break;
                    }

                    case MESSAGE_USER_ENABLE:
                    {
                        log("MESSAGE_USER_ENABLE: " + msg.arg1);
                        nativePerfUserScnEnable(msg.arg1);
                        break;
                    }

                    case MESSAGE_USER_DISABLE:
                    {
                        log("MESSAGE_USER_DISABLE: " + msg.arg1);
                        nativePerfUserScnDisable(msg.arg1);
                        break;
                    }

                    case MESSAGE_USER_ENABLE_TIMEOUT:
                    {
                        log("MESSAGE_USER_ENABLE_TIMEOUT: " + msg.arg1 + ", " + msg.arg2);
                        nativePerfUserScnEnable(msg.arg1);
                        startCheckUserTimer(msg.arg1, msg.arg2);
                        break;
                    }

                    case MESSAGE_USER_ENABLE_TIMEOUT_MS:
                    {
                        log("MESSAGE_USER_ENABLE_TIMEOUT_MS: " + msg.arg1 + ", " + msg.arg2);
                        nativePerfUserScnEnable(msg.arg1);
                        startCheckUserTimerMs(msg.arg1, msg.arg2);
                        break;
                    }

                    case MESSAGE_USER_UNREG:
                    {
                        log("MESSAGE_USER_UNREG: " + msg.arg1);
                        nativePerfUserScnUnreg(msg.arg1);
                        break;
                    }

                    case MESSAGE_USER_UNREG_SCN:
                    {
                        log("MESSAGE_USER_UNREG_SCN: " + msg.arg1);
                        nativePerfUserUnregScn(msg.arg1);
                        break;
                    }

                    case MESSAGE_USER_RESET_ALL:
                    {
                        log("MESSAGE_USER_RESET_ALL");
                        stopAllUserTimer();
                        removeAllUserTimerList();
                        nativePerfUserScnResetAll();
                        break;
                    }

                    case MESSAGE_USER_DISABLE_ALL:
                    {
                        log("MESSAGE_USER_DISABLE_ALL");
                        //stopAllUserTimer();
                        nativePerfUserScnDisableAll();
                        break;
                    }

                    case MESSAGE_USER_RESTORE_ALL:
                    {
                        log("MESSAGE_USER_RESTORE_ALL");
                        nativePerfUserScnRestoreAll();
                        break;
                    }

                    case MESSAGE_SET_FAVOR_PID:
                    {
                        //log("MESSAGE_SET_FAVOR_PID");
                        nativePerfSetFavorPid(msg.arg1);
                        break;
                    }

                    case MESSAGE_SET_UEVENT_INDEX:
                    {
                        log("MESSAGE_SET_UEVENT_INDEX: " + msg.arg1);
                        nativePerfSetUeventIndex(msg.arg1);
                        break;
                    }

                    case MESSAGE_START_DETECT:
                    {
                        // start detect
                        //log("MESSAGE_START_DETECT: ");
                        nativePerfNotifyUserStatus(IPerfServiceWrapper.NOTIFY_USER_TYPE_DETECT, 1);
                        break;
                    }

                    //case MESSAGE_LEVEL_BOOST:
                    //{
                    //    //log("MESSAGE_LEVEL_BOOST");
                    //    startCheckTimerMs(msg.arg1, msg.arg2);
                    //    break;
                    //}

                    default:
                    {
                        int msgId = msg.what;
                        log("MESSAGE_TIMEOUT:" + msgId);

                        if (msgId >= MESSAGE_TIMER_SCN_BASE && msgId < MESSAGE_TIMER_SCN_USER_BASE)
                        {
                            int scenario = msgId - MESSAGE_TIMER_SCN_BASE;
                            if (IPerfServiceWrapper.SCN_APP_TOUCH == scenario) {
                                if( touchDisable() == false)
                                    break;
                            }
                            nativePerfBoostDisable(scenario);

                            switch(scenario) {
                            case IPerfServiceWrapper.SCN_SW_FRAME_UPDATE:
                                if (bDuringTouch == false) {
                                    bRenderAwareValid = false;
                                }

                                // adjust heap utilization
                                mRuntime.setTargetHeapUtilization(mDefaultUtilization);
                                float utilization = mRuntime.getTargetHeapUtilization();
                                log("set utilization:" + utilization);
                                break;

                            default:
                                break;
                            }
                        } else if (msgId >= MESSAGE_TIMER_SCN_USER_BASE) {
                            nativePerfUserScnDisable(msg.arg1);
                        }
                        break;
                    }
                }
            } catch (NullPointerException e) {
                loge("Exception in PerfServiceThreadHandler.handleMessage: " + e);
            }
        }

        private void startCheckTimer(int scenario, int timeout) {
            if (scenario <= IPerfServiceWrapper.SCN_NONE || scenario >= IPerfServiceWrapper.SCN_MAX)
            {
                return;
            }

            Message msg = this.obtainMessage();
            msg.what = MESSAGE_TIMER_SCN_BASE + scenario;
            msg.arg1 = scenario;
            this.sendMessageDelayed(msg, timeout * 1000);

            if (!mTimeList.contains(scenario)) {
                mTimeList.add(scenario);
                //log("Add to mTimeList:" + scenario);
            }
        }

        private void startCheckTimerMs(int scenario, int timeout_ms) {
            if (scenario <= IPerfServiceWrapper.SCN_NONE || scenario >= IPerfServiceWrapper.SCN_MAX)
            {
                return;
            }

            Message msg = this.obtainMessage();
            msg.what = MESSAGE_TIMER_SCN_BASE + scenario;
            msg.arg1 = scenario;
            this.sendMessageDelayed(msg, timeout_ms);

            if (!mTimeList.contains(scenario)) {
                mTimeList.add(scenario);
                //log("Add to mTimeList:" + scenario);
            }
        }

        private void stopCheckTimer(int scenario) {
            int timer = MESSAGE_TIMER_SCN_BASE + scenario;
            this.removeMessages(timer);
        }

        private void startCheckRenderAwareTimerMs(int timeout_ms) {
            Message msg = this.obtainMessage();
            msg.what = MESSAGE_TIMER_RENDER_AWARE_DURATION;
            this.sendMessageDelayed(msg, timeout_ms);
        }

        private void stopCheckRenderAwareTimer() {
            this.removeMessages(MESSAGE_TIMER_RENDER_AWARE_DURATION);
        }

        private void startCheckUserTimer(int handle, int timeout) {
            Message msg = this.obtainMessage();
            msg.what = MESSAGE_TIMER_SCN_USER_BASE + handle;
            msg.arg1 = handle;
            this.sendMessageDelayed(msg, timeout * 1000);

            if (!mTimeList.contains(handle)) {
                mTimeList.add(handle);
                //log("Add to mTimeList:" + handle);
            }
        }

        private void startCheckUserTimerMs(int handle, int timeout_ms) {
            Message msg = this.obtainMessage();
            msg.what = MESSAGE_TIMER_SCN_USER_BASE + handle;
            msg.arg1 = handle;
            this.sendMessageDelayed(msg, timeout_ms);

            if (!mTimeList.contains(handle)) {
                mTimeList.add(handle);
                //log("Add to mTimeList:" + handle);
            }
        }

        private void stopCheckUserTimer(int handle) {
            int timer = MESSAGE_TIMER_SCN_USER_BASE + handle;
            this.removeMessages(timer);
        }

        private void stopAllUserTimer() {
            for (int i = 0; i < mTimeList.size(); i++) {
                int timer;
                int handle = mTimeList.get(i);
                if (handle < IPerfServiceWrapper.SCN_MAX) {
                    timer = MESSAGE_TIMER_SCN_BASE + handle;
                } else {
                    timer = MESSAGE_TIMER_SCN_USER_BASE + handle;
                }
                this.removeMessages(timer);
                //log("Stop mTimeList:" + handle);
            }
        }

        private void removeAllUserTimerList() {
            for (int i = mTimeList.size() - 1; i >= 0; i--) {
                mTimeList.remove(i);
                //log("Remove mTimeList:" + i);
            }
            //int size = mTimeList.size();
            //log("mTimeList size:" + size);
        }
    }

    private class SmartObserver extends UEventObserver {
        //private static final String TAG = "HdmiObserver";

        private static final String SMART_UEVENT = "DEVPATH=/devices/virtual/misc/m_smart_misc";

        public SmartObserver() {}

        public void startObserve() {
            startObserving(SMART_UEVENT);
        }

        public void stopObserve() {
            stopObserving();
        }

        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            //log("Smart Observer onUEvent");
            String index_name = event.get("DETECT");
            String action = event.get("ACTION");
            int index = Integer.parseInt(index_name);
            //log("ACTION=" + action + ", DETECT=" + index_name);
            setUeventIndex(index);
        }

    }


    private void log(String info) {
        Log.d("@M_" + TAG, "[PerfService] " + info + " ");
    }

    private void loge(String info) {
        Log.e("@M_" + TAG, "[PerfService] ERR: " + info + " ");
    }

}

