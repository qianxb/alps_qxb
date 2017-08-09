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


import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.util.Log;

public class PerfServiceImpl extends IPerfService.Stub {

    private static final String TAG = "PerfService";

    private IPerfServiceManager perfServiceMgr;
    final   Context mContext;

    class PerfServiceBroadcastReceiver extends android.content.BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                log("Intent.ACTION_SCREEN_OFF");
                perfServiceMgr.userDisableAll();
                return;
            }
            else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                log("Intent.ACTION_SCREEN_ON");
                perfServiceMgr.userRestoreAll();
                return;
            }
            else {
                log("Unexpected intent " + intent);
            }
        }
    }

    public PerfServiceImpl(Context context, IPerfServiceManager pm) {
        perfServiceMgr = pm;
        mContext = context;

        final IntentFilter broadcastFilter = new IntentFilter();
        broadcastFilter.addAction(Intent.ACTION_SCREEN_OFF);
        broadcastFilter.addAction(Intent.ACTION_SCREEN_ON);
        mContext.registerReceiver(new PerfServiceBroadcastReceiver(), broadcastFilter);

    }

    public void boostEnable(int scenario) {
        //log("boostEnable");
        perfServiceMgr.boostEnable(scenario);
    }

    public void boostDisable(int scenario) {
        //log("boostDisable");
        perfServiceMgr.boostDisable(scenario);
    }

    public void boostEnableTimeout(int scenario, int timeout) {
        //log("boostEnableTimeout");
        perfServiceMgr.boostEnableTimeout(scenario, timeout);
    }

    public void boostEnableTimeoutMs(int scenario, int timeout_ms) {
        //log("boostEnableTimeoutMs");
        perfServiceMgr.boostEnableTimeoutMs(scenario, timeout_ms);
    }

    public void notifyAppState(java.lang.String packName, java.lang.String className,
                                                          int state, int pid) {
        //log("notifyAppState");
        perfServiceMgr.notifyAppState(packName, className, state, pid);
    }

    public int userReg(int scn_core, int scn_freq, int pid, int tid) {
        //log("userReg");
        return perfServiceMgr.userReg(scn_core, scn_freq, pid, tid);
    }

    public int userRegBigLittle(int scn_core_big, int scn_freq_big, int scn_core_little, int scn_freq_little, int pid, int tid) {
        //log("userRegBigLittle");
        return perfServiceMgr.userRegBigLittle(scn_core_big, scn_freq_big, scn_core_little, scn_freq_little, pid, tid);
    }

    public void userUnreg(int handle) {
        //log("userUnreg");
        perfServiceMgr.userUnreg(handle);
    }

    public int userGetCapability(int cmd) {
        //log("userGetCapability");
        return perfServiceMgr.userGetCapability(cmd);
    }

    public int userRegScn(int pid, int tid) {
        //log("userRegScn");
        return perfServiceMgr.userRegScn(pid, tid);
    }

    public void userRegScnConfig(int handle, int cmd, int param_1, int param_2, int param_3, int param_4) {
        //log("userRegScnConfig");
        perfServiceMgr.userRegScnConfig(handle, cmd, param_1, param_2, param_3, param_4);
    }

    public void userUnregScn(int handle) {
        //log("userUnregScn");
        perfServiceMgr.userUnregScn(handle);
    }

    public void userEnable(int handle) {
        //log("userEnable");
        perfServiceMgr.userEnable(handle);
    }

    public void userEnableTimeout(int handle, int timeout) {
        //log("userEnableTimeout");
        perfServiceMgr.userEnableTimeout(handle, timeout);
    }

    public void userEnableTimeoutMs(int handle, int timeout_ms) {
        //log("userEnableTimeoutMs");
        perfServiceMgr.userEnableTimeoutMs(handle, timeout_ms);
    }

    public void userEnableAsync(int handle) {
        //log("userEnableAsync");
        perfServiceMgr.userEnableAsync(handle);
    }

    public void userEnableTimeoutAsync(int handle, int timeout) {
        //log("userEnableTimeoutAsync");
        perfServiceMgr.userEnableTimeoutAsync(handle, timeout);
    }

    public void userEnableTimeoutMsAsync(int handle, int timeout_ms) {
        //log("userEnableTimeoutMsAsync");
        perfServiceMgr.userEnableTimeoutMsAsync(handle, timeout_ms);
    }

    public void userDisable(int handle) {
        //log("userDisable");
        perfServiceMgr.userDisable(handle);
    }

    public void userResetAll() {
        //log("userDisable");
        perfServiceMgr.userResetAll();
    }

    public void userDisableAll() {
        //log("userDisable");
        perfServiceMgr.userDisableAll();
    }

    public void userRestoreAll() {
        //log("userRestoreAll");
        perfServiceMgr.userRestoreAll();
    }

    public void dumpAll() {
        //log("dumpAll");
        perfServiceMgr.dumpAll();
    }

    public void setFavorPid(int pid) {
        //log("setFavorPid");
        perfServiceMgr.setFavorPid(pid);
    }

    public void restorePolicy(int pid) {
        //log("restorePolicy");
        perfServiceMgr.restorePolicy(pid);
    }

    public void notifyFrameUpdate(int level) {
        //log("notifyFrameUpdate");
        perfServiceMgr.notifyFrameUpdate(level);
    }

    public void notifyDisplayType(int type) {
        //log("notifyDisplayType");
        perfServiceMgr.notifyDisplayType(type);
    }

    public int getLastBoostPid() {
        //log("notifyDisplayType");
        return perfServiceMgr.getLastBoostPid();
    }

    public void notifyUserStatus(int type, int status) {
        //log("notifyUserStatus");
        perfServiceMgr.notifyUserStatus(type, status);
    }

    public int getClusterInfo(int cmd, int id) {
        //log("getClusteInfo");
        return perfServiceMgr.getClusterInfo(cmd, id);
    }

    public void levelBoost(int level) {
        //log("levelBoost");
        perfServiceMgr.levelBoost(level);
    }

    public int getPackAttr(String packName, int cmd) {
        //log("getPackAttr");
        return perfServiceMgr.getPackAttr(packName, cmd);
    }

    private void log(String info) {
        Log.d("@M_" + TAG, "[PerfService] " + info + " ");
    }

    private void loge(String info) {
        Log.e("@M_" + TAG, "[PerfService] ERR: " + info + " ");
    }
}

