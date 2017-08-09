package com.mediatek.telecom;

import android.os.Bundle;
import android.telecom.PhoneAccountHandle;

/**
 * @hide
 */
public class TelecomManagerEx {
    //-------------For VoLTE SS------------------
    /// M: CC: Error message due to VoLTE SS checking @{
    /**
     * Here defines a special disconnect reason to distinguish that the disconnected call is
     * a VoLTE SS request without data connection open. (Telephony -> Telecomm)
     * see android.telecom.DisconnectCause.mDisconnectReason
     * @hide
     */
    public static final String DISCONNECT_REASON_VOLTE_SS_DATA_OFF =
            "disconnect.reason.volte.ss.data.off";
    /// @}

    /// M: CC: phoneAccount special handling @{
    // broadcast phone account changes
    /**
     * The action used to broadcast phone account have changed.
     * @hide
     */
    public static final String ACTION_PHONE_ACCOUNT_CHANGED =
            "android.telecom.action.PHONE_ACCOUNT_CHANGED";
    /**
     * The action used to broadcast default phone account has changed.
     * @hide
     */
    public static final String ACTION_DEFAULT_ACCOUNT_CHANGED =
            "android.telecom.action.DEFAULT_ACCOUNT_CHANGED";

    // Suggested phone account
    /**
     * The extra used with an {@link android.content.Intent#ACTION_CALL} and
     * {@link android.content.Intent#ACTION_DIAL} {@code Intent} to specify a
     * {@link PhoneAccountHandle} which is suggested to use when making the call.
     * <p class="note">
     * Retrieve with {@link android.content.Intent#getParcelableExtra(String)}.
     * @hide
     */
    public static final String EXTRA_SUGGESTED_PHONE_ACCOUNT_HANDLE =
            "android.telecom.extra.SUGGESTED_PHONE_ACCOUNT_HANDLE";
    /// @}

    //-------------For VoLTE normal call switch to ECC------------------
    /**
     * Here defines a special key to distinguish the call is marked as Ecc by NW.
     * Its value is should be Boolean. see IConnectionServiceAdapter.updateExtras()
     * @hide
     */
    public static final String EXTRA_VOLTE_MARKED_AS_EMERGENCY = "com.mediatek.volte.isMergency";

    //-------------For VoLTE PAU field------------------
    /**
     * Here defines a special key to pass "pau" information of the call.
     * Its value should be String. see IConnectionServiceAdapter.updateExtras()
     * @hide
     */
    public static final String EXTRA_VOLTE_PAU_FIELD = "com.mediatek.volte.pau";

    //-------------For VoLTE Conference Call
    /**
     * Optional extra for {@link android.content.Intent#ACTION_CALL} and
     * {@link android.content.Intent#ACTION_CALL_PRIVILEGED} containing a phone
     * number {@link ArrayList} that used to launch the volte conference call.
     * The phone number in the list may be normal phone number, sip phone
     * address or IMS call phone number. This extra takes effect only when the
     * {@link #EXTRA_VOLTE_CONF_CALL_DIAL} is true.
     * @hide
     */
    public static final String EXTRA_VOLTE_CONF_CALL_NUMBERS =
            "com.mediatek.volte.ConfCallNumbers";

    /**
     * Optional extra for {@link android.content.Intent#ACTION_CALL} and
     * {@link android.content.Intent#ACTION_CALL_PRIVILEGED} containing an
     * boolean value that determines if it should launch a volte conference
     * call.
     * @hide
     */
    public static final String EXTRA_VOLTE_CONF_CALL_DIAL = "com.mediatek.volte.ConfCallDial";

    /**
     * extra Boolean-key info in {@link android.telecom.TelecomManager#EXTRA_INCOMING_CALL_EXTRAS}
     * to indicate the incoming call is VoLTE conference invite.
     * @hide
     */
    public static final String EXTRA_VOLTE_CONF_CALL_INCOMING = "com.mediatek.volte.conference.invite";

    /**
     * [STK notify]STK wants to know the InCallScreen state.
     * broadcast the InCallActivity state change.
     * @hide
     */
    public static final String ACTION_INCALL_SCREEN_STATE_CHANGED =
            "com.mediatek.telecom.action.INCALL_SCREEN_STATE_CHANGED";

