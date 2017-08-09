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
 * MediaTek Inc. (C) 2016. All rights reserved.
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

package com.mediatek.server.am.AutoBootControl;

import static android.content.pm.PackageManager.GET_META_DATA;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.UserInfo;
import android.content.pm.ResolveInfo;
import android.os.Environment;
import android.os.IUserManager;
import android.os.FileObserver;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Xml;
import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

class ReceiverRecordHelper {
    private static final String TAG = "ReceiverRecordHelper";
    private static final String FILE_NAME = "bootreceiver";
    private static final String FILE_EXTENSION = ".xml";
    public static final boolean DEFAULT_STATUS = true;
    public static boolean SUPPORT_SYSTEM_APP = false;
    private static final String CLIENT_PKGNAME = "com.mediatek.security";

    private Context mContext = null;
    private AtomicFile mFile = null;
    // Map<UserId, Map<PackageName, ReceiverRecord>>
    private Map<Integer, Map<String, ReceiverRecord>> mBootReceiverList =
            new HashMap<Integer, Map<String, ReceiverRecord>>();
    // Record the pending disbled settings
    private Map<Integer, List<String>> mPendingSettings =
            new HashMap<Integer, List<String>>();
    private IUserManager mUm = null;
    private IPackageManager mPm = null;
    private boolean mReady = false;
    private BootReceiverPolicy mBootReceiverPolicy = null ;
    private FileChangeListener mFileChangeListener = null ;

    public ReceiverRecordHelper(
            Context context, IUserManager um, IPackageManager pm) {

        File dataDir = Environment.getDataDirectory();
        File clientDataDir = new File(dataDir, "data/" + CLIENT_PKGNAME);
        File storeDir = new File(clientDataDir, FILE_NAME + FILE_EXTENSION);
        mFile = new AtomicFile(storeDir);
        mContext = context;
        mUm = um;
        mPm = pm;
        mBootReceiverPolicy = BootReceiverPolicy.getInstance(mContext) ;
        Log.d(TAG, "storeDir = " + clientDataDir.getPath()) ;
        mFileChangeListener = new FileChangeListener(clientDataDir.getPath()) ;
    }

    public void initReceiverList() {
        // Should NOT modify basic policy
        enforceBasicBootPolicy();

        List<UserInfo> userList = getUserList();
        synchronized (mBootReceiverList) {
            for (int i = 0; i < userList.size(); i++) {
                initReceiverCache(userList.get(i).id, false);
            }
            // Read xml file to cache
            loadDataFromFileToCache();
        }
        mReady = true;

        if (mFileChangeListener != null) {
            mFileChangeListener.startWatching();
        }
    }

    public void initReceiverCache(int userId) {
        initReceiverCache(userId, false);
    }

    // Discard old data
    private void initReceiverCache(int userId, boolean readFile) {
        Log.d(TAG, "initReceiverCache() at User(" + userId + ")");
        List<String> list = getPackageListReceivingSpecifiedIntent(userId);
        Map<String, ReceiverRecord> receiverList = new HashMap<String, ReceiverRecord>();

        synchronized (mBootReceiverList) {
            mBootReceiverList.put(userId, receiverList);
        }

        for (int i = 0; i < list.size(); i++) {
            String packageName = list.get(i);
            receiverList.put(packageName, new ReceiverRecord(packageName, DEFAULT_STATUS));
            Log.d(TAG, "initReceiverCache() packageName: " + packageName);
        }
        if (readFile) {
            loadDataFromFileToCache();
        }
    }

