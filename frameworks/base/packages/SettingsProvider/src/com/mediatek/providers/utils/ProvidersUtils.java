package com.mediatek.providers.utils;

import android.content.Context;
import android.content.res.Resources;
import android.database.sqlite.SQLiteStatement;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.os.SystemProperties;
import android.provider.Settings;

import com.android.ims.ImsConfig;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyProperties;
import com.android.providers.settings.R;

import com.mediatek.common.MPlugin;
import com.mediatek.providers.settings.ext.DefaultDatabaseHelperExt;
import com.mediatek.providers.settings.ext.IDatabaseHelperExt;

public class ProvidersUtils {
    private static final String TAG = "ProvidersUtils";
    private IDatabaseHelperExt mExt;
    private Context mContext;
    private Resources mRes;

    public ProvidersUtils(Context context) {
        mContext = context;
        mRes = mContext.getResources();
        initDatabaseHelperPlgin(mContext);
    }

    private void initDatabaseHelperPlgin(Context context) {
        mExt = (IDatabaseHelperExt) MPlugin.createInstance(
                IDatabaseHelperExt.class.getName(), context);
        if (mExt == null) {
            mExt = new DefaultDatabaseHelperExt(context);
        }
    }

    public void loadCustomSystemSettings(SQLiteStatement stmt) {
        // M: Add for Streaming
        loadStringSetting(stmt, Settings.System.MTK_RTSP_NAME, R.string.mtk_rtsp_name);
        loadStringSetting(stmt, Settings.System.MTK_RTSP_TO_PROXY,
                R.string.mtk_rtsp_to_proxy);
        loadStringSetting(stmt, Settings.System.MTK_RTSP_NETINFO,
                R.string.mtk_rtsp_netinfo);
        loadStringSetting(stmt, Settings.System.MTK_RTSP_TO_NAPID,
                R.string.mtk_rtsp_to_napid);
        loadStringSetting(stmt, Settings.System.MTK_RTSP_MAX_UDP_PORT,
                R.string.mtk_rtsp_max_udp_port);
        loadStringSetting(stmt, Settings.System.MTK_RTSP_MIN_UDP_PORT,
                R.string.mtk_rtsp_min_udp_port);

        // M: Add for HDMI
        loadIntegerSetting(stmt, Settings.System.HDMI_ENABLE_STATUS,
                R.integer.def_hdmi_enable_status);
        loadIntegerSetting(stmt, Settings.System.HDMI_VIDEO_RESOLUTION,
                R.integer.def_hdmi_video_resolution);
        loadIntegerSetting(stmt, Settings.System.HDMI_VIDEO_SCALE,
                R.integer.def_hdmi_video_scale);
        loadIntegerSetting(stmt, Settings.System.HDMI_COLOR_SPACE,
                R.integer.def_hdmi_color_space);
        loadIntegerSetting(stmt, Settings.System.HDMI_DEEP_COLOR,
                R.integer.def_hdmi_deep_color);
        loadIntegerSetting(stmt, Settings.System.HDMI_CABLE_PLUGGED,
                R.integer.def_hdmi_cable_plugged);

        // M: Add for enable/disable ANR mechanism from ADB command
        loadSetting(stmt, Settings.System.ANR_DEBUGGING_MECHANISM, 1);
        loadSetting(stmt, Settings.System.ANR_DEBUGGING_MECHANISM_STATUS, 0);

        // M: Add for Voice-wake-up
        boolean isSupport = SystemProperties.getBoolean("ro.mtk_voice_unlock_support", false);
        int defResId = isSupport ? R.integer.def_voice_unlock_mode
                : R.integer.def_voice_wakeup_mode;
        loadIntegerSetting(stmt, Settings.System.VOICE_WAKEUP_MODE, defResId);

        loadBooleanSetting(stmt, Settings.System.IPO_SETTING, R.bool.def_ipo_setting);

        loadIntegerSetting(stmt, Settings.System.WIFI_SELECT_SSID_TYPE,
                R.integer.wifi_select_ssid_type);

        loadIntegerSetting(stmt, Settings.System.VOICE_CALL_REJECT_MODE,
                R.integer.def_voice_call_reject_mode);

        loadIntegerSetting(stmt, Settings.System.IVSR_SETTING, R.integer.def_ivsr_setting);

        loadIntegerSetting(stmt, Settings.System.TETHER_IPV6_FEATURE,
                R.integer.def_tether_ipv6_feature);

        loadIntegerSetting(stmt, Settings.System.CT_TIME_DISPLAY_MODE,
                R.integer.def_ct_time_display_mode);

        loadSetting(stmt, Settings.System.DTMF_TONE_WHEN_DIALING, 1);

        loadSetting(stmt, Settings.System.GPRS_CONNECTION_SETTING,
                Settings.System.GPRS_CONNECTION_SETTING_DEFAULT);

        loadIntegerSetting(stmt, Settings.System.BG_POWER_SAVING_ENABLE,
                R.integer.def_bg_power_saving);
    }

