package android.net.dhcp;

import android.net.DhcpResults;
import android.net.LinkAddress;
import android.net.NetworkUtils;
import android.util.Log;

import com.android.internal.util.HexDump;

import java.io.UnsupportedEncodingException;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines basic data and operations needed to build and use packets for the
 * DHCP protocol.  Subclasses create the specific packets used at each
 * stage of the negotiation.
 */
abstract class Dhcp6Packet {
    protected static final String TAG = "Dhcp6Packet";

    protected static final Boolean DBG = true;

    // dhcpcd has a minimum lease of 20 seconds, but DhcpStateMachine would refuse to wake up the
    // CPU for anything shorter than 5 minutes. For sanity's sake, this must be higher than the
    // DHCP client timeout.
    public static final int MINIMUM_LEASE = 60;
    public static final int INFINITE_LEASE = (int) 0xffffffff;

    public static final Inet6Address INADDR_ANY = (Inet6Address) Inet6Address.ANY;
    public static final Inet6Address INADDR_BROADCAST_ROUTER =
            (Inet6Address) NetworkUtils.hexToInet6Address("FF020000000000000000000000010002");
    public static final byte[] ETHER_BROADCAST = new byte[] {
            (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff,
    };

    /**
     * Minimum length of a DHCPv6 packet, excluding options, in the above encapsulations.
     */
    public static final int MIN_PACKET_LENGTH_L3 = 40;
    public static final int MIN_PACKET_LENGTH_L2 = MIN_PACKET_LENGTH_L3 + 14;

    public static final int HWADDR_LEN = 16;
    public static final int MAX_OPTION_LEN = 255 * 255;
    /**
     * IP layer definitions.
     */
    private static final byte IP_TYPE_UDP = (byte) 0x11;

    /**
     * IP: Version 6, Class, Flow Control.
     */
    private static final byte[] IPV6_VERSION_HEADER = new byte[] {
            (byte) 0x60, (byte) 0x00, (byte) 0x00, (byte) 0x00
    };

    /**
     * IP: TTL -- use default 64 from RFC1340.
     */
    private static final byte IPV6_HOT_LIMIT = (byte) 0x01;

    /**
     * The client DHCP port.
     */
    static final short DHCP_CLIENT = (short) 546;

    /**
     * The server DHCP port.
     */
    static final short DHCP_SERVER = (short) 547;

    /**
     * The code type used to identify an Ethernet MAC address in the
     * Client-ID field.
     */
    protected static final short CLIENT_ID_ETHER = (short) 3;

    /**
     * The maximum length of a packet that can be constructed.
     */
    protected static final int MAX_LENGTH = 1500;

    protected Inet6Address mServerAddress;
    protected Inet6Address mSubnetMask;
    protected Inet6Address mGateway;
    protected List<Inet6Address> mDnsServers;
    protected String mDomainName;
    protected Short mMtu;
    protected Integer mLeaseTime;

    protected static final byte DHCP_MESSAGE_TYPE = 53;
    // the actual type values
    protected static final byte DHCP_MESSAGE_TYPE_SOLICIT = 1;
    protected static final byte DHCP_MESSAGE_TYPE_ADVERTISE = 2;
    protected static final byte DHCP_MESSAGE_TYPE_REQUEST = 3;
    protected static final byte DHCP_MESSAGE_TYPE_CONFIRM = 4;
    protected static final byte DHCP_MESSAGE_TYPE_RENEW = 5;
    protected static final byte DHCP_MESSAGE_TYPE_REBIND = 6;
    protected static final byte DHCP_MESSAGE_TYPE_REPLY = 7;
    protected static final byte DHCP_MESSAGE_TYPE_RELEASE = 8;
    protected static final byte DHCP_MESSAGE_TYPE_DECLINE = 9;
    protected static final byte DHCP_MESSAGE_TYPE_INFO_REQUEST = 11;

    protected static final byte DUID_LLT_TYPE = 1;
    protected static final byte DUID_EN_TYPE = 2;
    protected static final byte DUID_LL_TYPE = 3;

    protected byte[] mServerIdentifier;
    protected short[] mRequestedParams;
    protected byte[] mIana;
    protected Integer mT1;
    protected Integer mT2;
    protected String mVendorId;

    /**
     * DHCP Optional Type: DHCP Client Identifier.
     */
    protected static final short OPTION_CLIENTID = 1;

    /**
     * DHCP Optional Type: DHCP Server Identifier.
     */
    protected static final short OPTION_SERVERID = 2;

    /**
     * DHCP Optional Type: DHCP Identity Association for Non-temporary Address.
     */
    protected static final short OPTION_IA_NA = 3;

    /**
     * DHCP Optional Type: DHCP Identity Association for temporary Address.
     */
    protected static final short OPTION_IA_TA = 4;

    /**
     * DHCP Optional Type: DHCP IA Address.
     */
    protected static final short OPTION_IAADDR = 5;

    /**
     * DHCP Optional Type: Option Request Option.
     */
    protected static final short OPTION_ORO = 6;

    /**
     * DHCP Optional Type: Preference Option.
     */
    protected static final short OPTION_PREFERENCE = 7;

    /**
     * DHCP Optional Type: Domain Search List option.
     */
    protected static final short OPTION_ELAPSED_TIME = 8;

    /**
     * DHCP Optional Type: DNS Recursive Name Server option.
     */
    protected static final short OPTION_DNS_SERVERS = 23;

    /**
     * DHCP Optional Type: Domain Search List option.
     */
    protected static final short OPTION_DOMAIN_LIST = 24;

    /**
     * DHCP zero-length option code: pad.
     */
    protected static final short DHCP_OPTION_PAD = 0x00;

    /**
     * DHCP zero-length option code: end of options.
     */
    protected static final short DHCP_OPTION_END = 0xff;

    /**
     * The transaction identifier used in this particular DHCP negotiation.
     */
    protected final byte[] mTransId;

    /**
     * The IP address of the client host.  This address is typically
     * proposed by the client (from an earlier DHCP negotiation) or
     * supplied by the server.
     */
    protected Inet6Address mRequestedIp;
    protected final Inet6Address mServerIp;
    private final Inet6Address mNextIp;
    private final Inet6Address mRelayIp;

    /**
     * The six-octet MAC of the client.
     */
    protected final byte[] mClientMac;

    /**
     * Asks the packet object to create a ByteBuffer serialization of
     * the packet for transmission.
     */
    public abstract ByteBuffer buildPacket(short destUdp,
        short srcUdp);

    /**
     * Allows the concrete class to fill in packet-type-specific details,
     * typically optional parameters at the end of the packet.
     */
    abstract void finishPacket(ByteBuffer buffer);

    protected Dhcp6Packet(byte[] transId, Inet6Address sourceIP,
                         Inet6Address nextIp, Inet6Address relayIp,
                         byte[] clientMac) {
        mTransId = transId;
        mServerIp = sourceIP;
        mNextIp = nextIp;
        mRelayIp = relayIp;
        mClientMac = clientMac;
    }

    /**
     * Returns the transaction ID.
     */
    public byte[] getTransactionId() {
        return mTransId;
    }

    /**
     * Returns the client MAC.
     */
    public byte[] getClientMac() {
        if (mClientMac != null) {
            return mClientMac;
        }
        return null;
    }

    /**
     * Returns the client ID. This follows RFC 2132 and is based on the hardware address.
     */
    public byte[] getClientId() {
        ByteBuffer buffer = ByteBuffer.allocate(14);
        buffer.clear();
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort(DUID_LLT_TYPE);
        buffer.putShort((short) 1);
        buffer.put(Dhcp6Client.getTimeStamp());
        buffer.put(mClientMac);
        return buffer.array();
    }

    /**
     * Return the Identity Association for Non-temporary Address.
     *
     */
    private byte[] getIaNa() {
        byte[] data = new byte[] {0x0E, 0x00, 0x08, (byte) 0xca, 0x00, 0x00,
                                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        return data;
    }

    /**
     * Creates a new L3 packet (including IP header) containing the
     * DHCP udp packet.  This method relies upon the delegated method
     * finishPacket() to insert the per-packet contents.
     */
    protected void fillInPacket(Inet6Address destIp,
        Inet6Address srcIp, short destUdp, short srcUdp, ByteBuffer buf,
        byte requestCode) {
        byte[] destIpArray = destIp.getAddress();
        byte[] srcIpArray = srcIp.getAddress();
        int ipHeaderOffset = 0;
        int ipLengthOffset = 0;
        int ipChecksumOffset = 0;
        int endIpHeader = 0;
        int udpHeaderOffset = 0;
        int udpLengthOffset = 0;
        int udpChecksumOffset = 0;

        buf.clear();
        buf.order(ByteOrder.BIG_ENDIAN);

        // DHCP payload
        buf.put(requestCode);
        buf.put(mTransId);  // Transaction ID

        finishPacket(buf);

        if (DBG) {
            Log.d(TAG, HexDump.toHexString(buf.array()));
        }
    }

    /**
     * Converts a signed short value to an unsigned int value.  Needed
     * because Java does not have unsigned types.
     */
    private static int intAbs(short v) {
        return v & 0xFFFF;
    }

    /**
     * Performs an IP checksum (used in IP header and across UDP
     * payload) on the specified portion of a ByteBuffer.  The seed
     * allows the checksum to commence with a specified value.
     */
    private int checksum(ByteBuffer buf, int seed, int start, int end) {
        int sum = seed;
        int bufPosition = buf.position();

        // set position of original ByteBuffer, so that the ShortBuffer
        // will be correctly initialized
        buf.position(start);
        ShortBuffer shortBuf = buf.asShortBuffer();

        // re-set ByteBuffer position
        buf.position(bufPosition);

        short[] shortArray = new short[(end - start) / 2];
        shortBuf.get(shortArray);

        for (short s : shortArray) {
            sum += intAbs(s);
        }

        start += shortArray.length * 2;

        // see if a singleton byte remains
        if (end != start) {
            short b = buf.get(start);

            // make it unsigned
            if (b < 0) {
                b += 256;
            }

            sum += b * 256;
        }

        sum = ((sum >> 16) & 0xFFFF) + (sum & 0xFFFF);
        sum = ((sum + ((sum >> 16) & 0xFFFF)) & 0xFFFF);
        int negated = ~sum;
        return intAbs((short) negated);
    }

    /**
     * Adds an optional parameter containing a single byte value.
     */
    protected static void addTlv(ByteBuffer buf, short type, byte value) {
        buf.putShort(type);
        buf.putShort((short) 1);
        buf.put(value);
    }

    /**
     * Adds an optional parameter containing an array of bytes.
     */
    protected static void addTlv(ByteBuffer buf, short type, byte[] payload) {
        if (payload != null) {
            if (payload.length > MAX_OPTION_LEN) {
                throw new IllegalArgumentException("DHCP option too long: "
                        + payload.length + " vs. " + MAX_OPTION_LEN);
            }
            buf.putShort(type);
            buf.putShort((short) payload.length);
            buf.put(payload);
        }
    }

    /**
     * Adds an optional parameter containing an array of short.
     */
    protected static void addTlv(ByteBuffer buf, short type, short[] payload) {
        if (payload != null) {
            if (payload.length > MAX_OPTION_LEN) {
                throw new IllegalArgumentException("DHCP option too long: "
                        + payload.length + " vs. " + MAX_OPTION_LEN);
            }
            byte[] rawBtyes = new byte[payload.length * 2];
            ByteBuffer.wrap(rawBtyes).order(ByteOrder.BIG_ENDIAN)
                    .asShortBuffer().put(payload);
            addTlv(buf, type, rawBtyes);
        }
    }

    /**
     * Adds an optional parameter containing an IP address.
     */
    protected static void addTlv(ByteBuffer buf, short type, Inet6Address addr) {
        if (addr != null) {
            addTlv(buf, type, addr.getAddress());
        }
    }

    /**
     * Adds an optional parameter containing a list of IP addresses.
     */
    protected static void addTlv(ByteBuffer buf, short type, List<Inet6Address> addrs) {
        if (addrs == null || addrs.size() == 0) return;

        int optionLen = 4 * addrs.size();
        if (optionLen > MAX_OPTION_LEN) {
            throw new IllegalArgumentException("DHCP option too long: "
                    + optionLen + " vs. " + MAX_OPTION_LEN);
        }

        buf.putShort(type);
        buf.put((byte) (optionLen));

        for (Inet6Address addr : addrs) {
            buf.put(addr.getAddress());
        }
    }

    /**
     * Adds an optional parameter containing a short integer.
     */
    protected static void addTlv(ByteBuffer buf, short type, Short value) {
        if (value != null) {
            buf.putShort(type);
            buf.putShort((short) 2);
            buf.putShort(value.shortValue());
        }
    }

    /**
     * Adds an optional parameter containing a simple integer.
     */
    protected static void addTlv(ByteBuffer buf, short type, Integer value) {
        if (value != null) {
            buf.putShort(type);
            buf.putShort((short) 4);
            buf.putInt(value.intValue());
        }
    }

    /**
     * Adds an optional parameter containing an ASCII string.
     */
    protected static void addTlv(ByteBuffer buf, short type, String str) {
        try {
            addTlv(buf, type, str.getBytes("US-ASCII"));
        } catch (UnsupportedEncodingException e) {
           throw new IllegalArgumentException("String is not US-ASCII: " + str);
        }
    }

    /**
     * Adds common client TLVs.
     *
     * TODO: Does this belong here? The alternative would be to modify all the buildXyzPacket
     * methods to take them.
     */
    protected void addCommonClientTlvs(ByteBuffer buf) {
        addTlv(buf, OPTION_IA_NA, getIaNa());
        addTlv(buf, OPTION_ELAPSED_TIME, (short) 0x0000);
    }

    /**
     * Converts a MAC from an array of octets to an ASCII string.
     */
    public static String macToString(byte[] mac) {
        String macAddr = "";

        if (mac == null) return "";

        for (int i = 0; i < mac.length; i++) {
            String hexString = "0" + Integer.toHexString(mac[i]);

            // substring operation grabs the last 2 digits: this
            // allows signed bytes to be converted correctly.
            macAddr += hexString.substring(hexString.length() - 2);

            if (i != (mac.length - 1)) {
                macAddr += ":";
            }
        }

        return macAddr;
    }

    public String toString() {
        String macAddr = macToString(mClientMac);

        return macAddr;
    }

    /**
     * Reads a four-octet value from a ByteBuffer and construct
     * an IPv4 address from that value.
     */
    private static Inet6Address readIpAddress(ByteBuffer packet) {
        Inet6Address result = null;
        byte[] ipAddr = new byte[16];
        packet.get(ipAddr);

        try {
            result = (Inet6Address) Inet6Address.getByAddress(ipAddr);
        } catch (UnknownHostException ex) {
            // ipAddr is numeric, so this should not be
            // triggered.  However, if it is, just nullify
            result = null;
        }
        Log.i(TAG, "readIpAddress:" + result);
        return result;
    }

    /**
     * Reads a string of specified length from the buffer.
     */
    private static String readAsciiString(ByteBuffer buf, int byteCount, boolean nullOk) {
        byte[] bytes = new byte[byteCount];
        buf.get(bytes);
        int length = bytes.length;
        if (!nullOk) {
            // Stop at the first null byte. This is because some DHCP options (e.g., the domain
            // name) are passed to netd via FrameworkListener, which refuses arguments containing
            // null bytes. We don't do this by default because vendorInfo is an opaque string which
            // could in theory contain null bytes.
            for (length = 0; length < bytes.length; length++) {
                if (bytes[length] == 0) {
                    break;
                }
            }
        }
        return new String(bytes, 0, length, StandardCharsets.US_ASCII);
    }

    /**
     * Creates a concrete Dhcp6Packet from the supplied ByteBuffer.  The
     * buffer may have an L2 encapsulation (which is the full EthernetII
     * format starting with the source-address MAC) or an L3 encapsulation
     * (which starts with the IP header).
     * <br>
     * A subset of the optional parameters are parsed and are stored
     * in object fields.
     */
    public static Dhcp6Packet decodeFullPacket(ByteBuffer packet) {
        // bootp parameters
        byte[] transactionId;
        short secs;
        byte[] clientMac = null;
        List<Inet6Address> dnsServers = new ArrayList<Inet6Address>();
        byte[] serverIdentifier = null;
        byte[] iana = null;
        String hostName = null;
        String domainName = null;
        Inet6Address requestedAddress = null;
        Inet6Address serverIp = null;

        // The following are all unsigned integers. Internally we store them as signed integers of
        // the same length because that way we're guaranteed that they can't be out of the range of
        // the unsigned field in the packet. Callers wanting to pass in an unsigned value will need
        // to cast it.
        Integer T1 = null;
        Integer T2 = null;

        packet.order(ByteOrder.BIG_ENDIAN);

        byte dhcpType = packet.get();
        transactionId = new byte[3];
        packet.get(transactionId);

        // parse options
        boolean notFinishedOptions = true;

        while ((packet.position() < packet.limit()) && notFinishedOptions) {
            try {
                short optionType = packet.getShort();

                Log.d(TAG, "optionType:" + optionType);

                if (optionType == DHCP_OPTION_END) {
                    notFinishedOptions = false;
                } else if (optionType == DHCP_OPTION_PAD) {
                    // The pad option doesn't have a length field. Nothing to do.
                } else {
                    int optionLen = packet.getShort() & 0xFFFF;
                    int expectedLen = 0;

                    switch(optionType) {
                        case OPTION_CLIENTID: { // Client identifier
                            byte[] id = new byte[optionLen];
                            packet.get(id);
                            expectedLen = optionLen;

                            // Get client MAC address
                            ByteBuffer buf = ByteBuffer.wrap(id);
                            short duidType = buf.getShort();
                            if (duidType == DUID_LLT_TYPE || duidType == DUID_LL_TYPE) {
                                short hwType = buf.getShort();
                                if (hwType == 1) {
                                    // Skip time type
                                    if (duidType == DUID_LLT_TYPE) {
                                        buf.getInt();
                                    }
                                    clientMac = new byte[6];
                                    buf.get(clientMac);
                                }
                            }
                        } break;
                        case OPTION_SERVERID:
                            serverIdentifier = new byte[optionLen];
                            packet.get(serverIdentifier);
                            expectedLen = optionLen;
                            break;
                        case OPTION_IA_NA:
                            iana = new byte[optionLen];
                            packet.get(iana);
                            ByteBuffer buf = ByteBuffer.wrap(iana);
                            T1 = Integer.valueOf(buf.getInt(4));
                            T2 = Integer.valueOf(buf.getInt(8));
                            Log.d(TAG, "T1:" + T1);
                            Log.d(TAG, "T2:" + T2);

                            if (optionLen > 12) {
                                buf.position(12);
                                short iaAddress = buf.getShort();
                                if (iaAddress == OPTION_IAADDR) {
                                    int iaLen = buf.getShort() & 0xFFFF;
                                    requestedAddress = readIpAddress(buf);
                                    expectedLen = optionLen;
                                }
                            }
                            break;
                        case OPTION_DNS_SERVERS:
                            for (expectedLen = 0; expectedLen < optionLen; expectedLen += 16) {
                                dnsServers.add(readIpAddress(packet));
                            }
                            break;
                        default:
                            // ignore any other parameters
                            for (int i = 0; i < optionLen; i++) {
                                expectedLen++;
                                byte throwaway = packet.get();
                            }
                    }

                    Log.d(TAG, "expectedLen:" + expectedLen);
                    Log.d(TAG, "optionLen:" + optionLen);

                    if (expectedLen != optionLen) {
                        Log.e(TAG, "optionType:" + optionType);
                        return null;
                    }
                }
            } catch (BufferUnderflowException e) {
                e.printStackTrace();
                return null;
            } catch (Exception ee) {
                ee.printStackTrace();
                return null;
            }
        }

        Dhcp6Packet newPacket;

        switch(dhcpType) {
            case -1: return null;
            case DHCP_MESSAGE_TYPE_ADVERTISE:
                newPacket = new Dhcp6AdvertisePacket(
                    transactionId, serverIp, requestedAddress, clientMac);
                break;
            case DHCP_MESSAGE_TYPE_REPLY:
                newPacket = new Dhcp6ReplyPacket(
                    transactionId, serverIp, requestedAddress, clientMac);
                break;
            default:
                Log.e(TAG, "Unimplemented type: " + dhcpType);
                return null;
        }

        newPacket.mRequestedIp = requestedAddress;
        newPacket.mDnsServers = dnsServers;
        newPacket.mServerIdentifier = serverIdentifier;
        newPacket.mT1 = T1;
        newPacket.mT2 = T2;
        newPacket.mLeaseTime = T1;
        return newPacket;
    }

    /**
     * Parse a packet from an array of bytes, stopping at the given length.
     */
    public static Dhcp6Packet decodeFullPacket(byte[] packet, int length) {
        ByteBuffer buffer = ByteBuffer.wrap(packet, 0, length).order(ByteOrder.BIG_ENDIAN);
        return decodeFullPacket(buffer);
    }

    /**
     *  Construct a DhcpResults object from a DHCP reply packet.
     */
    public DhcpResults toDhcpResults() {
        Inet6Address ipAddress = mRequestedIp;

        if (ipAddress.equals(Inet6Address.ANY)) {
            return null;
        }

        int prefixLength = 64;

        DhcpResults results = new DhcpResults();
        try {
            results.ipAddress = new LinkAddress(ipAddress, prefixLength);
        } catch (IllegalArgumentException e) {
            return null;
        }
        results.gateway = mGateway;
        results.dnsServers.addAll(mDnsServers);
        results.domains = mDomainName;
        results.serverAddress = null; // Todo.
        results.leaseDuration = (mLeaseTime != null) ? mLeaseTime : INFINITE_LEASE;
        return results;
    }

    /**
     * Returns the parsed lease time, in milliseconds, or 0 for infinite.
     */
    public long getLeaseTimeMillis() {
        // dhcpcd treats the lack of a lease time option as an infinite lease.
        if (mLeaseTime == null || mLeaseTime == INFINITE_LEASE) {
            return 0;
        } else if (0 <= mLeaseTime && mLeaseTime < MINIMUM_LEASE) {
            return MINIMUM_LEASE * 1000;
        } else {
            return (mLeaseTime & 0xffffffffL) * 1000;
        }
    }

    /**
     * Builds a DHCP-SOLICIT packet from the required specified
     * parameters.
     */
    public static ByteBuffer buildSolicitPacket(byte[] transactionId,
        short secs, byte[] clientMac, short[] expectedParams) {
        Dhcp6Packet pkt = new Dhcp6SolicitPacket(
            transactionId, clientMac);
        pkt.mRequestedParams = expectedParams;
        return pkt.buildPacket(DHCP_SERVER, DHCP_CLIENT);
    }

    /**
     * Builds a DHCP-REQUEST packet from the required specified parameters.
     */
    public static ByteBuffer buildRequestPacket(
        byte[] transactionId, short secs, Inet6Address clientIp,
        byte[] clientMac, Inet6Address requestedIpAddress,
        byte[]  serverIdentifier, short[] requestedParams) {
        Dhcp6Packet pkt = new Dhcp6RequestPacket(transactionId, clientMac);
        pkt.mRequestedIp = requestedIpAddress;
        pkt.mServerIdentifier = serverIdentifier;
        ByteBuffer result = pkt.buildPacket(DHCP_SERVER, DHCP_CLIENT);
        return result;
    }

    /**
     * Builds a DHCP-INFORMATION-REQUEST packet from the required specified parameters.
     */
    public static ByteBuffer buildInfoRequestPacket(
        byte[] transactionId, short secs, byte[] clientMac, short[] expectedParams) {
        Dhcp6Packet pkt = new Dhcp6InfoRequestPacket(transactionId, clientMac);
        pkt.mRequestedParams = expectedParams;
        ByteBuffer result = pkt.buildPacket(DHCP_SERVER, DHCP_CLIENT);
        return result;
    }


    /**
     * Builds a DHCP-RENEW packet from the required specified parameters.
     */
    public static ByteBuffer buildRenewPacket(
        byte[] transactionId, short secs, Inet6Address clientIp, boolean broadcast,
        byte[] clientMac, Inet6Address requestedIpAddress,
        byte[]  serverIdentifier, byte[] requestedParams) {
        Dhcp6Packet pkt = new Dhcp6RenewPacket(transactionId, clientMac);
        pkt.mRequestedIp = requestedIpAddress;
        pkt.mServerIdentifier = serverIdentifier;
        ByteBuffer result = pkt.buildPacket(DHCP_SERVER, DHCP_CLIENT);
        return result;
    }

    /**
     * Builds a DHCP-REBIND packet from the required specified parameters.
     */
    public static ByteBuffer buildRebindPacket(
        byte[] transactionId, short secs, Inet6Address clientIp, boolean broadcast,
        byte[] clientMac, Inet6Address requestedIpAddress,
        byte[]  serverIdentifier, byte[] requestedParams) {
        Dhcp6Packet pkt = new Dhcp6RebindPacket(transactionId, clientMac);
        pkt.mRequestedIp = requestedIpAddress;
        pkt.mServerIdentifier = serverIdentifier;
        ByteBuffer result = pkt.buildPacket(DHCP_SERVER, DHCP_CLIENT);
        return result;
    }

}
