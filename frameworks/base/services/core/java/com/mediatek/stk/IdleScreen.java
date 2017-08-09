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
package com.mediatek.stk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Slog;

import com.mediatek.am.AMEventHookData.EndOfActivityIdle;
import com.mediatek.am.AMEventHookData.SystemReady;

/**
 * M: STK IDLE SCREEN feature.
 *
 * {@hide}
 */
public class IdleScreen {
    private static final String TAG = "IdleScreen";
    private static final boolean DEBUG = false;

    /**
     * Use AMEventHook.
     */
    public void onSystemReady(SystemReady data) {
        int phase = data.getInt(SystemReady.Index.phase);

        // phase   0: On start of AMS systemReady(final Runnable goingCallback) method
        if (phase == 0) {
            Context context = (Context) data.get(SystemReady.Index.context);
            registerIdleScreenReceiver(context);
        }
    }

    /**
     * Use AMEventHook.
     */
    public void onEndOfActivityIdle(EndOfActivityIdle data) {
        Context context = (Context) data.get(EndOfActivityIdle.Index.context);
        Intent idleIntent = (Intent) data.get(EndOfActivityIdle.Index.intent);
        activityIdleScreen(context, idleIntent);
    }

    /* M:
     * STK IDLE SCREEN feature
     * true, AMS needs to notify STK that it's in IDLE SCREEN
     */
    private static boolean mNotifyNeeded = false;

    /**
     * M:
     * IDLE SCREEN feature:
     * register the broadcast receiver to know if enable/disable IDLE SCREEN feature
     **/
    private void registerIdleScreenReceiver(Context context) {
        if (DEBUG) {
            Slog.d(TAG, "registerIdleScreenReceiver: " + context);
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.IDLE_SCREEN_NEEDED");

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("android.intent.action.IDLE_SCREEN_NEEDED".equals(intent.getAction())) {
                    mNotifyNeeded = intent.getBooleanExtra("_enable", false);
                    Slog.v(TAG, "mNotifyNeeded = " + mNotifyNeeded);
                }
            }
        }, filter);
    }

    /**
     * M:
     * IDLE SCREEN feature:
     * Broadcast android.intent.action.stk.IDLE_SCREEN_AVAILABLE when
     * device in IDLE SCREEN state
     **/
    private void activityIdleScreen(Context context, Intent idleIntent) {
        if (DEBUG) {
            Slog.d(TAG, "activityIdleScreen: " + context + " mNotifyNeeded: " + mNotifyNeeded +
                " idleIntent: " + idleIntent);
        }
        if (!mNotifyNeeded) {
            return;
        }
        if (idleIntent != null) {
            if (idleIntent.hasCategory(Intent.CATEGORY_HOME)) {
                Slog.v(TAG, "In IDLE SCREEN, broadcast intent to receivers");
                Intent intent = new Intent("android.intent.action.stk.IDLE_SCREEN_AVAILABLE");
                context.sendBroadcast(intent);
            }
        }
    }
}
