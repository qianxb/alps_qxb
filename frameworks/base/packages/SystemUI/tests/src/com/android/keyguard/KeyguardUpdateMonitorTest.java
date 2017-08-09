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
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.keyguard.KeyguardUpdateMonitor.BatteryStatus;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import static org.mockito.Mockito.*;


/**
 * Base class that does System UI specific setup.
 */
public class KeyguardUpdateMonitorTest extends KeyguardTestcase {

    private static final String TAG = "KeyguardUpdateMonitorTest";
    private static final int MSG_TIME_UPDATE = 301;
    private static final int MSG_BATTERY_UPDATE = 302;
    private static final int MSG_SIM_STATE_CHANGE = 304;
    private static final int MSG_USER_SWITCHING = 310;
    private static final int MSG_REPORT_EMERGENCY_CALL_ACTION = 318;
    private static final int MSG_SIM_SUBSCRIPTION_INFO_CHANGED = 328;
    private KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private OnSubscriptionsChangedListener mSubscriptionListener;
    private KeyguardUpdateMonitorCallback mKeyguardCallback;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Mockito stuff.
        mKeyguardUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        mSubscriptionListener = mock(OnSubscriptionsChangedListener.class);
        mKeyguardCallback = mock(KeyguardUpdateMonitorCallback.class);
        setSubscriptionMonitor();
        registerUpdateCallback();
    }

    protected void tearDown() throws Exception {
        unRegisterUpdateCallback();
        super.tearDown();
    }

    public void setSubscriptionMonitor() {
        doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocation) {
                mKeyguardUpdateMonitor.mHandler.sendEmptyMessage(MSG_SIM_SUBSCRIPTION_INFO_CHANGED);
                return null;
            }
        }).when(mSubscriptionListener).onSubscriptionsChanged();
    }

    public void registerUpdateCallback() {
        mKeyguardUpdateMonitor.registerCallback(mKeyguardCallback);
    }

    public void unRegisterUpdateCallback() {
        mKeyguardUpdateMonitor.removeCallback(mKeyguardCallback);
        mKeyguardCallback = null;
    }

    public void testCarrierText_onRefreshCarrierInfo() {
        // triger subscription change
        mSubscriptionListener.onSubscriptionsChanged();
        waiting(100);
        // verify onRefreshCarrierInfo of carrier
        verify(mKeyguardCallback).onRefreshCarrierInfo();
    }

    public void testKeyguardStatus_onTimeChanged() {
        // triger time change
        mKeyguardUpdateMonitor.mHandler.sendEmptyMessage(MSG_TIME_UPDATE);

        // verify onTimeChanged of KeygaurdStatus
        verify(mKeyguardCallback).onTimeChanged();
    }

    public void testKeyguard_onRefreshBatteryInfo() {
        // triger battery info change
        int status = 1;
        int level = 100;
        int plugged = 0;
        int health = 0;
        int maxChargingCurrent = 0;
        final Message msg = mKeyguardUpdateMonitor.mHandler.obtainMessage(
                MSG_BATTERY_UPDATE, new BatteryStatus(status, level, plugged,
                health, maxChargingCurrent));
        mKeyguardUpdateMonitor.mHandler.sendMessage(msg);

        // verify
        ArgumentCaptor<BatteryStatus> captor = ArgumentCaptor.forClass(BatteryStatus.class);
        verify(mKeyguardCallback).onRefreshBatteryInfo(captor.capture());
        assertEquals(status, (int) captor.getValue().status);
        assertEquals(level, (int) captor.getValue().level);
        assertEquals(plugged, (int) captor.getValue().plugged);
        assertEquals(health, (int) captor.getValue().health);
    }

    public void test_onEmergencyCallAction() {

        mKeyguardUpdateMonitor.reportEmergencyCallAction(true);

        // verify
        verify(mKeyguardCallback).onEmergencyCallAction();
    }

    private void waiting(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            // ignore it
        }
    }
}
