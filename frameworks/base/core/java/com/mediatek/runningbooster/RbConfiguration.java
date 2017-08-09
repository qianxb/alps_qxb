/*
* Copyright (C) 2016 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/

package com.mediatek.runningbooster;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;

/**
 * A class representing a configured Wi-Fi network, including the
 * security configuration.
 */
public class RbConfiguration implements Parcelable {

    private static final String TAG = "RbConfiguration";
    public static final String INITIAL = "initial";
    public static final int DEFAULT_KEEP_RECENT_TASK_NUMBER = 3;
    public static final int MAX_KEEP_RECENT_TASK_NUMBER = 10;
    public static final int MIN_KEEP_RECENT_APP_NUMBER = 1;
    public static final int DEFAULT_KEEP_NOTIFICATION_APP_NUMBER = -1;
    public static final int MIN_KEEP_NOTIFICATION_APP_NUMBER = 3;
    public static final int INVALIDE_VALUE = -1000;
    public static final int INVALID_STATE = -1;
    public static final SuppressionPoint DEAULT_STARTPOINT =
            new SuppressionPoint(INITIAL, INVALID_STATE);

    /** set the adj not to suppress (default value is AdjValue.PerceptibleAppAdj)  */
    public int adj = INVALIDE_VALUE;

    /** set the number of recent app which RB wants to keep
         *(default value is DEFAULT_KEEP_RECENT_TASK_NUMBER)  */
    public int keepRecentTaskNumner = INVALIDE_VALUE;

    /** set the number of notification app which RB wants to keep
         *(default value is DEFAULT_KEEP_NOTIFICATION_APP_NUMBER)  */
    public int keepNotificationAPPNumber = INVALIDE_VALUE;

    /** set the value to  decide whether add app to white list which using location service
         *(default value is true)  */
    public boolean checkLocationServiceApp = true;

    /** set the value to  decide whether add LauncherWidget app to white list
         *(default value is true)  */
    public boolean enableLauncherWidget = true;

    /** set the app to allow list  */
    public ArrayList <String> whiteList = new ArrayList <String>();

    /** set the timing of suppression
        *default value: SuppressionPoint(HOME, APP_PAUSE) and
        *SuppressionPoint(RECENT_APP, APP_PAUSE)
       */
    public ArrayList <SuppressionPoint> suppressPoint = new ArrayList <SuppressionPoint>();
    public ArrayList <String> suppressPkg = new ArrayList <String>();
    public int[] suppressAppStatus;

    /** set enable/disable RB (default value is false)  */
    public boolean enableRunningBooster = false;

    /** add  the app to suppress list  */
    public ArrayList <String> blackList = new ArrayList <String>();
    public int suppressPointListSize;

    /// M: adj configuration @{
    /**
         * adj value should be set by enum Adj
         */
    public static enum AdjValue {
        PerceptibleAppAdj(200),
        BackupAppAdj(300),
        HeavyWeightAppAdj(400),
        ServiceAdj(500),
        HomeAppAdj(600),
        PreviousAppAdj(700);

        AdjValue (int value) {
            this.mAdjValue = value;
        }

        private int mAdjValue;

        public int getAdjValue() {
            return mAdjValue;
        }
    }
    /// adj configuration @}

    public static class SuppressionPoint {
        public static final int APP_PAUSE = 0;
        public static final int APP_RESUME = 1;
        public static final  String HOME = "launcher";
        public static final  String RECENT_APP = "recentAPP";
        public int mAppState;
        public boolean mSuppressState;
        public String mPackageName;
        public String mSuppressTag;
        public RbConfiguration mConfig;

        public SuppressionPoint() {}
        public SuppressionPoint(String packageName, int appState) {
            mAppState = appState;
            mPackageName = packageName;
        }
        public boolean equal (SuppressionPoint oldPoint) {
            if(this.mAppState == oldPoint.mAppState &&
                    this.mPackageName.equals(oldPoint.mPackageName))
                return true;
            else
                return false;
            }
    }

    public static class PolicyList {
        public int mUid;
        public int mUserId;
        public String mPackageName;
        public boolean mForceStopState;
        public RbConfiguration mConfig;
        public PolicyList() {}
        public PolicyList(int uid, int userId, String packageName,
                boolean forceStopState, RbConfiguration config) {
            mUid = uid;
            mUserId = userId;
            mPackageName = packageName;
            mForceStopState = forceStopState;
            mConfig = config;
        }
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(adj);
        dest.writeInt(keepRecentTaskNumner);
        dest.writeInt(keepNotificationAPPNumber);
        dest.writeInt(checkLocationServiceApp ? 1 : 0 );
        dest.writeInt(enableLauncherWidget ? 1 : 0 );
        dest.writeInt(enableRunningBooster ? 1 : 0 );
        dest.writeStringList(whiteList);
        dest.writeStringList(blackList);
        suppressPointListSize = suppressPoint.size();
        suppressAppStatus = new int[suppressPointListSize];
        int index=0;
        suppressPkg.clear();
        for (SuppressionPoint point:suppressPoint){
            suppressPkg.add(point.mPackageName);
            suppressAppStatus[index] = point.mAppState;
            index++;
        }
        dest.writeInt(suppressPointListSize);
        dest.writeStringList(suppressPkg);
        dest.writeIntArray(suppressAppStatus);
    }

    public RbConfiguration() {
        /**initial whitelist, blacklist and suppressPoint,
                *let RBS can decide whither APP modify the configuration
                */
        whiteList = new ArrayList<String>();
        whiteList.add(INITIAL);
        blackList = new ArrayList<String>();
        blackList.add(INITIAL);
        suppressPoint = new ArrayList<SuppressionPoint>();
        suppressPoint.add(DEAULT_STARTPOINT);
    }

    public RbConfiguration(Parcel in) {
        adj = in.readInt();
        keepRecentTaskNumner = in.readInt();
        keepNotificationAPPNumber = in.readInt();
        checkLocationServiceApp = in.readInt() == 1;
        enableLauncherWidget = in.readInt() == 1;
        enableRunningBooster = in.readInt() == 1;
        in.readStringList(whiteList);
        in.readStringList(blackList);
        suppressPointListSize = in.readInt();
        in.readStringList(suppressPkg);
        suppressAppStatus = new int[suppressPointListSize];
        in.readIntArray(suppressAppStatus);

        int index = 0;
        for (String point:suppressPkg) {
            SuppressionPoint tmpPoint = new SuppressionPoint();
            tmpPoint.mAppState = suppressAppStatus[index];
            tmpPoint.mPackageName = point;
            suppressPoint.add(tmpPoint);
            index++;
        }
    }

    /** Implement the Parcelable interface {@hide} */
    public int describeContents() {
        return 0;
    }

    public static final Creator<RbConfiguration> CREATOR = new Creator<RbConfiguration>() {
        public RbConfiguration createFromParcel(Parcel source) {
            return new RbConfiguration(source);
        }

        public RbConfiguration[] newArray(int size) {
            return new RbConfiguration[size];
        }
    };
}
