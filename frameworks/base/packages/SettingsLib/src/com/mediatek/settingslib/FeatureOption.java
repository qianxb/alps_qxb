package com.mediatek.settingslib;

import android.os.SystemProperties;

public class FeatureOption {
    /// M: Add for MTK new feature DUN profile. @ {
    public static final boolean MTK_Bluetooth_DUN = getValue("bt.profiles.dun.enabled");
    /// @ }

    // Important!!!  the SystemProperties key's length must less than 31 , or will have JE
    /* get the key's value*/
    private static boolean getValue(String key) {
        return SystemProperties.get(key).equals("1");
    }
}
