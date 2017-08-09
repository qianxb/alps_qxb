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

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;
import android.text.method.SingleLineTransformationMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settingslib.WirelessUtils;

import com.mediatek.keyguard.ext.ICarrierTextExt;
import com.mediatek.keyguard.ext.IOperatorSIMString;
import com.mediatek.keyguard.ext.IOperatorSIMString.SIMChangedTag;
import com.mediatek.keyguard.Plugin.KeyguardPluginFactory;

public class CarrierText extends TextView {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "CarrierText";

    private static CharSequence mSeparator;

    private final boolean mIsEmergencyCallCapable;

    private KeyguardUpdateMonitor mKeyguardUpdateMonitor;

    private WifiManager mWifiManager;

    ///M: added for multi-sim project
    private Context mContext ;
    private KeyguardUpdateMonitor mUpdateMonitor;
    private int mNumOfPhone;
    private State mSimState[];
    private StatusMode mStatusMode[];
    private String mCarrier[];
    private boolean mCarrierNeedToShow[];
    private static final String CARRIER_DIVIDER = " | " ;
    private boolean mUseAllCaps;
     /// M: To get the proper SIM UIM string according to operator.
    private IOperatorSIMString mIOperatorSIMString;
    /// M: To changed the plmn  CHINA TELECOM to China Telecom,just for CT feature
    private ICarrierTextExt mCarrierTextExt;

