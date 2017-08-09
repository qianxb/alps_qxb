/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

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

package com.mediatek.internal.telephony;


import android.net.LinkProperties;

import android.os.Bundle;
import android.os.Message;

import android.telephony.NeighboringCellInfo;
import android.telephony.RadioAccessFamily;

import com.mediatek.internal.telephony.BtSimapOperResponse;

import com.mediatek.internal.telephony.MmsIcpInfo;
import com.mediatek.internal.telephony.MmsConfigInfo;
import com.mediatek.internal.telephony.ISetDefaultSubResultCallback;

/**
 * Interface used to interact with the phone.  Mostly this is used by the
 * TelephonyManager class.  A few places are still using this directly.
 * Please clean them up if possible and use TelephonyManager insteadl.
 *
 * {@hide}
 */
interface ITelephonyEx {

    Bundle queryNetworkLock(int subId, int category);

    int supplyNetworkDepersonalization(int subId, String strPasswd);

    /**
     * Modem SML change feature.
     * This function will query the SIM state of the given slot. And broadcast
     * ACTION_UNLOCK_SIM_LOCK if the SIM state is in network lock.
     *
     * @param subId: Indicate which sub to query
     * @param needIntent: The caller can deside to broadcast ACTION_UNLOCK_SIM_LOCK or not
     *                    in this time, because some APs will receive this intent (eg. Keyguard).
     *                    That can avoid this intent to effect other AP.
     */
    void repollIccStateForNetworkLock(int subId, boolean needIntent);

    int setLine1Number(int subId, String alphaTag, String number);

    boolean isFdnEnabled(int subId);

    String getIccCardType(int subId);

    boolean isAppTypeSupported(int slotId, int appType);

    boolean isTestIccCard(int slotId);

    String getMvnoMatchType(int subId);

    String getMvnoPattern(int subId, String type);

    String getNetworkOperatorNameGemini(int slotId);
    String getNetworkOperatorNameUsingSub(int subId);

    String getNetworkOperatorGemini(int slotId);
    String getNetworkOperatorUsingSub(int subId);

    /**
     * Request to run AKA authenitcation on UICC card by indicated family.
     *
     * @param slotId indicated sim id
     * @param family indiacted family category
     *        UiccController.APP_FAM_3GPP =  1; //SIM/USIM
     *        UiccController.APP_FAM_3GPP2 = 2; //RUIM/CSIM
     *        UiccController.APP_FAM_IMS   = 3; //ISIM
     * @param byteRand random challenge in byte array
     * @param byteAutn authenication token in byte array
     *
     * @return reponse paramenters/data from UICC
     *
     */
    byte[] simAkaAuthentication(int slotId, int family, in byte[] byteRand, in byte[] byteAutn);

    /**
     * Request to run GBA authenitcation (Bootstrapping Mode)on UICC card
     * by indicated family.
     *
     * @param slotId indicated sim id
     * @param family indiacted family category
     *        UiccController.APP_FAM_3GPP =  1; //SIM/USIM
     *        UiccController.APP_FAM_3GPP2 = 2; //RUIM/CSIM
     *        UiccController.APP_FAM_IMS   = 3; //ISIM
     * @param byteRand random challenge in byte array
     * @param byteAutn authenication token in byte array
     *
     * @return reponse paramenters/data from UICC
     *
     */
    byte[] simGbaAuthBootStrapMode(int slotId, int family, in byte[] byteRand, in byte[] byteAutn);

    /**
     * Request to run GBA authenitcation (NAF Derivation Mode)on UICC card
     * by indicated family.
     *
     * @param slotId indicated sim id
     * @param family indiacted family category
     *        UiccController.APP_FAM_3GPP =  1; //SIM/USIM
     *        UiccController.APP_FAM_3GPP2 = 2; //RUIM/CSIM
     *        UiccController.APP_FAM_IMS   = 3; //ISIM
     * @param byteNafId network application function id in byte array
     * @param byteImpi IMS private user identity in byte array
     *
     * @return reponse paramenters/data from UICC
     *
     */
    byte[] simGbaAuthNafMode(int slotId, int family, in byte[] byteNafId, in byte[] byteImpi);

    /**
     * Since MTK keyguard has dismiss feature, we need to retrigger unlock event
     * when user try to access the SIM card.
     *
     * @param subId inidicated subscription
     *
     * @return true represent broadcast a unlock intent to notify keyguard
     *         false represent current state is not LOCKED state. No need to retrigger.
     *
     */
    boolean broadcastIccUnlockIntent(int subId);

    /**
     * Query if the radio is turned off by user.
     *
     * @param subId inidicated subscription
     *
     * @return true radio is turned off by user.
     *         false radio isn't turned off by user.
     *
     */
    boolean isRadioOffBySimManagement(int subId);

    /**
     * Get current phone capability
     *
     * @return the capability of phone. (@see PhoneConstants)
     * @internal
     */
    int getPhoneCapability(int phoneId);

