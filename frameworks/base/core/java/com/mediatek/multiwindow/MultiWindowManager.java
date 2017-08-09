package com.mediatek.multiwindow;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.SystemProperties;
import android.util.DisplayMetrics;
import android.util.Log;
import com.android.internal.R;

/**
  * M: BMW
  * MultiWindowManager.
  *
  * @hide
  */
public class MultiWindowManager {
    private static final String TAG = "MultiWindowManager";
    private static final boolean FEATURE_SUPPORTED = SystemProperties.get(
            "ro.mtk_multiwindow").equals("1");
    private static final String USER_LOAD = "user";
    public static boolean DEBUG = !USER_LOAD.equals(SystemProperties.get(
            "ro.build.type"));
    private static final float WIDTH_OFFSET = 5f;
    private static final float HEIGHT_OFFSET = 10f;
    private static final float WIDTH_OFFSET_HUGE = 1.5f;
    private static final float HEIGHT_OFFSET_HUGE = 5f;
    private static final int ROWS = 6;
    private static final int COLS = 3;
    private static final int LCM_CONFIGS[][] = {
            {800,1280,160},
            {800,1280,213},
            {600,1024,160},
            {600,1024,213},
            {1200,1920,240},
            {1200,1920,320}
    };

    private static final int TASK_POSITIONS[][] = {
            {672,1016,480},
            {672,939,432},
            {504,768,355},
            {558,748,337},
            {1008,1524,728},
            {1008,1408,632}
    };

    private static final int TASK_POSITIONS_LAND[][] = {
            {672,536,249},
            {672,459,252},
            {504,344,244},
            {558,462,252},
            {1008,804,373},
            {1008,688,295}
    };

    /**
     * To check whether Multi-Window feature is supported or not.
     *
     * @return true if supported.
     * @internal
     */
    public static boolean isSupported() {
        // / We add a system property for turning off/on Multi-Window dynamically.
        boolean disableMultiWindow = SystemProperties.getInt(
                "persist.sys.mtk.disable.mw", 0) == 1;
        return FEATURE_SUPPORTED && !disableMultiWindow;
    }

    /**
     * computeLaunchBounds.
     *
     * @internal
     */
    public static Rect computeLaunchBounds(Context context, boolean isFirstFreeformWindow) {
        final Resources res = context.getResources();
        int statusBarHeight =
            res.getDimensionPixelSize(R.dimen.status_bar_height);
        boolean isLandscape =
            res.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        DisplayMetrics dm = res.getDisplayMetrics();
        if (DEBUG) {
            Log.d(TAG, "computeLaunchBounds, dm = " + dm);
        }

        Rect bounds = null;
        bounds = computeLaunchBounds(dm, statusBarHeight, isFirstFreeformWindow, isLandscape);
        if (bounds != null) {
            if (DEBUG) {
                Log.d(TAG, "computeLaunchBounds by query common configs, bounds = " + bounds);
            }
            return bounds;
        }

        int width = dm.widthPixels;
        int height = dm.heightPixels;
        float widthOffSet = WIDTH_OFFSET;
        float heightOffSet = HEIGHT_OFFSET;
        if (dm.densityDpi >= 240) {
            widthOffSet = WIDTH_OFFSET_HUGE;
            heightOffSet = HEIGHT_OFFSET_HUGE;
        }
        int firstFreeformWindowRight = (int)(width - statusBarHeight * widthOffSet);
        int firstFreeformWindowBottom = height - statusBarHeight * 10;
        int secondFreeformWindowRight = firstFreeformWindowRight;
        int secondFreeformWindowBottom = (firstFreeformWindowBottom - statusBarHeight) / 2;
        firstFreeformWindowBottom += statusBarHeight;

        int firstFreeformWindowRightLand = width / 2 + statusBarHeight;
        int firstFreeformWindowBottomLand = (int)(height - statusBarHeight * heightOffSet);
        int secondFreeformWindowRightLand = firstFreeformWindowRightLand;
        int secondFreeformWindowBottomLand = (firstFreeformWindowBottomLand - statusBarHeight) / 2;
        if (isFirstFreeformWindow) {
            if (isLandscape) {
                bounds = new Rect(0, statusBarHeight, firstFreeformWindowRightLand,
                                    firstFreeformWindowBottomLand);
            } else {
                bounds = new Rect(0, statusBarHeight, firstFreeformWindowRight,
                                    firstFreeformWindowBottom);
            }
        } else {
            if (isLandscape) {
                bounds = new Rect(0, statusBarHeight, secondFreeformWindowRightLand,
                                    secondFreeformWindowBottomLand);
            } else {
                bounds = new Rect(0, statusBarHeight, secondFreeformWindowRight,
                                    secondFreeformWindowBottom);
            }
        }
        if (DEBUG) {
            Log.d(TAG, "computeLaunchBounds, bounds = " + bounds + ", isLandscape = "
                + isLandscape + ", isFirstFreeformWindow = " + isFirstFreeformWindow
                + ", width = " + width + ", height = " + height);
        }
        return bounds;
    }

    /**
     * computeLaunchBounds.
     *
     * @internal
     */
    private static Rect computeLaunchBounds(DisplayMetrics dm, int statusBarHeight,
                          boolean isFirstFreeformWindow, boolean isLandscape) {
        int width = dm.widthPixels;
        int height = dm.heightPixels + 2 * statusBarHeight;
        if (isLandscape) {
            width = dm.heightPixels;
            height = dm.widthPixels + 2 * statusBarHeight;
        }
        int density = dm.densityDpi;
        Rect bounds = null;
        for(int i = 0; i < ROWS; i++) {
            if (LCM_CONFIGS[i][0] == width && LCM_CONFIGS[i][1] == height
                           && LCM_CONFIGS[i][2] == density) {
                int firstFreeformWindowRight = TASK_POSITIONS[i][0];
                int firstFreeformWindowBottom = TASK_POSITIONS[i][1];
                int secondFreeformWindowRight = firstFreeformWindowRight;
                int secondFreeformWindowBottom = TASK_POSITIONS[i][2];

                int firstFreeformWindowRightLand = TASK_POSITIONS_LAND[i][0];
                int firstFreeformWindowBottomLand = TASK_POSITIONS_LAND[i][1];
                int secondFreeformWindowRightLand = firstFreeformWindowRightLand;
                int secondFreeformWindowBottomLand = TASK_POSITIONS_LAND[i][2];
                if (isFirstFreeformWindow) {
                    if (isLandscape) {
                        bounds = new Rect(0, statusBarHeight, firstFreeformWindowRightLand,
                                    firstFreeformWindowBottomLand);
                    } else {
                        bounds = new Rect(0, statusBarHeight, firstFreeformWindowRight,
                                    firstFreeformWindowBottom);
                    }
                } else {
                    if (isLandscape) {
                        bounds = new Rect(0, statusBarHeight, secondFreeformWindowRightLand,
                                    secondFreeformWindowBottomLand);
                    } else {
                        bounds = new Rect(0, statusBarHeight, secondFreeformWindowRight,
                                    secondFreeformWindowBottom);
                    }
                }
                return bounds;
            }
        }
        return bounds;
    }
}
