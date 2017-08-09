/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
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

package com.mediatek.storage;

import android.os.Environment;
import android.os.ServiceManager;
import android.os.storage.IMountService;
import android.os.SystemProperties;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.storage.VolumeInfo;
import android.content.Context;
import android.util.Log;

import java.io.File;


public class StorageManagerEx {
    private static final String TAG = "StorageManagerEx";

    public static final String PROP_SD_DEFAULT_PATH = "persist.sys.sd.defaultpath";
    private static final String PROP_DEVICE_TYPE = "ro.build.characteristics";
    private static final String PROP_DEVICE_TABLET = "tablet";

    private static final String STORAGE_PATH_SD1 = "/storage/sdcard0";
    private static final String STORAGE_PATH_SD2 = "/storage/sdcard1";
    private static final String STORAGE_PATH_EMULATED = "/storage/emulated/";
    private static final String STORAGE_PATH_SD1_ICS = "/mnt/sdcard";
    private static final String STORAGE_PATH_SD2_ICS = "/mnt/sdcard2";

    private static final String DIR_ANDROID = "Android";
    private static final String DIR_DATA = "data";
    private static final String DIR_CACHE = "cache";

    /**
     * Returns default path for writing.
     * @hide
     * @internal
     */
    public static String getDefaultPath() {
        String path = "";
        boolean deviceTablet = false;
        boolean supportMultiUsers = false;

        try {
            path = SystemProperties.get(PROP_SD_DEFAULT_PATH);
            Log.i(TAG, "get path from system property, path=" + path);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException when get default path:" + e);
        }

        // Property will be empty when first boot, should set to default
        // For OTA upgrade, path is invalid, need update default path
        if (path.equals("")
                || path.equals(STORAGE_PATH_SD1_ICS) || path.equals(STORAGE_PATH_SD1)
                || path.equals(STORAGE_PATH_SD2_ICS) || path.equals(STORAGE_PATH_SD2)) {
            Log.i(TAG, "DefaultPath invalid! " + "path = " + path + ", set to default.");
            try {
                IMountService mountService =
                  IMountService.Stub.asInterface(ServiceManager.getService("mount"));
                if (mountService == null) {
                    Log.e(TAG, "mount service is not initialized!");
                    return "";
                }
                int userId = UserHandle.myUserId();
                VolumeInfo[] volumeInfos = mountService.getVolumes(0);
                for (int i = 0; i < volumeInfos.length; ++i) {
                    VolumeInfo vol = volumeInfos[i];
                    if (vol.isVisibleForWrite(userId) && vol.isPrimary()) {
                        path = vol.getPathForUser(userId).getAbsolutePath();
                        Log.i(TAG, "Find primary and visible volumeInfo, "
                        + "path=" + path + ", volumeInfo:" + vol);
                        break;
                    }
                }
                setDefaultPath(path);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException when set default path:" + e);
            }
        }
        return path;
    }

    /**
     * set default path for APP to storage data.
     * this ONLY can used by settings.
     * @hide
     * @internal
     */
    public static void setDefaultPath(String path) {
        Log.i(TAG, "setDefaultPath path=" + path);

        try {
            IMountService mountService =
              IMountService.Stub.asInterface(ServiceManager.getService("mount"));
            if (mountService == null) {
                Log.e(TAG, "mount service is not initialized!");
                return;
            }
            mountService.setDefaultPath(path);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException when set default path:" + e);
        }
    }

    /**
     * Generates the path to Gallery/mms.
     * @hide
     * @internal
     */
    public static File getExternalCacheDir(String packageName) {
        if (null == packageName) {
            Log.w(TAG, "packageName = null!");
            return null;
        }

        File externalCacheDir = new File(getDefaultPath());
        externalCacheDir = Environment.buildPath(externalCacheDir, DIR_ANDROID, DIR_DATA,
                                                 packageName, DIR_CACHE);
        Log.d(TAG, "getExternalCacheDir path = " + externalCacheDir);
        return externalCacheDir;
    }

