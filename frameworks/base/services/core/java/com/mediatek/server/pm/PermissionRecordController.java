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
package com.mediatek.server.pm;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionRecords;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.Slog;
import android.util.SparseArray;
import android.util.Xml;

import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.XmlUtils;
import com.android.server.ServiceThread;

import com.mediatek.cta.CtaUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

public class PermissionRecordController {

    static final String TAG = "PermRecordController";

    private static final boolean IS_ENG_BUILD = "eng".equals(Build.TYPE);

    private static final int MSG_REPORT_PERM_RECORDS = 1;

    private static final String KEY_PERM_NAME = "PERM_NAME";
    private static final String KEY_UID = "UID";
    private static final String KEY_REQUEST_TIME = "REQUEST_TIME";

    static final boolean DEBUG_WRITEFILE = false;
    // Write at most every 30 minutes.
    static final long WRITE_DELAY = DEBUG_WRITEFILE ? 1000 : 30*60*1000;

    Context mContext;
    final AtomicFile mFile;
    private final SparseArray<UserData> mUserDatas = new SparseArray<>();
    boolean mWriteScheduled;
    PermRecordsHandler mHandler;
    static int RECORDS_LIMIT;
    boolean mShutDown;

    final Runnable mWriteRunner = new Runnable() {
        public void run() {
            synchronized (PermissionRecordController.this) {
                mWriteScheduled = false;
                AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                    @Override protected Void doInBackground(Void... params) {
                        writeState();
                        return null;
                    }
                };
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
            }
        }
    };

    class PermRecordsHandler extends Handler {

        PermRecordsHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REPORT_PERM_RECORDS:
                    handlePermRequestUsage(msg.getData());
                    break;
            }
        }
    }

    private static final class UserData {
        public final int userId;
        public ArrayMap<String, PackageData> mPackageDatas;

        public UserData(int userId) {
            this.userId = userId;
            mPackageDatas = new ArrayMap<>();
        }

        public void addPackageData(PackageData data) {
            this.mPackageDatas.put(data.packageName, data);
        }
    }

    private static final class PackageData {
        public final String packageName;
        public ArrayMap<String, PermissionRecord> mRecords;

        public PackageData(String packageName) {
            this.packageName = packageName;
            mRecords = new ArrayMap<>();
        }

        public void addPermissionRecord(PermissionRecord record) {
            this.mRecords.put(record.permission, record);
        }
    }

    private static final class PermissionRecord {
        public final String permission;
        public List<Long> requestTimes;

        public PermissionRecord(String permission) {
            this.permission = permission;
            this.requestTimes = new ArrayList<>();
        }

        public void addUsageTime(long time) {
            this.requestTimes.add(time);
            if (this.requestTimes.size() > RECORDS_LIMIT) {
                this.requestTimes.remove(0);
            }
        }
    }

    public PermissionRecordController(Context context) {
        mContext = context;
        mFile = new AtomicFile(new File(new File(Environment.getDataDirectory(), "system"),
                "permission_records.xml"));
        RECORDS_LIMIT = mContext.getResources().getInteger(
                com.mediatek.internal.R.integer.permission_records_limit);
        setupHandler();
        readState();
    }

    private void setupHandler() {
        ServiceThread handlerThread = new ServiceThread(TAG,
                android.os.Process.THREAD_PRIORITY_FOREGROUND, false /*allowIo*/);
        handlerThread.start();
        mHandler = new PermRecordsHandler(handlerThread.getLooper());
    }

    public void reportPermRequestUsage(final String permName, final int uid) {
        // precondition check
        if (!CtaUtils.isCtaSupported()) {
            return;
        }
        if (TextUtils.isEmpty(permName)) {
            Slog.w(TAG, "reportPermRequestUsage() permName is null; ignoring");
            return;
        }
        if (!CtaUtils.isCtaMonitoredPerms(permName)) {
            return;
        }
        if (mShutDown) {
            return;
        }

        final long requestTime = System.currentTimeMillis();
        if (IS_ENG_BUILD) {
            Slog.d(TAG, "reportPermRequestUsage() permName = " + permName +
                    ", uid = " + uid + ", requestTime = " + requestTime);
        }

        Message msg = Message.obtain();
        msg.what = MSG_REPORT_PERM_RECORDS;
        Bundle bundle = new Bundle();
        bundle.putString(KEY_PERM_NAME, permName);
        bundle.putInt(KEY_UID, uid);
        bundle.putLong(KEY_REQUEST_TIME, requestTime);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    private void handlePermRequestUsage(Bundle bundle) {
        String permName = bundle.getString(KEY_PERM_NAME);
        int uid = bundle.getInt(KEY_UID);
        long requestTime = bundle.getLong(KEY_REQUEST_TIME);
        String[] packages = mContext.getPackageManager().getPackagesForUid(uid);
        if (packages == null || packages.length == 0) {
            Slog.w(TAG, "handlePermRequestUsage() packages for uid = " + uid +
                    " is null; ignoring");
            return;
        }
        int userHandle = UserHandle.getUserId(uid);
        for (String pkgName : packages) {
            if (isPackageRequestingPermission(pkgName, permName, userHandle)) {
                addPermissionRecords(userHandle, permName, pkgName, requestTime, true);
            }
        }
    }

    private boolean isPackageRequestingPermission(String pkgName, String permName,
            int userHandle) {
        PackageInfo pkgInfo;
        try {
            pkgInfo = mContext.getPackageManager().getPackageInfoAsUser(pkgName,
                    PackageManager.GET_PERMISSIONS, userHandle);
        } catch (NameNotFoundException e) {
            Slog.w(TAG, "Couldn't retrieve permissions for package:" + pkgName);
            return false;
        }
        final int permCount = pkgInfo.requestedPermissions != null ?
                pkgInfo.requestedPermissions.length : 0;
        for (int i = 0; i < permCount; i++) {
            if (permName.equals(pkgInfo.requestedPermissions[i])) {
                return true;
            }
        }
        return false;
    }

    public void addPermissionRecords(int userId, String permName, String pkgName, long time,
            boolean doWrite) {
        if (IS_ENG_BUILD) {
            Slog.d(TAG, "addPermissionRecords userId = " + userId + ", pkgName = " + pkgName +
                    ", permName = " + permName + ", time = " + time + ", doWrite = " + doWrite);
        }
        UserData userData = mUserDatas.get(userId);
        if (userData == null) {
            userData = new UserData(userId);
            mUserDatas.put(userId, userData);
        }
        PackageData pkgData = userData.mPackageDatas.get(pkgName);
        if (pkgData == null) {
            pkgData = new PackageData(pkgName);
            userData.addPackageData(pkgData);
        }
        PermissionRecord permRecord = pkgData.mRecords.get(permName);
        if (permRecord == null) {
            permRecord = new PermissionRecord(permName);
            pkgData.addPermissionRecord(permRecord);
        }
        permRecord.addUsageTime(time);
        if (doWrite) {
            scheduleWriteLocked();
        }
    }

    public List<String> getPermRecordPkgs(int userId) {
        synchronized (mUserDatas) {
            UserData userData = mUserDatas.get(userId);
            if (userData == null) {
                Slog.w(TAG, "getPermRecordPkgs(), no permission records for userId = " + userId);
                return null;
            }
            Set<String> pkgs = userData.mPackageDatas.keySet();
            return new ArrayList<String>(pkgs);
        }
    }

    public List<String> getPermRecordPerms(int userId, String packageName) {
        synchronized (mUserDatas) {
            UserData userData = mUserDatas.get(userId);
            if (userData == null) {
                Slog.w(TAG, "getPermRecordPerms(), no permission records for userId = " + userId);
                return null;
            }
            PackageData pkgData = userData.mPackageDatas.get(packageName);
            if (pkgData == null) {
                Slog.w(TAG, "getPermRecordPerms(), no permission records for userId = " + userId
                        + ", packageName = " + packageName);
                return null;
            }
            Set<String> perms = pkgData.mRecords.keySet();
            return new ArrayList<String>(perms);
        }
    }

    public PermissionRecords getPermRecords(int userId, String packageName, String permName) {
        synchronized (mUserDatas) {
            UserData userData = mUserDatas.get(userId);
            if (userData == null) {
                Slog.w(TAG, "getPermRecords(), no permission records for userId = " + userId);
                return null;
            }
            PackageData pkgData = userData.mPackageDatas.get(packageName);
            if (pkgData == null) {
                Slog.w(TAG, "getPermRecords(), no permission records for userId = " + userId
                        + ", packageName = " + packageName);
                return null;
            }
            PermissionRecord permRecord = pkgData.mRecords.get(permName);
            if (permRecord == null) {
                Slog.w(TAG, "getPermRecords(), no permission records for userId = " + userId
                        + ", packageName = " + packageName + ", permName = " + permName);
                return null;
            }
            List<Long> times = permRecord.requestTimes;
            return new PermissionRecords(packageName, permName, new ArrayList<Long>(times));
        }
    }

    private void scheduleWriteLocked() {
        if (!mWriteScheduled) {
            mWriteScheduled = true;
            mHandler.postDelayed(mWriteRunner, WRITE_DELAY);
        }
    }

    public void shutdown() {
        Slog.d(TAG, "Writing data to file before shutdown...");
        boolean doWrite = false;
        mShutDown = true;
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        synchronized (this) {
            if (mWriteScheduled) {
                mWriteScheduled = false;
                doWrite = true;
            }
        }
        if (doWrite) {
            writeState();
        }
    }

    void readState() {
        Slog.d(TAG, "readState() BEGIN");
        synchronized (mFile) {
            synchronized (this) {
                FileInputStream stream;
                try {
                    stream = mFile.openRead();
                } catch (FileNotFoundException e) {
                    Slog.i(TAG, "No existing permission records " + mFile.getBaseFile()
                        + "; starting empty");
                    return;
                }
                boolean success = false;
                mUserDatas.clear();
                try {
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setInput(stream, StandardCharsets.UTF_8.name());
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
                        if (tagName.equals("userId")) {
                            readUserId(parser);
                        } else {
                            Slog.w(TAG, "Unknown element under <perm-records>: "
                                    + parser.getName());
                            XmlUtils.skipCurrentTag(parser);
                        }
                    }
                    success = true;
                } catch (IllegalStateException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (NullPointerException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (NumberFormatException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (XmlPullParserException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (IOException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } catch (IndexOutOfBoundsException e) {
                    Slog.w(TAG, "Failed parsing " + e);
                } finally {
                    if (!success) {
                        Slog.w(TAG, "readState() fails");
                        mUserDatas.clear();
                    }
                    try {
                        if (stream != null) {
                            stream.close();
                        }
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }
        }
        Slog.d(TAG, "readState() END");
    }

    void readUserId(XmlPullParser parser) throws NumberFormatException,
        XmlPullParserException, IOException {
        int userId = Integer.parseInt(parser.getAttributeValue(null, "n"));
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }
            String tagName = parser.getName();
            if (tagName.equals("pkg")) {
                readPackage(parser, userId);
            }
        }
    }

    void readPackage(XmlPullParser parser, int userId) throws NumberFormatException,
        XmlPullParserException, IOException {
        String pkgName = parser.getAttributeValue(null, "n");
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }
            String tagName = parser.getName();
            if (tagName.equals("perm")) {
                readPermission(parser, userId, pkgName);
            }
        }
    }

    void readPermission(XmlPullParser parser, int userId, String pkgName)
            throws XmlPullParserException, IOException {
        String permName = parser.getAttributeValue(null, "n");
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }
            String tagName = parser.getName();
            if (tagName.equals("time")) {
                long requestTime = Long.parseLong(parser.getAttributeValue(null, "n"));
                addPermissionRecords(userId, permName, pkgName, requestTime, false);
            }
        }
    }

    void writeState() {
        Slog.d(TAG, "writeState() BEGIN");
        synchronized (mFile) {
            synchronized (this) {
                FileOutputStream stream;
                try {
                    stream = mFile.startWrite();
                } catch (IOException e) {
                    Slog.w(TAG, "Failed to write state: " + e);
                    return;
                }
                try {
                    XmlSerializer out = new FastXmlSerializer();
                    out.setOutput(stream, StandardCharsets.UTF_8.name());
                    out.startDocument(null, true);
                    out.startTag(null, "perm-records");
                    final int userDataCount = mUserDatas.size();
                    for (int i = 0; i < userDataCount; i++) {
                        UserData userData = mUserDatas.valueAt(i);
                        out.startTag(null, "userId");
                        out.attribute(null, "n", String.valueOf(userData.userId));
                        for (int j = 0; j < userData.mPackageDatas.size(); j++) {
                            PackageData pkgData = userData.mPackageDatas.valueAt(j);
                            out.startTag(null, "pkg");
                            out.attribute(null, "n", pkgData.packageName);
                            for (int k = 0; k < pkgData.mRecords.size(); k++) {
                                PermissionRecord record = pkgData.mRecords.valueAt(k);
                                out.startTag(null, "perm");
                                out.attribute(null, "n", record.permission);
                                for (int l = 0; l < record.requestTimes.size(); l++) {
                                    out.startTag(null, "time");
                                    out.attribute(null, "n",
                                            String.valueOf(record.requestTimes.get(l)));
                                    out.endTag(null, "time");
                                }
                                out.endTag(null, "perm");
                            }
                            out.endTag(null, "pkg");
                        }
                        out.endTag(null, "userId");
                    }
                    out.endTag(null, "perm-records");
                    out.endDocument();
                    mFile.finishWrite(stream);
                } catch (IOException e) {
                    Slog.w(TAG, "Failed to write state, restoring backup.", e);
                    mFile.failWrite(stream);
                }
            }
        }
        Slog.d(TAG, "writeState() END");
    }
}
