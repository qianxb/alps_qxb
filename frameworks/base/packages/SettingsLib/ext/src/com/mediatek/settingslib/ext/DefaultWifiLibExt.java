package com.mediatek.settingslib.ext;

import com.mediatek.common.PluginImpl ;
import com.mediatek.settingslib.ext.IWifiLibExt;

/**
 * Default plugin implementation.
 */
@PluginImpl(interfaceName = "com.mediatek.settingslib.ext.IWifiLibExt")
public class DefaultWifiLibExt implements IWifiLibExt {

    @Override
    public void appendApSummary(StringBuilder summary, int autoJoinStatus,
        String connectFail, String disabled) {
        summary.append(connectFail);
    }
}
