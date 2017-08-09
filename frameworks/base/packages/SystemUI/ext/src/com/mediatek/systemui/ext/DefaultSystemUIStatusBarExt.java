package com.mediatek.systemui.ext;

import android.content.Context;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * Default implementation of Plug-in definition of Status bar.
 */
public class DefaultSystemUIStatusBarExt implements ISystemUIStatusBarExt {

    /**
     * Constructs a new DefaultSystemUIStatusBarExt instance with Context.
     * @param context A Context object
     */
    public DefaultSystemUIStatusBarExt(Context context) {
    }

    @Override
    public void getServiceStateForCustomizedView(int subId) {
    }

    @Override
    public int getCustomizeCsState(ServiceState serviceState, int state) {
        return state;
    }

    @Override
    public int getNetworkTypeIcon(int subId, int iconId, int networkType,
                    ServiceState serviceState) {
        return iconId;
    }

    @Override
    public int getDataTypeIcon(int subId, int iconId,
                    int dataType, int dataState, ServiceState serviceState) {
        return iconId;
    }

    @Override
    public int getCustomizeSignalStrengthIcon(int subId, int iconId,
                    SignalStrength signalStrength, int networkType,
                    ServiceState serviceState) {
        return iconId;
    }

    @Override
    public int getCustomizeSignalStrengthLevel(int signalLevel,
            SignalStrength signalStrength, ServiceState serviceState) {
        return signalLevel;
    }

    @Override
    public void addCustomizedView(int subId,
                    Context context, ViewGroup root) {
    }

    @Override
    public void addSignalClusterCustomizedView(Context context, ViewGroup root, int index) {
    }

    @Override
    public void setCustomizedNetworkTypeView(int subId,
                    int networkTypeId, ImageView networkTypeView) {
    }

    @Override
    public void setCustomizedDataTypeView(int subId,
                    int dataTypeId, boolean dataIn, boolean dataOut) {
    }

    @Override
    public void setCustomizedMobileTypeView(int subId,
                    ImageView mobileTypeView) {
    }

    @Override
    public void setCustomizedSignalStrengthView(int subId,
                    int signalStrengthId, ImageView signalStrengthView) {
    }

    @Override
    public void setCustomizedView(int subId) {
    }

    @Override
    public void setCustomizedNoSimView(ImageView noSimView) {
    }

    @Override
    public void setCustomizedVolteView(int iconId, ImageView volteView) {
    }

    @Override
    public void setCustomizedAirplaneView(View noSimView, boolean airplaneMode) {
    }

    @Override
    public void setCustomizedNoSimsVisible(boolean noSimsVisible) {
    }

    @Override
    public boolean updateSignalStrengthWifiOnlyMode(ServiceState serviceState,
                       boolean connected) {
        return connected;
    }

    @Override
    public void registerOpStateListener() {
    }

    @Override
    public void setSimInserted(int slotId, boolean insert) {
    }

    @Override
    public void setImsSlotId(final int slotId) {
    }
}