    public List<String> getPackageListReceivingSpecifiedIntent(int userId) {
        List<String> bootReceivers = new ArrayList<String>();
        List<String> policy = mBootReceiverPolicy.getBootPolicy();
        int pmsFlag = PackageManager.MATCH_DEBUG_TRIAGED_MISSING |
            PackageManager.GET_SHARED_LIBRARY_FILES ;

        for (int i = 0; i < policy.size(); i++) {
            Intent intent = new Intent(policy.get(i));
            try {
                Log.d(TAG, "getPackageListReceivingSpecifiedIntent() "
                    + "find activities receiving intent = "
                    + intent.getAction()) ;
                ParceledListSlice<ResolveInfo> parceledList =
                    mPm.queryIntentReceivers(intent,
                        null,
                        pmsFlag, userId);
                if (parceledList != null) {
                    List<ResolveInfo> receivers = parceledList.getList();
                    if (receivers != null) {
                        for (int j = 0; j < receivers.size(); j++) {
                            ResolveInfo info = receivers.get(j);
                            String packageName =
                                (info.activityInfo != null) ? info.activityInfo.packageName : null;
                            if ((SUPPORT_SYSTEM_APP == false) && isSystemApp(userId, packageName)) {
                                continue;
                            }
                            if (packageName != null && !bootReceivers.contains(packageName)) {
                                Log.d(TAG,
                                    "getPackageListReceivingSpecifiedIntent() "
                                    + "add " + packageName + " in the list") ;
                                bootReceivers.add(packageName);
                            }
                        }
                    }
                }
            } catch (RemoteException e) {
                // Should never happen
                continue;
            }
        }
        return bootReceivers;
    }

    private void enforceBasicBootPolicy() {
        List<String> policy = mBootReceiverPolicy.getBootPolicy();
        boolean valid = true;

        do {
            if (!policy.contains(Intent.ACTION_BOOT_COMPLETED)) {
                valid = false;
                break;
            }
            if (!policy.contains("android.intent.action.ACTION_BOOT_IPO")) {
                valid = false;
                break;
            }
        } while (false);

        if (!valid) {
            throw new RuntimeException("Should NOT remove basic boot policy!");
        }
    }

    public boolean getReceiverDataEnabled(int userId, String packageName) {
        if (mReady) {
            Map<String, ReceiverRecord> receiverList = getBootReceiverListByUser(userId);
            if (receiverList != null && receiverList.containsKey(packageName)) {
                ReceiverRecord data = receiverList.get(packageName);
                if (data != null) {
                    return data.enabled;
                }
            }
        } else {
            Log.e(TAG, "getReceiverDataEnabled() not ready!");
        }
        // Default status is enabled
        return true;
    }

    private boolean setReceiverRecord(int userId, String packageName, boolean enable) {
        boolean success = false;
        Map<String, ReceiverRecord> receiverList = null;
        receiverList = getBootReceiverListByUser(userId);
        if (receiverList != null && receiverList.containsKey(packageName)) {
            ReceiverRecord data = receiverList.get(packageName);
            data.enabled = enable;
            success = true;
        }

        return success;
    }

    private void addReceiverRecord(int userId, String packageName, boolean enabled) {
        Log.d(TAG, "addReceiverRecord() with " + packageName
            + " at User(" + userId + ") enabled: " + enabled);

        Map<String, ReceiverRecord> receiverList = null;
        receiverList = getBootReceiverListByUser(userId);
        if (receiverList == null) {
            receiverList = new HashMap<String, ReceiverRecord>();
            synchronized (mBootReceiverList) {
                mBootReceiverList.put(userId, receiverList);
            }
        }
        receiverList.put(packageName, new ReceiverRecord(packageName, enabled));
    }

    private boolean isPendingSetting(int userId, String packageName) {
        synchronized (mPendingSettings) {
            List<String> settings = mPendingSettings.get(userId);
            if (settings != null) {
                if (settings.contains(packageName)) {
                    Log.d(TAG, "Found a pending setting for pkg: " +
                            packageName + " at User(" + userId + ")");
                    return true;
                }
            }
        }
        return false;
    }

    private void removePendingSetting(int userId, String packageName) {
        synchronized (mPendingSettings) {
            List<String> settings = mPendingSettings.get(userId);
            if (settings != null) {
                if (settings.contains(packageName)) {
                    settings.remove(packageName);
                }
            }
        }
    }

