package com.mediatek.cta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.Manifest;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.mediatek.cta.CtaUtils;

/**
 *  @hide
 */
public class CtaPermissions {

    public static final ArrayMap<String, List<String>> MAP =
            new ArrayMap<String, List<String>>();
    static final Set<String> CTA_ONLY_PERMISSIONS = new ArraySet<>();
    static final Set<String> CTA_MONITOR_PERMISSIONS = new ArraySet<>();
    static final Set<String> CTA_ADDED_PERMISSION_GROUPS = new ArraySet<>();

    static {
        // init permission mapping between AOSP and CTA
        List<String> subs;
        subs = new ArrayList<String>();
        subs.add(com.mediatek.Manifest.permission.CTA_CONFERENCE_CALL);
        MAP.put(Manifest.permission.CALL_PHONE, subs);

        if (CtaUtils.isEmailMmsSupported()) {
            subs = new ArrayList<String>();
            subs.add(com.mediatek.Manifest.permission.CTA_SEND_EMAIL);
            subs.add(com.mediatek.Manifest.permission.CTA_SEND_MMS);
            MAP.put(Manifest.permission.INTERNET, subs);
        }

        subs = new ArrayList<String>();
        subs.add(com.mediatek.Manifest.permission.CTA_ENABLE_WIFI);
        MAP.put(Manifest.permission.CHANGE_WIFI_STATE, subs);

        subs = new ArrayList<String>();
        subs.add(com.mediatek.Manifest.permission.CTA_ENABLE_BT);
        MAP.put(Manifest.permission.BLUETOOTH_ADMIN, subs);

        for (String parentPerm: MAP.keySet()) {
            for (String subPerm : MAP.get(parentPerm)) {
                CTA_ONLY_PERMISSIONS.add(subPerm);
            }
        }

        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.READ_CALENDAR);
        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.WRITE_CALENDAR);
        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.CAMERA);
        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.READ_CONTACTS);
        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.WRITE_CONTACTS);
        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.ACCESS_FINE_LOCATION);
        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.RECORD_AUDIO);
        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.READ_PHONE_STATE);
        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.CALL_PHONE);
        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.READ_CALL_LOG);
        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.WRITE_CALL_LOG);
        CTA_MONITOR_PERMISSIONS.add(com.mediatek.Manifest.permission.CTA_CONFERENCE_CALL);
        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.SEND_SMS);
        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.RECEIVE_SMS);
        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.READ_SMS);
        CTA_MONITOR_PERMISSIONS.add(Manifest.permission.RECEIVE_MMS);
        CTA_MONITOR_PERMISSIONS.add(com.mediatek.Manifest.permission.CTA_ENABLE_WIFI);
        CTA_MONITOR_PERMISSIONS.add(com.mediatek.Manifest.permission.CTA_ENABLE_BT);
        if (CtaUtils.isEmailMmsSupported()) {
            CTA_MONITOR_PERMISSIONS.add(com.mediatek.Manifest.permission.CTA_SEND_EMAIL);
            CTA_MONITOR_PERMISSIONS.add(com.mediatek.Manifest.permission.CTA_SEND_MMS);
        }

        CTA_ADDED_PERMISSION_GROUPS.add(com.mediatek.Manifest.permission_group.WIFI);
        CTA_ADDED_PERMISSION_GROUPS.add(com.mediatek.Manifest.permission_group.BT);
        if (CtaUtils.isEmailMmsSupported()) {
            CTA_ADDED_PERMISSION_GROUPS.add(com.mediatek.Manifest.permission_group.EMAIL);
        }
    }

}