    ///M: fix ALPS02068458, clear carrier text when IPO shut down.
    private static final String IPO_SHUTDOWN = "android.intent.action.ACTION_SHUTDOWN_IPO";
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (IPO_SHUTDOWN.equals(action)) {
                Log.w(TAG, "receive IPO_SHUTDOWN & clear carrier text.");
                setText("") ;
            }
        }
    };

    private KeyguardUpdateMonitorCallback mCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onRefreshCarrierInfo() {
            updateCarrierText() ;
        }

        @Override
        public void onSimStateChangedUsingPhoneId(int phoneId, IccCardConstants.State simState) {
            updateCarrierText() ;
        }

        public void onFinishedGoingToSleep(int why) {
            setSelected(false);
        };

        public void onStartedWakingUp() {
            setSelected(true);
        };
    };
    /**
     * The status of this lock screen. Primarily used for widgets on LockScreen.
     */
    private static enum StatusMode {
        Normal, // Normal case (sim card present, it's not locked)
        NetworkLocked, // SIM card is 'network locked'.
        SimMissing, // SIM card is missing.
        SimMissingLocked, // SIM card is missing, and device isn't provisioned; don't allow access
        SimPukLocked, // SIM card is PUK locked because SIM entered wrong too many times
        SimLocked, // SIM card is currently locked
        SimPermDisabled, // SIM card is permanently disabled due to PUK unlock failure
        SimNotReady, // SIM is not ready yet. May never be on devices w/o a SIM.

        /// M: mediatek add sim state
        SimUnknown,
        NetworkSearching;  //The sim card is ready, but searching network
    }

    /// M: Support GeminiPlus
    private void initMembers() {
        mNumOfPhone = KeyguardUtils.getNumOfPhone();

        mCarrier = new String[mNumOfPhone];
        mCarrierNeedToShow = new boolean[mNumOfPhone];

        mStatusMode = new StatusMode[mNumOfPhone];
        for (int i = 0; i < mNumOfPhone ; i++) {
            mStatusMode[i] = StatusMode.Normal;
        }
    }

    public CarrierText(Context context) {
        this(context, null);
        /// M: Support GeminiPlus
        initMembers();
    }

    public CarrierText(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context ;
        mIsEmergencyCallCapable = context.getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);
        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        /// M: Support GeminiPlus
        initMembers();

        /// M: Init the plugin for changing the String with SIM according to Operator.
        mIOperatorSIMString = KeyguardPluginFactory.getOperatorSIMString(mContext);
        mCarrierTextExt = KeyguardPluginFactory.getCarrierTextExt(mContext);

        boolean useAllCaps;
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.CarrierText, 0, 0);
        try {
            useAllCaps = a.getBoolean(R.styleable.CarrierText_allCaps, false);
        } finally {
            a.recycle();
        }
        setTransformationMethod(new CarrierTextTransformationMethod(mContext, useAllCaps));

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    protected void updateCarrierText() {
        // boolean allSimsMissing = true;
        boolean anySimReadyAndInService = false;
        CharSequence displayText = null;
        ///M: TODO, temp added for hnb & csg.
        CharSequence hnbName = null ;
        CharSequence csgId = null ;

        /***List<SubscriptionInfo> subs = mKeyguardUpdateMonitor.getSubscriptionInfo(false);
        final int N = subs.size();
        if (DEBUG) Log.d(TAG, "updateCarrierText(): " + N);
        for (int i = 0; i < N; i++) {
            int subId = subs.get(i).getSubscriptionId();
            State simState = mKeyguardUpdateMonitor.getSimState(subId);
            CharSequence carrierName = subs.get(i).getCarrierName();
            CharSequence carrierTextForSimState = getCarrierTextForSimState(simState, carrierName);
            if (DEBUG) {
                Log.d(TAG, "Handling (subId=" + subId + "): " + simState + " " + carrierName);
            }
            if (carrierTextForSimState != null) {
                allSimsMissing = false;
                displayText = concatenate(displayText, carrierTextForSimState);
            }
        } ***/
        /***Google default if (allSimsMissing) {
            if (simState == IccCardConstants.State.READY) {
                ServiceState ss = mKeyguardUpdateMonitor.mServiceStates.get(subId);
                if (ss != null && ss.getDataRegState() == ServiceState.STATE_IN_SERVICE) {
                    // hack for WFC (IWLAN) not turning off immediately once
                    // Wi-Fi is disassociated or disabled
                    if (ss.getRilDataRadioTechnology() != ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN
                            || (mWifiManager.isWifiEnabled()
                                    && mWifiManager.getConnectionInfo() != null
                                    && mWifiManager.getConnectionInfo().getBSSID() != null)) {
                        if (DEBUG) {
                            Log.d(TAG, "SIM ready and in service: subId=" + subId + ", ss=" + ss);
                        }
                        anySimReadyAndInService = true;
                    }
                }
            }

            if (N != 0) {
                // Shows "No SIM card | Emergency calls only" on devices that are voice-capable.
                // This depends on mPlmn containing the text "Emergency calls only" when the radio
                // has some connectivity. Otherwise, it should be null or empty and just show
                // "No SIM card"
                // Grab the first subscripton, because they all should contain the emergency text,
                // described above.
                displayText =  makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_missing_sim_message_short),
                        subs.get(0).getCarrierName());
            } else {
                // We don't have a SubscriptionInfo to get the emergency calls only from.
                // Grab it from the old sticky broadcast if possible instead. We can use it
                // here because no subscriptions are active, so we don't have
                // to worry about MSIM clashing.
                CharSequence text =
                        getContext().getText(com.android.internal.R.string.emergency_calls_only);
                Intent i = getContext().registerReceiver(null,
                        new IntentFilter(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION));
                if (i != null) {
                    String spn = "";
                    String plmn = "";
                    if (i.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false)) {
                        spn = i.getStringExtra(TelephonyIntents.EXTRA_SPN);
                    }
                    if (i.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false)) {
                        plmn = i.getStringExtra(TelephonyIntents.EXTRA_PLMN);
                    }
                    if (DEBUG) Log.d(TAG, "Getting plmn/spn sticky brdcst " + plmn + "/" + spn);
                    if (Objects.equals(plmn, spn)) {
                        text = plmn;
                    } else {
                        text = concatenate(plmn, spn);
                    }
                }
                displayText =  makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_missing_sim_message_short), text);
            } *****/
        if (isWifiOnlyDevice()) {
            Log.d(TAG, "updateCarrierText() - WifiOnly deivce, not show carrier text.");
            setText("") ;
            return;
        }

        boolean allSimsMissing = showOrHideCarrier() ;

        for (int phoneId = 0; phoneId < mNumOfPhone; phoneId++) {
            int subId = KeyguardUtils.getSubIdUsingPhoneId(phoneId) ;
            State simState = mKeyguardUpdateMonitor.getSimStateOfPhoneId(phoneId);

            SubscriptionInfo subInfo = mKeyguardUpdateMonitor.getSubscriptionInfoForSubId(subId) ;
            CharSequence carrierName = (subInfo == null) ? null : subInfo.getCarrierName() ;
            Log.d(TAG, "updateCarrierText(): subId = " + subId + " , phoneId = " + phoneId +
                ", simState = " + simState + ", carrierName = " + carrierName) ;

            if (simState == IccCardConstants.State.READY) {
                ServiceState ss = mKeyguardUpdateMonitor.mServiceStates.get(subId);
                if (ss != null && ss.getDataRegState() == ServiceState.STATE_IN_SERVICE) {
                    // hack for WFC (IWLAN) not turning off immediately once
                    // Wi-Fi is disassociated or disabled
                    if (ss.getRilDataRadioTechnology() != ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN
                            || (mWifiManager.isWifiEnabled()
                                    && mWifiManager.getConnectionInfo() != null
                                    && mWifiManager.getConnectionInfo().getBSSID() != null)) {
                        if (DEBUG) {
                            Log.d(TAG, "SIM ready and in service: subId=" + subId + ", ss=" + ss);
                        }
                        anySimReadyAndInService = true;
                    }
                }
            }

            ///M: TODO, temp added for hnb & csg.
            //hnbName = read from submgr ;
            //csgId = read from submgr ;

            CharSequence carrierTextForSimState =
                getCarrierTextForSimState(phoneId, simState, carrierName, hnbName, csgId);

            if (carrierTextForSimState != null) {
                /// M: Change the String with SIM according to Operator.
                carrierTextForSimState = mIOperatorSIMString.getOperatorSIMString(
                    carrierTextForSimState.toString(),
                    phoneId, SIMChangedTag.DELSIM, mContext);

                if (carrierTextForSimState != null) {
                    carrierTextForSimState = mCarrierTextExt.customizeCarrierTextCapital(
                        carrierTextForSimState.toString()).toString() ;
                } else {
                    carrierTextForSimState = null ;
                }
                Log.d(TAG, "updateCarrierText() - after customizeCarrierTextCapital, " +
                    "carrierTextForSimState = " + carrierTextForSimState) ;
            }

            if (carrierTextForSimState != null) {
                mCarrier[phoneId] = carrierTextForSimState.toString() ;
            } else {
                mCarrier[phoneId] = null ;
            }
        }
        // find all need-to-show carrier text, combine, and set text.
        String carrierFinalContent = null;
        String divider = mCarrierTextExt.customizeCarrierTextDivider(mSeparator.toString());
        for (int i = 0 ; i < mNumOfPhone ; i++) {
            Log.d(TAG, "updateCarrierText() - mCarrierNeedToShow[i] = " + mCarrierNeedToShow[i]
                + " mCarrier[i] = " + mCarrier[i]) ;
            ///M: fix ALPS01963660, do not show "null" string.
            if (mCarrierNeedToShow[i] && (mCarrier[i] != null)) {
                if (carrierFinalContent == null) {
                    //first need-to-show
                    carrierFinalContent = mCarrier[i] ;
                } else {
                    carrierFinalContent = new StringBuilder(carrierFinalContent).
                                          append(divider).
                                          append(mCarrier[i]).toString();
                }
            }
        }

        // APM (airplane mode) != no carrier state. There are carrier services
        // (e.g. WFC = Wi-Fi calling) which may operate in APM.
        if (!anySimReadyAndInService && WirelessUtils.isAirplaneModeOn(mContext)) {
            carrierFinalContent = getContext().getString(R.string.airplane_mode);
        }

        Log.d(TAG, "updateCarrierText() - after combination, carrierFinalContent = " +
                   carrierFinalContent) ;

        // setText(displayText);
        setText(carrierFinalContent);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSeparator = getResources().getString(
                com.android.internal.R.string.kg_text_message_separator);
        boolean shouldMarquee = KeyguardUpdateMonitor.getInstance(mContext).isDeviceInteractive();
        setSelected(shouldMarquee); // Allow marquee to work.

        ///M: added
        setLayerType(LAYER_TYPE_HARDWARE, null); // work around nested unclipped SaveLayer bug
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (ConnectivityManager.from(mContext).isNetworkSupported(
                ConnectivityManager.TYPE_MOBILE)) {
            mKeyguardUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
            mKeyguardUpdateMonitor.registerCallback(mCallback);
        } else {
            // Don't listen and clear out the text when the device isn't a phone.
            mKeyguardUpdateMonitor = null;
            setText("");
        }

        registerBroadcastReceiver();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mKeyguardUpdateMonitor != null) {
            mKeyguardUpdateMonitor.removeCallback(mCallback);
        }

        unregisterBroadcastReceiver();
    }

    /**
     * Top-level function for creating carrier text. Makes text based on simState, PLMN
     * and SPN as well as device capabilities, such as being emergency call capable.
     *
     * @param simState
     * @param text
     * @param spn
     * @return Carrier text if not in missing state, null otherwise.
     */
    /**private CharSequence getCarrierTextForSimState(IccCardConstants.State simState,
            CharSequence text) { **/
    private CharSequence getCarrierTextForSimState(int phoneId, IccCardConstants.State simState,
            CharSequence text, CharSequence hnbName, CharSequence csgId) {
        CharSequence carrierText = null;
        StatusMode status = getStatusForIccState(simState);
        switch (status) {
            case Normal:
                carrierText = text;
                break;

            case SimNotReady:
                // Null is reserved for denoting missing, in this case we have nothing to display.
                carrierText = ""; // nothing to display yet.
                carrierText = null;
                break;

            case NetworkLocked:
                // carrierText = makeCarrierStringOnEmergencyCapable(
                //        mContext.getText(R.string.keyguard_network_locked_message), text);
                carrierText = makeCarrierStringOnEmergencyCapable(
                        mContext.getText(R.string.keyguard_network_locked_message),
                        text, hnbName, csgId);
                break;

            case SimMissing:
                // carrierText = null;
                // Shows "No SIM card | Emergency calls only" on devices that are voice-capable.
                // This depends on mPlmn containing the text "Emergency calls only" when the radio
                // has some connectivity. Otherwise, it should be null or empty and just show
                // "No SIM card"
                CharSequence simMessage = getContext().getText(
                                            R.string.keyguard_missing_sim_message_short);

                /// M: migrate from 5.1.0_r3 patch, but there is a bug.
                ///    Because it is a sticky intent, we may receive wrong plmn.
                ///    So we just migrate it but not enable the code.
                /*
                if (text == null) {
                    // We won't have a SubscriptionInfo to get the emergency calls only from.
                    // Grab it from the old sticky broadcast if possible instead. We can use it
                    // here because no subscriptions are active, so we don't have
                    // to worry about MSIM clashing.
                    text = getContext().getText(com.android.internal.R.string.emergency_calls_only);
                    Intent i = getContext().registerReceiver(null,
                            new IntentFilter(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION));
                    if (i != null) {
                        String spn = "";
                        String plmn = "";
                        if (i.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false)) {
                            spn = i.getStringExtra(TelephonyIntents.EXTRA_SPN);
                        }
                        if (i.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false)) {
                            plmn = i.getStringExtra(TelephonyIntents.EXTRA_PLMN);
                        }
                        Log.d(TAG, "Getting plmn/spn sticky brdcst " + plmn + "/" + spn);
                        text = concatenate(plmn, spn);
                    }
                }
                */

                carrierText =
                    makeCarrierStringOnEmergencyCapable(simMessage, text, hnbName, csgId);

                carrierText = mCarrierTextExt.customizeCarrierText(carrierText,
                        simMessage, phoneId);
                /// M: sync the carrier text with systemui expended notification bar.
                carrierText = mCarrierTextExt.customizeCarrierTextWhenSimMissing(carrierText);
                break;

            case SimPermDisabled:
                carrierText = getContext().getText(
                        R.string.keyguard_permanent_disabled_sim_message_short);
                break;

            case SimMissingLocked:
                carrierText = null;
                break;

            case SimLocked:
                // carrierText = makeCarrierStringOnEmergencyCapable(
                //        getContext().getText(R.string.keyguard_sim_locked_message),
                //        text);
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_locked_message),
                        text, hnbName, csgId);
                break;

            case SimPukLocked:
                // carrierText = makeCarrierStringOnEmergencyCapable(
                //         getContext().getText(R.string.keyguard_sim_puk_locked_message),
                //         text);
                carrierText = makeCarrierStringOnEmergencyCapable(
                        getContext().getText(R.string.keyguard_sim_puk_locked_message),
                        text, hnbName, csgId);
                break;
            default:
                carrierText = text;
                break;
        }

        /// M: added for CDMA card type is locked.
        if (carrierText != null) {
            carrierText = mCarrierTextExt.customizeCarrierTextWhenCardTypeLocked(
                    carrierText, mContext, phoneId, false).toString();
        }
        Log.d(TAG, "getCarrierTextForSimState simState=" + simState +
            " text(carrierName)=" + text + " HNB=" + hnbName +
            " CSG=" + csgId + " carrierText=" + carrierText);
        return carrierText;
    }

    /*
     * Add emergencyCallMessage to carrier string only if phone supports emergency calls.
     */
    /**private CharSequence makeCarrierStringOnEmergencyCapable(
            CharSequence simMessage, CharSequence emergencyCallMessage) {
        if (mIsEmergencyCallCapable) {
            return concatenate(simMessage, emergencyCallMessage);
        }
        return simMessage;
    }**/

    private CharSequence makeCarrierStringOnEmergencyCapable(
            CharSequence simMessage, CharSequence emergencyCallMessage,
            CharSequence hnbName, CharSequence csgId) {

        CharSequence emergencyCallMessageExtend = emergencyCallMessage;
        if (!TextUtils.isEmpty(emergencyCallMessage)) {
            emergencyCallMessageExtend = appendCsgInfo(emergencyCallMessage, hnbName, csgId);
        }

        if (mIsEmergencyCallCapable) {
            return concatenate(simMessage, emergencyCallMessageExtend);
        }
        return simMessage;
    }

    /**
     * Determine the current status of the lock screen given the SIM state and other stuff.
     */
    private StatusMode getStatusForIccState(IccCardConstants.State simState) {
        // Since reading the SIM may take a while, we assume it is present until told otherwise.
        if (simState == null) {
            // return StatusMode.Normal;
            return StatusMode.SimUnknown;
        }

        final boolean missingAndNotProvisioned =
                !KeyguardUpdateMonitor.getInstance(mContext).isDeviceProvisioned()
                && (simState == IccCardConstants.State.ABSENT ||
                        simState == IccCardConstants.State.PERM_DISABLED);
        // Assume we're NETWORK_LOCKED if not provisioned
        // M: Directly maps missing and not Provisioned to SimMissingLocked Status.
        if (missingAndNotProvisioned) {
            return StatusMode.SimMissingLocked;
        }
        //simState = missingAndNotProvisioned ? IccCardConstants.State.NETWORK_LOCKED : simState;
        //@}

        switch (simState) {
            case ABSENT:
                return StatusMode.SimMissing;
            case NETWORK_LOCKED:
                // return StatusMode.SimMissingLocked;
                // M: correct IccCard state NETWORK_LOCKED maps to NetowrkLocked.
                return StatusMode.NetworkLocked;
            case NOT_READY:
                return StatusMode.SimNotReady;
            case PIN_REQUIRED:
                return StatusMode.SimLocked;
            case PUK_REQUIRED:
                return StatusMode.SimPukLocked;
            case READY:
                return StatusMode.Normal;
            case PERM_DISABLED:
                return StatusMode.SimPermDisabled;
            case UNKNOWN:
                // return StatusMode.SimMissing;
                return StatusMode.SimUnknown;
        }
        return StatusMode.SimMissing;
    }

    private static CharSequence concatenate(CharSequence plmn, CharSequence spn) {
        final boolean plmnValid = !TextUtils.isEmpty(plmn);
        final boolean spnValid = !TextUtils.isEmpty(spn);
        if (plmnValid && spnValid) {
            return new StringBuilder().append(plmn).append(mSeparator).append(spn).toString();
        } else if (plmnValid) {
            return plmn;
        } else if (spnValid) {
            return spn;
        } else {
            return "";
        }
    }

    /**
     * M: Used to check weather this device is wifi only.
     */
    private boolean isWifiOnlyDevice() {
        ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(
                                                        Context.CONNECTIVITY_SERVICE);
        return  !(cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE));
    }

    /**
     * M: Used to control carrier TextView visibility in Gemini.
     * (1) if the device is wifi only, we hide both carrier TextView.
     * (2) if both sim are missing, we shwon only one carrier TextView center.
     * (3) if either one sim is missing, we shwon the visible carrier TextView center.
     * (4) if both sim are not missing, we shwon boteh TextView, one in the left the other right.
     */
    /// M: Support GeminiPlus
    private boolean showOrHideCarrier() {
        int mNumOfSIM = 0;

        for (int i = 0; i < mNumOfPhone; i++) {
            State simState = mKeyguardUpdateMonitor.getSimStateOfPhoneId(i);
            StatusMode statusMode = getStatusForIccState(simState);
            boolean simMissing = (statusMode == StatusMode.SimMissing
                || statusMode == StatusMode.SimMissingLocked
                || statusMode == StatusMode.SimUnknown);
            Log.d(TAG, "showOrHideCarrier() - before showCarrierTextWhenSimMissing," +
                       "phone#" + i + " simMissing = " + simMissing) ;
            simMissing = mCarrierTextExt.showCarrierTextWhenSimMissing(simMissing, i);
            Log.d(TAG, "showOrHideCarrier() - after showCarrierTextWhenSimMissing," +
                       "phone#" + i + " simMissing = " + simMissing) ;

            if (!simMissing) {
                mCarrierNeedToShow[i] = true ;
                mNumOfSIM++;
            } else {
                mCarrierNeedToShow[i] = false ;
            }
        }

        List<SubscriptionInfo> subs = mKeyguardUpdateMonitor.getSubscriptionInfo(false);
        if (mNumOfSIM == 0) {
            String defaultPlmn = mUpdateMonitor.getDefaultPlmn().toString();
            int index = 0;
            for (int i = 0; i < subs.size(); i++) {
                //CharSequence plmn = mUpdateMonitor.getTelephonyPlmn(i);
                SubscriptionInfo info = subs.get(i);
                int subId = info.getSubscriptionId() ;
                int phoneId = info.getSimSlotIndex() ;
                CharSequence carrierName = info.getCarrierName();
                if (carrierName != null && defaultPlmn.contentEquals(carrierName) == false) {
                    index = phoneId;
                    break;
                }
            }
            mCarrierNeedToShow[index] = true ;
            Log.d(TAG, "updateOperatorInfo, No SIM cards, force slotId " +
                       index + " to visible.");
        }

        return (mNumOfSIM == 0) ;
    }

    private CharSequence appendCsgInfo(CharSequence srcText, CharSequence hnbName,
        CharSequence csgId) {

        CharSequence outText = srcText;
        if (!TextUtils.isEmpty(hnbName)) {
            outText = concatenate(srcText, hnbName);
        } else if (!TextUtils.isEmpty(csgId)) {
            outText = concatenate(srcText, csgId);
        }

        return outText;
    }

    private CharSequence getCarrierHelpTextForSimState(IccCardConstants.State simState,
            String plmn, String spn) {
        int carrierHelpTextId = 0;
        StatusMode status = getStatusForIccState(simState);
        switch (status) {
            case NetworkLocked:
                carrierHelpTextId = R.string.keyguard_instructions_when_pattern_disabled;
                break;

            case SimMissing:
                carrierHelpTextId = R.string.keyguard_missing_sim_instructions_long;
                break;

            case SimPermDisabled:
                carrierHelpTextId = R.string.keyguard_permanent_disabled_sim_instructions;
                break;

            case SimMissingLocked:
                carrierHelpTextId = R.string.keyguard_missing_sim_instructions;
                break;

            case Normal:
            case SimLocked:
            case SimPukLocked:
                break;
        }

        return mContext.getText(carrierHelpTextId);
    }

    private class CarrierTextTransformationMethod extends SingleLineTransformationMethod {
        private final Locale mLocale;
        private final boolean mAllCaps;

        public CarrierTextTransformationMethod(Context context, boolean allCaps) {
            mLocale = context.getResources().getConfiguration().locale;
            mAllCaps = allCaps;
        }

        @Override
        public CharSequence getTransformation(CharSequence source, View view) {
            source = super.getTransformation(source, view);

            if (mAllCaps && source != null) {
                source = source.toString().toUpperCase(mLocale);
            }

            return source;
        }
    }

    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(IPO_SHUTDOWN);
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    private void unregisterBroadcastReceiver() {
        mContext.unregisterReceiver(mBroadcastReceiver);
    }
}
