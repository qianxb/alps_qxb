package com.mediatek.keyguard.Plugin;

import android.content.Context;
import android.util.Log;

import com.mediatek.common.MPlugin ;
import com.mediatek.keyguard.ext.DefaultCarrierTextExt ;
import com.mediatek.keyguard.ext.DefaultEmergencyButtonExt ;
import com.mediatek.keyguard.ext.DefaultKeyguardUtilExt;
import com.mediatek.keyguard.ext.DefaultOperatorSIMString ;
import com.mediatek.keyguard.ext.ICarrierTextExt ;
import com.mediatek.keyguard.ext.IEmergencyButtonExt ;
import com.mediatek.keyguard.ext.IKeyguardUtilExt;
import com.mediatek.keyguard.ext.IOperatorSIMString ;

/**
 * M: Plug-in helper class as the facade for accessing related add-ons.
 */
public class KeyguardPluginFactory {
    private static final String TAG = "KeyguardPluginFactory";
    private static IEmergencyButtonExt mEmergencyButtonExt = null;
    private static ICarrierTextExt mCarrierTextExt = null;
    private static IKeyguardUtilExt mKeyguardUtilExt = null;
    private static IOperatorSIMString mOperatorSIMString = null;

    public static synchronized IEmergencyButtonExt getEmergencyButtonExt(Context context) {
        if (mEmergencyButtonExt == null) {
            mEmergencyButtonExt = (IEmergencyButtonExt) MPlugin.createInstance(
                IEmergencyButtonExt.class.getName(), context);
            Log.d(TAG, "getEmergencyButtonExt emergencyButtonExt= " + mEmergencyButtonExt);

            if (mEmergencyButtonExt == null) {
                mEmergencyButtonExt = new DefaultEmergencyButtonExt();
                Log.d(TAG, "getEmergencyButtonExt get DefaultEmergencyButtonExt = "
                        + mEmergencyButtonExt);
            }
        }

        return mEmergencyButtonExt;
    }

    public static synchronized ICarrierTextExt getCarrierTextExt(Context context) {
        if (mCarrierTextExt == null) {
            mCarrierTextExt = (ICarrierTextExt) MPlugin.createInstance(
                ICarrierTextExt.class.getName(), context);
            Log.d(TAG, "getCarrierTextExt carrierTextExt= " + mCarrierTextExt);

            if (mCarrierTextExt == null) {
                mCarrierTextExt = new DefaultCarrierTextExt();
                Log.d(TAG, "getCarrierTextExt get DefaultCarrierTextExt = " + mCarrierTextExt);
            }
        }

        return mCarrierTextExt;
    }

    public static synchronized IKeyguardUtilExt getKeyguardUtilExt(Context context) {
        if (mKeyguardUtilExt == null) {
            mKeyguardUtilExt = (IKeyguardUtilExt) MPlugin.createInstance(
                IKeyguardUtilExt.class.getName(), context);
            Log.d(TAG, "getKeyguardUtilExt keyguardUtilExt= " + mKeyguardUtilExt);

            if (mKeyguardUtilExt == null) {
                mKeyguardUtilExt = new DefaultKeyguardUtilExt();
                Log.d(TAG, "getKeyguardUtilExt get DefaultKeyguardUtilExt = " + mKeyguardUtilExt);
            }
        }
        return mKeyguardUtilExt;
    }

    public static synchronized IOperatorSIMString getOperatorSIMString(Context context) {
        if (mOperatorSIMString == null) {
                mOperatorSIMString = (IOperatorSIMString) MPlugin.createInstance(
                    IOperatorSIMString.class.getName(), context);
                Log.d(TAG, "getOperatorSIMString operatorSIMString= " + mOperatorSIMString);

            if (mOperatorSIMString == null) {
                mOperatorSIMString = new DefaultOperatorSIMString();
                Log.d(TAG, "getOperatorSIMString get DefaultOperatorSIMString = "
                        + mOperatorSIMString);
            }
        }

        return mOperatorSIMString;
    }

}
