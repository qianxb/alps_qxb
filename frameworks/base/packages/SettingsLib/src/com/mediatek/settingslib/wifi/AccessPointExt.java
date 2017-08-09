package com.mediatek.settingslib.wifi;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.util.Log;

import com.mediatek.common.MPlugin;
import com.android.settingslib.R;
import com.mediatek.settingslib.ext.IWifiLibExt;
import com.mediatek.settingslib.ext.DefaultWifiLibExt;


public class AccessPointExt {
    private static final String TAG = "AccessPointExt";

    /* security type */
    public static final int SECURITY_WAPI_PSK = 4;
    public static final int SECURITY_WAPI_CERT = 5;
    public static IWifiLibExt sWifiLibExt;

    public AccessPointExt(Context context) {
         getWifiPlugin(context);
    }

    /**
     * add other security, like as wapi, wep
     * @param config
     * @return
     */
    public static int getSecurity(WifiConfiguration config) {
        /* support wapi psk/cert */
        if (config.allowedKeyManagement.get(KeyMgmt.WAPI_PSK)) {
            return SECURITY_WAPI_PSK;
        }
        if (config.allowedKeyManagement.get(KeyMgmt.WAPI_CERT)) {
            return SECURITY_WAPI_CERT;
        }
        return -1;
    }

    public static int getSecurity(ScanResult result) {
        if (result.capabilities.contains("WAPI-PSK")) {
            /*  WAPI_PSK */
            return SECURITY_WAPI_PSK;
        } else if (result.capabilities.contains("WAPI-CERT")) {
            /* WAPI_CERT */
            return SECURITY_WAPI_CERT;
        }
        return -1;
    }

    public String getSecurityString(int security, Context context) {
        switch(security) {
            case SECURITY_WAPI_PSK:
                /*return WAPI_PSK string */
                return context.getString(R.string.wifi_security_wapi_psk);
            case SECURITY_WAPI_CERT:
                /* return WAPI_CERT string */
                return context.getString(R.string.wifi_security_wapi_certificate);
            default:
        }
        return null;
    }

    /**
     * append reason to access point summary.
     * @param summary current summary
     * @param autoJoinStatus Access point's auto join status
     * @param connectFail the disabled fail string
     * @param disabled the generic fail string
     */
    public void appendApSummary(StringBuilder summary, int autoJoinStatus,
        String connectFail, String disabled) {
        sWifiLibExt.appendApSummary(summary, autoJoinStatus, connectFail, disabled);
    }

    /**
     * should check network capabilities.
     * @return default return true means need do the check.
     */
    public static IWifiLibExt getWifiPlugin(Context context) {
        if (sWifiLibExt == null) {
            sWifiLibExt = (IWifiLibExt) MPlugin.createInstance(
                         IWifiLibExt.class.getName(), context);
            if (sWifiLibExt == null) {
                sWifiLibExt = new DefaultWifiLibExt();
            }
        }
        return sWifiLibExt;
    }
}
