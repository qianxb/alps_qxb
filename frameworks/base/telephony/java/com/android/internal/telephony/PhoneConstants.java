/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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
package com.android.internal.telephony;

/**
 * @hide
 */
public class PhoneConstants {

    /**
     * The phone state. One of the following:<p>
     * <ul>
     * <li>IDLE = no phone activity</li>
     * <li>RINGING = a phone call is ringing or call waiting.
     *  In the latter case, another call is active as well</li>
     * <li>OFFHOOK = The phone is off hook. At least one call
     * exists that is dialing, active or holding and no calls are
     * ringing or waiting.</li>
     * </ul>
     */
    public enum State {
        IDLE, RINGING, OFFHOOK;
    };

    /**
      * The state of a data connection.
      * <ul>
      * <li>CONNECTED = IP traffic should be available</li>
      * <li>CONNECTING = Currently setting up data connection</li>
      * <li>DISCONNECTED = IP not available</li>
      * <li>SUSPENDED = connection is created but IP traffic is
      *                 temperately not available. i.e. voice call is in place
      *                 in 2G network</li>
      * </ul>
      */
    public enum DataState {
        CONNECTED, CONNECTING, DISCONNECTED, SUSPENDED;
    };

    // M: [LTE][Low Power][UL traffic shaping] Start
    // abnormal mode
    public static final String LTE_ACCESS_STRATUM_STATE_UNKNOWN  = "unknown";
    // power saving mode candidate
    public static final String LTE_ACCESS_STRATUM_STATE_IDLE  = "idle";
    // normal power mode
    public static final String LTE_ACCESS_STRATUM_STATE_CONNECTED  = "connected";
    // M: [LTE][Low Power][UL traffic shaping] End

    public static final String STATE_KEY = "state";

    /// M: CC: Notify Call state with phoneType
    public static final String PHONE_TYPE_KEY = "phoneType";

    // Radio Type
    public static final int PHONE_TYPE_NONE = RILConstants.NO_PHONE;
    public static final int PHONE_TYPE_GSM = RILConstants.GSM_PHONE;
    public static final int PHONE_TYPE_CDMA = RILConstants.CDMA_PHONE;
    public static final int PHONE_TYPE_SIP = RILConstants.SIP_PHONE;
    public static final int PHONE_TYPE_THIRD_PARTY = RILConstants.THIRD_PARTY_PHONE;
    public static final int PHONE_TYPE_IMS = RILConstants.IMS_PHONE;
    // Currently this is used only to differentiate CDMA and CDMALTE Phone in GsmCdma* files. For
    // anything outside of that, a cdma + lte phone is still CDMA_PHONE
    public static final int PHONE_TYPE_CDMA_LTE = RILConstants.CDMA_LTE_PHONE;

    // Modes for LTE_ON_CDMA
    public static final int LTE_ON_CDMA_UNKNOWN = RILConstants.LTE_ON_CDMA_UNKNOWN;
    public static final int LTE_ON_CDMA_FALSE = RILConstants.LTE_ON_CDMA_FALSE;
    public static final int LTE_ON_CDMA_TRUE = RILConstants.LTE_ON_CDMA_TRUE;

    // Number presentation type for caller id display (From internal/Connection.java)
    public static final int PRESENTATION_ALLOWED = 1;    // normal
    public static final int PRESENTATION_RESTRICTED = 2; // block by user
    public static final int PRESENTATION_UNKNOWN = 3;    // no specified or unknown by network
    public static final int PRESENTATION_PAYPHONE = 4;   // show pay phone info


    public static final String PHONE_NAME_KEY = "phoneName";
    public static final String FAILURE_REASON_KEY = "reason";
    public static final String STATE_CHANGE_REASON_KEY = "reason";
    public static final String DATA_NETWORK_TYPE_KEY = "networkType";
    public static final String DATA_FAILURE_CAUSE_KEY = "failCause";
    public static final String DATA_APN_TYPE_KEY = "apnType";
    public static final String DATA_APN_KEY = "apn";
    public static final String DATA_LINK_PROPERTIES_KEY = "linkProperties";
    public static final String DATA_NETWORK_CAPABILITIES_KEY = "networkCapabilities";

    public static final String DATA_IFACE_NAME_KEY = "iface";
    public static final String NETWORK_UNAVAILABLE_KEY = "networkUnvailable";
    public static final String DATA_NETWORK_ROAMING_KEY = "networkRoaming";
    public static final String PHONE_IN_ECM_STATE = "phoneinECMState";
    public static final String PHONE_IN_EMERGENCY_CALL = "phoneInEmergencyCall";

