package com.mediatek.systemui.ext;

import android.content.Context;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * M: the interface for Plug-in definition of Status bar.
 */
public interface ISystemUIStatusBarExt {

    /**
     * Get the current service state for op customized view update.
     * @param subId the sub id of SIM.
     * @internal
     */
    void getServiceStateForCustomizedView(int subId);

    /**
     * Get the cs state from ss and add to mobileState. so that if cs state has changed,
     * it will call op update flow to update the network tower icon.
     * @param serviceState the current service state.
     * @param state the default cs state which will return.
     * @return the customized cs register state.
     * @internal
     */
    int getCustomizeCsState(ServiceState serviceState, int state);

    /**
     * Get the customized network type icon id.
     * @param subId the sub id of SIM.
     * @param iconId the original network type icon id.
     * @param networkType the network type.
     * @param serviceState the service state.
     * @return the customized network type icon id.
     * @internal
     */
    int getNetworkTypeIcon(int subId, int iconId, int networkType,
            ServiceState serviceState);

    /**
     * Get the customized data type icon id.
     * @param subId the sub id of SIM.
     * @param iconId the original data type icon id.
     * @param dataType the data connection type.
     * @param dataState the data connection state.
     * @param serviceState the service state.
     * @return the customized data type icon id.
     * @internal
     */
    int getDataTypeIcon(int subId, int iconId, int dataType, int dataState,
            ServiceState serviceState);

    /**
     * Get the customized signal strength icon id.
     * @param subId the sub id of SIM.
     * @param iconId the original signal strength icon id.
     * @param signalStrength the signal strength.
     * @param networkType the network type.
     * @param serviceState the service state.
     * @return the customized signal strength icon id.
     * @internal
     */
    int getCustomizeSignalStrengthIcon(int subId, int iconId,
            SignalStrength signalStrength, int networkType,
            ServiceState serviceState);

    /**
     * Get the customized signal strength level.
     * @param signalLevel the original signal strength level.
     * @param signalStrength the signal strength.
     * @param serviceState the service state.
     * @return the customized signal strength level.
     * @internal
     */
    int getCustomizeSignalStrengthLevel(int signalLevel,
            SignalStrength signalStrength, ServiceState serviceState);

    /**
     * Add the customized view.
     * @param subId the sub id of SIM.
     * @param context the context.
     * @param root the root view group in which the customized view
     *             will be added.
     * @internal
     */
    void addCustomizedView(int subId, Context context, ViewGroup root);

    /**
     * Add the customized view in signal cluster view.
     * @param context the context.
     * @param root the root view group in which the customized view
     *             will be added.
     * @param index the add index.
     * @internal
     */
    void addSignalClusterCustomizedView(Context context, ViewGroup root, int index);

    /**
     * Set the customized network type view.
     * @param subId the sub id of SIM.
     * @param networkTypeId the customized network type icon id.
     * @param networkTypeView the network type view
     *                        which needs to be customized.
     * @internal
     */
    void setCustomizedNetworkTypeView(int subId,
            int networkTypeId, ImageView networkTypeView);

    /**
     * Set the customized data type view.
     * @param subId the sub id of SIM.
     * @param dataTypeId the customized data type icon id.
     * @param dataIn the data in state.
     * @param dataOut the data out state.
     * @internal
     */
    void setCustomizedDataTypeView(int subId,
            int dataTypeId, boolean dataIn, boolean dataOut);

    /**
     * Set the customized mobile type view.
     * @param subId the sub id of SIM.
     * @param mobileTypeView the mobile type view which needs to be customized.
     * @internal
     */
    void setCustomizedMobileTypeView(int subId, ImageView mobileTypeView);

    /**
     * Set the customized signal strength view.
     * @param subId the sub id of SIM.
     * @param signalStrengthId the customized signal strength icon id.
     * @param signalStrengthView the signal strength view
     *                           which needs to be customized.
     * @internal
     */
    void setCustomizedSignalStrengthView(int subId,
            int signalStrengthId, ImageView signalStrengthView);

    /**
     * Set the other customized views.
     * @param subId the sub id of SIM.
     * @internal
     */
    void setCustomizedView(int subId);

    /**
     * Set the customized no sim view.
     * @param noSimView the no sim view which needs to be customized.
     * @internal
     */
    void setCustomizedNoSimView(ImageView noSimView);

    /**
     * Set the customized volte view.
     * @param iconId the original volte icon id.
     * @param volteView the volte view which needs to be customized.
     * @internal
     */
    void setCustomizedVolteView(int iconId, ImageView volteView);

    /**
     * Set the customized no sim and airplane mode view.
     * @param noSimView the no sim view which needs to be customized.
     * @param airplaneMode the airplane mode.
     * @internal
     */
    void setCustomizedAirplaneView(View noSimView, boolean airplaneMode);

    /**
     * Set the customized noSimsVisible.
     * @param noSimsVisible the noSims visible or not.
     * @internal
     */
    void setCustomizedNoSimsVisible(boolean noSimsVisible);

    /**
     * To remove network icons in case of wifi only mode for WFC.
     * @param serviceState the current service state.
     * @return whether in serive or not - false for iWLAN
     * @internal
     */
    boolean updateSignalStrengthWifiOnlyMode(ServiceState serviceState,
                boolean connected);

    /**
     * To register op phone state listener.
     * @internal
     */
    void registerOpStateListener();

    /**
     * Set the if the sim is inserted.
     * @param slotId the slot id.
     * @param insert the insert status.
     * @internal
     */
    public void setSimInserted(int slotId, boolean insert);

    /**
     * save slot id for IMS to update wfc icon.
     * @internal
     */
    void setImsSlotId(final int slotId);
}