    /**
     * Set capability to phones
     *
     * @param phoneId phones want to change capability
     * @param capability new capability for each phone
     * @internal
     */
    void setPhoneCapability(in int[] phoneId, in int[] capability);
    /**
     * To config SIM swap mode(for dsda).
     *
     * @return true if config SIM Swap mode successful, or return false
     * @internal
     */
    boolean configSimSwap(boolean toSwapped);
    /**
     * To check SIM is swapped or not(for dsda).
     *
     * @return true if swapped, or return false
     * @internal
     */
    boolean isSimSwapped();
    /**
     * To Check if Capability Switch Manual Control Mode Enabled.
     *
     * @return true if Capability Switch manual control mode is enabled, else false;
     * @internal
     */
    boolean isCapSwitchManualEnabled();

    /**
     * Get item list that will be displayed on manual switch setting
     *
     * @return String[] contains items
     * @internal
     */
    String[] getCapSwitchManualList();

  /**
     * To get located PLMN from sepcified SIM modem  protocol
     * Returns current located PLMN string(ex: "46000") or null if not availble (ex: in flight mode or no signal area or this SIM is turned off)
     * @param subId Indicate which SIM subscription to query
     * @internal
     */
    String getLocatedPlmn(int subId);

   /**
     * Check if phone is hiding network temporary out of service state.
     * @param subId Indicate which SIM subscription to query
     * @return if phone is hiding network temporary out of service state.
     * @internal
    */
    int getNetworkHideState(int subId);

   /**
     * get the network service state for specified SIM
     * @param subId Indicate which SIM subscription to query
     * @return service state.
     * @internal
    */
    Bundle getServiceState(int subId);

    /**
     * This function is used to get SIM phonebook storage information
     * by sim id.
     *
     * @param simId Indicate which sim(slot) to query
     * @return int[] which incated the storage info
     *         int[0]; // # of remaining entries
     *         int[1]; // # of total entries
     *         int[2]; // # max length of number
     *         int[3]; // # max length of alpha id
     *
     * @internal
     */
    int[] getAdnStorageInfo(int subId);

    /**
     * This function is used to check if the SIM phonebook is ready
     * by sim id.
     *
     * @param simId Indicate which sim(slot) to query
     * @return true if phone book is ready.
     * @internal
     */
    boolean isPhbReady(int subId);

    /**
     * Get service center address
     *
     * @param subId subscription identity
     *
     * @return bundle value with error code and service message center address
     */
    Bundle getScAddressUsingSubId(in int subId);

    /**
     * Set service message center address
     *
     * @param subId subscription identity
     * @param service message center addressto be set
     *
     * @return true for success, false for failure
     */
    boolean setScAddressUsingSubId(in int subId, in String address);

    /**
     * This function will check if phone can enter airplane mode right now
     *
     * @return boolean: return phone can enter flight mode
     *                true: phone can enter flight mode
     *                false: phone cannot enter flight mode
     */
    boolean isAirplanemodeAvailableNow();

    // VoLTE
    /**
     * This function will get DcFailCause with int format.
     *
     * @return int: return int failCause value
     */
    int getLastDataConnectionFailCause(String apnType, int phoneId);

    /**
     * This function will get link properties of input apn type.
     *
     * @param apnType input apn type for geting link properties
     * @return LinkProperties: return correspondent link properties with input apn type
     */
    LinkProperties getLinkProperties(String apnType, int phoneId);

    /**
     * Set phone radio type and access technology.
     *
     * @param rafs an RadioAccessFamily array to indicate all phone's
     *        new radio access family. The length of RadioAccessFamily
     *        must equal to phone count.
     * @return true if start setPhoneRat successfully.
     */
    boolean setRadioCapability(in RadioAccessFamily[] rafs);

    /**
     * Check if under capability switching.
     *
     * @return true if switching
     */
    boolean isCapabilitySwitching();

    /**
    * Set TRM
    *
    * @param mode indicate which case want to set to modem
    */
    void setTrmForPhone(int phoneId, int mode);

    /**
     * Get main capability phone id.
     * @return The phone id with highest capability.
     */
    int getMainCapabilityPhoneId();

   /**
     * Initialze external SIM service on phone process.
     *
     * @hide
     */
    void initializeService(String serviceName);

   /**
     * Finalize external SIM service on phone process.
     *
     * @hide
     */
    void finalizeService(String serviceName);

    /**
     * Return the sim card if in home network.
     *
     * @param subId subscription ID to be queried
     * @return true if in home network
     */
    boolean isInHomeNetwork(int subId);

    // M: [LTE][Low Power][UL traffic shaping] Start
    /**
     * Set LTE access stratum urc report
     * @param enabled the LTE AS state URC report is enable or disable
     * @return true if enabled/disable urc report successfully.
     */
    boolean setLteAccessStratumReport(boolean enabled);

    /**
     * Set LTE uplink data transfer
     * @param isOn the LTE uplink data transfer is on or off
     * @param timeMillis the close timer
     * @return true if enabled/disable uplink data transfer successfully.
     */
    boolean setLteUplinkDataTransfer(boolean isOn, int timeMillis);

    /**
     * Get LTE access stratum state
     * @return unknown/idle/connected if abnormal mode/power saving mode candidate/normal power mode.
     */
    String getLteAccessStratumState();