    /**
     * [STK notify]STK wants to know the InCallScreen show/disappear state.
     * broadcast the InCallActivity show or not.
     * this is a boolean extra.
     * if the Activity shows, the value will be true.
     * if the Activity disappears, the value will be false.
     * @hide
     */
    public static final String EXTRA_INCALL_SCREEN_SHOW =
            "com.mediatek.telecom.extra.INCALL_SCREEN_SHOW";

    /**
     * event string for remote side hold the call
     * it is used to inform Telecom that remote side hold the call.
     * Sent to Telecom via {@link #sendConnectionEvent(String)}
     * @hide
     */
    public static final String EVENT_ON_REMOTE_HOLD =
            "com.mediatek.telecom.event.ON_REMOTE_HOLD";

    /**
     * event string for remote side resume the hold call
     * it is used to inform Telecom that remote side resume the hold call.
     * Sent to Telecom via {@link #sendConnectionEvent(String)}
     * @hide
     */
    public static final String EVENT_ON_REMOTE_RESUME =
            "com.mediatek.telecom.event.ON_REMOTE_RESUME";

    /**
     * event string to notify Telecom that connection lost
     * Sent to Telecom via {@link #sendConnectionEvent(String)}
     * @hide
     */
    public static final String EVENT_CONNECTION_LOST =
            "com.mediatek.telecom.event.CONNECTION_LOST";

    /**
     * event string to notify Telecom user operation failed.
     * Sent to Telecom via {@link #sendConnectionEvent(String)}
     * @hide
     */
    public static final String EVENT_OPERATION_FAIL =
            "com.mediatek.telecom.event.OPERATION_FAIL";

    /**
     * event string to send SS notification to Telecom.
     * Sent to Telecom via {@link #sendConnectionEvent(String)}
     * @hide
     */
    public static final String EVENT_SS_NOTIFICATION =
            "com.mediatek.telecom.event.SS_NOTIFICATION";

    /**
     * event string to notify Telecom that number updated.
     * Sent to Telecom via {@link #sendConnectionEvent(String)}
     * @hide
     */
    public static final String EVENT_NUMBER_UPDATED =
            "com.mediatek.telecom.event.NUMBER_UPDATED";

    /**
     * event string to notify Telecom that incoming info updated.
     * Sent to Telecom via {@link #sendConnectionEvent(String)}
     * @hide
     */
    public static final String EVENT_INCOMING_INFO_UPDATED =
            "com.mediatek.telecom.event.INCOMING_INFO_UPDATED";

    /**
     * event string to notify Telecom that CDMA call accepted.
     * Sent to Telecom via {@link #sendConnectionEvent(String)}
     * @hide
     */
    public static final String EVENT_CDMA_CALL_ACCEPTED =
            "com.mediatek.telecom.event.CDMA_CALL_ACCEPTED";

    /**
     * event string to notify Telecom that phone account changed.
     * Sent to Telecom via {@link #sendConnectionEvent(String)}
     * @hide
     */
    public static final String EVENT_PHONE_ACCOUNT_CHANGED =
            "com.mediatek.telecom.event.PHONE_ACCOUNT_CHANGED";

    /**
     * event string to notify Telecom that VT status updated.
     * Sent to Telecom via {@link #sendConnectionEvent(String)}
     * @hide
     */
    public static final String EVENT_VT_STATUS_UPDATED =
            "com.mediatek.telecom.event.VT_STATUS_UPDATED";

    /**
     * event string to update volte call extra to Telecom
     * Sent to Telecom via {@link #sendConnectionEvent(String)}
     * @hide
     */
    public static final String EVENT_UPDATE_VOLTE_EXTRA =
            "com.mediatek.telecom.event.UPDATE_VOLTE_EXTRA";

    /**
     * User operation from telecom to disconnect call
     * @hide
     */
    public static final String OPERATION_DISCONNECT_CALL = "disconnect";

    /**
     * User operation from telecom to hold call
     * @hide
     */
    public static final String OPERATION_HOLD_CALL = "hold";

    /**
     * User operation from telecom to unhold call
     * @hide
     */
    public static final String OPERATION_UNHOLD_CALL = "unhold";

