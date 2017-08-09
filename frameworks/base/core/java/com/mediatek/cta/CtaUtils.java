package com.mediatek.cta;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManagerNative;
import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.Manifest;
import android.os.Binder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.app.IAppOpsService;

/**
 *  @hide
 */
public class CtaUtils {

    private static final String TAG = "CtaUtils";

    private static final boolean FEATURE_SUPPORTED =
            SystemProperties.getInt("ro.mtk_mobile_management", 0) == 1;

    private static final String OS_PKG = "android";
    private static final String MTK_OS_PKG = "com.mediatek";

    private static IPackageManager sPackageManager;
    private static IAppOpsService sAppOpsService;

    public static boolean isCtaSupported() {
        boolean isDisabled =
                SystemProperties.getInt("persist.sys.mtk.disable.moms", 0) == 1;
        return FEATURE_SUPPORTED && !isDisabled;
    }

    public static boolean isEmailMmsSupported() {
        if (!isCtaSupported()) return false;
        return false;
    }

    public static boolean isCtaOnlyPermission(String perm) {
        if (!isCtaSupported()) return false;
        return CtaPermissions.CTA_ONLY_PERMISSIONS.contains(perm);
    }

    public static boolean isCtaMonitoredPerms(String perm) {
        if (!isCtaSupported()) return false;
        return CtaPermissions.CTA_MONITOR_PERMISSIONS.contains(perm);
    }

    public static boolean isCtaAddedPermGroup(String group) {
        if (!isCtaSupported()) return false;
        return CtaPermissions.CTA_ADDED_PERMISSION_GROUPS.contains(group);
    }

    public static String[] getCtaOnlyPermissions() {
        if (!isCtaSupported()) return null;
        return CtaPermissions.CTA_ONLY_PERMISSIONS.toArray(
                new String[CtaPermissions.CTA_ONLY_PERMISSIONS.size()]);
    }

    public static String[] getCtaAddedPermissionGroups() {
        if (!isCtaSupported()) return null;
        return CtaPermissions.CTA_ADDED_PERMISSION_GROUPS.toArray(
                new String[CtaPermissions.CTA_ADDED_PERMISSION_GROUPS.size()]);
    }

    private static String getCallingPkgName(int pid, int uid) {
        IActivityManager am = ActivityManagerNative.getDefault();
        List runningProcesses = null;
        try {
            runningProcesses = am.getRunningAppProcesses();
        } catch (RemoteException e) {
            return null;
        }
        Iterator iterator = runningProcesses.iterator();
        while (iterator.hasNext()) {
            RunningAppProcessInfo info = (RunningAppProcessInfo) iterator.next();
            try {
                if (info.uid == uid && info.pid == pid) {
                    return info.processName;
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static boolean enforceCheckPermission(final String permission, final String action) {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        String callingPackage = getCallingPkgName(pid, uid);
        Slog.d(TAG, "enforceCheckPermission callingPackage = " + callingPackage);
        return enforceCheckPermission(callingPackage, permission, action);
    }

    public static boolean enforceCheckPermission(final String pkgName, final String permission,
            final String action) {
        if (!isCtaSupported()) return true;
        if (sPackageManager == null) {
            sPackageManager = AppGlobals.getPackageManager();
        }
        if (sAppOpsService == null) {
            sAppOpsService = IAppOpsService.Stub.asInterface(
                    ServiceManager.getService(Context.APP_OPS_SERVICE));
        }
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        Slog.d(TAG, "enforceCheckPermission uid = " + uid + ", pid = " + pid +
               ", pkgName = " + pkgName + ", permission = " + permission);
        int appop = AppOpsManager.permissionToOpCode(permission);
        if (TextUtils.isEmpty(pkgName)) {
            Slog.w(TAG, "enforceCheckPermission callingPackage is null");
            return true;
        }
        if (!isCtaOnlyPermission(permission)) {
            Slog.w(TAG, "enforceCheckPermission(): not support for non-cta permission = "
                    + permission);
            return true;
        }
        try {
            boolean pmsAllowed = sPackageManager.checkUidPermission(permission, uid) ==
                    PERMISSION_GRANTED;
            boolean appopsAllowed = sAppOpsService.noteOperation(appop, uid, pkgName) ==
                    AppOpsManager.MODE_ALLOWED;
            Slog.d(TAG, "enforceCheckPermission pmsAllowed = " + pmsAllowed +
                    ", appopsAllowed = " + appopsAllowed);
            if (!pmsAllowed || !appopsAllowed) {
                throw new SecurityException("Permission Denial: " + action + " requires "
                        + permission);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "enforceCheckPermission RemoteException", e);
            return false;
        }
        return true;
    }

    public static boolean isPlatformPermission(String pkgName, String permName) {
        if (OS_PKG.equals(pkgName)) {
            return true;
        }
        if (isCtaSupported()
                && MTK_OS_PKG.equals(pkgName)
                && isCtaOnlyPermission(permName)) {
            return true;
        }
        return false;
    }

    public static boolean isPlatformPermissionGroup(String pkgName, String groupName) {
        if (OS_PKG.equals(pkgName)) {
            return true;
        }
        if (isCtaSupported()
                && MTK_OS_PKG.equals(pkgName)
                && isCtaAddedPermGroup(groupName)) {
            return true;
        }
        return false;
    }

}
