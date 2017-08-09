/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2015. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.mediatek.internal.telephony.cdma.pluscode;

import android.telephony.Rlog;

/**
 * The plus code processor.
 * @hide
 */
public class PlusCodeProcessor {

    private static final String LOG_TAG = "PlusCodeProcessor";
    private static final String PLUS_CODE_IMPL_CLASS_NAME =
            "com.mediatek.internal.telephony.cdma.pluscode.CdmaPlusCodeUtils";

    private static final Object mLock = new Object();
    private static IPlusCodeUtils sPlusCodeUtilsInstance;

    /**
     * Create IPlusCodeUtils. Will return DefaultPlusCodeUtils If
     * cannot find VIA proprietary implementation
     * @return The implementation of IPlusCodeUtils
     */
    public static IPlusCodeUtils getPlusCodeUtils() {
        if (sPlusCodeUtilsInstance == null) {
            synchronized (mLock) {
                if (sPlusCodeUtilsInstance == null) {
                    sPlusCodeUtilsInstance = makePlusCodeUtis();
                }
            }
        }
        log("getPlusCodeUtils sPlusCodeUtilsInstance=" + sPlusCodeUtilsInstance);
        return sPlusCodeUtilsInstance;
    }

    private static IPlusCodeUtils makePlusCodeUtis() {
        try {
            Class policyClass = Class.forName(PLUS_CODE_IMPL_CLASS_NAME);
            return (IPlusCodeUtils) policyClass.newInstance();
        } catch (ClassNotFoundException ex) {
            log("makePlusCodeUtis ClassNotFoundException, return default DefaultPlusCodeUtils");
            return new DefaultPlusCodeUtils();
        } catch (IllegalAccessException ex) {
            log("makePlusCodeUtis IllegalAccessException, return default DefaultPlusCodeUtils");
            return new DefaultPlusCodeUtils();
        } catch (InstantiationException ex) {
            log("makePlusCodeUtis InstantiationException, return default DefaultPlusCodeUtils");
            return new DefaultPlusCodeUtils();
        }
    }

    private static void log(String string) {
        Rlog.d(LOG_TAG, string);
    }
}
