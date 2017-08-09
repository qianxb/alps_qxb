/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import android.hardware.display.WifiDisplayStatus;
import android.net.wifi.p2p.WifiP2pDevice;

import java.util.Set;

public interface CastController extends Listenable {
    void addCallback(Callback callback);
    void removeCallback(Callback callback);
    void setDiscovering(boolean request);
    void setCurrentUserId(int currentUserId);
    Set<CastDevice> getCastDevices();
    void startCasting(CastDevice device);
    void stopCasting(CastDevice device);
    /// M: WFD sink support {@
    boolean isWfdSinkSupported();
    boolean isNeedShowWfdSink();
    void updateWfdFloatMenu(boolean start);
    WifiP2pDevice getWifiP2pDev();
    /// @}

    public interface Callback {
        void onCastDevicesChanged();
        /// M: WFD sink support {@
        void onWfdStatusChanged(WifiDisplayStatus status, boolean sinkMode);
        void onWifiP2pDeviceChanged(WifiP2pDevice device);
        /// @}
    }

    public static final class CastDevice {
        public static final int STATE_DISCONNECTED = 0;
        public static final int STATE_CONNECTING = 1;
        public static final int STATE_CONNECTED = 2;

        public String id;
        public String name;
        public String description;
        public int state = STATE_DISCONNECTED;
        public Object tag;
    }
}
