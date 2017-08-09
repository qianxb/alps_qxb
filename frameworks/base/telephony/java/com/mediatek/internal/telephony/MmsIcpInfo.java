/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2015. All rights reserved.
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

package com.mediatek.internal.telephony;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * MMS ICP info.
 */
public class MmsIcpInfo implements Parcelable {
    //Support WAP only
    public String mImplementation;  // WAP, HTTP, SIP, I-MAP
    public String mRelayOrServerAddress; //Relay Server address
    public String mDomainName;  // Domain name
    public String mAddress; // Address
    public String mAddressType;  // Address type
    public int mPort; // Port id
    public String mService; // Service type, mms service type is WAP
    public String mAuthType; // Auth type
    public String mAuthId; // Auth Id
    public String mAuthPw; // Auth password
    public String mAuthMechanism; // Auth mechanism

    /**
     * Read and init class from Parcel.
     * @param p the parcel to read.
     */
    public void readFrom(Parcel p) {
        mImplementation = p.readString();
        mRelayOrServerAddress = p.readString();
        mDomainName = p.readString();
        mAddress = p.readString();
        mAddressType = p.readString();
        mPort = p.readInt();
        mService = p.readString();
        mAuthType = p.readString();
        mAuthId = p.readString();
        mAuthPw = p.readString();
        mAuthMechanism = p.readString();
    }

    /**
     * Write the MmsConfigInfo to a Parcel.
     * @param p the parcel to write.
     */
    public void writeTo(Parcel p) {
        p.writeString(mImplementation);
        p.writeString(mRelayOrServerAddress);
        p.writeString(mDomainName);
        p.writeString(mAddress);
        p.writeString(mAddressType);
        p.writeInt(mPort);
        p.writeString(mService);
        p.writeString(mAuthType);
        p.writeString(mAuthId);
        p.writeString(mAuthPw);
        p.writeString(mAuthMechanism);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeTo(dest);
    }

    public static final Parcelable.Creator<MmsIcpInfo> CREATOR =
            new Parcelable.Creator<MmsIcpInfo>() {
        @Override
        public MmsIcpInfo createFromParcel(Parcel source) {
            MmsIcpInfo mmsIcpInfo = new MmsIcpInfo();
            mmsIcpInfo.readFrom(source);
            return mmsIcpInfo;
        }

        @Override
        public MmsIcpInfo[] newArray(int size) {
            return new MmsIcpInfo[size];
        }
    };
}
