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

import java.nio.ByteBuffer;

/**
 * This class implements the DHCP-SOLICIT packet.
 */
class Dhcp6SolicitPacket extends Dhcp6Packet {
    /**
     * Generates a SOLICIT packet with the specified parameters.
     */
    Dhcp6SolicitPacket(byte[] transId, byte[] clientMac) {
        super(transId, INADDR_ANY, INADDR_ANY, INADDR_ANY, clientMac);
    }

    public String toString() {
        String s = super.toString();
        return s + " SOLICIT broadcast";
    }

    /**
     * Fills in a packet with the requested SOLICIT parameters.
     */
    public ByteBuffer buildPacket(short destUdp, short srcUdp) {
        ByteBuffer result = ByteBuffer.allocate(MAX_LENGTH);
        fillInPacket(INADDR_BROADCAST_ROUTER, INADDR_ANY, destUdp,
                srcUdp, result, DHCP_MESSAGE_TYPE_SOLICIT);
        result.flip();
        return result;
    }

    /**
     * Adds optional parameters to a SOLICIT packet.
     */
    void finishPacket(ByteBuffer buffer) {
        addTlv(buffer, OPTION_CLIENTID, getClientId());
        addCommonClientTlvs(buffer);
        addTlv(buffer, OPTION_ORO, mRequestedParams);
    }
}
