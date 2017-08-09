package com.mediatek.runningbooster;

import com.mediatek.runningbooster.RbConfiguration;
/**
 * System API for talking with Running Booster APP.
 *
 */
interface IRunningBoosterManager {
    void applyUserConfig(in String packageName, in RbConfiguration config);
    String getAPIVersion();
    List<String> getPlatformWhiteList();
}