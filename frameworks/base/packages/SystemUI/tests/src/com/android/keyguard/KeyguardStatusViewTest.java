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

import android.os.Message;
import android.os.SystemProperties;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import static org.mockito.Mockito.*;


/**
 * Base class that does System UI specific setup.
 */
public class KeyguardStatusViewTest extends KeyguardTestcase {

    private static final int MSG_TIME_UPDATE = 301;

    private static final String TAG = "KeyguardUpdateMonitorTest";
    private KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private KeyguardStatusView mKeyguardStatusView;

    /// M: A1 support
    private static boolean sA1Support =
            SystemProperties.get("ro.mtk_a1_feature").equals("1");

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mKeyguardStatusView = new KeyguardStatusView(mContext);
        mKeyguardUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);

        // Mockito stuff.
        mKeyguardStatusView.mOwnerInfo = mock(TextView.class);
        mKeyguardStatusView.mDateView = mock(TextClock.class);
        mKeyguardStatusView.mAlarmStatusView = mock(TextView.class);
        mKeyguardStatusView.mClockView = mock(TextClock.class);

        // register callback
        mKeyguardUpdateMonitor.registerCallback(mKeyguardStatusView.mInfoCallback);
    }

    public void testKeyguardStatusView_setFormat24Hour() {
        // triger time change
        mKeyguardUpdateMonitor.mHandler.sendEmptyMessage(MSG_TIME_UPDATE);
        // verify the clock time update
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mKeyguardStatusView.mClockView).setFormat24Hour(captor.capture());
    }

    public void testKeyguardStatusView_setVisibility() {
        // triger the Keyguard visibility change
        mKeyguardUpdateMonitor.onKeyguardVisibilityChanged(true);

        // verify the owner info visibility change
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(mKeyguardStatusView.mOwnerInfo).setVisibility(captor.capture());
    }

    public void testKeyguardStatusView_setFormat24Hour2() {
        // triger time change
        mKeyguardUpdateMonitor.mHandler.sendEmptyMessage(MSG_TIME_UPDATE);

        // verify set the time format
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mKeyguardStatusView.mDateView).setFormat24Hour(captor.capture());
    }

    public void testKeyguardStatusView_setEnableMarquee() {
        // triger the wakeup event
        mKeyguardUpdateMonitor.handleStartedWakingUp();

        // when wakeup event occur, the ownerinfo set selected as true
        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        verify(mKeyguardStatusView.mOwnerInfo).setSelected(captor.capture());
        assertEquals(true, (boolean) captor.getValue());
    }

    public void testKeyguardStatusView_setEnableMarquee2() {
        // triger the sleep event
        mKeyguardUpdateMonitor.handleFinishedGoingToSleep(0);

        // when sleep event occur, the ownerinfo set selected as false
        ArgumentCaptor<Boolean> captor = ArgumentCaptor.forClass(Boolean.class);
        verify(mKeyguardStatusView.mOwnerInfo).setSelected(captor.capture());
        assertEquals(false, (boolean) captor.getValue());
    }

    public void testKeyguardStatusView_UserSwitchComplete() {
        // triger the user switch event
        mKeyguardUpdateMonitor.handleUserSwitchComplete(1);

        // verify the owner info visibility change
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(mKeyguardStatusView.mOwnerInfo).setVisibility(captor.capture());
        // verify the clock time update
        ArgumentCaptor<String> captor2 = ArgumentCaptor.forClass(String.class);
        // verify(mKeyguardStatusView.mClockView).setFormat24Hour(captor2.capture());
    }

    protected void tearDown() throws Exception {
        // remove callback
        mKeyguardUpdateMonitor.removeCallback(mKeyguardStatusView.mInfoCallback);
        super.tearDown();
    }

}
