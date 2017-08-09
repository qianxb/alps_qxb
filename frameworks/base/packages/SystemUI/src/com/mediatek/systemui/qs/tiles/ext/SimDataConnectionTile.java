package com.mediatek.systemui.qs.tiles.ext;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.MeasureSpec;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTile.Icon;
import com.android.systemui.qs.QSIconView;

import com.mediatek.systemui.ext.IQuickSettingsPlugin;
import com.mediatek.systemui.PluginManager;
import com.mediatek.systemui.statusbar.extcb.IconIdWrapper;
import com.mediatek.systemui.statusbar.util.SIMHelper;

import java.util.List;

/**
 * M: Customize the sim switching data connection for Operator.
 */
public class SimDataConnectionTile extends QSTile<QSTile.BooleanState> {
    private static final String TAG = "SimDataConnectionTile";
    private CharSequence mTileLabel;

    private IconIdWrapper mSimConnectionIconWrapperArray[] =
                        new IconIdWrapper[SIMConnState.values().length];

    private SimDataSwitchStateMachine mSimDataSwitchStateMachine;

    private boolean mListening;

    /**
     * constructor.
     * @param host The QSTileHost.
     */
    public SimDataConnectionTile(Host host) {
        super(host);
        init();
    }

    @Override
    public CharSequence getTileLabel() {
        mTileLabel = PluginManager.getQuickSettingsPlugin(mContext)
                .getTileLabel("simdataconnection");
        return mTileLabel;
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    private void init() {
        for (int i = 0; i < mSimConnectionIconWrapperArray.length; i++) {
            mSimConnectionIconWrapperArray[i] = new IconIdWrapper();
        }
        mSimDataSwitchStateMachine = new SimDataSwitchStateMachine();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleLongClick() {
        handleClick();
    }

    @Override
    protected void handleClick() {
        if (mSimDataSwitchStateMachine.isClickable()) {
            mSimDataSwitchStateMachine.toggleState(mContext);
        }
        refreshState();
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            mSimDataSwitchStateMachine.registerReceiver();
        } else {
            mSimDataSwitchStateMachine.unregisterReceiver();
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_PANEL;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        int simConnState = mSimDataSwitchStateMachine.getCurrentSimConnState().ordinal();
        IQuickSettingsPlugin quickSettingsPlugin = PluginManager
                .getQuickSettingsPlugin(mContext);
        quickSettingsPlugin.customizeSimDataConnectionTile(simConnState,
                                                    mSimConnectionIconWrapperArray[simConnState]);
        state.icon = QsIconWrapper.get(mSimConnectionIconWrapperArray[simConnState].getIconId(),
                mSimConnectionIconWrapperArray[simConnState]);
        state.label =  quickSettingsPlugin.getTileLabel("simdataconnection");
    }

    /**
     * The Sim data connection quick settiings tile state.
     */
    public enum SIMConnState {
        /// M: Add for op09, SIM1_E_D means SIM1 open, the other sim is enable, data disable. @ {
        SIM1_E_D,
        SIM1_E_E,
        SIM1_D_D,
        SIM1_D_E,
        SIM2_E_D,
        SIM2_E_E,
        SIM2_D_D,
        SIM2_D_E,
        NO_SIM,
        /// @}
        /// M: Add for op09, SIM1_E_F means SIM1 open, the other sim is enable, sim1 radio off. @ {
        SIM1_E_F,
        SIM1_D_F,
        SIM2_E_F,
        SIM2_D_F
        /// @}
    };

    /**
     * The Sim data connection tile state machine.
     */
    private class SimDataSwitchStateMachine {
        private static final String TRANSACTION_START = "com.android.mms.transaction.START";
        private static final String TRANSACTION_STOP = "com.android.mms.transaction.STOP";
        private static final int EVENT_SWITCH_TIME_OUT = 2000;
        private static final int SWITCH_TIME_OUT_LENGTH = 30000;

        private SIMConnState mCurrentSimConnState = SIMConnState.NO_SIM;
        protected boolean mIsUserSwitching;
        private boolean mIsAirlineMode;

        boolean mSimConnStateTrackerReady;
        boolean mMmsOngoing;
        TelephonyManager mTelephonyManager;
        private PhoneStateListener[] mPhoneStateListener;
        private int mSlotCount = 0;

        public SIMConnState getCurrentSimConnState() {
            return mCurrentSimConnState;
        }

        public SimDataSwitchStateMachine() {
            mTelephonyManager = (TelephonyManager) mContext
                    .getSystemService(Context.TELEPHONY_SERVICE);

            mSlotCount = SIMHelper.getSlotCount();
            mPhoneStateListener = new PhoneStateListener[mSlotCount];
        }

        private void registerReceiver() {
            IntentFilter simIntentFilter = new IntentFilter();
            simIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            simIntentFilter.addAction(TRANSACTION_START);
            simIntentFilter.addAction(TRANSACTION_STOP);
            simIntentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE);
            simIntentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
            simIntentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            simIntentFilter.addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
            mContext.registerReceiver(mSimStateIntentReceiver, simIntentFilter);
        }

        private void unregisterReceiver() {
            mContext.unregisterReceiver(mSimStateIntentReceiver);
        }

        private void addConnTile() {
            mSimConnStateTrackerReady = true;
        }

        private void updateSimConnTile() {
            onActualStateChange(mContext, null);
            refreshState();
        }

        public void refresh() {
            onActualStateChange(mContext, null);
            setUserSwitching(false);
        }

        public void onActualStateChange(Context context, Intent intent) {
            List<SubscriptionInfo> infos =
                SubscriptionManager.from(context).getActiveSubscriptionInfoList();

            boolean sim1Enable = isSimEnable(infos, PhoneConstants.SIM_ID_1);
            boolean sim2Enable = isSimEnable(infos, PhoneConstants.SIM_ID_2);

            boolean sim1Conn = false;
            boolean sim2Conn = false;

            int dataConnectionId = SubscriptionManager.getDefaultDataSubscriptionId();
            if (SubscriptionManager.getSlotId(dataConnectionId) == PhoneConstants.SIM_ID_1) {
                sim1Conn = true;
                sim2Conn = false;
            } else if (SubscriptionManager.getSlotId(dataConnectionId) == PhoneConstants.SIM_ID_2) {
                sim1Conn = false;
                sim2Conn = true;
            }

            Log.d(TAG, "SimConnStateTracker onActualStateChange sim1Enable = " + sim1Enable
                    + ", sim2Enable = " + sim2Enable);
            if (sim1Enable || sim2Enable) {
                boolean dataConnected = isDataConnected();

                Log.d(TAG, "onActualStateChange, dataConnected = " + dataConnected
                        + ", sim1Enable = " + sim1Enable + ", sim2Enable = " + sim2Enable
                        + ", sim1Conn = " + sim1Conn + ", sim2Conn = " + sim2Conn);
                if (dataConnected) {
                    if (sim1Enable && sim2Enable) {
                        if (sim1Conn) {
                            mCurrentSimConnState = SIMConnState.SIM1_E_E;
                        } else {
                            mCurrentSimConnState = SIMConnState.SIM2_E_E;
                        }
                    } else if (!sim1Enable && sim2Enable) {
                        if (isSimInsertedWithUnAvaliable(infos, PhoneConstants.SIM_ID_1)
                                && sim1Conn) {
                            mCurrentSimConnState = SIMConnState.SIM1_E_F;
                        } else {
                            mCurrentSimConnState = SIMConnState.SIM2_D_E;
                        }
                    } else if (sim1Enable && !sim2Enable) {
                        if (isSimInsertedWithUnAvaliable(infos, PhoneConstants.SIM_ID_2)
                                && sim2Conn) {
                            mCurrentSimConnState = SIMConnState.SIM2_E_F;
                        } else {
                            mCurrentSimConnState = SIMConnState.SIM1_D_E;
                        }
                    }
                } else {
                    if (sim1Enable && sim2Enable) {
                        if (sim1Conn) {
                            mCurrentSimConnState = SIMConnState.SIM1_E_D;
                        } else {
                            mCurrentSimConnState = SIMConnState.SIM2_E_D;
                        }
                    } else if (!sim1Enable && sim2Enable) {
                        if (isSimInsertedWithUnAvaliable(infos, PhoneConstants.SIM_ID_1)
                                && sim1Conn) {
                            mCurrentSimConnState = SIMConnState.SIM1_E_F;
                        } else {
                            mCurrentSimConnState = SIMConnState.SIM2_D_D;
                        }
                    } else if (sim1Enable && !sim2Enable) {
                        if (isSimInsertedWithUnAvaliable(infos, PhoneConstants.SIM_ID_2)
                                && sim2Conn) {
                            mCurrentSimConnState = SIMConnState.SIM2_E_F;
                        } else {
                            mCurrentSimConnState = SIMConnState.SIM1_D_D;
                        }
                    }
                }
            } else {
                if (isSimInsertedWithUnAvaliable(infos, PhoneConstants.SIM_ID_1)
                        && sim1Conn) {
                    mCurrentSimConnState = SIMConnState.SIM1_D_F;
                } else if (isSimInsertedWithUnAvaliable(infos, PhoneConstants.SIM_ID_2)
                        && sim2Conn) {
                    mCurrentSimConnState = SIMConnState.SIM2_D_F;
                } else {
                    mCurrentSimConnState = SIMConnState.NO_SIM;
                }
            }
            setUserSwitching(false);
        }

        private boolean isSimEnable(List<SubscriptionInfo> infos, int slotId) {
            return isSimInsertedBySlot(infos, slotId)
                    && !isAirplaneMode() && isRadioOn(slotId)
                    && !isSimLocked(slotId);
        }

        private boolean isSimInsertedWithUnAvaliable(
                            List<SubscriptionInfo> infos, int slotId) {
            return isSimInsertedBySlot(infos, slotId)
                    && (!isRadioOn(slotId) || isAirplaneMode() || isSimLocked(slotId));
        }

        private boolean isRadioOn(int slotId) {
            int subId1 = SIMHelper.getFirstSubInSlot(slotId);
            return SIMHelper.isRadioOn(subId1);
        }

        private boolean isSimLocked(int slotId) {
            int simState = TelephonyManager.getDefault().getSimState(slotId);
            boolean bSimLocked = simState == TelephonyManager.SIM_STATE_PIN_REQUIRED
                    || simState == TelephonyManager.SIM_STATE_PUK_REQUIRED
                    || simState == TelephonyManager.SIM_STATE_NETWORK_LOCKED;
            Log.d(TAG, "isSimLocked, slotId=" + slotId + " simState=" + simState
                    + " bSimLocked= " + bSimLocked);
            return bSimLocked;
        }

        public void toggleState(Context context) {
            enterNextState(mCurrentSimConnState);
        }

        private void enterNextState(SIMConnState state) {
            Log.d(TAG, "enterNextState state is " + state);
            switch (state) {
            case NO_SIM:
            case SIM1_D_D:
            case SIM1_D_E:
            case SIM2_D_D:
            case SIM2_D_E:
            case SIM1_D_F:
            case SIM2_D_F:
                Log.d(TAG, "No Sim or one Sim do nothing!");
                break;
            case SIM1_E_D:
                Log.d(TAG, "Try to switch from Sim1 to Sim2! mSimCurrentCurrentState="
                        + mCurrentSimConnState);
                mCurrentSimConnState = SIMConnState.SIM2_E_D;
                switchDataDefaultSIM(PhoneConstants.SIM_ID_2);
                break;
            case SIM1_E_E:
                Log.d(TAG, "Try to switch from Sim1 to Sim2! mSimCurrentCurrentState="
                        + mCurrentSimConnState);
                mCurrentSimConnState = SIMConnState.SIM2_E_E;
                switchDataDefaultSIM(PhoneConstants.SIM_ID_2);
                break;
            case SIM2_E_D:
                Log.d(TAG, "Try to switch from Sim2 to Sim1! mSimCurrentCurrentState="
                        + mCurrentSimConnState);
                mCurrentSimConnState = SIMConnState.SIM1_E_D;
                switchDataDefaultSIM(PhoneConstants.SIM_ID_1);
                break;
            case SIM2_E_E:
                Log.d(TAG, "Try to switch from Sim2 to Sim1! mSimCurrentCurrentState="
                        + mCurrentSimConnState);
                mCurrentSimConnState = SIMConnState.SIM1_E_E;
                switchDataDefaultSIM(PhoneConstants.SIM_ID_1);
                break;
            case SIM1_E_F:
                Log.d(TAG, "Try to switch from Sim1 to Sim2! mSimCurrentCurrentState="
                        + mCurrentSimConnState);
                switchDataDefaultSIM(PhoneConstants.SIM_ID_2);
                break;
            case SIM2_E_F:
                Log.d(TAG, "Try to switch from Sim2 to Sim1! mSimCurrentCurrentState="
                        + mCurrentSimConnState);
                switchDataDefaultSIM(PhoneConstants.SIM_ID_1);
                break;
                default:
                    break;
            }
        }

        private void switchDataDefaultSIM(int slotId) {
            if (!isWifiOnlyDevice()) {
                setUserSwitching(true);

                handleDataConnectionChange(slotId);
            }
        }

        private void handleDataConnectionChange(int newSlot) {
            Log.d(TAG, "handleDataConnectionChange, newSlot=" + newSlot);
            if (SubscriptionManager.getSlotId(SubscriptionManager.getDefaultDataSubscriptionId())
                    != newSlot) {
                mDataTimerHandler.sendEmptyMessageDelayed(EVENT_SWITCH_TIME_OUT,
                        SWITCH_TIME_OUT_LENGTH);
                List<SubscriptionInfo> si =  SubscriptionManager.from(mContext)
                        .getActiveSubscriptionInfoList();
                if (si != null && si.size() > 0) {
                    int subId;
                    boolean dataEnabled = mTelephonyManager.getDataEnabled();
                    for (int i = 0; i < si.size(); i++) {
                        SubscriptionInfo subInfo = si.get(i);
                        subId = subInfo.getSubscriptionId();
                        if (newSlot == subInfo.getSimSlotIndex()) {
                            Log.d(TAG, "handleDataConnectionChange. newSlot = "
                                    + newSlot + " subId = " + subId);
                            SubscriptionManager.from(mContext).setDefaultDataSubId(subId);
                            if (dataEnabled) {
                                mTelephonyManager.setDataEnabled(subId, true);
                            }
                        } else {
                            if (dataEnabled) {
                                mTelephonyManager.setDataEnabled(subId, false);
                            }
                        }
                    }
                }
            }
        }

        /// M: Add for op09 SIM data connection switching.
        private Handler mDataTimerHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                int simFrom = msg.arg1;
                int simTo = msg.arg2;

                switch (msg.what) {
                case EVENT_SWITCH_TIME_OUT:
                    Log.d(TAG, "switching time out..... switch from " + simFrom + " to " + simTo);
                    /// M: only apply if NOT wifi-only device @{
                    if (!isWifiOnlyDevice()) {
                        refresh();
                    }
                    /// M: }@
                    break;
                default:
                    break;
                }
            }
        };

