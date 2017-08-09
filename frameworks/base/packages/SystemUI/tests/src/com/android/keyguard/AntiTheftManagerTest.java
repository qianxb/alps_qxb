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

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardSecurityCallback;
import com.android.keyguard.ViewMediatorCallback;

import com.mediatek.keyguard.AntiTheft.AntiTheftManager;
import com.mediatek.keyguard.AntiTheft.AntiTheftManager.AntiTheftMode;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;

public class AntiTheftManagerTest extends KeyguardTestcase  {

    private static final String TAG = "AntiTheftManagerTest";

    private AntiTheftManager mAntiTheftManager;

    @Mock
    private ViewMediatorCallback mViewMediatorCallback;

    @Mock
    private LockPatternUtils mLockPatternUtils;

    @Mock
    private KeyguardSecurityCallback mKeyguardSecurityCallback;

    private final int MSG_ARG_LOCK = 0;
    private final int MSG_ARG_UNLOCK = 1;
    private static final int MSG_ANTITHEFT_KEYGUARD_UPDATE = 1001;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);

        mAntiTheftManager = new AntiTheftManager(mContext, mViewMediatorCallback,
                mLockPatternUtils);
        mAntiTheftManager.setSecurityViewCallback(mKeyguardSecurityCallback);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void test_getAntiTheftModeName() {
        assertEquals("AntiTheftMode.None", mAntiTheftManager
               .getAntiTheftModeName(AntiTheftMode.None));

        assertEquals("AntiTheftMode.DmLock", mAntiTheftManager
               .getAntiTheftModeName(AntiTheftMode.DmLock));

        assertEquals("AntiTheftMode.PplLock", mAntiTheftManager
               .getAntiTheftModeName(AntiTheftMode.PplLock));
    }

    public void test_bindPplService() {
        mAntiTheftManager.doBindAntiThftLockServices();
        assertEquals(false, mAntiTheftManager.getPPLManagerInstance()!=null);
    }

    public void test_PPLLock() {
        Intent intent = new Intent(mAntiTheftManager.PPL_LOCK);
        Handler handler = mAntiTheftManager.getHandlerInstance();
        Message msg = handler.obtainMessage(MSG_ANTITHEFT_KEYGUARD_UPDATE);
        msg.arg1 = AntiTheftMode.PplLock;
        msg.arg2 = MSG_ARG_LOCK;
        when(mViewMediatorCallback.isShowing()).thenReturn(true);

        mAntiTheftManager.getPPLBroadcastReceiverInstance().onReceive(mContext, intent);
        handler.handleMessage(msg);

        verify(mViewMediatorCallback).resetStateLocked();
        verify(mViewMediatorCallback).adjustStatusBarLocked();
    }

    public void test_PPLUnLock() {
        Intent intent = new Intent(mAntiTheftManager.PPL_UNLOCK);
        Handler handler = mAntiTheftManager.getHandlerInstance();
        Message msg = handler.obtainMessage(MSG_ANTITHEFT_KEYGUARD_UPDATE);
        msg.arg1 = AntiTheftMode.PplLock;
        msg.arg2 = MSG_ARG_UNLOCK;

        mAntiTheftManager.getPPLBroadcastReceiverInstance().onReceive(mContext, intent);
        handler.handleMessage(msg);

        verify(mKeyguardSecurityCallback).dismiss(true);
        verify(mViewMediatorCallback).adjustStatusBarLocked();
    }
}

