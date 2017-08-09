/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.net.dhcp;

import java.net.Inet6Address;
import java.nio.ByteBuffer;

/**
 * This class implements the DHCP6-NAK packet.
 */
class Dhcp6NakPacket extends Dhcp6Packet {
    /**
     * Generates a NAK packet with the specified parameters.
     */
    Dhcp6NakPacket(byte[] transId, Inet6Address serverIp,
                  Inet6Address requestAddress) {
        super(transId, requestAddress, serverIp, INADDR_ANY, null);
    }

    public String toString() {
        return super.toString();
    }

    /**
     * Fills in a packet with the requested NAK attributes.
     */
    public ByteBuffer buildPacket(short destUdp, short srcUdp) {
        return null;
    }

    /**
     * Adds the optional parameters to the client-generated NAK packet.
     */
    void finishPacket(ByteBuffer buffer) {
    }
}
