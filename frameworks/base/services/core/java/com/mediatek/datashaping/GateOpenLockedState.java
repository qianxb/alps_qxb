package com.mediatek.datashaping;

import android.content.Context;
import android.content.Intent;
import android.util.Slog;

public class GateOpenLockedState extends DataShapingState {

    private static final String TAG = "GateOpenLockedState";

    public GateOpenLockedState(DataShapingServiceImpl dataShapingServiceImpl, Context context) {
        super(dataShapingServiceImpl, context);
    }

    @Override
    public void onNetworkTypeChanged(Intent intent) {
        if (mDataShapingUtils.isNetworkTypeLte(intent)) {
            setStateFromLockedToOpen();
        }
    }

    @Override
    public void onSharedDefaultApnStateChanged(Intent intent) {
        if (!mDataShapingUtils.isSharedDefaultApnEstablished(intent)) {
            setStateFromLockedToOpen();
        }
    }

    @Override
    public void onScreenStateChanged(boolean isOn) {
        if (!isOn) {
            setStateFromLockedToOpen();
        }
    }

    @Override
    public void onWifiTetherStateChanged(Intent intent) {
        if (!mDataShapingUtils.isWifiTetheringEnabled(intent)) {
            setStateFromLockedToOpen();
        }
    }

    @Override
    public void onUsbConnectionChanged(Intent intent) {
        if (!mDataShapingUtils.isUsbConnected(intent)) {
            setStateFromLockedToOpen();
        }
    }

    @Override
    public void onBTStateChanged(Intent intent) {
        if (!mDataShapingUtils.isBTStateOn(intent)) {
            setStateFromLockedToOpen();
        }
    }

    /// M: integrate Doze and App Standby @{
    @Override
    public void onDeviceIdleStateChanged(boolean enabled) {
        Slog.d(TAG, "[onDeviceIdleStateChanged] DeviceIdle enable is =" + enabled);
        if(!enabled)
            setStateFromLockedToOpen();
    }

    @Override
    public void onAPPStandbyStateChanged(boolean isParoleOn) {
        Slog.d(TAG, "[onAPPStandbyStateChanged] APPStandby parole state is =" + isParoleOn);
        if(!isParoleOn)
            setStateFromLockedToOpen();
    }
    /// integrate Doze and App Standby @}

    private void setStateFromLockedToOpen() {
        if (mDataShapingUtils.canTurnFromLockedToOpen()
                && mDataShapingUtils.setLteAccessStratumReport(true)) {
            mDataShapingManager.setCurrentState(DataShapingServiceImpl.DATA_SHAPING_STATE_OPEN);
        } else {
            // TODO error handle
            Slog.d(TAG, "Still stay in Open Locked state!");
        }
    }
}
