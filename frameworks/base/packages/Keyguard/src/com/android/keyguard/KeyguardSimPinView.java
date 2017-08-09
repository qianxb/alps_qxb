/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.PhoneConstants;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;


/**
 * Displays a PIN pad for unlocking.
 */
public class KeyguardSimPinView extends KeyguardPinBasedInputView {
    private static final String LOG_TAG = "KeyguardSimPinView";
    private static final boolean DEBUG = KeyguardConstants.DEBUG_SIM_STATES;
    public static final String TAG = "KeyguardSimPinView";

    private ProgressDialog mSimUnlockProgressDialog = null;
    private CheckSimPin mCheckSimPinThread;

    private AlertDialog mRemainingAttemptsDialog;
    //M:
    KeyguardUtils mKeyguardUtils;
    private int mSubId;
    private ImageView mSimImageView;

    //M:
    private int mPhoneId = 0;

    KeyguardUpdateMonitorCallback mUpdateMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        // public void onSimStateChanged(int phoneId, /*int slotId,*/ State simState) {
        public void onSimStateChangedUsingPhoneId(int phoneId, IccCardConstants.State simState) {
            // if (DEBUG)
            // Log.v(TAG, "onSimStateChanged(subId=" + subId + ",state=" + simState + ")");
            // resetState();
            if (DEBUG) {
                Log.d(TAG, "onSimStateChangedUsingSubId: " + simState + ", phoneId=" + phoneId);
            }

            switch (simState) {
                case NOT_READY:
                case ABSENT:
                    if (phoneId == mPhoneId) {
                        KeyguardUpdateMonitor.getInstance(getContext())
                            .reportSimUnlocked(mPhoneId);
                        mCallback.dismiss(true);
                    }
                    break;
            }
       }
    };

    public KeyguardSimPinView(Context context) {
        this(context, null);
    }

    public KeyguardSimPinView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mKeyguardUtils = new KeyguardUtils(context);
    }

    @Override
    public void resetState() {
        super.resetState();
        if (DEBUG) Log.v(TAG, "Resetting state");
        /** KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(mContext);
        mSubId = monitor.getNextSubIdForState(IccCardConstants.State.PIN_REQUIRED);
        if (SubscriptionManager.isValidSubscriptionId(mSubId)) {
            int count = TelephonyManager.getDefault().getSimCount();
            Resources rez = getResources();
            final String msg;
            int color = Color.WHITE;
            if (count < 2) {
                msg = rez.getString(R.string.kg_sim_pin_instructions);
            } else {
                SubscriptionInfo info = monitor.getSubscriptionInfoForSubId(mSubId);
                CharSequence displayName = info != null ? info.getDisplayName() : ""; // don't crash
                msg = rez.getString(R.string.kg_sim_pin_instructions_multi, displayName);
                if (info != null) {
                    color = info.getIconTint();
                }
            }
            mSecurityMessageDisplay.setMessage(msg, true);
            mSimImageView.setImageTintList(ColorStateList.valueOf(color));
        } **/
        mSecurityMessageDisplay.setMessage(R.string.kg_sim_pin_instructions, true);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resetState();
    }

    @Override
    protected int getPromtReasonStringRes(int reason) {
        // No message on SIM Pin
        return 0;
    }

    private String getPinPasswordErrorMessage(int attemptsRemaining) {
        String displayMessage;

        if (attemptsRemaining == 0) {
            displayMessage = getContext().getString(R.string.kg_password_wrong_pin_code_pukked);
        } else if (attemptsRemaining > 0) {
            displayMessage = getContext().getResources()
                    .getQuantityString(R.plurals.kg_password_wrong_pin_code, attemptsRemaining,
                            attemptsRemaining);
        } else {
            displayMessage = getContext().getString(R.string.kg_password_pin_failed);
        }
        if (DEBUG) Log.d(LOG_TAG, "getPinPasswordErrorMessage:"
                + " attemptsRemaining=" + attemptsRemaining + " displayMessage=" + displayMessage);
        return displayMessage;
    }

    @Override
    protected boolean shouldLockout(long deadline) {
        // SIM PIN doesn't have a timed lockout
        return false;
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.simPinEntry;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mPhoneId = KeyguardUpdateMonitor.getInstance(getContext()).getSimPinLockPhoneId();
        if (KeyguardUtils.getNumOfPhone() > 1) {
            View simIcon = findViewById(R.id.keyguard_sim);
            if (simIcon != null) {
                simIcon.setVisibility(View.GONE);
            }
            View simInfoMsg = findViewById(R.id.sim_info_message);
            if (simInfoMsg != null) {
                simInfoMsg.setVisibility(View.VISIBLE);
            }
            dealwithSIMInfoChanged();
        }

        mSecurityMessageDisplay.setTimeout(0); // don't show ownerinfo/charging status by default
        if (mEcaView instanceof EmergencyCarrierArea) {
            ((EmergencyCarrierArea) mEcaView).setCarrierTextVisible(true);
        }
        // mSimImageView = (ImageView) findViewById(R.id.keyguard_sim);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mUpdateMonitorCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mUpdateMonitorCallback);
    }

    @Override
    public void showUsabilityHint() {
    }

    @Override
    public void onPause() {
        // dismiss the dialog.
        if (mSimUnlockProgressDialog != null) {
            mSimUnlockProgressDialog.dismiss();
            mSimUnlockProgressDialog = null;
        }
    }

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPin extends Thread {
        private final String mPin;
        // private int mSubId;

        protected CheckSimPin(String pin/**, int subId**/) {
            mPin = pin;
            // mSubId = subId;
        }

        abstract void onSimCheckResponse(final int result, final int attemptsRemaining);

        @Override
        public void run() {
            try {
                if (DEBUG) {
                    Log.v(TAG, "call supplyPinReportResultForSubscriber(subid=" + mSubId + ")");
                }
                Log.d(TAG, "call supplyPinReportResultForSubscriber() mPhoneId = " + mPhoneId);
                int subId = KeyguardUtils.getSubIdUsingPhoneId(mPhoneId);
                final int[] result = ITelephony.Stub.asInterface(ServiceManager
                        .checkService("phone")).supplyPinReportResultForSubscriber(mSubId, mPin);
                if (DEBUG) {
                    Log.v(TAG, "supplyPinReportResult returned: " + result[0] + " " + result[1]);
                }
                post(new Runnable() {
                    @Override
                    public void run() {
                        onSimCheckResponse(result[0], result[1]);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException for supplyPinReportResult:", e);
                post(new Runnable() {
                    @Override
                    public void run() {
                        onSimCheckResponse(PhoneConstants.PIN_GENERAL_FAILURE, -1);
                    }
                });
            }
        }
    }

    private Dialog getSimUnlockProgressDialog() {
        if (mSimUnlockProgressDialog == null) {
            mSimUnlockProgressDialog = new ProgressDialog(mContext);
            mSimUnlockProgressDialog.setMessage(
                    mContext.getString(R.string.kg_sim_unlock_progress_dialog_message));
            mSimUnlockProgressDialog.setIndeterminate(true);
            mSimUnlockProgressDialog.setCancelable(false);
            mSimUnlockProgressDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        }
        return mSimUnlockProgressDialog;
    }

    private Dialog getSimRemainingAttemptsDialog(int remaining) {
        String msg = getPinPasswordErrorMessage(remaining);
        if (mRemainingAttemptsDialog == null) {
            Builder builder = new AlertDialog.Builder(mContext);
            builder.setMessage(msg);
            builder.setCancelable(false);
            builder.setNeutralButton(R.string.ok, null);
            mRemainingAttemptsDialog = builder.create();
            mRemainingAttemptsDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        } else {
            mRemainingAttemptsDialog.setMessage(msg);
        }
        return mRemainingAttemptsDialog;
    }

    @Override
    protected void verifyPasswordAndUnlock() {
        String entry = mPasswordEntry.getText();

        if (entry.length() < 4) {
            // otherwise, display a message to the user, and don't submit.
            mSecurityMessageDisplay.setMessage(R.string.kg_invalid_sim_pin_hint, true);
            resetPasswordText(true /* animate */, true /* announce */);
            mCallback.userActivity();
            return;
        }

        getSimUnlockProgressDialog().show();

        if (mCheckSimPinThread == null) {
            // mCheckSimPinThread = new CheckSimPin(mPasswordEntry.getText(), mSubId) {
            mCheckSimPinThread = new CheckSimPin(mPasswordEntry.getText()) {
                @Override
                void onSimCheckResponse(final int result, final int attemptsRemaining) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (mSimUnlockProgressDialog != null) {
                                mSimUnlockProgressDialog.hide();
                            }
                            //resetPasswordText(true /* animate */,
                            //        result != PhoneConstants.PIN_RESULT_SUCCESS /* announce */);
                            if (result == PhoneConstants.PIN_RESULT_SUCCESS) {
                                // KeyguardUpdateMonitor.getInstance(getContext())
                                //         .reportSimUnlocked(mSubId);
                                KeyguardUpdateMonitor.getInstance(getContext())
                                        .reportSimUnlocked(mPhoneId);
                                mCallback.dismiss(true);
                            } else {
                                if (result == PhoneConstants.PIN_PASSWORD_INCORRECT) {
                                    if (attemptsRemaining <= 2) {
                                        // this is getting critical - show dialog
                                        getSimRemainingAttemptsDialog(attemptsRemaining).show();
                                    } else {
                                        // show message
                                        mSecurityMessageDisplay.setMessage(
                                                getPinPasswordErrorMessage(attemptsRemaining), true);
                                    }
                                } else {
                                    // "PIN operation failed!" - no idea what this was and no way to
                                    // find out. :/
                                    mSecurityMessageDisplay.setMessage(getContext().getString(
                                            R.string.kg_password_pin_failed), true);
                                }
                                if (DEBUG) Log.d(LOG_TAG, "verifyPasswordAndUnlock "
                                        + " CheckSimPin.onSimCheckResponse: " + result
                                        + " attemptsRemaining=" + attemptsRemaining);
                                //M:
                                resetPasswordText(true /* animate */,
                                        result != PhoneConstants.PIN_RESULT_SUCCESS /* announce */);
                            }
                            mCallback.userActivity();
                            mCheckSimPinThread = null;
                        }
                    });
                }
            };
            mCheckSimPinThread.start();
        }
    }

    @Override
    public void startAppearAnimation() {
        // noop.
    }

    @Override
    public boolean startDisappearAnimation(Runnable finishRunnable) {
        return false;
    }

    private void dealwithSIMInfoChanged() {
        String operName = null;

        try {
            operName = mKeyguardUtils.getOptrNameUsingPhoneId(mPhoneId, mContext);
        } catch (IndexOutOfBoundsException e) {
            Log.w(TAG, "getOptrNameBySlot exception, mPhoneId=" + mPhoneId);
        }
        if (DEBUG) {
            Log.i(TAG, "dealwithSIMInfoChanged, mPhoneId=" + mPhoneId + ", operName=" + operName);
        }
        TextView forText = (TextView) findViewById(R.id.for_text);
        ImageView subIcon = (ImageView) findViewById(R.id.sub_icon);
        TextView simCardName = (TextView) findViewById(R.id.sim_card_name);
        if (null == operName) { //this is the new SIM card inserted
            if (DEBUG) {
                Log.d(TAG, "mPhoneId " + mPhoneId + " is new subInfo record");
            }
            setForTextNewCard(mPhoneId, forText);
            subIcon.setVisibility(View.GONE);
            simCardName.setVisibility(View.GONE);
        } else {
            if (DEBUG) {
                Log.d(TAG, "dealwithSIMInfoChanged, show operName for mPhoneId=" + mPhoneId);
            }
            forText.setText(mContext.getString(R.string.kg_slot_id, mPhoneId + 1) + " ");
            simCardName.setText(null == operName ?
                    mContext.getString(R.string.kg_detecting_simcard) : operName);
            Bitmap iconBitmap = mKeyguardUtils.getOptrBitmapUsingPhoneId(mPhoneId, mContext);
            subIcon.setImageBitmap(iconBitmap);
            subIcon.setVisibility(View.VISIBLE);
            simCardName.setVisibility(View.VISIBLE);
        }
    }

    private void setForTextNewCard(int phoneId, TextView forText) {
        StringBuffer forSb = new StringBuffer();

        forSb.append(mContext.getString(R.string.kg_slot_id, phoneId + 1));
        forSb.append(" ");
        forSb.append(mContext.getText(R.string.kg_new_simcard));
        forText.setText(forSb.toString());
    }
}

