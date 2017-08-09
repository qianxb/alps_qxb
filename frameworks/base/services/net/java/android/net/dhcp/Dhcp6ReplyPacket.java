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
 * This class implements the DHCP-REPLY packet.
 */
class Dhcp6ReplyPacket extends Dhcp6Packet {

    /**
     * The address of the server which sent this packet.
     */
    private final Inet6Address mSrcIp;

    Dhcp6ReplyPacket(byte[] transId, Inet6Address serverIp,
                  Inet6Address requestAddress, byte[] clientMac) {
        super(transId, requestAddress, serverIp, INADDR_ANY, clientMac);
        mSrcIp = serverIp;
    }

    public String toString() {
        String s = super.toString();
        String dnsServers = " DNS servers: ";

        for (Inet6Address dnsServer: mDnsServers) {
            dnsServers += dnsServer.toString() + " ";
        }

        return s + " Replay: your new IP " + mRequestedIp +
                ", netmask " + mSubnetMask +
                ", gateway " + mGateway + dnsServers +
                ", lease time " + mLeaseTime;
    }

    /**
     * Fills in a packet with the requested ACK parameters.
     */
    public ByteBuffer buildPacket(short destUdp, short srcUdp) {
        // TBD.
        return null;
    }

    /**
     * Adds the optional parameters to the client-generated ACK packet.
     */
    void finishPacket(ByteBuffer buffer) {
        // TBD.
    }

    /**
     * Un-boxes an Integer, returning 0 if a null reference is supplied.
     */
    private static final int getInt(Integer v) {
        if (v == null) {
            return 0;
        } else {
            return v.intValue();
        }
    }
}
