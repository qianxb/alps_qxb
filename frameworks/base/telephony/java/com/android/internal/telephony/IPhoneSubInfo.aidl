/*
 * Copyright (C) 2007 The Android Open Source Project
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

// MTK-START
import android.os.Message;
// MTK-END

/**
 * Interface used to retrieve various phone-related subscriber information.
 *
 */
interface IPhoneSubInfo {

    /**
     * Retrieves the unique device ID, e.g., IMEI for GSM phones.
     */
    String getDeviceId(String callingPackage);

     /**
     * Retrieves the unique Network Access ID
     */
    String getNaiForSubscriber(int subId, String callingPackage);

    /**
     * Retrieves the unique device ID of a phone for the device, e.g., IMEI
     * for GSM phones.
     */
    String getDeviceIdForPhone(int phoneId, String callingPackage);

    /**
     * Retrieves the IMEI.
     */
    String getImeiForSubscriber(int subId, String callingPackage);

    /**
     * Retrieves the software version number for the device, e.g., IMEI/SV
     * for GSM phones.
     */
    String getDeviceSvn(String callingPackage);

    /**
     * Retrieves the software version number of a subId for the device, e.g., IMEI/SV
     * for GSM phones.
     */
    String getDeviceSvnUsingSubId(int subId, String callingPackage);

    /**
     * Retrieves the unique sbuscriber ID, e.g., IMSI for GSM phones.
     */
    String getSubscriberId(String callingPackage);

    /**
     * Retrieves the unique subscriber ID of a given subId, e.g., IMSI for GSM phones.
     */
    String getSubscriberIdForSubscriber(int subId, String callingPackage);

    /**
     * Retrieves the Group Identifier Level1 for GSM phones.
     */
    String getGroupIdLevel1(String callingPackage);

    /**
     * Retrieves the Group Identifier Level1 for GSM phones of a subId.
     */
    String getGroupIdLevel1ForSubscriber(int subId, String callingPackage);

    /**
     * Retrieves the serial number of the ICC, if applicable.
     */
    String getIccSerialNumber(String callingPackage);

    /**
     * Retrieves the serial number of a given subId.
     */
    String getIccSerialNumberForSubscriber(int subId, String callingPackage);

    /**
     * Retrieves the phone number string for line 1.
     */
    String getLine1Number(String callingPackage);

    /**
     * Retrieves the phone number string for line 1 of a subcription.
     */
    String getLine1NumberForSubscriber(int subId, String callingPackage);


    /**
     * Retrieves the alpha identifier for line 1.
     */
    String getLine1AlphaTag(String callingPackage);

    /**
     * Retrieves the alpha identifier for line 1 of a subId.
     */
    String getLine1AlphaTagForSubscriber(int subId, String callingPackage);


    /**
     * Retrieves MSISDN Number.
     */
    String getMsisdn(String callingPackage);

    /**
     * Retrieves the Msisdn of a subId.
     */
    String getMsisdnForSubscriber(int subId, String callingPackage);

    /**
     * Retrieves the voice mail number.
     */
    String getVoiceMailNumber(String callingPackage);

    /**
     * Retrieves the voice mail number of a given subId.
     */
    String getVoiceMailNumberForSubscriber(int subId, String callingPackage);

    /**
     * Retrieves the complete voice mail number.
     */
    String getCompleteVoiceMailNumber();

    /**
     * Retrieves the complete voice mail number for particular subId
     */
    String getCompleteVoiceMailNumberForSubscriber(int subId);

    /**
     * Retrieves the alpha identifier associated with the voice mail number.
     */
    String getVoiceMailAlphaTag(String callingPackage);

    /**
     * Retrieves the alpha identifier associated with the voice mail number
     * of a subId.
     */
    String getVoiceMailAlphaTagForSubscriber(int subId, String callingPackage);

