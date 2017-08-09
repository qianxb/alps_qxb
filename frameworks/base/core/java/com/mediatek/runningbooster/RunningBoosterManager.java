package com.mediatek.runningbooster;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;
import com.mediatek.runningbooster.RbConfiguration;
import com.mediatek.runningbooster.IRunningBoosterManager;
import java.util.List;

public class RunningBoosterManager {

    private final String TAG = "RunningBoosterManager";
    private Context mContext;
    private IRunningBoosterManager mRunningBoosterService;

    public RunningBoosterManager(Context context) {
        mContext = context;

        if (null == mRunningBoosterService) {
            mRunningBoosterService = IRunningBoosterManager.Stub.asInterface
                    (ServiceManager.getService("running_booster"));
            Slog.d(TAG, "Get RunningBoosterService");
        }
    }

   public void applyUserConfig(String packageName, RbConfiguration config)
            throws SecurityException {
        Slog.d(TAG, "applyUserConfig packageName=" + packageName + " config="+config);

        try {
            if(null != mRunningBoosterService) {
                mRunningBoosterService.applyUserConfig(packageName, config);
            }
        } catch (RemoteException e) {
            Slog.d(TAG, "applyUserConfig packageName RemoteException");
            e.printStackTrace();
        }
    }

    public String getAPIVersion() throws SecurityException {
        Slog.d(TAG, "[RunningBoosterManager] getAPIVersion");
        try {
            if(null != mRunningBoosterService) {
                return mRunningBoosterService.getAPIVersion();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<String> getPlatformWhiteList() throws SecurityException {
        Slog.d(TAG, "[RunningBoosterManager] getPlatformWhiteList");
        try {
            if(null != mRunningBoosterService) {
                return mRunningBoosterService.getPlatformWhiteList();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }
}