    public static final String REASON_LINK_PROPERTIES_CHANGED = "linkPropertiesChanged";

    // M: [LTE][Low Power][UL traffic shaping] Start
    public static final String LTE_ACCESS_STRATUM_STATE_KEY = "lteAccessStratumState";
    public static final String SHARED_DEFAULT_APN_KEY = "sharedDefaultApn";
    public static final String PS_NETWORK_TYPE_KEY = "psNetworkType";
    // M: [LTE][Low Power][UL traffic shaping] End

    /**
     * Return codes for supplyPinReturnResult and
     * supplyPukReturnResult APIs
     */
    public static final int PIN_RESULT_SUCCESS = 0;
    public static final int PIN_PASSWORD_INCORRECT = 1;
    public static final int PIN_GENERAL_FAILURE = 2;

    /**
     * Return codes for <code>enableApnType()</code>
     */
    public static final int APN_ALREADY_ACTIVE     = 0;
    public static final int APN_REQUEST_STARTED    = 1;
    public static final int APN_TYPE_NOT_AVAILABLE = 2;
    public static final int APN_REQUEST_FAILED     = 3;
    public static final int APN_ALREADY_INACTIVE   = 4;

    /**
     * APN types for data connections.  These are usage categories for an APN
     * entry.  One APN entry may support multiple APN types, eg, a single APN
     * may service regular internet traffic ("default") as well as MMS-specific
     * connections.<br/>
     * APN_TYPE_ALL is a special type to indicate that this APN entry can
     * service all data connections.
     */
    public static final String APN_TYPE_ALL = "*";
    /** APN type for default data traffic */
    public static final String APN_TYPE_DEFAULT = "default";
    /** APN type for MMS traffic */
    public static final String APN_TYPE_MMS = "mms";
    /** APN type for SUPL assisted GPS */
    public static final String APN_TYPE_SUPL = "supl";
    /** APN type for DUN traffic */
    public static final String APN_TYPE_DUN = "dun";
    /** APN type for HiPri traffic */
    public static final String APN_TYPE_HIPRI = "hipri";
    /** APN type for FOTA */
    public static final String APN_TYPE_FOTA = "fota";
    /** APN type for IMS */
    public static final String APN_TYPE_IMS = "ims";
    /** APN type for CBS */
    public static final String APN_TYPE_CBS = "cbs";
    /** APN type for IA Initial Attach APN */
    public static final String APN_TYPE_IA = "ia";
    /** APN type for Emergency PDN. This is not an IA apn, but is used
     * for access to carrier services in an emergency call situation. */
    public static final String APN_TYPE_EMERGENCY = "emergency";

    /** M: APN type for device management */
    public static final String APN_TYPE_DM = "dm";
    /** M: APN type for WAP */
    public static final String APN_TYPE_WAP = "wap";
    /** M: APN type for NET */
    public static final String APN_TYPE_NET = "net";
    /** M: APN type for CMMAIL */
    public static final String APN_TYPE_CMMAIL = "cmmail";
    /** M: APN type for dedicate tethering apn */
    public static final String APN_TYPE_TETHERING = "tethering";
    /** M: APN type for RCSE */
    public static final String APN_TYPE_RCSE = "rcse";
    /** M: APN type for XCAP */
    public static final String APN_TYPE_XCAP = "xcap";
    /** M: APN type for RCS */
    public static final String APN_TYPE_RCS = "rcs";
    /** M: APN type for BIP */
    public static final String APN_TYPE_BIP = "bip";

    /* M: SS part */
    // CFU query type
    public static final String CFU_QUERY_TYPE_PROP = "persist.radio.cfu.querytype";
    public static final String CFU_QUERY_TYPE_DEF_VALUE = "0";
    /* M: SS part end */

    /**
     * used to query current capability switch setting value.
     * @internal
     */
    public static final String PROPERTY_CAPABILITY_SWITCH = "persist.radio.simswitch";

    public static final int RIL_CARD_MAX_APPS    = 8;

    public static final int DEFAULT_CARD_INDEX   = 0;

    public static final int MAX_PHONE_COUNT_SINGLE_SIM = 1;

    public static final int MAX_PHONE_COUNT_DUAL_SIM = 2;

    public static final int MAX_PHONE_COUNT_TRI_SIM = 3;

    public static final String PHONE_KEY = "phone";

    public static final String SLOT_KEY  = "slot";