    /**
     * Returns external SD card path.
     * SD card might have multi partitions
     * will return first partition path
     * @hide
     * @internal
     */
    public static String getExternalStoragePath() {
        String path = "";
        try {
            IMountService mountService =
              IMountService.Stub.asInterface(ServiceManager.getService("mount"));
            if (mountService == null) {
                Log.e(TAG, "mount service is not initialized!");
                return "";
            }
            int userId = UserHandle.myUserId();
            boolean isEMMCProject = SystemProperties.get("ro.mtk_emmc_support").equals("1");
            VolumeInfo[] volumeInfos = mountService.getVolumes(0);
            for (int i = 0; i < volumeInfos.length; ++i) {
                VolumeInfo vol = volumeInfos[i];
                String diskID = vol.getDiskId();
                Log.d(TAG, "getExternalStoragePath, diskID=" + diskID);
                if (diskID != null) {
                    // portable sd card
                    if (vol.isVisibleForWrite(userId)
                            && vol.getState() == VolumeInfo.STATE_MOUNTED) {
                        if (isEMMCProject) {
                            // sd card disk id is "179,128" or "179,xxx", but not "179,0"
                            if (diskID.startsWith("disk:179") && !diskID.endsWith(",0")) {
                                path = vol.getPathForUser(userId).getAbsolutePath();
                                break;
                            }
                        } else {
                            // sd card disk id is "179,0"
                            if (diskID.equals("disk:179,0")) {
                                path = vol.getPathForUser(userId).getAbsolutePath();
                                break;
                            }
                        }
                    }
                } else {
                    // sd card is adopted and migrate data
                    if (vol.getType() == VolumeInfo.TYPE_EMULATED
                            && vol.getState() == VolumeInfo.STATE_MOUNTED) {
                        String emulatedPath = vol.getPathForUser(userId).getAbsolutePath();
                        File internalPathFile = vol.getInternalPath();
                        if (internalPathFile != null
                                && !internalPathFile.getAbsolutePath().equals("/data/media")) {
                            path = emulatedPath;
                            break;
                        } else {
                            Log.d(TAG, "getExternalStoragePath, igore path=" + emulatedPath);
                        }
                    }
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException when getExternalStoragePath:" + e);
        }
        Log.d(TAG, "getExternalStoragePath path=" + path);
        return path ;
    }

    /**
     * Return if this path is external sd card.
     * @hide
     * @internal
     */
    public static boolean isExternalSDCard(String path) {
        Log.e(TAG, "isExternalSDcard path=" + path);
        boolean result = false;
        if (path == null) {
            return false;
        }
        String externalStoragePath = getExternalStoragePath();
        if (externalStoragePath.equals("")) {
            return false;
        }
        if (!path.equals(externalStoragePath)) {
            Log.e(TAG, "path=" + path
                    + ", externalStoragePath=" + externalStoragePath
                    + ", return false");
            return false;
        }

        try {
            IMountService mountService =
              IMountService.Stub.asInterface(ServiceManager.getService("mount"));
            if (mountService == null) {
                Log.e(TAG, "mount service is not initialized!");
                return false;
            }
            int userId = UserHandle.myUserId();
            VolumeInfo[] volumeInfos = mountService.getVolumes(0);
            for (int i = 0; i < volumeInfos.length; ++i) {
                VolumeInfo vol = volumeInfos[i];
                if (vol.isVisibleForWrite(userId) && vol.getState() == VolumeInfo.STATE_MOUNTED) {
                    String volPath = vol.getPathForUser(userId).getAbsolutePath();
                    if (volPath.equals(path)) {
                        File internalPathFile = vol.getInternalPath();
                        if (internalPathFile != null
                                && !internalPathFile.getAbsolutePath().equals("/data/media")) {
                            result = true;
                            break;
                        }
                    }
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException when invoke isExternalSDcard:" + e);
        }
        Log.d(TAG, "isExternalSDcard path=" + path + ", return " + result);
        return result ;
    }

    /**
     * Returns internal Storage path.
     * @hide
     * @internal
     */
    @Deprecated
    public static String getInternalStoragePath() {
        String path = "";
        Log.d(TAG, "getInternalStoragePath path=" + path);
        return path ;
    }

    /**
     * Returns the sd swap state.
     * @hide
     * @internal
     */
    @Deprecated
    public static boolean getSdSwapState() {
        return false;
    }

    /**
     * For log tool only.
     * modify internal path to "/storage/emulated/0" for multi user
     * @hide
     * @internal
     */
    @Deprecated
    public static String getInternalStoragePathForLogger() {
        String path = getInternalStoragePath();
        Log.i(TAG, "getInternalStoragePathForLogger raw path=" + path);
        // if path start with "/storage/emulated/"
        // means MTK_SHARED_SDCARD==true, MTK_2SDCARD_SWAP==false
        // so just check path directly
        if (path != null && path.startsWith(STORAGE_PATH_EMULATED)) {
            path = "/storage/emulated/0";
        }
        Log.i(TAG, "getInternalStoragePathForLogger path=" + path);
        return path;
    }

    /**
     * Check if the path is USBOTG.
     * Used to replace Enviroment.DIRECTORY_USBOTG
     * @hide
     * @internal
     */
    public static boolean isUSBOTG(String path) {
        Log.d(TAG, "isUSBOTG, path=" + path);

        if (path == null) {
            Log.d(TAG, "isUSBOTG, path is null, ruturn false");
            return false;
        }

        boolean result = false;
        try {
            IMountService mountService =
              IMountService.Stub.asInterface(ServiceManager.getService("mount"));
            if (mountService == null) {
                Log.e(TAG, "mount service is not initialized!");
                return false;
            }
            VolumeInfo[] volumeInfos = mountService.getVolumes(0);
            for (int i = 0; i < volumeInfos.length; ++i) {
                VolumeInfo vol = volumeInfos[i];
                if (vol.path != null && path.startsWith(vol.path)) {
                    String diskID = vol.getDiskId();
                    Log.d(TAG, "isUSBOTG, diskID=" + diskID);
                    result = vol.isUSBOTG();
                    if (result) {
                        break;
                    }
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException when invoke isUSBOTG:" + e);
        }
        Log.d(TAG, "isUSBOTG return=" + result);
        return result ;
    }

    /**
     * Find the phone storage volume info.
     * @hide
     * @internal
     */
    public static VolumeInfo findPhoneStorage() {
        Log.d(TAG, "findPhoneStorage VolumeInfo");
        VolumeInfo phoneStorage = null;
        try {
            IMountService mountService =
              IMountService.Stub.asInterface(ServiceManager.getService("mount"));
            if (mountService == null) {
                Log.e(TAG, "mount service is not initialized!");
                return null;
            }
            VolumeInfo[] volumeInfos = mountService.getVolumes(0);
            for (int i = 0; i < volumeInfos.length; ++i) {
                VolumeInfo vol = volumeInfos[i];
                if (vol.isPhoneStorage()) {
                    phoneStorage = vol;
                    break;
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException when invoke findPhoneStorage VolumeInfo:" + e);
        }
        return phoneStorage;
    }

    /**
     *  For setting check if set primary storage Uuid is finished or not
     * @hide
     */
    public static boolean isSetPrimaryStorageUuidFinished() {
        Log.d(TAG, "isSetPrimaryStorageUuidFinished");
        boolean result = true;
        try {
            IMountService mountService =
              IMountService.Stub.asInterface(ServiceManager.getService("mount"));
            if (mountService == null) {
                Log.e(TAG, "mount service is not initialized!");
                return result;
            }
            result = mountService.isSetPrimaryStorageUuidFinished();
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException when invoke findPhoneStorage VolumeInfo:" + e);
        }
        Log.d(TAG, "isSetPrimaryStorageUuidFinished return " + result);
        return result;
    }
}
