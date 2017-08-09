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
package com.android.server.am;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.BidiFormatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import static com.android.server.am.ActivityManagerService.IS_USER_BUILD;

public class MtkAppErrorDialog extends BaseErrorDialog implements View.OnClickListener {

    private final ActivityManagerService mService;
    private final AppErrorResult mResult;
    private final ProcessRecord mProc;
    private final boolean mRepeating;
    private final boolean mForeground;

    private CharSequence mName;

    // Event 'what' codes
    public static final int FORCE_QUIT = 1;
    public static final int FORCE_QUIT_AND_REPORT = 2;
    public static final int RESTART = 3;
    public static final int MUTE = 5;
    public static final int TIMEOUT = 6;
    public static final int CANCEL = 7;

    // 5-minute timeout, then we automatically dismiss the crash dialog
    static final long DISMISS_TIMEOUT = 1000 * 60 * 5;

    public MtkAppErrorDialog(Context context, ActivityManagerService service,
            AppErrorDialog.Data data) {
        super(context);
        Resources res = context.getResources();

        mService = service;
        mProc = data.proc;
        mResult = data.result;
        mRepeating = data.repeating;
        mForeground = data.task != null;
        BidiFormatter bidi = BidiFormatter.getInstance();

        if ((mProc.pkgList.size() == 1) &&
                (mName = context.getPackageManager().getApplicationLabel(mProc.info)) != null) {
            setTitle(res.getString(
                    mRepeating ? com.android.internal.R.string.aerr_application_repeated
                            : com.android.internal.R.string.aerr_application,
                    bidi.unicodeWrap(mName.toString()),
                    bidi.unicodeWrap(mProc.info.processName)));
        } else {
            mName = mProc.processName;
            setTitle(res.getString(
                    mRepeating ? com.android.internal.R.string.aerr_process_repeated
                            : com.android.internal.R.string.aerr_process,
                    bidi.unicodeWrap(mName.toString())));
        }

        setCancelable(true);
        setCancelMessage(mHandler.obtainMessage(CANCEL));

        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        attrs.setTitle("Application Error: " + mProc.info.processName);
        attrs.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_SYSTEM_ERROR
                | WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
        getWindow().setAttributes(attrs);
        if (mProc.persistent) {
            getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
        }

        // After the timeout, pretend the user clicked the quit button
        mHandler.sendMessageDelayed(
                mHandler.obtainMessage(TIMEOUT),
                DISMISS_TIMEOUT);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final FrameLayout frame = (FrameLayout) findViewById(android.R.id.custom);
        initLayout(frame);
    }

    @Override
    public void onStart() {
        super.onStart();
        getContext().registerReceiver(mReceiver,
                new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }

    @Override
    protected void onStop() {
        super.onStop();
        getContext().unregisterReceiver(mReceiver);
    }

    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            final int result = msg.what;

            synchronized (mService) {
                if (mProc != null && mProc.crashDialog == MtkAppErrorDialog.this) {
                    mProc.crashDialog = null;
                }
            }
            mResult.set(result);

            // Make sure we don't have time timeout still hanging around.
            removeMessages(TIMEOUT);

            dismiss();
        }
    };

    @Override
    public void dismiss() {
        if (!mResult.mHasResult) {
            // We are dismissing and the result has not been set...go ahead and set.
            mResult.set(FORCE_QUIT);
        }
        super.dismiss();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case com.android.internal.R.id.aerr_restart:
                clickButtonForResult(RESTART);
                break;
            case com.android.internal.R.id.aerr_report:
                clickButtonForResult(FORCE_QUIT_AND_REPORT);
                break;
            case com.android.internal.R.id.aerr_close:
                clickButtonForResult(FORCE_QUIT);
                break;
            case com.android.internal.R.id.aerr_mute:
                clickButtonForResult(MUTE);
                break;
            default:
                break;
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) {
                cancel();
            }
        }
    };

    /**
     * Initialize custom view on dialog.
     */
    public void initLayout(FrameLayout frame) {
        final Context context = getContext();
        LayoutInflater.from(context).inflate(
                com.android.internal.R.layout.app_error_dialog, frame, true);

        boolean hasRestart = !mRepeating && mForeground;
        final boolean hasReceiver = mProc.errorReportReceiver != null;

        final TextView restart = (TextView) findViewById(com.android.internal.R.id.aerr_restart);
        restart.setOnClickListener(this);
        restart.setVisibility(hasRestart ? View.VISIBLE : View.GONE);
        final TextView report = (TextView) findViewById(com.android.internal.R.id.aerr_report);
        report.setOnClickListener(this);
        report.setVisibility(hasReceiver ? View.VISIBLE : View.GONE);
        final TextView close = (TextView) findViewById(com.android.internal.R.id.aerr_close);
        close.setVisibility(!hasRestart ? View.VISIBLE : View.GONE);
        close.setOnClickListener(this);

        boolean showMute = !IS_USER_BUILD && Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
        final TextView mute = (TextView) findViewById(com.android.internal.R.id.aerr_mute);
        mute.setOnClickListener(this);
        mute.setVisibility(showMute ? View.VISIBLE : View.GONE);

        findViewById(com.android.internal.R.id.customPanel).setVisibility(View.VISIBLE);
    }

    /**
     * Send result to AMS after clicking the button.
     */
    public void clickButtonForResult(int result) {
        mHandler.obtainMessage(result).sendToTarget();
    }
}
