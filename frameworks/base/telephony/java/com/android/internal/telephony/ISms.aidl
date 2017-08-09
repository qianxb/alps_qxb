/*
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.android.internal.telephony;

import android.app.PendingIntent;
import android.net.Uri;
import com.android.internal.telephony.SmsRawData;
// MTK-START
import com.mediatek.internal.telephony.IccSmsStorageStatus;
import android.telephony.SimSmsInsertStatus;
import android.os.Bundle;
import android.telephony.SmsParameters;
import com.mediatek.internal.telephony.SmsCbConfigInfo;
// MTK-END

/** Interface for applications to access the ICC phone book.
 *
 * <p>The following code snippet demonstrates a static method to
 * retrieve the ISms interface from Android:</p>
 * <pre>private static ISms getSmsInterface()
            throws DeadObjectException {
    IServiceManager sm = ServiceManagerNative.getDefault();
    ISms ss;
    ss = ISms.Stub.asInterface(sm.getService("isms"));
    return ss;
}
 * </pre>
 */

interface ISms {
    /**
     * Retrieves all messages currently stored on ICC.
     * @param subId the subId id.
     * @return list of SmsRawData of all sms on ICC
     */
    List<SmsRawData> getAllMessagesFromIccEfForSubscriber(in int subId, String callingPkg);

    /**
     * Update the specified message on the ICC.
     *
     * @param messageIndex record index of message to update
     * @param newStatus new message status (STATUS_ON_ICC_READ,
     *                  STATUS_ON_ICC_UNREAD, STATUS_ON_ICC_SENT,
     *                  STATUS_ON_ICC_UNSENT, STATUS_ON_ICC_FREE)
     * @param pdu the raw PDU to store
     * @param subId the subId id.
     * @return success or not
     *
     */
     boolean updateMessageOnIccEfForSubscriber(in int subId, String callingPkg,
             int messageIndex, int newStatus, in byte[] pdu);

    /**
     * Copy a raw SMS PDU to the ICC.
     *
     * @param pdu the raw PDU to store
     * @param status message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *               STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @param subId the subId id.
     * @return success or not
     *
     */
    boolean copyMessageToIccEfForSubscriber(in int subId, String callingPkg, int status,
            in byte[] pdu, in byte[] smsc);

    /**
     * Send a data SMS.
     *
     * @param smsc the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param data the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applicaitons,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     * @param subId the subId id.
     */
    void sendDataForSubscriber(int subId, String callingPkg, in String destAddr,
            in String scAddr, in int destPort, in byte[] data, in PendingIntent sentIntent,
            in PendingIntent deliveryIntent);

    /**
     * Send a data SMS. Only for use internally.
     *
     * @param smsc the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param data the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applicaitons,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     * @param subId the subId id.
     */
    void sendDataForSubscriberWithSelfPermissions(int subId, String callingPkg, in String destAddr,
            in String scAddr, in int destPort, in byte[] data, in PendingIntent sentIntent,
            in PendingIntent deliveryIntent);

    /**
     * Send an SMS.
     *
     * @param smsc the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param text the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     * @param subId the subId on which the SMS has to be sent.
     * @param persistMessageForNonDefaultSmsApp whether the sent message should
     *   be automatically persisted in the SMS db. It only affects messages sent
     *   by a non-default SMS app. Currently only the carrier app can set this
     *   parameter to false to skip auto message persistence.
     */
    void sendTextForSubscriber(in int subId, String callingPkg, in String destAddr,
            in String scAddr, in String text, in PendingIntent sentIntent,
            in PendingIntent deliveryIntent, in boolean persistMessageForNonDefaultSmsApp);

    /**
     * Send an SMS. Internal use only.
     *
     * @param smsc the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param text the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     * @param subId the subId on which the SMS has to be sent.
     */
    void sendTextForSubscriberWithSelfPermissions(in int subId, String callingPkg,
            in String destAddr, in String scAddr, in String text, in PendingIntent sentIntent,
            in PendingIntent deliveryIntent);

    /**
     * Inject an SMS PDU into the android platform.
     *
     * @param subId the subId on which the SMS has to be injected.
     * @param pdu is the byte array of pdu to be injected into android application framework
     * @param format is the format of SMS pdu (android.telephony.SmsMessage.FORMAT_3GPP or
     * android.telephony.SmsMessage.FORMAT_3GPP2)
     * @param receivedIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully received by the
     *  android application framework. This intent is broadcasted at
     *  the same time an SMS received from radio is acknowledged back.
     */
    void injectSmsPduForSubscriber(
            int subId, in byte[] pdu, String format, in PendingIntent receivedIntent);