    /**
     * Returns the IMS private user identity (IMPI) that was loaded from the ISIM.
     * @return the IMPI, or null if not present or not loaded
     */
    String getIsimImpi();

    /**
     * Returns the IMS home network domain name that was loaded from the ISIM.
     * @return the IMS domain name, or null if not present or not loaded
     */
    String getIsimDomain();

    /**
     * Returns the IMS public user identities (IMPU) that were loaded from the ISIM.
     * @return an array of IMPU strings, with one IMPU per string, or null if
     *      not present or not loaded
     */
    String[] getIsimImpu();

    /**
     * Returns the IMS Service Table (IST) that was loaded from the ISIM.
     * @return IMS Service Table or null if not present or not loaded
     */
    String getIsimIst();

    /**
     * Returns the IMS Proxy Call Session Control Function(PCSCF) that were loaded from the ISIM.
     * @return an array of PCSCF strings with one PCSCF per string, or null if
     *      not present or not loaded
     */
    String[] getIsimPcscf();

    /**
     * TODO: Deprecate and remove this interface. Superceded by getIccsimChallengeResponse.
     * Returns the response of ISIM Authetification through RIL.
     * @return the response of ISIM Authetification, or null if
     *     the Authentification hasn't been successed or isn't present iphonesubinfo.
     */
    String getIsimChallengeResponse(String nonce);

    /**
     * Returns the response of the SIM application on the UICC to authentication
     * challenge/response algorithm. The data string and challenge response are
     * Base64 encoded Strings.
     * Can support EAP-SIM, EAP-AKA with results encoded per 3GPP TS 31.102.
     *
     * @param subId subscription ID to be queried
     * @param appType ICC application type (@see com.android.internal.telephony.PhoneConstants#APPTYPE_xxx)
     * @param authType Authentication type, see PhoneConstants#AUTHTYPE_xxx
     * @param data authentication challenge data
     * @return challenge response
     */
    String getIccSimChallengeResponse(int subId, int appType, int authType, String data);

     // MTK-START
    /**
     * Returns the IMS private user identity (IMPI) that was loaded from the ISIM for particular subId.
     * @param subId subscription ID to be queried
     * @return the IMPI, or null if not present or not loaded
     */
    String getIsimImpiForSubscriber(int subId);

    /**
     * Returns the IMS home network domain name that was loaded from the ISIM for particular subId.
     * @param subId subscription ID to be queried
     * @return the IMS domain name, or null if not present or not loaded
     */
    String getIsimDomainForSubscriber(int subId);

    /**
     * Returns the IMS public user identities (IMPU) that were loaded from the ISIM for particular subId.
     * @param subId subscription ID to be queried
     * @return an array of IMPU strings, with one IMPU per string, or null if
     *      not present or not loaded
     */
    String[] getIsimImpuForSubscriber(int subId);

    /**
     * Returns the IMS Service Table (IST) that was loaded from the ISIM for particular subId.
     * @param subId subscription ID to be queried
     * @return IMS Service Table or null if not present or not loaded
     */
    String getIsimIstForSubscriber(int subId);

    /**
     * Returns the IMS Proxy Call Session Control Function(PCSCF) that were loaded from the ISIM for particular subId.
     * @param subId subscription ID to be queried
     * @return an array of PCSCF strings with one PCSCF per string, or null if
     *      not present or not loaded
     */
    String[] getIsimPcscfForSubscriber(int subId);

    /**
     * Returns the GBA bootstrapping parameters (GBABP) that was loaded from the ISIM.
     * @return GBA bootstrapping parameters or null if not present or not loaded
     */
    String getIsimGbabp();

    /**
     * Returns the GBA bootstrapping parameters (GBABP) that was loaded from the ISIM for particular subId.
     * @param subId subscription ID to be queried
     * @return GBA bootstrapping parameters or null if not present or not loaded
     */
    String getIsimGbabpForSubscriber(int subId);