    public void loadDataFromFileToCache() {
        Log.d(TAG, "loadDataFromFileToCache()");
        synchronized (mFile) {
            FileInputStream stream = null;
            try {
                stream = mFile.openRead();
            } catch (FileNotFoundException e) {
                Log.i(TAG, "No existing " + mFile.getBaseFile()
                    + "; starting empty");
                //since the file is deleted, we need to reset all records.
                resetAllReceiverRecords() ;
                return;
            }
            boolean success = false;
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(stream, null);
                int type;
                while ((type = parser.next()) != XmlPullParser.START_TAG
                        && type != XmlPullParser.END_DOCUMENT) {
                    ;
                }

                if (type != XmlPullParser.START_TAG) {
                    throw new IllegalStateException("no start tag found");
                }

                int outerDepth = parser.getDepth();
                while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                        && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                    if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                        continue;
                    }
                    String tagName = parser.getName();
                    if (tagName.equals("pkg")) {
                        String pkgName = parser.getAttributeValue(null, "n");
                        int userId = Integer.parseInt(parser.getAttributeValue(null, "u"));
                        boolean enabled =
                            Boolean.parseBoolean(parser.getAttributeValue(null, "e"));
                        Log.d(TAG, "Read package name: " + pkgName
                                + " enabled: " + enabled + " at User(" + userId + ")");
                        if (!setReceiverRecord(userId, pkgName, enabled)) {
                            Log.w(TAG, "Found a pending settings for package: " + pkgName);
                            synchronized (mPendingSettings) {
                                List<String> pendingSettings = null;
                                if (!mPendingSettings.containsKey(userId)) {
                                    pendingSettings = new ArrayList<String>();
                                    mPendingSettings.put(userId, pendingSettings);
                                }
                                pendingSettings = mPendingSettings.get(userId);
                                if (!pendingSettings.contains(pkgName)) {
                                    pendingSettings.add(pkgName);
                                }
                            }
                        }

                    } else {
                        Log.w(TAG, "Unknown element under <boot-receiver>: "
                                + parser.getName());
                        XmlUtils.skipCurrentTag(parser);
                    }
                }
                success = true;
            } catch (IllegalStateException e) {
                Log.w(TAG, "Failed parsing " + e);
            } catch (NullPointerException e) {
                Log.w(TAG, "Failed parsing " + e);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed parsing " + e);
            } catch (XmlPullParserException e) {
                Log.w(TAG, "Failed parsing " + e);
            } catch (IOException e) {
                Log.w(TAG, "Failed parsing " + e);
            } catch (IndexOutOfBoundsException e) {
                Log.w(TAG, "Failed parsing " + e);
            } finally {
                if (!success) {
                    synchronized (mBootReceiverList) {
                        mBootReceiverList.clear();
                    }
                }
                try {
                    stream.close();
                } catch (IOException e) {
                    throw new RuntimeException("Fail to read receiver list");
                }
            }
        }
    }

    private List<UserInfo> getUserList() {
        List<UserInfo> list = null;
        try {
            list = mUm.getUsers(false);
        } catch (RemoteException e) {
            Log.e(TAG, "getUserList() failed!", e);
        }
        return list;
    }

    private PackageInfo getPackageInfoByUser(int userId, String packageName) {
        PackageInfo packageInfo = null;
        try {
            packageInfo = mPm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS, userId);
        } catch (RemoteException e) {
            Log.e(TAG, "getPackageInfoByUser() failed! with userId: " + userId, e);
        }
        return packageInfo;
    }

    private boolean isSystemApp(int userId, String packageName) {
        PackageInfo pkgInfo = getPackageInfoByUser(userId, packageName);
        if (pkgInfo != null && pkgInfo.applicationInfo != null) {
            int appId = UserHandle.getAppId(pkgInfo.applicationInfo.uid);
            boolean result = ((pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) ||
                (appId == Process.SYSTEM_UID);
            return result ;
        } else {
            Log.d(TAG, "isSystemApp() return false with null packageName");
            return false;
        }
    }

    private Map<String, ReceiverRecord> getBootReceiverListByUser(int userId) {
        synchronized (mBootReceiverList) {
            if (mBootReceiverList.containsKey(userId)) {
                return mBootReceiverList.get(userId);
            }
        }
        return null;
    }

    private class FileChangeListener extends FileObserver {
        public FileChangeListener(String path) {
            super(path);
        }

        @Override
        public void onEvent(int event, String path) {
            if (path != null && path.equals(FILE_NAME + FILE_EXTENSION)) {
                switch(event) {
                    case FileObserver.CREATE:
                    case FileObserver.CLOSE_WRITE:
                    case FileObserver.DELETE:
                    case FileObserver.MODIFY:
                        Log.d(TAG, "FileChangeListener.onEvent()," +
                            " event = " + event +
                            ", reload the file.");
                        loadDataFromFileToCache() ;
                        break;
                    default:
                        break;
                }
            }
        }
    }

    public void updateReceiverCache() {
        Log.d(TAG, "updateReceiverCache()") ;

        List<UserInfo> userList = getUserList() ;
        if (userList != null) {
            Iterator<UserInfo> itor = userList.iterator();
            while (itor.hasNext()) {
                UserInfo user = (UserInfo) itor.next();
                updateReceiverCache(user.id);
            }
        }
    }

    void updateReceiverCache(int userId) {
        Log.d(TAG, "updateReceiverCache() at User(" + userId + ")");

        List<ReceiverRecord> oldList = getReceiverList(userId);
        List<String> updateList = getPackageListReceivingSpecifiedIntent(userId);

        for (int i = 0; i < updateList.size(); i++) {
            boolean enabled = true;
            boolean found = false;
            String packageName = updateList.get(i);
            for (int j = 0; j < oldList.size(); j++) {
                ReceiverRecord record = oldList.get(j);
                if (record != null && packageName.equals(record.packageName)) {
                    enabled = record.enabled;
                    found = true;
                    break;
                }
            }
            if (found == true) {
                setReceiverRecord(userId, packageName, enabled);
            } else {
                if (isPendingSetting(userId, packageName)) {
                    addReceiverRecord(userId, packageName, false);
                    removePendingSetting(userId, packageName);
                } else {
                    addReceiverRecord(userId, packageName, DEFAULT_STATUS);
                }
            }
        }
    }

    // Deep copy of receiver list
    private List<ReceiverRecord> getReceiverList(int userId) {
        List<ReceiverRecord> res = new ArrayList<ReceiverRecord>();
        Map<String, ReceiverRecord> data = getBootReceiverListByUser(userId);
        if (data != null) {
            Iterator<String> itor = data.keySet().iterator();
            while (itor.hasNext()) {
                String pkgName = itor.next();
                res.add(new ReceiverRecord(data.get(pkgName)));
            }
        }
        return res;
    }

    void resetAllReceiverRecords() {
        Log.d(TAG, "resetAllReceiverRecords()") ;
        List<UserInfo> userList = getUserList() ;
        if (userList != null) {
            Iterator<UserInfo> itor = userList.iterator();
            while (itor.hasNext()) {
                UserInfo user = (UserInfo) itor.next();
                int userId = user.id ;
                List<ReceiverRecord> curList = getReceiverList(userId);

                for (int index = 0 ; index < curList.size() ; index++) {
                     ReceiverRecord record = curList.get(index) ;
                     if (record != null && record.enabled == false) {
                         Log.d(TAG, "resetAllReceiverRecords() - found pkg = "
                             + record.packageName + " to be reset") ;
                         record.enabled = DEFAULT_STATUS ;
                     }
                }
            }
        }
    }
}