    /**
     * Send a multi-part text based SMS.
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK<code> for success,
     *   or one of these errors:
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code>
     *   <code>RESULT_ERROR_RADIO_OFF</code>
     *   <code>RESULT_ERROR_NULL_PDU</code>.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     * @param subId the subId on which the SMS has to be sent.
     * @param persistMessageForNonDefaultSmsApp whether the sent message should
     *   be automatically persisted in the SMS db. It only affects messages sent
     *   by a non-default SMS app. Currently only the carrier app can set this
     *   parameter to false to skip auto message persistence.
     */
    void sendMultipartTextForSubscriber(in int subId, String callingPkg,
            in String destinationAddress, in String scAddress,
            in List<String> parts, in List<PendingIntent> sentIntents,
            in List<PendingIntent> deliveryIntents, in boolean persistMessageForNonDefaultSmsApp);

    /**
     * Enable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier and RAN type. The RAN type specify this message ID
     * belong to 3GPP (GSM) or 3GPP2(CDMA). Note that if two different clients
     * enable the same message identifier, they must both disable it for the
     * device to stop receiving those messages.
     *
     * @param messageIdentifier Message identifier as specified in TS 23.041 (3GPP) or
     *   C.R1001-G (3GPP2)
     * @param subId for which the broadcast has to be enabled
     * @param ranType as defined in class SmsManager, the value can be one of these:
     *    android.telephony.SmsMessage.CELL_BROADCAST_RAN_TYPE_GSM
     *    android.telephony.SmsMessage.CELL_BROADCAST_RAN_TYPE_CDMA
     * @return true if successful, false otherwise
     *
     * @see #disableCellBroadcastForSubscriber(int, int, int)
     */
    boolean enableCellBroadcastForSubscriber(int subId, int messageIdentifier, int ranType);

    /**
     * Disable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier and RAN type. The RAN type specify this message ID
     * belong to 3GPP (GSM) or 3GPP2(CDMA). Note that if two different clients
     * enable the same message identifier, they must both disable it for the
     * device to stop receiving those messages.
     *
     * @param messageIdentifier Message identifier as specified in TS 23.041 (3GPP) or
     *   C.R1001-G (3GPP2)
     * @param subId for which the broadcast has to be disabled
     * @param ranType as defined in class SmsManager, the value can be one of these:
     *    android.telephony.SmsMessage.CELL_BROADCAST_RAN_TYPE_GSM
     *    android.telephony.SmsMessage.CELL_BROADCAST_RAN_TYPE_CDMA
     * @return true if successful, false otherwise
     *
     * @see #enableCellBroadcastForSubscriber(int, int, int)
     */
    boolean disableCellBroadcastForSubscriber(int subId, int messageIdentifier, int ranType);

    /*
     * Enable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier range and RAN type. The RAN type specify this message ID range
     * belong to 3GPP (GSM) or 3GPP2(CDMA). Note that if two different clients enable
     * a message identifier range, they must both disable it for the device
     * to stop receiving those messages.
     *
     * @param startMessageId first message identifier as specified in TS 23.041 (3GPP) or
     *   C.R1001-G (3GPP2)
     * @param endMessageId last message identifier as specified in TS 23.041 (3GPP) or
     *   C.R1001-G (3GPP2)
     * @param subId for which the broadcast has to be enabled
     * @param ranType as defined in class SmsManager, the value can be one of these:
     *    android.telephony.SmsMessage.CELL_BROADCAST_RAN_TYPE_GSM
     *    android.telephony.SmsMessage.CELL_BROADCAST_RAN_TYPE_CDMA
     * @return true if successful, false otherwise
     *
     * @see #disableCellBroadcastRangeForSubscriber(int, int, int, int)
     */
    boolean enableCellBroadcastRangeForSubscriber(int subId, int startMessageId, int endMessageId,
            int ranType);

    /**
     * Disable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier range and RAN type. The RAN type specify this message ID range
     * belong to 3GPP (GSM) or 3GPP2(CDMA). Note that if two different clients enable
     * a message identifier range, they must both disable it for the device
     * to stop receiving those messages.
     *
     * @param startMessageId first message identifier as specified in TS 23.041 (3GPP) or
     *   C.R1001-G (3GPP2)
     * @param endMessageId last message identifier as specified in TS 23.041 (3GPP) or
     *   C.R1001-G (3GPP2)
     * @param subId for which the broadcast has to be disabled
     * @param ranType as defined in class SmsManager, the value can be one of these:
     *    android.telephony.SmsMessage.CELL_BROADCAST_RAN_TYPE_GSM
     *    android.telephony.SmsMessage.CELL_BROADCAST_RAN_TYPE_CDMA
     * @return true if successful, false otherwise
     *
     * @see #enableCellBroadcastRangeForSubscriber(int, int, int, int)
     */
    boolean disableCellBroadcastRangeForSubscriber(int subId, int startMessageId,
            int endMessageId, int ranType);

