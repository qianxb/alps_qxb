/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.telecom.TelecomManager;

import android.telephony.ServiceState;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.view.View;
import android.widget.Button;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.widget.LockPatternUtils;

import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.keyguard.AntiTheft.AntiTheftManager;
import com.mediatek.keyguard.Plugin.KeyguardPluginFactory;
import com.mediatek.keyguard.ext.IEmergencyButtonExt;

/**
 * This class implements a smart emergency button that updates itself based
 * on telephony state.  When the phone is idle, it is an emergency call button.
 * When there's a call in progress, it presents an appropriate message and
 * allows the user to return to the call.
 */
public class EmergencyButton extends Button {
    private static final Intent INTENT_EMERGENCY_DIAL = new Intent()
            .setAction("com.android.phone.EmergencyDialer.DIAL")
            .setPackage("com.android.phone")
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    private static final String TAG = "EmergencyButton" ;
    private KeyguardUpdateMonitor mUpdateMonitor = null;
    /// M: add for phoneId in Ecc intent in none security mode
    private int mEccPhoneIdForNoneSecurityMode = -1;

    ///M : Added for operator feature. This will help to check the position
    // of current ECC button(i.e. Notification Keyguard or Bouncer)
    private boolean mLocateAtNonSecureView = false ;

    private static final String LOG_TAG = "EmergencyButton";

    KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onSimStateChanged(int subId, int slotId, State simState) {
            updateEmergencyCallButton();
        }


        @Override
        public void onSimStateChangedUsingPhoneId(int phoneId, State simState) {
            Log.d(TAG, "onSimStateChangedUsingSubId: " + simState + ", phoneId=" + phoneId);
            updateEmergencyCallButton();
        }

        @Override
        public void onPhoneStateChanged(int phoneState) {
            updateEmergencyCallButton();
        }