    public void loadCustomGlobalSettings(SQLiteStatement stmt) {
        loadBooleanSetting(stmt, Settings.Global.AUTO_TIME_GPS, R.bool.def_auto_time_gps);

        loadSetting(
                stmt,
                Settings.Global.TELEPHONY_MISC_FEATURE_CONFIG,
                getIntegerValue(Settings.Global.TELEPHONY_MISC_FEATURE_CONFIG,
                        R.integer.def_telephony_misc_feature_config));

        loadSetting(
                    stmt,
                    Settings.Global.INSTALL_NON_MARKET_APPS,
                    getBooleanValue(Settings.Global.INSTALL_NON_MARKET_APPS,
                             R.bool.def_install_non_market_apps));

        loadSetting(
                stmt,
                Settings.Global.WFC_IMS_ENABLED,
                getValue(Settings.Global.WFC_IMS_ENABLED,
                        ImsConfig.FeatureValueConstants.OFF));

        boolean isSupport = SystemProperties.getBoolean("persist.mtk_wfc_support", false);
        loadSetting(
                stmt,
                Settings.Global.WFC_IMS_MODE,
                isSupport ? getValue(Settings.Global.WFC_IMS_MODE,
                        ImsConfig.WfcModeFeatureValueConstants.WIFI_PREFERRED) :
                        Integer.toString(ImsConfig.WfcModeFeatureValueConstants.CELLULAR_ONLY));

        // M: Add for SIM-Mode
        String mSimConfig = SystemProperties.get(TelephonyProperties.PROPERTY_MULTI_SIM_CONFIG);
        int defResId = R.integer.def_single_sim_mode;
        if (mSimConfig.equals("dsds") || mSimConfig.equals("dsda")) {
            defResId = R.integer.def_dual_sim_mode;
        } else if (mSimConfig.equals("tsts")) {
            defResId = R.integer.def_triple_sim_mode;
        } else if (mSimConfig.equals("fsfs")) {
            defResId = R.integer.def_four_sim_mode;
        }
        loadIntegerSetting(stmt, Settings.Global.MSIM_MODE_SETTING, defResId);
    }

    public String upgradeNameForSvlteIfNeeded(String name) {
        String checkString = Settings.Global.LTE_ON_CDMA_RAT_MODE;
        int checkLength = checkString.length();
        if (name.length() > checkLength &&
                checkString.equals(name.substring(0, checkLength))) {
            return Settings.Global.PREFERRED_NETWORK_MODE + name.substring(checkLength);
        } else {
            return null;
        }
    }

    public String getSvlteUpgradeValue(String oldValue) {
        switch(Integer.parseInt(oldValue)) {
            case 0://TelephonyManagerEx.SVLTE_RAT_MODE_4G
                return String.valueOf(Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA);
            case 1://TelephonyManagerEx.SVLTE_RAT_MODE_3G
                return String.valueOf(Phone.NT_MODE_GLOBAL);
            case 2://TelephonyManagerEx.SVLTE_RAT_MODE_4G_DATA_ONLY
                return String.valueOf(Phone.NT_MODE_LTE_TDD_ONLY);
            default:
                return null;
        }
    }

    private void loadSetting(SQLiteStatement stmt, String key, Object value) {
        stmt.bindString(1, key);
        stmt.bindString(2, value.toString());
        stmt.execute();
    }

    private void loadStringSetting(SQLiteStatement stmt, String key, int resid) {
        loadSetting(stmt, key, mRes.getString(resid));
    }

    private void loadBooleanSetting(SQLiteStatement stmt, String key, int resid) {
        loadSetting(stmt, key, mRes.getBoolean(resid) ? "1" : "0");
    }

    private void loadIntegerSetting(SQLiteStatement stmt, String key, int resid) {
        loadSetting(stmt, key, Integer.toString(mRes.getInteger(resid)));
    }

    private void loadFractionSetting(SQLiteStatement stmt, String key, int resid, int base) {
        loadSetting(stmt, key, Float.toString(mRes.getFraction(resid, base, base)));
    }

    public String getBooleanValue(String name, int resId) {
        String defaultValue = mRes.getBoolean(resId) ? "1" : "0";
        return mExt.getResBoolean(mContext, name, defaultValue);
    }

    public String getStringValue(String name, int resId) {
        return mExt.getResStr(mContext, name, mRes.getString(resId));
    }

    public String getIntegerValue(String name, int resId) {
        String defaultValue = Integer.toString(mRes.getInteger(resId));
        return mExt.getResInteger(mContext, name, defaultValue);
    }

    public String getValue(String name, int defaultValue) {
        return mExt.getResInteger(mContext, name, Integer.toString(defaultValue));
    }
}