    /**
     * Set the GBA bootstrapping parameters (GBABP) value into the ISIM.
     * @param gbabp a GBA bootstrapping parameters value in String type
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    void setIsimGbabp(String gbabp, in Message onComplete);

    /**
     * Set the GBA bootstrapping parameters (GBABP) value into the ISIM for particular subId.
     * @param subId subscription ID to be queried
     * @param gbabp a GBA bootstrapping parameters value in String type
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    void setIsimGbabpForSubscriber(int subId, String gbabp, in Message onComplete);

    /**
     * Returns the USIM Service Table (UST) that was loaded from the USIM.
     * @param service service index on UST
     * @return  the indicated service is supported or not
     */
    boolean getUsimService(int service, String callingPackage);

    /**
     * Returns the USIM Service Table (UST) that was loaded from the USIM for particular subId.
     * @param subId subscription ID to be queried
     * @param service service index on UST
     * @return  the indicated service is supported or not
     */
    boolean getUsimServiceForSubscriber(int subId, int service, String callingPackage);

    /**
     * Returns the GBA bootstrapping parameters (GBABP) that was loaded from the USIM.
     * @return GBA bootstrapping parameters or null if not present or not loaded
     */
    String getUsimGbabp();

    /**
     * Returns the GBA bootstrapping parameters (GBABP) that was loaded from the USIM for particular subId.
     * @param subId subscription ID to be queried
     * @return GBA bootstrapping parameters or null if not present or not loaded
     */
    String getUsimGbabpForSubscriber(int subId);

    /**
     * Set the GBA bootstrapping parameters (GBABP) value into the USIM.
     * @param gbabp a GBA bootstrapping parameters value in String type
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    void setUsimGbabp(String gbabp, in Message onComplete);

    /**
     * Set the GBA bootstrapping parameters (GBABP) value into the USIM for particular subId.
     * @param subId subscription ID to be queried
     * @param gbabp a GBA bootstrapping parameters value in String type
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    void setUsimGbabpForSubscriber(int subId, String gbabp, in Message onComplete);

    /**
     * Returns the Public Service Identity of the SM-SC (PSISMSC) that was loaded from the ISIM.
     * @return PSISMSC or null if not present or not loaded
     */
    byte[] getIsimPsismsc();

    /**
     * Returns the Public Service Identity of the SM-SC (PSISMSC) that was loaded from the ISIM for particular subId.
     * @param subId subscription ID to be queried
     * @return PSISMSC or null if not present or not loaded
     */
    byte[] getIsimPsismscForSubscriber(int subId);

    /**
     * Returns the Public Service Identity of the SM-SC (PSISMSC) that was loaded from the USIM.
     * @return PSISMSC or null if not present or not loaded
     */
    byte[] getUsimPsismsc();

    /**
     * Returns the Public Service Identity of the SM-SC (PSISMSC) that was loaded from the USIM for particular subId.
     * @param subId subscription ID to be queried
     * @return PSISMSC or null if not present or not loaded
     */
    byte[] getUsimPsismscForSubscriber(int subId);

    /**
     * Returns the Short message parameter (SMSP) that was loaded from the USIM.
     * @return SMSP or null if not present or not loaded
     */
    byte[] getUsimSmsp();

    /**
     * Returns the Short message parameter (SMSP) that was loaded from the USIM for particular subId.
     * @param subId subscription ID to be queried
     * @return SMSP or null if not present or not loaded
     */
    byte[] getUsimSmspForSubscriber(int subId);

    /**
     * Returns the MCC+MNC length that was loaded from the USIM.
     * @return MCC+MNC length or 0 if not present or not loaded
     */
    int getMncLength();

    /**
     * Returns the MCC+MNC length that was loaded from the USIM for particular subId.
     * @param subId subscription ID to be queried
     * @return MCC+MNC length or 0 if not present or not loaded
     */
    int getMncLengthForSubscriber(int subId);
     // MTK-END
}
