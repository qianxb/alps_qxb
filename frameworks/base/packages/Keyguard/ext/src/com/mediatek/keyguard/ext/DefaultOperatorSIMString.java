package com.mediatek.keyguard.ext;

import android.content.Context;

import com.mediatek.common.PluginImpl ;

/**
 * Default plugin implementation.
 */
@PluginImpl(interfaceName = "com.mediatek.keyguard.ext.IOperatorSIMString")
public class DefaultOperatorSIMString implements IOperatorSIMString {
    @Override
    public String getOperatorSIMString(String sourceStr, int slotId, SIMChangedTag simChangedTag,
            Context context) {
        return sourceStr;
    }
}