    /**
     * Returns the premium SMS send permission for the specified package.
     * Requires system permission.
     */
    int getPremiumSmsPermission(String packageName);

    /**
     * Returns the premium SMS send permission for the specified package.
     * Requires system permission.
     */
    int getPremiumSmsPermissionForSubscriber(int subId, String packageName);

    /**
     * Set the SMS send permission for the specified package.
     * Requires system permission.
     */
    void setPremiumSmsPermission(String packageName, int permission);

     /**
     * Set the SMS send permission for the specified package.
     * Requires system permission.
     */
    void setPremiumSmsPermissionForSubscriber(int subId, String packageName, int permission);

    /**
     * SMS over IMS is supported if IMS is registered and SMS is supported
     * on IMS.
     * @param subId for subId which isImsSmsSupported is queried
     * @return true if SMS over IMS is supported, false otherwise
     *
     * @see #getImsSmsFormatForSubscriber(int)
     */
    boolean isImsSmsSupportedForSubscriber(int subId);

    /**
     * User needs to pick SIM for SMS if multiple SIMs present and if current subId passed in is not
     * active/valid.
     * @param subId current subId for sending SMS
     * @return true if SIM for SMS sending needs to be chosen
     */
    boolean isSmsSimPickActivityNeeded(int subId);

    /*
     * get user prefered SMS subId
     * @return subId id
     */
    int getPreferredSmsSubscription();

    /**
     * Gets SMS format supported on IMS.  SMS over IMS format is
     * either 3GPP or 3GPP2.
     * @param subId for subId which getImsSmsFormat is queried
     * @return android.telephony.SmsMessage.FORMAT_3GPP,
     *         android.telephony.SmsMessage.FORMAT_3GPP2
     *      or android.telephony.SmsMessage.FORMAT_UNKNOWN
     *
     * @see #isImsSmsSupportedForSubscriber(int)
     */
    String getImsSmsFormatForSubscriber(int subId);

    /*
     * Get SMS prompt property,  enabled or not
     * @return true if enabled, false otherwise
     */
    boolean isSMSPromptEnabled();

    /**
     * Send a system stored text message.
     *
     * This is used for sending a previously sent, but failed-to-send, message or
     * for sending a text message that has been stored as a draft.
     *
     * @param subId the SIM id.
     * @param callingPkg the package name of the calling app
     * @param messageUri the URI of the stored message
     * @param scAddress is the service center address or null to use the current default SMSC
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK</code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     */
    void sendStoredText(int subId, String callingPkg, in Uri messageUri, String scAddress,
            in PendingIntent sentIntent, in PendingIntent deliveryIntent);

    /**
     * Send a system stored multi-part text message.
     *
     * This is used for sending a previously sent, but failed-to-send, message or
     * for sending a text message that has been stored as a draft.
     * The provided <code>PendingIntent</code> lists should match the part number of the
     * divided text of the stored message by using <code>divideMessage</code>
     *
     * @param subId the SIM id.
     * @param callingPkg the package name of the calling app
     * @param messageUri the URI of the stored message
     * @param scAddress is the service center address or null to use
     *   the current default SMSC
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK</code> for success,
     *   or one of these errors:<br>
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *   <code>RESULT_ERROR_RADIO_OFF</code><br>
     *   <code>RESULT_ERROR_NULL_PDU</code><br>
     *   For <code>RESULT_ERROR_GENERIC_FAILURE</code> each sentIntent may include
     *   the extra "errorCode" containing a radio technology specific value,
     *   generally only useful for troubleshooting.<br>
     *   The per-application based SMS control checks sentIntent. If sentIntent
     *   is NULL the caller will be checked against all unknown applications,
     *   which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     */
    void sendStoredMultipartText(int subId, String callingPkg, in Uri messageUri,
                String scAddress, in List<PendingIntent> sentIntents,
                in List<PendingIntent> deliveryIntents);