        /// M: CTA new feature
        @Override
        public void onRefreshCarrierInfo() {
            updateEmergencyCallButton();
        }
    };

    public interface EmergencyButtonCallback {
        public void onEmergencyButtonClickedWhenInCall();
    }

    private LockPatternUtils mLockPatternUtils;
    private PowerManager mPowerManager;

    /// M: For the extra info of the intent to start emergency dialer
    private IEmergencyButtonExt mEmergencyButtonExt;

    private EmergencyButtonCallback mEmergencyButtonCallback;

    private final boolean mIsVoiceCapable;
    private final boolean mEnableEmergencyCallWhileSimLocked;

    public EmergencyButton(Context context) {
        this(context, null);
    }

    public EmergencyButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIsVoiceCapable = context.getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);
        mEnableEmergencyCallWhileSimLocked = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enable_emergency_call_while_sim_locked);

        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        /// M: Init keyguard operator plugin @{
        try {
            mEmergencyButtonExt = KeyguardPluginFactory.getEmergencyButtonExt(context);
        } catch (Exception e) {
            Log.d(TAG, "EmergencyButton() - error in calling getEmergencyButtonExt().") ;
            e.printStackTrace();
        }
        /// @}

        TypedArray localAtNonSecureValue =
                context.obtainStyledAttributes(attrs, R.styleable.ECCButtonAttr);
        mLocateAtNonSecureView = localAtNonSecureValue.getBoolean(
                R.styleable.ECCButtonAttr_locateAtNonSecureView, mLocateAtNonSecureView);
        localAtNonSecureValue = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLockPatternUtils = new LockPatternUtils(mContext);
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                takeEmergencyCallAction();
            }
        });

        /// M: Save secure query result here, when lockscreen is created, secure result should
        /// stay unchanged @{
        mIsSecure = mLockPatternUtils.isSecure(KeyguardUpdateMonitor.getCurrentUser());
        /// @}

        updateEmergencyCallButton();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateEmergencyCallButton();
        ///M: fix ALPS01969662, force to reload string when config(locale) changed.
        setText(R.string.kg_emergency_call_label);
    }

    /**
     * Shows the emergency dialer or returns the user to the existing call.
     */
    public void takeEmergencyCallAction() {
        MetricsLogger.action(mContext, MetricsEvent.ACTION_EMERGENCY_CALL);
        // TODO: implement a shorter timeout once new PowerManager API is ready.
        // should be the equivalent to the old userActivity(EMERGENCY_CALL_TIMEOUT)
        mPowerManager.userActivity(SystemClock.uptimeMillis(), true);
        try {
            ActivityManagerNative.getDefault().stopSystemLockTaskMode();
        } catch (RemoteException e) {
            Slog.w(LOG_TAG, "Failed to stop app pinning");
        }
        if (isInCall()) {
            resumeCall();
            if (mEmergencyButtonCallback != null) {
                mEmergencyButtonCallback.onEmergencyButtonClickedWhenInCall();
            }
        } else {
            KeyguardUpdateMonitor.getInstance(mContext).reportEmergencyCallAction(
                    true /* bypassHandler */);

            /// --------------- L PreMigration ------------
            /// M: Fill the extra info the intent to start emergency dialer.
            /// M: add for Ecc intent in none security mode
            int phoneId = getCurPhoneId();
            if (phoneId == -1) {
                phoneId = mEccPhoneIdForNoneSecurityMode;
            }
            mEmergencyButtonExt.customizeEmergencyIntent(INTENT_EMERGENCY_DIAL, phoneId);
            /// --------------- L PreMigration ------------

            getContext().startActivityAsUser(INTENT_EMERGENCY_DIAL,
                    ActivityOptions.makeCustomAnimation(getContext(), 0, 0).toBundle(),
                    new UserHandle(KeyguardUpdateMonitor.getCurrentUser()));
        }
    }

    private void updateEmergencyCallButton() {
        boolean visible = false;
        /// M: remove mIsVoiceCapable qualification @{
        //if (mIsVoiceCapable) {
            // Emergency calling requires voice capability.
            if (isInCall()) {
                visible = true; // always show "return to call" if phone is off-hook
            } else if (mLockPatternUtils.isEmergencyCallCapable()) {
                final boolean simLocked = KeyguardUpdateMonitor.getInstance(mContext)
                        .isSimPinVoiceSecure();
                if (simLocked) {
                    // Some countries can't handle emergency calls while SIM is locked.
                    visible = mEnableEmergencyCallWhileSimLocked;
                } else {
                    // Only show if there is a secure screen (pin/pattern/SIM pin/SIM puk);
                    visible = mLockPatternUtils.isSecure(KeyguardUpdateMonitor.getCurrentUser());
                }
            }

            boolean show = false ;

            /// M: If antitheft lock is on, we should also show ECC button @{
            boolean antiTheftLocked = AntiTheftManager.isAntiTheftLocked();
            /// M:CTA new feature
            boolean eccShouldShow = eccButtonShouldShow();

            Log.i(TAG, "mLocateAtNonSecureView = " + mLocateAtNonSecureView) ;

            if (mLocateAtNonSecureView && !mEmergencyButtonExt.showEccInNonSecureUnlock()) {
                Log.i(TAG, "ECC Button is located on Notification Keygaurd and OP do not ask"
                        + " to show. So this is a normal case ,we never show it.") ;
                show = false ;
            } else {
                show = (visible || antiTheftLocked
                        || mEmergencyButtonExt.showEccInNonSecureUnlock())
                        && eccShouldShow;

                Log.i(TAG, "show = " + show + " --> visible= " + visible + ", antiTheftLocked="
                    + antiTheftLocked + ", mEmergencyButtonExt.showEccInNonSecureUnlock() ="
                    + mEmergencyButtonExt.showEccInNonSecureUnlock() + ", eccShouldShow="
                    + eccShouldShow);
            }

            if (mLocateAtNonSecureView && !show) {
                Log.i(TAG, "If the button is on NotificationKeyguard and will not show," +
                    " we should just set it View.GONE to give more space to IndicationText.") ;
                this.setVisibility(View.GONE);
            } else {
                mLockPatternUtils.updateEmergencyCallButtonState(this, show, false);
            }

        //}
        /// @}
    }

    public void setCallback(EmergencyButtonCallback callback) {
        mEmergencyButtonCallback = callback;
    }

    /**
     * Resumes a call in progress.
     */
    private void resumeCall() {
        getTelecommManager().showInCallScreen(false);
    }

    /**
     * @return {@code true} if there is a call currently in progress.
     */
    private boolean isInCall() {
        return getTelecommManager().isInCall();
    }

    private TelecomManager getTelecommManager() {
        return (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }

    /// M: CTA new feature
    private boolean eccButtonShouldShow() {
        Bundle bd = null;
        int phoneCount = KeyguardUtils.getNumOfPhone();
        boolean[] isServiceSupportEcc = new boolean[phoneCount];

        try {
            ITelephonyEx phoneEx = ITelephonyEx.Stub.asInterface(
                    ServiceManager.checkService("phoneEx"));

            if (phoneEx != null) {
                /// M: add for Ecc intent in none security mode
                mEccPhoneIdForNoneSecurityMode = -1;
                for (int i = 0; i < phoneCount; i++) {
                    int subId = KeyguardUtils.getSubIdUsingPhoneId(i);
                    Log.i(TAG, "subId = " + subId + " , subIndex = " + i);
                    bd = phoneEx.getServiceState(subId);
                    if (bd != null) {
                        ServiceState ss = ServiceState.newFromBundle(bd);
                        Log.i(TAG, "ss.getState() = " + ss.getState() + " ss.isEmergencyOnly()="
                                + ss.isEmergencyOnly() + " for simId=" + i);
                        if (ServiceState.STATE_IN_SERVICE == ss.getState()
                                || ss.isEmergencyOnly()) {  //Full service or Limited service
                            isServiceSupportEcc[i] = true;
                            /// M: add for Ecc intent in none security mode
                            if (mEccPhoneIdForNoneSecurityMode == -1) {
                                mEccPhoneIdForNoneSecurityMode = i;
                            }
                        } else {
                            isServiceSupportEcc[i] = false;
                        }
                    }
                }
            }
        } catch (RemoteException e) {
            Log.i(TAG, "getServiceState error e:" + e.getMessage());
        }

        return mEmergencyButtonExt.showEccByServiceState(isServiceSupportEcc, getCurPhoneId());
    }

    /// M: Optimization, save lockpatternUtils's isSecure state
    private boolean mIsSecure;

    /**
     * M: Add for operator customization.
     * Get current sim slot id of PIN/PUK lock via security mode.
     *
     * @return Current sim phone id,
     *      return 0-3, current lockscreen is PIN/PUK,
     *      return -1, current lockscreen is not PIN/PUK.
     */
    private int getCurPhoneId() {
        KeyguardSecurityModel securityModel = new KeyguardSecurityModel(mContext);
        return securityModel.getPhoneIdUsingSecurityMode(securityModel.getSecurityMode());
    }
}
