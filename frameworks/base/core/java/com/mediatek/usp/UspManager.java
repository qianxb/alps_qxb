package com.mediatek.usp;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.os.RemoteException;

import java.util.Map;

/**
 * Uniservice-pack (Usp) Manager.
 */
public class UspManager {
    private static final String TAG = "UspManager";
    private static final boolean DBG = true;
    public static final String USER_CONFIRMATION_CONFIG_ACTION =
            "com.mediatek.usp.action.userinterface";
    public static final String OPERATOR_CONFIGURATION_CHANGED_ACTION =
            "com.mediatek.usp.action.configured";

    // current state of global device configuration
    // configuration can start only when state is idle
    public static final int SRV_CONFIG_STATE_INIT = 0;
    public static final int SRV_CONFIG_STATE_WAIT = 1;
    public static final int SRV_CONFIG_STATE_IDLE = 2;

    private final IUspService mService;

    /**
     * constructor of the manager.
     * @param service
     * {@hide}
     */
    public UspManager(IUspService service) {
        mService = checkNotNull(service, "missing IUspService");
    }

    /**
     * get active operator pack.
     * @return active operator pack
     * {@hide}
     */
    public String getActiveOpPack() {
        try {
            return mService.getActiveOpPack();
        } catch (RemoteException e) {
            return null;
        }
    }

    /**
     * it checks for operator pack for specific mcc mnc.
     * @param mccMnc string
     * @return operator pack for provided mcc_mnc
     * {@hide}
     */
    public String getOpPackFromSimInfo(String mccMnc) {
        try {
            return mService.getOpPackFromSimInfo(mccMnc);
        } catch (RemoteException e) {
            return null;
        }
    }

    /**
     * switches / activates to new operator.
     * @param opPack pack value (i.e. OP03)
     * {@hide}
     */
    public void setOpPackActive(String opPack) {
        try {
            mService.setOpPackActive(opPack);
        } catch (RemoteException e) {
            //doing nothing here
        }
    }

     /**
     * get all op pack list
     * @return operators pack list
     * {@hide}
     */
    public Map<String, String> getAllOpPackList() {
        try {
            return mService.getAllOpPackList();
        } catch (RemoteException e) {
            return null;
        }
    }
}