    // MTK-START
    /**
     * Retrieves all messages currently stored on ICC based on different mode.
     * Ex. CDMA mode or GSM mode for international cards.
     *
     * @param subId subscription identity
     * @param mode the GSM mode or CDMA mode
     *
     * @return list of SmsRawData of all sms on ICC
     */
    List<SmsRawData> getAllMessagesFromIccEfByModeForSubscriber(in int subId, String callingPkg,
            int mode);

    /**
     * Copy a text SMS to the ICC.
     *
     * @param subId subscription identity
     * @param scAddress Service center address
     * @param address   Destination address or original address
     * @param text      List of message text
     * @param status    message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *                  STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @param timestamp Timestamp when service center receive the message
     * @return success or not
     *
     */
    int copyTextMessageToIccCardForSubscriber(in int subId, String callingPkg,
                    in String scAddress, in String address, in List<String> text,
                    in int status, in long timestamp);

    /**
     * Send a data message with original port
     *
     * @param subId the subscription identity
     * @param destAddr destination address
     * @param scAddr the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param destPort destination port
     * @param originalPort original port
     * @param data the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applicaitons,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     */
    void sendDataWithOriginalPortForSubscriber(in int subId, String callingPkg,
            in String destAddr, in String scAddr, in int destPort,
            in int originalPort, in byte[] data, in PendingIntent sentIntent,
            in PendingIntent deliveryIntent);

    /**
     * Judge if SMS subsystem is ready or not.
     *
     * @param subId the subscription identity
     *
     * @return true for success
     */
    boolean isSmsReadyForSubscriber(in int subId);

    /**
     * Set the memory storage status of the SMS
     * This function is used for FTA test only
     *
     * @param subId the subscription identity
     * @param status false for storage full, true for storage available
     *
     */
    void setSmsMemoryStatusForSubscriber(in int subId, boolean status);

    /**
     * Get SMS SIM Card memory's total and used number
     *
     * @param subId the subscription identity
     *
     * @return <code>IccSmsStorageStatus</code> object
     *
     */
    IccSmsStorageStatus getSmsSimMemoryStatusForSubscriber(in int subId, String callingPkg);

    /**
     * Send an SMS with specified encoding type.
     *
     * @param subId subscriptioni identity
     * @param destAddr the address to send the message to
     * @param scAddr the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param text the body of the message to send
     * @param encodingType the encoding type of content of message(GSM 7-bit, Unicode or Automatic)
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     * @param persistMessageForNonDefaultSmsApp whether the sent message should
     *   be automatically persisted in the SMS db. It only affects messages sent
     *   by a non-default SMS app. Currently only the carrier app can set this
     *   parameter to false to skip auto message persistence.
     */
    void sendTextWithEncodingTypeForSubscriber(in int subId, String callingPkg,
            in String destAddr, in String scAddr, in String text, in int encodingType,
            in PendingIntent sentIntent, in PendingIntent deliveryIntent,
            in boolean persistMessageForNonDefaultSmsApp);

    /**
     * Send a multi-part text based SMS with specified encoding type.
     *
     * @param subId subscriptioni identity
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param encodingType the encoding type of content of message(GSM 7-bit, Unicode or Automatic)
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK<code> for success,
     *   or one of these errors:
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code>
     *   <code>RESULT_ERROR_RADIO_OFF</code>
     *   <code>RESULT_ERROR_NULL_PDU</code>.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     * @param persistMessageForNonDefaultSmsApp whether the sent message should
     *   be automatically persisted in the SMS db. It only affects messages sent
     *   by a non-default SMS app. Currently only the carrier app can set this
     *   parameter to false to skip auto message persistence.
     */
    void sendMultipartTextWithEncodingTypeForSubscriber(in int subId, String callingPkg,
            in String destAddr, in String scAddr, in List<String> parts, in int encodingType,
            in List<PendingIntent> sentIntents, in List<PendingIntent> deliveryIntents,
            in boolean persistMessageForNonDefaultSmsApp);

    /**
     * Copy a text SMS to the ICC.
     *
     * @param subId subscriptioni identity
     * @param scAddress Service center address
     * @param address   Destination address or original address
     * @param text      List of message text
     * @param status    message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *                  STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @param timestamp Timestamp when service center receive the message
     * @return SimSmsInsertStatus
     *
     */
    SimSmsInsertStatus insertTextMessageToIccCardForSubscriber(in int subId,
            String callingPkg, in String scAddress, in String address,
            in List<String> text, in int status, in long timestamp);