    /**
     * Get if shared default type apn
     * @return true if is shared default type apn occurred.
     */
    boolean isSharedDefaultApn();
    // M: [LTE][Low Power][UL traffic shaping] End


    /**
     * Get the MMS ICP information.
     * @param subId sub id.
     * @return the MmsIcpInfo.
     */
    MmsIcpInfo getMmsIcpInfo(in int subId);

    /**
     * Get the MMS Config information.
     * @param subId sub id.
     * @return the MmsConfigInfo.
     */
    MmsConfigInfo getMmsConfigInfo(in int subId);

    /**
     * Check if OMH is enable.
     * @param subId sub id.
     * @return the true if OMH enable.
     */
    boolean isOmhEnable(in int subId);

    /**
     * Check if the card support OMH.
     * @param subId sub id.
     * @return the true if support OMH.
     */
    boolean isOmhCard(in int subId);

    /**
     * Get call forwarding feature code.
     * @param type call forwarding type
     * @param subId sub id
     * @return call forwarding feature code
     */
    int[] getCallForwardingFc(in int type, in int subId);

    /**
     * Get call waiting feature code.
     * @param subId sub id
     * @return call waiting feature code
     */
    int[] getCallWaitingFc(in int subId);

    /**
     * Get do not disturb feature code.
     * @param subId sub id
     * @return do not disturb feature code
     */
    int[] getDonotDisturbFc(in int subId);

    /**
     * Get voice message retrieve feature code.
     * @param subId sub id
     * @return voice message retrieve feature code
     */
    int[] getVMRetrieveFc(in int subId);

    /**
     * Get cell broadcast priority from RUIM.
     * @param userCategory service category
     * @param userPriority user priority
     * @return cell broadcast priority
     */
    int getBcsmsCfgFromRuim(in int subId, in int userCategory, in int userPriority);

    /**
     * Get next message ID from RUIM.
     * @return message ID
     */
    int getNextMessageId(in int subId);

    /**
     * Get wap message ID from RUIM.
     * @return wap message ID
     */
    int getWapMsgId(in int subId);

    /**
     * Return the user customized ecc list.
     *
     * @return Return the user customized ecc list
     */
    Bundle getUserCustomizedEccList();

    /**
     * Update the user customized ecc list.
     *
     * @return true if succeed, or false
     */
    boolean updateUserCustomizedEccList(in Bundle bundle);

    /**
     * Check if the number is user customized ecc.
     * @param number The number need to check
     * @return true if yes, or false
     */
    boolean isUserCustomizedEcc(in String number);

    /**
     * Returns the observed cell information of the device using slotId.
     */
    Bundle getCellLocationUsingSlotId(int slotId);

    /**
     * Returns the neighboring cell information of the device using slotId.
     */
    List<NeighboringCellInfo> getNeighboringCellInfoUsingSlotId(int slotId);

    /**
     * Get CDMA subscription active status
     *
     * @return 1 if active, 0 if deactive
     */
    int getCdmaSubscriptionActStatus(in int subId);

    /**
     * Check if can switch default subId.
     * This api is not capable with Google's setDefaultXXXSubId.
     * It is only capable with setDefaultSubIdForAll.
     * @return true if yes, or false in these cases:
     *         1) voice call is in progress
     *         2) airplane mode is on
     *         3) phone's radio is not available
     *         4) radio capability switch is ongoing
     */
    boolean canSwitchDefaultSubId();

    /**
     * Set default subId for data, voice, sms.
     * @param type The switch type, indicate which setting type(data, voice or sms)
     *        triggers this synchronous change
     * @param subId The default subId of data/voice/sms
     * @param callback The callback for notifying application the result
     * @return true if succeed, or false
     */
    boolean setDefaultSubIdForAll(in int type, in int subId,
            in ISetDefaultSubResultCallback callback);

    /**
     * Set ECC in progress.
     * @param state ECC in pregress or not.
     */
    void setEccInProgress(boolean state);

    /**
     * Return if ECC in progress.
     * @return true if ECC in progress, otherwise false.
     */
    boolean isEccInProgress();

    /**
     * Get IMS registration state by given sub-id.
     * @param subId The subId for query
     * @return true if IMS is registered, or false
     * @hide
     */
    boolean isImsRegistered(in int subId);

    /**
     * Get Volte registration state by given sub-id.
     * @param subId The subId for query
     * @return true if volte is registered, or false
     * @hide
     */
    boolean isVolteEnabled(in int subId);

    /**
     * Get WFC registration state by given sub-id.
     * @param subId The subId for query
     * @return true if wfc is registered, or false
     * @hide
     */
    boolean isWifiCallingEnabled(in int subId);

    /**
     * Get icc app family by slot id.
     * @param slotId slot id
     * @return the family type
     * @hide
     */
    int getIccAppFamily(in int slotId);

    /**
     * Get IMEI by slotId.
     * @param slotId
     * @return IMEI
     * @hide
     */
    String getImei(int slotId);

    /**
     * Get MEID by slotId.
     * @param slotId
     * @return MEID
     * @hide
     */
    String getMeid(int slotId);
}