    /** Fired when a subscriptions phone state changes. */
    public static final String ACTION_SUBSCRIPTION_PHONE_STATE_CHANGED =
        "android.intent.action.SUBSCRIPTION_PHONE_STATE";

    // FIXME: This is used to pass a subId via intents, we need to look at its usage, which is
    // FIXME: extensive, and see if this should be an array of all active subId's or ...?

    public static final String SUBSCRIPTION_KEY  = "subscription";

    public static final String SUB_SETTING  = "subSettings";

    public static final int SUB1 = 0;
    public static final int SUB2 = 1;
    public static final int SUB3 = 2;

    // TODO: Remove these constants and use an int instead.
    public static final int SIM_ID_1 = 0;
    public static final int SIM_ID_2 = 1;
    public static final int SIM_ID_3 = 2;
    public static final int SIM_ID_4 = 3;

    public static final int EVENT_SUBSCRIPTION_ACTIVATED   = 500;
    public static final int EVENT_SUBSCRIPTION_DEACTIVATED = 501;

    // ICC SIM Application Types
    // TODO: Replace the IccCardApplicationStatus.AppType enums with these constants
    public static final int APPTYPE_UNKNOWN = 0;
    public static final int APPTYPE_SIM = 1;
    public static final int APPTYPE_USIM = 2;
    public static final int APPTYPE_RUIM = 3;
    public static final int APPTYPE_CSIM = 4;
    public static final int APPTYPE_ISIM = 5;

    public enum CardUnavailableReason {
        REASON_CARD_REMOVED,
        REASON_RADIO_UNAVAILABLE,
        REASON_SIM_REFRESH_RESET
    };

    // Initial MTU value.
    public static final int UNSET_MTU = 0;

    //FIXME maybe this shouldn't be here - sprout only
    public static final int CAPABILITY_3G   = 1;

    /**
     * Values for the adb property "persist.radio.videocall.audio.output"
     */
    public static final int AUDIO_OUTPUT_ENABLE_SPEAKER = 0;
    public static final int AUDIO_OUTPUT_DISABLE_SPEAKER = 1;
    public static final int AUDIO_OUTPUT_DEFAULT = AUDIO_OUTPUT_ENABLE_SPEAKER;

    // authContext (parameter P2) when doing SIM challenge,
    // per 3GPP TS 31.102 (Section 7.1.2)
    public static final int AUTH_CONTEXT_EAP_SIM = 128;
    public static final int AUTH_CONTEXT_EAP_AKA = 129;
    public static final int AUTH_CONTEXT_UNDEFINED = -1;

    // Added by M begin
    // MVNO-API START
    public static final String MVNO_TYPE_NONE = "";
    public static final String MVNO_TYPE_SPN = "spn";
    public static final String MVNO_TYPE_IMSI = "imsi";
    public static final String MVNO_TYPE_PNN = "pnn";
    public static final String MVNO_TYPE_GID = "gid";
    // MVNO-API END
    // Added by M end

    //[ALPS01577029]-START
    // The TELEPHONY_MISC_FEATURE_CONFIG for tester to switch some features via engineering mode
    //Bit 1: To support auto switch rat mode to 2G only for 3M TDD csfb project when we are not in china
    public static final int MISC_FEATURE_CONFIG_MASK_AUTO_SWITCH_RAT = 0x01;
    //[ALPS01577029]-END

    //VOLTE IMS STATE
    public static final int IMS_STATE_DISABLED = 0;

    public static final int IMS_STATE_ENABLE = 1;

    public static final int IMS_STATE_ENABLING = 2;

    public static final int IMS_STATE_DISABLING = 3;

    /**
     * UT/XCAP Supplementary Service request domain selection constant definitions from IR.92 A.4
     * IMS Voice Service settings management when using CS access.
     * UT_CSFB_PS_PREFERRED is to indicate that sending SS request in the PS domain.
     * @internal
     */
    public static final int UT_CSFB_PS_PREFERRED = 0;
    /**
     * UT_CSFB_ONCE is to indicate that sending SS request in the CS domain once, and restore to
     * the PS domain next time.
     * @internal
     */
    public static final int UT_CSFB_ONCE = 1;
    /**
     * UT_CSFB_UNTIL_NEXT_BOOT is to indicate that sending SS request in the CS domain until the
     * UE performs a power-off/power-on or the UE detects a change of USIM/ISIM.
     # @internal
     */
    public static final int UT_CSFB_UNTIL_NEXT_BOOT = 2;
}