    /**
     * Copy a raw SMS PDU to the ICC.
     *
     * @param subId subscriptioni identity
     * @param status message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *               STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @param pdu the raw PDU to store
     * @param smsc encoded smsc service center
     * @return SimSmsInsertStatus
     *
     */
    SimSmsInsertStatus insertRawMessageToIccCardForSubscriber(in int subId,
    		String callingPkg, int status, in byte[] pdu, in byte[] smsc);

    /**
     * Send an SMS with specified encoding type.
     *
     * @param subId subscriptioni identity
     * @param destAddr the address to send the message to
     * @param scAddr the SMSC to send the message through, or NULL for the
     *  default SMSC
     * @param text the body of the message to send
     * @param extraParams extra parameters, such as validity period, encoding type
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     * @param persistMessageForNonDefaultSmsApp whether the sent message should
     *   be automatically persisted in the SMS db. It only affects messages sent
     *   by a non-default SMS app. Currently only the carrier app can set this
     *   parameter to false to skip auto message persistence.
     */
    void sendTextWithExtraParamsForSubscriber(in int subId, String callingPkg,
            in String destAddr, in String scAddr, in String text,
            in Bundle extraParams, in PendingIntent sentIntent,
            in PendingIntent deliveryIntent, in boolean persistMessageForNonDefaultSmsApp);

    /**
     * Send a multi-part text based SMS with specified encoding type.
     *
     * @param subId subscriptioni identity
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param extraParams extra parameters, such as validity period, encoding type
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     * @param persistMessageForNonDefaultSmsApp whether the sent message should
     *   be automatically persisted in the SMS db. It only affects messages sent
     *   by a non-default SMS app. Currently only the carrier app can set this
     *   parameter to false to skip auto message persistence.
     */
    void sendMultipartTextWithExtraParamsForSubscriber(in int subId, String callingPkg,
            in String destAddr, in String scAddr, in List<String> parts,
            in Bundle extraParams, in List<PendingIntent> sentIntents,
            in List<PendingIntent> deliveryIntents,
            in boolean persistMessageForNonDefaultSmsApp);

    /*
    * Get sms parameters from EFsmsp, such as the validity period & its format,
    * protocol identifier and decode char set value
    *
    * @param subId subscriptioni identity
    */
    SmsParameters getSmsParametersForSubscriber(in int subId, String callingPkg);

    /*
     * Save sms parameters into EFsmsp
     *
     * @param subId subscriptioni identity
     */
    boolean setSmsParametersForSubscriber(in int subId, String callingPkg,
    		in SmsParameters params);

    /**
     * Retrieves message currently stored on ICC by index.
     *
     * @param subId subscriptioni identity
     * @param index the index of sms save in EFsms
     *
     * @return SmsRawData of sms on ICC
     */
    SmsRawData getMessageFromIccEfForSubscriber(in int subId, String callingPkg, in int index);

    /**
     * Get the cell broadcast config.
     *
     * @param subId subscriptioni identity
     *
     * @return Cell broadcast config.
     */
    SmsCbConfigInfo[] getCellBroadcastSmsConfigForSubscriber(in int subId);

    /**
     * Set the cell broadcast config.
     *
     * @param subId subscriptioni identity
     * @param channels the channels setting
     * @param languages the language setting
     *
     * @return true if set successfully; false if set failed
     */
    boolean setCellBroadcastSmsConfigForSubscriber(in int subId,
    		in SmsCbConfigInfo[] channels, in SmsCbConfigInfo[] languages);

    /**
     * Query the activation status of cell broadcast.
     *
     * @param subId subscriptioni identity
     *
     * @return true if activate; false if inactivate.
     */
    boolean queryCellBroadcastSmsActivationForSubscriber(in int subId);

    /**
     * Activate or deactivate cell broadcast SMS.
     *
     * @param subId subscriptioni identity
     * @param activate 0 = activate, 1 = deactivate
     *
     * @return true if activate successfully; false if activate failed
     */
    boolean activateCellBroadcastSmsForSubscriber(in int subId, in boolean activate);

    /**
     * Remove specified channel and serial of cb message.
     *
     * @param channelId removed channel id
     * @param serialId removed serial id
     *
     * @return true process successfully; false process failed.
     *
     */
    boolean removeCellBroadcastMsgForSubscriber(in int subId, in int channelId,
    		in int serialId);

    /**
     * Set the ETWS config to modem
     *
     * @param subId subscriptioni identity
     * @param mode the etws mode
     *
     * @return true if set successfully; false if set failed
     */
    boolean setEtwsConfigForSubscriber(in int subId, in int mode);
    // MTK-END
}
