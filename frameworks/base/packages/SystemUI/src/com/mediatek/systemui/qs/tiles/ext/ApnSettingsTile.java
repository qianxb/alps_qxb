package com.mediatek.systemui.qs.tiles.ext;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTile.Host;
import com.android.systemui.qs.QSTile.Icon;
import com.mediatek.systemui.PluginManager;
import com.mediatek.systemui.statusbar.extcb.IconIdWrapper;
import com.mediatek.systemui.statusbar.util.SIMHelper;

/**
 * M: Customize the Apn Settings Tile.
 */
public class ApnSettingsTile extends QSTile<QSTile.State> {
    private static final String TAG = "ApnSettingsTile";
    private static final boolean DEBUG = true;

    private static final Intent APN_SETTINGS = new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.Settings$ApnSettingsActivity"));

    private boolean mListening;
    private final IconIdWrapper mEnableApnStateIconWrapper = new IconIdWrapper();
    private final IconIdWrapper mDisableApnStateIconWrapper = new IconIdWrapper();

    private String mApnStateLabel = "";
    private CharSequence mTileLabel;
    private boolean mApnSettingsEnabled = false;

    private final SubscriptionManager mSubscriptionManager;
    private final UserManager mUm;
    private boolean mIsWifiOnly;
    private boolean mIsAirplaneMode;

    /**
     * Constructs a new ApnSettingsTile with host.
     *
     * @param host A Host object
     */
    public ApnSettingsTile(Host host) {
        super(host);
        mSubscriptionManager = SubscriptionManager.from(mContext);
        mUm = (UserManager) mContext.getSystemService(Context.USER_SERVICE);

        final ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        mIsWifiOnly = (cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE) == false);

        updateState();
    }

    @Override
    public State newTileState() {
        return new BooleanState();
    }

    @Override
    public CharSequence getTileLabel() {
        mTileLabel = PluginManager.getQuickSettingsPlugin(mContext)
                .getTileLabel("apnsettings");
        return mTileLabel;
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public void setListening(boolean listening) {
        Log.d(TAG, "setListening(), listening = " + listening);
        if (mListening == listening) {
            return;
        }

        mListening = listening;
        if (listening) {
            final IntentFilter mIntentFilter = new IntentFilter();
            mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            mIntentFilter.addAction(TelephonyIntents.ACTION_EF_CSP_CONTENT_NOTIFY);
            mIntentFilter.addAction(Intent.ACTION_MSIM_MODE_CHANGED);
            mIntentFilter.addAction(TelephonyIntents.ACTION_MD_TYPE_CHANGE);
            mIntentFilter.addAction(TelephonyIntents.ACTION_LOCATED_PLMN_CHANGED);
            mIntentFilter.addAction(TelephonyIntents.ACTION_SET_PHONE_RAT_FAMILY_DONE);
            mIntentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE);
            mIntentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
            mContext.registerReceiver(mReceiver, mIntentFilter);
            TelephonyManager.getDefault().listen(
                    mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        } else {
            mContext.unregisterReceiver(mReceiver);
            TelephonyManager.getDefault().listen(
                    mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_PANEL;
    }

    @Override
    protected void handleLongClick() {
        handleClick();
    }

    @Override
    protected void handleClick() {
        updateState();
        Log.d(TAG, "handleClick(), mApnSettingsEnabled = " + mApnSettingsEnabled);
        if (mApnSettingsEnabled) {
            final int subId = SubscriptionManager.getDefaultDataSubscriptionId();
            APN_SETTINGS.putExtra("sub_id", subId);
            Log.d(TAG, "handleClick(), " + APN_SETTINGS);
            mHost.startActivityDismissingKeyguard(APN_SETTINGS);
        }
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        if (mApnSettingsEnabled) {
            state.icon = QsIconWrapper.get(
                    mEnableApnStateIconWrapper.getIconId(), mEnableApnStateIconWrapper);
        } else {
            state.icon = QsIconWrapper.get(
                    mDisableApnStateIconWrapper.getIconId(), mDisableApnStateIconWrapper);
        }
        state.label = mApnStateLabel;
        state.contentDescription = mApnStateLabel;
    }

    private final void updateState() {
        boolean enabled = false;
        mIsAirplaneMode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;

        // WirelessSettings & MobileNetworkSettings
        final boolean isSecondaryUser = (UserHandle.myUserId() != UserHandle.USER_OWNER) ||
                (ActivityManager.getCurrentUser() != UserHandle.USER_OWNER);
        final boolean isRestricted = mUm
                .hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS);
        // Disable it for secondary users, or wifi-only device, or the settings are restricted.
        if (mIsWifiOnly || isSecondaryUser || isRestricted) {
            enabled = false;
            if (DEBUG) {
                Log.d(TAG, "updateState(), isSecondaryUser = " + isSecondaryUser
                        + ", mIsWifiOnly = " + mIsWifiOnly + ", isRestricted = " + isRestricted);
            }
        } else {
            final int simNum = mSubscriptionManager.getActiveSubscriptionInfoCount();
            final int callState = TelephonyManager.getDefault().getCallState();
            final boolean isIdle = callState == TelephonyManager.CALL_STATE_IDLE;
            if (!mIsAirplaneMode && simNum > 0 && isIdle && !isAllRadioOff()) {
                enabled = true;
            }

            if (DEBUG) {
                Log.d(TAG, "updateState(), mIsAirplaneMode = " + mIsAirplaneMode
                        + ", simNum = " + simNum + ", callstate = " + callState
                        + ", isIdle = " + isIdle);
            }
        }

        mApnSettingsEnabled = enabled;

        if (DEBUG) {
            Log.d(TAG, "updateState(), mApnSettingsEnabled = " + mApnSettingsEnabled);
        }

        updateStateResources();

        refreshState();
    }

    private final void updateStateResources() {
        if (mApnSettingsEnabled) {
            mApnStateLabel = PluginManager.getQuickSettingsPlugin(mContext)
                    .customizeApnSettingsTile(mApnSettingsEnabled,
                            mEnableApnStateIconWrapper, mApnStateLabel);
        } else {
            mApnStateLabel = PluginManager.getQuickSettingsPlugin(mContext)
                    .customizeApnSettingsTile(mApnSettingsEnabled,
                            mDisableApnStateIconWrapper, mApnStateLabel);
        }
    }

    private boolean isAllRadioOff() {
        boolean result = true;
        final int[] subIds = mSubscriptionManager.getActiveSubscriptionIdList();
        if (subIds != null && subIds.length > 0) {
            for (int subId : subIds) {
                if (SIMHelper.isRadioOn(subId)) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (DEBUG) {
                Log.d(TAG, "onReceive(), action: " + action);
            }

            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                final boolean enabled = intent.getBooleanExtra("state", false);
                Log.d(TAG, "onReceive(), airline mode changed: state is " + enabled);
                updateState();
            } else if (action.equals(TelephonyIntents.ACTION_EF_CSP_CONTENT_NOTIFY)
                    || action.equals(Intent.ACTION_MSIM_MODE_CHANGED)
                    || action.equals(TelephonyIntents.ACTION_MD_TYPE_CHANGE)
                    || action.equals(TelephonyIntents.ACTION_LOCATED_PLMN_CHANGED)
                    || action.equals(TelephonyIntents.ACTION_SET_PHONE_RAT_FAMILY_DONE)
                    || action.equals(TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE)
                    || action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
                updateState();
            }
        }
    };

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (DEBUG) {
                Log.d(TAG, "onCallStateChanged call state is " + state);
            }
            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE:
                    updateState();
                    break;
                default:
                    break;
            }
        }
    };
}
