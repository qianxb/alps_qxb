/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.keyguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardSecurityCallback;
import com.android.keyguard.ViewMediatorCallback;
import android.util.Log;

import com.mediatek.keyguard.VoiceWakeup.VoiceWakeupManager;
import com.mediatek.common.voicecommand.VoiceCommandListener;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;

public class VoiceWakeupTest extends KeyguardTestcase  {

    private static final String TAG = "VoiceWakeupTest";

    private VoiceWakeupManager mVOWManager;
    private boolean mIsSupportVOW;
    private static final String PKG_NAME = "com.mediatek.voicecommand";

    @Mock
    private ViewMediatorCallback mViewMediatorCallback;

    private final String ACTION_VOICE_WAKEUP_LAUNCH_SECURECAMERA_OWNER_ONLY =
            "com.android.keyguard.VoiceWakeupManager.LAUNCH_SEC_CAMERA_OWNER";
    private final String ACTION_VOICE_WAKEUP_LAUNCH_INSECURECAMERA_OWNER_ONLY =
            "com.android.keyguard.VoiceWakeupManager.LAUNCH_INSEC_CAMERA_OWNER";
    private final String ACTION_VOICE_WAKEUP_LAUNCH_SECURECAMERA_ANYONE =
            "com.android.keyguard.VoiceWakeupManager.LAUNCH_SEC_CAMERA_ANYONE";
    private final String ACTION_VOICE_WAKEUP_LAUNCH_INSECURECAMERA_ANYONE =
            "com.android.keyguard.VoiceWakeupManager.LAUNCH_INSEC_CAMERA_ANYONE";
    private final String ACTION_VOICE_WAKEUP_LAUNCH_MMS_OWNER_ONLY =
            "com.android.keyguard.VoiceWakeupManager.LAUNCH_MMS_OWNER";
    private final String ACTION_VOICE_WAKEUP_LAUNCH_MMS_ANYONE =
            "com.android.keyguard.VoiceWakeupManager.LAUNCH_MMS_ANYONE";
    private final int MSG_VOICE_WAKEUP_LAUNCH_SECURECAMERA_OWNER_ONLY = 1000;
    private final int MSG_VOICE_WAKEUP_LAUNCH_INSECURECAMERA_OWNER_ONLY = 1001;
    private final int MSG_VOICE_WAKEUP_LAUNCH_SECURECAMERA_ANYONE = 1002;
    private final int MSG_VOICE_WAKEUP_LAUNCH_INSECURECAMERA_ANYONE = 1003;
    private final int MSG_VOICE_WAKEUP_LAUNCH_MMS_OWNER_ONLY = 1004;
    private final int MSG_VOICE_WAKEUP_LAUNCH_MMS_ANYONE = 1005;
    private final int COMMAND_ID_LAUNCH_SECURECAMERA = 1;
    private final int COMMAND_ID_LAUNCH_INSECURECAMERA = 2;
    private final int COMMAND_ID_LAUNCH_MMS = 3;


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);

        mVOWManager = VoiceWakeupManager.getInstance();
        mIsSupportVOW = mVOWManager.checkIfVowSupport(mContext);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private void registerBroadcastReceiverForTest() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_VOICE_WAKEUP_LAUNCH_SECURECAMERA_OWNER_ONLY);
        filter.addAction(ACTION_VOICE_WAKEUP_LAUNCH_INSECURECAMERA_OWNER_ONLY);
        filter.addAction(ACTION_VOICE_WAKEUP_LAUNCH_SECURECAMERA_ANYONE);
        filter.addAction(ACTION_VOICE_WAKEUP_LAUNCH_INSECURECAMERA_ANYONE);
        filter.addAction(ACTION_VOICE_WAKEUP_LAUNCH_MMS_OWNER_ONLY) ;
        filter.addAction(ACTION_VOICE_WAKEUP_LAUNCH_MMS_ANYONE) ;
        mContext.registerReceiver(mBroadcastReceiverForTest, filter);
    }

    private final BroadcastReceiver mBroadcastReceiverForTest = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction() ;
            int what = -1 ;

            if (ACTION_VOICE_WAKEUP_LAUNCH_SECURECAMERA_OWNER_ONLY.equals(action)) {
                what = MSG_VOICE_WAKEUP_LAUNCH_SECURECAMERA_OWNER_ONLY ;
            }
            else if (ACTION_VOICE_WAKEUP_LAUNCH_INSECURECAMERA_OWNER_ONLY.equals(action)) {
                what = MSG_VOICE_WAKEUP_LAUNCH_INSECURECAMERA_OWNER_ONLY ;
            }
            else if (ACTION_VOICE_WAKEUP_LAUNCH_SECURECAMERA_ANYONE.equals(action)) {
                what = MSG_VOICE_WAKEUP_LAUNCH_SECURECAMERA_ANYONE;
            }
            else if (ACTION_VOICE_WAKEUP_LAUNCH_INSECURECAMERA_ANYONE.equals(action)) {
                what = MSG_VOICE_WAKEUP_LAUNCH_INSECURECAMERA_ANYONE;
            }
            else if (ACTION_VOICE_WAKEUP_LAUNCH_MMS_OWNER_ONLY.equals(action)) {
                what = MSG_VOICE_WAKEUP_LAUNCH_MMS_OWNER_ONLY ;
            }
            else if (ACTION_VOICE_WAKEUP_LAUNCH_MMS_ANYONE.equals(action)) {
                what = MSG_VOICE_WAKEUP_LAUNCH_MMS_ANYONE ;

            }

            Message msg = mVoiceCommandHandlerForTest.obtainMessage(what);
            msg.sendToTarget();
        }
    };

    private Handler mVoiceCommandHandlerForTest = new Handler() {
        public void handleMessage(Message msg) {
            int commandId = -1 ;
            int isUserDependentMode = VoiceCommandListener.VOICE_WAKEUP_MODE_SPEAKER_DEPENDENT;

            switch(msg.what) {
                case MSG_VOICE_WAKEUP_LAUNCH_SECURECAMERA_OWNER_ONLY:
                    commandId = COMMAND_ID_LAUNCH_SECURECAMERA ;
                    isUserDependentMode = VoiceCommandListener.VOICE_WAKEUP_MODE_SPEAKER_DEPENDENT;
                    break;
                case MSG_VOICE_WAKEUP_LAUNCH_INSECURECAMERA_OWNER_ONLY:
                    commandId = COMMAND_ID_LAUNCH_INSECURECAMERA ;
                    isUserDependentMode = VoiceCommandListener.VOICE_WAKEUP_MODE_SPEAKER_DEPENDENT;
                    break;
                case MSG_VOICE_WAKEUP_LAUNCH_SECURECAMERA_ANYONE:
                    commandId = COMMAND_ID_LAUNCH_SECURECAMERA ;
                    isUserDependentMode =
                            VoiceCommandListener.VOICE_WAKEUP_MODE_SPEAKER_INDEPENDENT;
                    break;
                case MSG_VOICE_WAKEUP_LAUNCH_INSECURECAMERA_ANYONE:
                    commandId = COMMAND_ID_LAUNCH_INSECURECAMERA ;
                    isUserDependentMode =
                            VoiceCommandListener.VOICE_WAKEUP_MODE_SPEAKER_INDEPENDENT;
                    break;
                case MSG_VOICE_WAKEUP_LAUNCH_MMS_OWNER_ONLY:
                    commandId = COMMAND_ID_LAUNCH_MMS ;
                    isUserDependentMode = VoiceCommandListener.VOICE_WAKEUP_MODE_SPEAKER_DEPENDENT;
                    break;
                case MSG_VOICE_WAKEUP_LAUNCH_MMS_ANYONE:
                    commandId = COMMAND_ID_LAUNCH_MMS ;
                    isUserDependentMode =
                            VoiceCommandListener.VOICE_WAKEUP_MODE_SPEAKER_INDEPENDENT;
                    break;
                default:
                    Log.v(TAG, "handleMessage() : msg.what is invalid!") ;
                    break;
            }

            Bundle data = new Bundle();
            data.putInt(VoiceCommandListener.ACTION_EXTRA_RESULT_INFO, commandId);
            data.putInt(VoiceCommandListener.ACTION_EXTRA_RESULT_INFO1, isUserDependentMode);
            mVOWManager.handleVoiceCommandNotified(data, true);
         }
    };

    public void test_VoiceWakeupInit() {
        if (!mIsSupportVOW) {
            Log.d(TAG, "test_VoiceWakeupInit, No support VOW.");
            return;
        }

        try {
            mVOWManager.init(mContext, mViewMediatorCallback);
            assertEquals(false, mVOWManager.isRegisted());
        } catch (Exception e) {}
    }

    public void test_startVoiceWakeup() {
        if (!mIsSupportVOW) {
            Log.d(TAG, "test_startVoiceWakeup, No support VOW.");
            return;
        }

        try {
            mVOWManager.setPackageName(PKG_NAME);
            mVOWManager.start();
            assertEquals(false, mVOWManager.isRegisted());
        } catch (Exception e) {}
    }

    public void test_stopVoiceWakeup() {
        if (!mIsSupportVOW) {
            Log.d(TAG, "test_stopVoiceWakeup, No support VOW.");
            return;
        }

        try {
            mVOWManager.setPackageName(PKG_NAME);
            mVOWManager.start();
            mVOWManager.stop();
            assertEquals(false, mVOWManager.isRegisted());
        } catch (Exception e) {}
    }

    public void test_sendVOWCommand() {
        if (!mIsSupportVOW) {
            Log.d(TAG, "test_sendVOWCommand, No support VOW.");
            return;
        }

        try {
            mVOWManager.setPackageName(PKG_NAME);
            mVOWManager.start();
            mVOWManager.sendVoiceCommand(PKG_NAME,
                    VoiceCommandListener.ACTION_MAIN_VOICE_RECOGNITION,
                    VoiceCommandListener.ACTION_VOICE_RECOGNITION_INTENSITY, null);
            assertEquals(false, mVOWManager.isRegisted());
            mVOWManager.stop();
        } catch (Exception e) {}
    }

    public void test_registerBroadcastReceiverForTest() {
        if (!mIsSupportVOW) {
            Log.d(TAG, "test_registerBroadcastReceiverForTest, No support VOW.");
            return;
        }

        try {
            registerBroadcastReceiverForTest();
        } catch (Exception e) {}
    }
}