        //Network Selection And Settings Broadcast receive to determine if there is SIM State
        //Change.
        private BroadcastReceiver mSimStateIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d(TAG, "onReceive action is " + action);
                if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                    updateSimConnTile();
                } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                    boolean enabled = intent.getBooleanExtra("state", false);
                    Log.d(TAG, "airline mode changed: state is " + enabled);
                    if (mSimConnStateTrackerReady) {
                        setAirplaneMode(enabled);
                    }
                    updateSimConnTile();
                } else if (action.equals(
                        TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                    PhoneConstants.DataState state = getMobileDataState(intent);
                    boolean isApnTypeChange = false;
                    String types = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                    if (types != null) {
                        String[] typeArray = types.split(",");
                        for (String type : typeArray) {
                            if (PhoneConstants.APN_TYPE_DEFAULT.equals(type)) {
                                isApnTypeChange = true;
                                break;
                            }
                        }
                    }
                    if (isApnTypeChange && ((state == PhoneConstants.DataState.CONNECTED)
                            || (state == PhoneConstants.DataState.DISCONNECTED))
                            && !isMmsOngoing()) {
                        updateSimConnTile();
                    }
                } else if (action.equals(TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE)) {
                    updateSimConnTile();
                } else if (action.equals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED)) {
                    unRegisterPhoneStateListener();
                    updateSimConnTile();
                    registerPhoneStateListener();
                } else if (action.equals(TRANSACTION_START)) {
                    /// M: only apply if NOT wifi-only device @{
                    if (!isWifiOnlyDevice() && mSimConnStateTrackerReady) {
                        setIsMmsOnging(true);
                        updateSimConnTile();
                    }
                    /// M: }@
                } else if (action.equals(TRANSACTION_STOP)) {
                    /// M: only apply if NOT wifi-only device @{
                    if (!isWifiOnlyDevice() && mSimConnStateTrackerReady) {
                        setIsMmsOnging(false);
                        updateSimConnTile();
                    }
                    /// M: }@
                }
            }
        };

        public boolean isClickable() {
            List<SubscriptionInfo> infos =
                SubscriptionManager.from(mContext).getActiveSubscriptionInfoList();
            return (isSimInsertedBySlot(infos, PhoneConstants.SIM_ID_1)
                    || isSimInsertedBySlot(infos, PhoneConstants.SIM_ID_2))
                    && (isRadioOn(PhoneConstants.SIM_ID_1) || isRadioOn(PhoneConstants.SIM_ID_2))
                    && !isAirplaneMode()
                    && !isMmsOngoing()
                    && !isUserSwitching();
        }

        private boolean isDataConnected() {
            return TelephonyManager.getDefault().getDataState() == TelephonyManager.DATA_CONNECTED;
        }
        private void setIsMmsOnging(boolean ongoing) {
            mMmsOngoing = ongoing;
        }

        private boolean isMmsOngoing() {
            return mMmsOngoing;
        }

        private void setAirplaneMode(boolean airplaneMode) {
            mIsAirlineMode = airplaneMode;
        }

        private boolean isAirplaneMode() {
            return mIsAirlineMode;
        }

        private void setUserSwitching(boolean userSwitching) {
            mIsUserSwitching = userSwitching;
        }

        private boolean isUserSwitching() {
            return mIsUserSwitching;
        }

        private PhoneConstants.DataState getMobileDataState(Intent intent) {
            String str = intent.getStringExtra(PhoneConstants.STATE_KEY);
            if (str != null) {
                return Enum.valueOf(PhoneConstants.DataState.class, str);
            } else {
                return PhoneConstants.DataState.DISCONNECTED;
            }
        }

        /**
         * Register the Lte Phone state listener.
         * @param telephonyManager The telephonManger.
         * @param subId the SubId.
         * @param slotId The slotId.
         */
        private void registerPhoneStateListener() {
            for (int i = 0 ; i < mSlotCount ; i++) {
                final int subId = SIMHelper.getFirstSubInSlot(i);
                if (subId >= 0) {
                    mPhoneStateListener[i] = getPhoneStateListener(subId, i);
                    mTelephonyManager.listen(mPhoneStateListener[i],
                        PhoneStateListener.LISTEN_SERVICE_STATE);
                } else {
                    mPhoneStateListener[i] = null;
                }
            }
        }

        private PhoneStateListener getPhoneStateListener(int subId, final int slotId) {
            return new PhoneStateListener(subId) {
                @Override
                public void onServiceStateChanged(ServiceState state) {
                    Log.d(TAG, "PhoneStateListener:onServiceStateChanged, slot "
                            + slotId + " servicestate = " + state);
                    updateSimConnTile();
                }
            };
        }

        private void unRegisterPhoneStateListener() {
            for (int i = 0 ; i < mSlotCount ; i++) {
                if (mPhoneStateListener[i] != null) {
                    mTelephonyManager.listen(mPhoneStateListener[i],
                            PhoneStateListener.LISTEN_NONE);
                }
            }
        }
    }

    /**
     * M: Used to check weather this device is wifi only.
     */
    private boolean isWifiOnlyDevice() {
        ConnectivityManager cm = (ConnectivityManager) mContext
                .getSystemService(mContext.CONNECTIVITY_SERVICE);
        return !(cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE));
    }

    private boolean isSimInsertedBySlot(List<SubscriptionInfo> infos,
                        int slotId) {
        if (slotId >= SIMHelper.getSlotCount()) {
            return false;
        }

        if (infos != null && infos.size() > 0) {
            for (SubscriptionInfo info : infos) {
                if (info.getSimSlotIndex() == slotId) {
                    return true;
                }
            }
            return false;
        } else {
            Log.d(TAG, "isSimInsertedBySlot, SubscriptionInfo is null");
            return false;
        }
    }
}
