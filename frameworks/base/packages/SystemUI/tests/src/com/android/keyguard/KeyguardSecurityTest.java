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

import android.app.admin.DevicePolicyManager;

import com.android.keyguard.KeyguardSecurityContainer;
import com.android.keyguard.KeyguardSecurityModel;
import com.android.keyguard.KeyguardSecurityModel.SecurityMode;
import com.android.keyguard.KeyguardSecurityViewFlipper;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardPINView;
import com.android.keyguard.KeyguardPasswordView;
import com.android.internal.widget.LockPatternUtils;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.when;

public class KeyguardSecurityTest extends KeyguardTestcase {

    private static final String TAG = "KeyguardSecurityTest";

    private KeyguardUpdateMonitor mKeyguardUpdateMonitor;

    private KeyguardSecurityContainer mSecurityContainer;

    @Mock
    private KeyguardSecurityModel mSecurityModel;
    private SecurityMode mCurrentSecuritySelection = SecurityMode.Invalid;

    @Mock
    private LockPatternUtils mLockPatternUtils;

    private final static int OWNER = 0;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        KeyguardUpdateMonitor.setCurrentUser(OWNER);
        mKeyguardUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        mSecurityContainer = new KeyguardSecurityContainer(mContext);
        mSecurityModel = new KeyguardSecurityModel(mContext);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void test_getCurrentSecurityMode() {
        mCurrentSecuritySelection = SecurityMode.PIN;
        mSecurityContainer.setCurrentSecurityMode(mCurrentSecuritySelection);
        assertEquals(SecurityMode.PIN, mSecurityContainer.getCurrentSecurityMode());
    }

    public void test_getSecurityMode() {
        mSecurityModel.setLockPatternUtils(mLockPatternUtils);

        //Test PIN security mode
        when(mLockPatternUtils.getActivePasswordQuality(OWNER))
                .thenReturn(DevicePolicyManager.PASSWORD_QUALITY_NUMERIC);
        assertEquals(SecurityMode.PIN, mSecurityModel.getSecurityMode());

        //Test Password security mode
        when(mLockPatternUtils.getActivePasswordQuality(OWNER))
                .thenReturn(DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC);
        assertEquals(SecurityMode.Password, mSecurityModel.getSecurityMode());

        //Test Pattern security mode
        when(mLockPatternUtils.getActivePasswordQuality(OWNER))
                .thenReturn(DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        assertEquals(SecurityMode.Pattern, mSecurityModel.getSecurityMode());

        //Test None security mode
        when(mLockPatternUtils.getActivePasswordQuality(OWNER))
                .thenReturn(DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
        assertEquals(SecurityMode.None, mSecurityModel.getSecurityMode());
    }
}