    /**
     * User operation from telecom to answer call
     * @hide
     */
    public static final String OPERATION_ANSWER_CALL = "answer";

    /**
     * User operation from telecom to reject call
     * @hide
     */
    public static final String OPERATION_REJECT_CALL = "reject";

    /**
     * User operation from telecom to dial a outgoing call
     * @hide
     */
    public static final String OPERATION_OUTGOING = "outgoing";

    /**
     * @hide
     */
    public static final String KEY_OF_FAILED_OPERATION = "FAILED_OPERATION";
    /**
     * @hide
     */
    public static final String KEY_OF_SS_NOTIFICATION_NOTITYPE = "SS_NOTIFICATION_NOTITYPE";
    /**
     * @hide
     */
    public static final String KEY_OF_SS_NOTIFICATION_TYPE = "SS_NOTIFICATION_TYPE";
    /**
     * @hide
     */
    public static final String KEY_OF_SS_NOTIFICATION_CODE = "SS_NOTIFICATION_CODE";
    /**
     * @hide
     */
    public static final String KEY_OF_SS_NOTIFICATION_NUMBER = "SS_NOTIFICATION_NUMBER";
    /**
     * @hide
     */
    public static final String KEY_OF_SS_NOTIFICATION_INDEX = "SS_NOTIFICATION_INDEX";
    /**
     * @hide
     */
    public static final String KEY_OF_UPDATED_NUMBER = "UPDATED_NUMBER";
    /**
     * @hide
     */
    public static final String KEY_OF_UPDATED_INCOMING_INFO_TYPE = "UPDATED_INCOMING_INFO_TYPE";
    /**
     * @hide
     */
    public static final String KEY_OF_UPDATED_INCOMING_INFO_ALPHAID =
            "UPDATED_INCOMING_INFO_ALPHAID";
    /**
     * @hide
     */
    public static final String KEY_OF_UPDATED_INCOMING_INFO_CLI_VALIDITY =
            "INCOMING_INFO_CLI_VALIDITY";
    /**
     * @hide
     */
    public static final String KEY_OF_CHANGED_PHONE_ACCOUNT = "CHANGED_PHONE_ACCOUNT";
    /**
     * @hide
     */
    public static final String KEY_OF_UPDATED_VT_STATUS = "UPDATED_VT_STATUS";

    /**
     * @hide
     */
    public static Bundle createOperationFailBundle(int operation) {
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_OF_FAILED_OPERATION, operation);
        return bundle;
    }

    /**
     * @hide
     */
    public static Bundle createSsNotificationBundle(
            int notiType, int type, int code, String number, int index) {
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_OF_SS_NOTIFICATION_NOTITYPE, notiType);
        bundle.putInt(KEY_OF_SS_NOTIFICATION_TYPE, type);
        bundle.putInt(KEY_OF_SS_NOTIFICATION_CODE, code);
        bundle.putString(KEY_OF_SS_NOTIFICATION_NUMBER, number);
        bundle.putInt(KEY_OF_SS_NOTIFICATION_INDEX, index);
        return bundle;
    }

    /**
     * @hide
     */
    public static Bundle createNumberUpdatedBundle(String number) {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_OF_UPDATED_NUMBER, number);
        return bundle;
    }

    /**
     * @hide
     */
    public static Bundle createIncomingInfoUpdatedBundle(
            int type, String alphaid, int cliValidity) {
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_OF_UPDATED_INCOMING_INFO_TYPE, type);
        bundle.putString(KEY_OF_UPDATED_INCOMING_INFO_ALPHAID, alphaid);
        bundle.putInt(KEY_OF_UPDATED_INCOMING_INFO_CLI_VALIDITY, cliValidity);
        return bundle;
    }

    /**
     * @hide
     */
    public static Bundle createPhoneAccountChangedBundle(PhoneAccountHandle handle) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_OF_CHANGED_PHONE_ACCOUNT, handle);
        return bundle;
    }

    /**
     * @hide
     */
    public static Bundle createVtStatudUpdatedBundle(int status) {
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_OF_UPDATED_VT_STATUS, status);
        return bundle;
    }
}
