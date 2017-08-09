/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static android.net.dhcp.Dhcp6Packet.*;
import static android.system.OsConstants.*;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.DhcpResults;
import android.net.InterfaceConfiguration;
import android.net.LinkAddress;
import android.net.NetworkUtils;
import android.net.metrics.DhcpErrorEvent;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.util.SparseArray;
import android.util.TimeUtils;

import com.android.internal.util.HexDump;
import com.android.internal.util.MessageUtils;
import com.android.internal.util.Protocol;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.internal.util.WakeupMessage;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Random;
import java.util.TimeZone;

import libcore.io.IoBridge;


/**
 * A DHCPv6 client.
 *
 * Written to behave similarly to the DhcpStateMachine + dhcpcd 5.5.6 combination used in Android
 * 5.1 and below, as configured on Nexus 6. The interface is the same as DhcpStateMachine.
 *
 * TODO:
 *
 * - Exponential backoff when receiving NAKs (not specified by the RFC, but current behaviour).
 * - Support persisting lease state and support INIT-REBOOT. Android 5.1 does this, but it does not
 *   do so correctly: instead of requesting the lease last obtained on a particular network (e.g., a
 *   given SSID), it requests the last-leased IP address on the same interface, causing a delay if
 *   the server NAKs or a timeout if it doesn't.
 *
 * Known differences from current behaviour:
 *
 * - Does not request the "static routes" option.
 * - Does not support BOOTP servers. DHCP has been around since 1993, should be everywhere now.
 * - Requests the "broadcast" option, but does nothing with it.
 * - Rejects invalid subnet masks such as 255.255.255.1 (current code treats that as 255.255.255.0).
 *
 * @hide
 */
public class Dhcp6Client extends StateMachine {

    private static final String TAG = "Dhcp6Client";
    private static final boolean DBG = true;
    private static final boolean STATE_DBG = true;
    private static final boolean MSG_DBG = true;
    private static final boolean PACKET_DBG = true;

    private static final int STATELESS_DHCPV6 = 1;
    private static final int STATEFUL_DHCPV6  = 2;
    private static final int DHCP_POLL_TOTAL_COUNTER = 8; // check RA flags until 8 times

    // Timers and timeouts.
    private static final int SECONDS = 1000;
    private static final int FIRST_TIMEOUT_MS   =   2 * SECONDS;
    private static final int MAX_TIMEOUT_MS     = 128 * SECONDS;

    // This is not strictly needed, since the client is asynchronous and implements exponential
    // backoff. It's maintained for backwards compatibility with the previous DHCP code, which was
    // a blocking operation with a 30-second timeout. We pick 36 seconds so we can send packets at
    // t=0, t=2, t=6, t=14, t=30, allowing for 10% jitter.
    private static final int DHCP_TIMEOUT_MS    =  36 * SECONDS;

    private static final int PUBLIC_BASE = Protocol.BASE_DHCP + 200;

    /* Commands from controller to start/stop DHCP */
    public static final int CMD_START_DHCP                  = PUBLIC_BASE + 1;
    public static final int CMD_STOP_DHCP                   = PUBLIC_BASE + 2;

    /* Notification from DHCP state machine prior to DHCP discovery/renewal */
    public static final int CMD_PRE_DHCP_ACTION             = PUBLIC_BASE + 3;
    /* Notification from DHCP state machine post DHCP discovery/renewal. Indicates
     * success/failure */
    public static final int CMD_POST_DHCP_ACTION            = PUBLIC_BASE + 4;
    /* Notification from DHCP state machine before quitting */
    public static final int CMD_ON_QUIT                     = PUBLIC_BASE + 5;

    /* Command from controller to indicate DHCP discovery/renewal can continue
     * after pre DHCP action is complete */
    public static final int CMD_PRE_DHCP_ACTION_COMPLETE    = PUBLIC_BASE + 6;

    /* Command and event notification to/from IpManager requesting the setting
     * (or clearing) of an IPv6 LinkAddress.
     */
    public static final int CMD_CLEAR_LINKADDRESS           = PUBLIC_BASE + 7;
    public static final int CMD_CONFIGURE_DNSV6             = PUBLIC_BASE + 8;
    public static final int EVENT_LINKADDRESS_CONFIGURED    = PUBLIC_BASE + 9;

    // Messages.
    private static final int BASE                 = Protocol.BASE_DHCP + 100;
    private static final int CMD_KICK             = BASE + 1;
    private static final int CMD_RECEIVED_PACKET  = BASE + 2;
    private static final int CMD_TIMEOUT          = BASE + 3;
    private static final int CMD_RENEW_DHCP       = BASE + 4;
    private static final int CMD_POLL_CHECK       = BASE + 5;


    private static byte[] sTimeStamp = null;

    // For message logging.
    private static final Class[] sMessageClasses = { DhcpClient.class };
    private static final SparseArray<String> sMessageNames =
            MessageUtils.findMessageNames(sMessageClasses);

    // DHCP parameters that we request.
    private static final short[] REQUESTED_PARAMS = new short[] {
        OPTION_DNS_SERVERS,
        OPTION_DOMAIN_LIST,
    };

    // DHCP flag that means "yes, we support unicast."
    private static final boolean DO_UNICAST   = false;

    // System services / libraries we use.
    private final Context mContext;
    private final AlarmManager mAlarmManager;
    private final Random mRandom;
    private final INetworkManagementService mNMService;

    // Sockets.
    // - We use a packet socket to receive, because servers send us packets bound for IP addresses
    //   which we have not yet configured, and the kernel protocol stack drops these.
    // - We use a UDP socket to send, so the kernel handles ARP and routing for us (DHCP servers can
    //   be off-link as well as on-link).
    private FileDescriptor mUdpSock;
    private ReceiveThread mReceiveThread;

    // State variables.
    private final StateMachine mController;
    private final WakeupMessage mKickAlarm;
    private final WakeupMessage mTimeoutAlarm;
    private final WakeupMessage mRenewAlarm;
    private final String mIfaceName;

    private boolean mRegisteredForPreDhcpNotification;
    private NetworkInterface mIface;
    private byte[] mHwAddr;
    private byte[] mTransactionId;
    private byte[] mServerIdentifier;
    private long mTransactionStartMillis;
    private DhcpResults mDhcpLease;
    private DhcpResults mOffer;
    private Inet6Address mServerIpAddress;
    private long mDhcpLeaseExpiry;

    private int  mDhcpServerType;
    private int  mDhcpRaFlagPollCount;

    // States.
    private State mStoppedState = new StoppedState();
    private State mDhcpCheckState = new DhcpCheckState();
    private State mDhcpState = new DhcpState();
    private State mDhcpInitState = new DhcpInitState();
    private State mDhcpSelectingState = new DhcpSelectingState();
    private State mDhcpRequestingState = new DhcpRequestingState();
    private State mDhcpHaveAddressState = new DhcpHaveAddressState();
    private State mDhcpBoundState = new DhcpBoundState();
    private State mDhcpRenewingState = new DhcpRenewingState();
    private State mDhcpRebindingState = new DhcpRebindingState();
    private State mDhcpInitRebootState = new DhcpInitRebootState();
    private State mDhcpRebootingState = new DhcpRebootingState();
    private State mWaitBeforeStartState = new WaitBeforeStartState(mDhcpInitState);
    private State mWaitBeforeRenewalState = new WaitBeforeRenewalState(mDhcpRenewingState);

    private WakeupMessage makeWakeupMessage(String cmdName, int cmd) {
        cmdName = DhcpClient.class.getSimpleName() + "." + mIfaceName + "." + cmdName;
        return new WakeupMessage(mContext, getHandler(), cmdName, cmd);
    }

    private Dhcp6Client(Context context, StateMachine controller, String iface) {
        super(TAG);

        mContext = context;
        mController = controller;
        mIfaceName = iface;

        addState(mStoppedState);
        addState(mDhcpCheckState);
        addState(mDhcpState);
            addState(mDhcpInitState, mDhcpState);
            addState(mWaitBeforeStartState, mDhcpState);
            addState(mDhcpSelectingState, mDhcpState);
            addState(mDhcpRequestingState, mDhcpState);
            addState(mDhcpHaveAddressState, mDhcpState);
                addState(mDhcpBoundState, mDhcpHaveAddressState);
                addState(mWaitBeforeRenewalState, mDhcpHaveAddressState);
                addState(mDhcpRenewingState, mDhcpHaveAddressState);
                addState(mDhcpRebindingState, mDhcpHaveAddressState);
            addState(mDhcpInitRebootState, mDhcpState);
            addState(mDhcpRebootingState, mDhcpState);

        setInitialState(mStoppedState);

        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);

        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        mNMService = INetworkManagementService.Stub.asInterface(b);

        mRandom = new Random();

        // Used to schedule packet retransmissions.
        mKickAlarm = makeWakeupMessage("KICK", CMD_KICK);
        // Used to time out PacketRetransmittingStates.
        mTimeoutAlarm = makeWakeupMessage("TIMEOUT", CMD_TIMEOUT);
        // Used to schedule DHCP renews.
        mRenewAlarm = makeWakeupMessage("RENEW", CMD_RENEW_DHCP);
    }

    public void registerForPreDhcpNotification() {
        mRegisteredForPreDhcpNotification = true;
    }

    public static Dhcp6Client makeDhcp6Client(
            Context context, StateMachine controller, String intf) {
        Dhcp6Client client = new Dhcp6Client(context, controller, intf);
        client.start();
        Log.i(TAG, "makeDhcp6Client");
        return client;
    }

    private boolean initInterface() {
        try {
            mIface = NetworkInterface.getByName(mIfaceName);
            mHwAddr = mIface.getHardwareAddress();
            return true;
        } catch(SocketException | NullPointerException e) {
            Log.e(TAG, "Can't determine ifindex or MAC address for " + mIfaceName, e);
            Log.e(TAG, "mIface = " + mIface);
            return false;
        }
    }

    private void startNewTransaction() {
        mTransactionId = intToByteArray(mRandom.nextInt());
        mTransactionStartMillis = SystemClock.elapsedRealtime();
    }

    private InetAddress getIpv6LinkLocalAddress(NetworkInterface iface) {
        for (Enumeration<InetAddress> ipAddres = iface.getInetAddresses();
                        ipAddres.hasMoreElements(); ) {
            InetAddress inetAddress = ipAddres.nextElement();
            if (inetAddress.isLinkLocalAddress()) {
                Log.i(TAG, "Source address:" + inetAddress);
                return inetAddress;
            }
        }
        return (InetAddress) Inet6Address.ANY;
    }

    private boolean initSockets() {
        try {
            mUdpSock = Os.socket(AF_INET6, SOCK_DGRAM, IPPROTO_UDP);
            Os.setsockoptInt(mUdpSock, SOL_SOCKET, SO_REUSEADDR, 1);
            Os.setsockoptIfreq(mUdpSock, SOL_SOCKET, SO_BINDTODEVICE, mIfaceName);
            Os.setsockoptInt(mUdpSock, SOL_SOCKET, SO_BROADCAST, 1);
            Os.bind(mUdpSock, getIpv6LinkLocalAddress(mIface), Dhcp6Packet.DHCP_CLIENT);
            NetworkUtils.protectFromVpn(mUdpSock);
        } catch (SocketException|ErrnoException e) {
            Log.e(TAG, "Error creating UDP socket", e);
            return false;
        }
        return true;
    }

    private boolean connectUdpSock(Inet6Address to) {
        try {
            Os.connect(mUdpSock, to, Dhcp6Packet.DHCP_SERVER);
            return true;
        } catch (SocketException|ErrnoException e) {
            Log.e(TAG, "Error connecting UDP socket", e);
            return false;
        }
    }

    private static void closeQuietly(FileDescriptor fd) {
        try {
            IoBridge.closeAndSignalBlockedThreads(fd);
        } catch (IOException ignored) { }
    }

    private void closeSockets() {
        closeQuietly(mUdpSock);
    }

    private boolean setIpAddress(LinkAddress address) {
        InterfaceConfiguration ifcg = new InterfaceConfiguration();
        ifcg.setLinkAddress(address);
        try {
            mNMService.setInterfaceConfig(mIfaceName, ifcg);
        } catch (RemoteException|IllegalStateException e) {
            Log.e(TAG, "Error configuring IP address : " + e);
            return false;
        }
        return true;
    }

    class ReceiveThread extends Thread {

        private final byte[] mPacket = new byte[Dhcp6Packet.MAX_LENGTH];
        private boolean stopped = false;

        public void halt() {
            stopped = true;
            closeSockets();  // Interrupts the read() call the thread is blocked in.
        }

        @Override
        public void run() {
            if (DBG) Log.d(TAG, "Receive thread started");
            while (!stopped) {
                int length = 0;  // Or compiler can't tell it's initialized if a parse error occurs.
                try {
                    length = Os.read(mUdpSock, mPacket, 0, mPacket.length);
                    Dhcp6Packet packet = null;
                    packet = Dhcp6Packet.decodeFullPacket(mPacket, length);
                    if (packet != null) {
                        if (DBG) Log.d(TAG, "Received packet: " + packet);
                        sendMessage(CMD_RECEIVED_PACKET, packet);
                    } else if (PACKET_DBG) {
                        Log.d(TAG,
                                "Can't parse packet" + HexDump.dumpHexString(mPacket, 0, length));
                    }
                } catch (IOException|ErrnoException e) {
                    if (!stopped) {
                        Log.e(TAG, "Read error", e);
                        DhcpErrorEvent.logReceiveError("v6:" + mIfaceName);
                    }
                }
            }
            if (DBG) Log.d(TAG, "Receive thread stopped");
        }
    }

    private short getSecs() {
        return (short) ((SystemClock.elapsedRealtime() - mTransactionStartMillis) / 1000);
    }

    private boolean transmitPacket(ByteBuffer buf, String description, Inet6Address to) {
        try {
            if (DBG) Log.d(TAG, "Sending " + description + " to " + to.getHostAddress());
            Os.sendto(mUdpSock, buf.array(), 0, buf.limit(), 0, to, Dhcp6Packet.DHCP_SERVER);
        } catch (ErrnoException|IOException e) {
            Log.e(TAG, "Can't send packet: ", e);
            return false;
        }
        return true;
    }

    private boolean sendSolicitPacket() {
        ByteBuffer packet = Dhcp6Packet.buildSolicitPacket(
                mTransactionId, getSecs(), mHwAddr,
                REQUESTED_PARAMS);
        return transmitPacket(packet, "DHCPSOLICIT", INADDR_BROADCAST_ROUTER);
    }

    private boolean sendInfoRequestPacket() {
        ByteBuffer packet = Dhcp6Packet.buildInfoRequestPacket(
                mTransactionId, getSecs(), mHwAddr,
                REQUESTED_PARAMS);
        return transmitPacket(packet, "DHCP_INFO_REQUEST", INADDR_BROADCAST_ROUTER);
    }


    private boolean sendRequestPacket(
            Inet6Address clientAddress, Inet6Address requestedAddress,
            Inet6Address serverAddress, Inet6Address to) {

        ByteBuffer packet = Dhcp6Packet.buildRequestPacket(
                mTransactionId, getSecs(), clientAddress,
                mHwAddr, requestedAddress,
                mServerIdentifier, REQUESTED_PARAMS);
        String description = "DHCPREQUEST " +
                             " request=" + requestedAddress.getHostAddress();
        return transmitPacket(packet, description, INADDR_BROADCAST_ROUTER);
    }

    private void scheduleRenew() {
        if (mDhcpLeaseExpiry != 0) {
            long now = SystemClock.elapsedRealtime();
            long alarmTime = (now + mDhcpLeaseExpiry);
            mRenewAlarm.schedule(alarmTime);
            Log.d(TAG, "Scheduling renewal in " + ((alarmTime - now) / 1000) + "s");
        } else {
            Log.d(TAG, "Infinite lease, no renewal needed");
        }
    }

    private void clearDhcpState() {
        mDhcpLease = null;
        mDhcpLeaseExpiry = 0;
        mOffer = null;
    }

    /**
     * Quit the DhcpStateMachine.
     *
     * @hide
     */
    public void doQuit() {
        Log.d(TAG, "doQuit");
        quit();
    }

    protected void onQuitting() {
        Log.d(TAG, "onQuitting");
        mController.sendMessage(CMD_ON_QUIT);
    }

    abstract class LoggingState extends State {
        public void enter() {
            if (STATE_DBG) Log.d(TAG, "Entering state " + getName());
        }

        private String messageName(int what) {
            return sMessageNames.get(what, Integer.toString(what));
        }

        private String messageToString(Message message) {
            long now = SystemClock.uptimeMillis();
            StringBuilder b = new StringBuilder(" ");
            TimeUtils.formatDuration(message.getWhen() - now, b);
            b.append(" ").append(messageName(message.what))
                    .append(" ").append(message.arg1)
                    .append(" ").append(message.arg2)
                    .append(" ").append(message.obj);
            return b.toString();
        }

        @Override
        public boolean processMessage(Message message) {
            if (MSG_DBG) {
                Log.d(TAG, getName() + messageToString(message));
            }
            return NOT_HANDLED;
        }
    }

    // Sends CMD_PRE_DHCP_ACTION to the controller, waits for the controller to respond with
    // CMD_PRE_DHCP_ACTION_COMPLETE, and then transitions to mOtherState.
    abstract class WaitBeforeOtherState extends LoggingState {
        protected State mOtherState;

        @Override
        public void enter() {
            super.enter();
            mController.sendMessage(CMD_PRE_DHCP_ACTION);
        }

        @Override
        public boolean processMessage(Message message) {
            super.processMessage(message);
            switch (message.what) {
                case CMD_PRE_DHCP_ACTION_COMPLETE:
                    transitionTo(mOtherState);
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class StoppedState extends LoggingState {
        @Override
        public boolean processMessage(Message message) {
            super.processMessage(message);
            switch (message.what) {
                case CMD_START_DHCP:
                    if (mRegisteredForPreDhcpNotification) {
                        transitionTo(mDhcpCheckState);
                    } else {
                        transitionTo(mDhcpInitState);
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class DhcpCheckState extends LoggingState {
        boolean mIsPreDhcpComplete;

        @Override
        public void enter() {
            super.enter();

            // Check DHCPv6 environment support or not.
            mIsPreDhcpComplete = false;
            mDhcpRaFlagPollCount = DHCP_POLL_TOTAL_COUNTER;
            checkDhcp6Support(mIsPreDhcpComplete);
        }

        @Override
        public boolean processMessage(Message message) {
            super.processMessage(message);
            switch (message.what) {
                case CMD_STOP_DHCP:
                    transitionTo(mStoppedState);
                    return HANDLED;
                case CMD_PRE_DHCP_ACTION_COMPLETE:
                    mIsPreDhcpComplete = true;
                    return HANDLED;
                case CMD_POLL_CHECK:
                    // Check DHCPv6 environment support or not.
                    checkDhcp6Support(mIsPreDhcpComplete);
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    private boolean checkDhcp6Support(boolean isPreDhcpComplete) {
        int raFlags = NetworkUtils.getRaFlags(mIfaceName);
        if (raFlags == STATEFUL_DHCPV6) {
            mDhcpServerType = raFlags;
            if (isPreDhcpComplete) {
                transitionTo(mDhcpInitState);
            } else {
                transitionTo(mWaitBeforeStartState);
            }
        } else if (raFlags == STATELESS_DHCPV6) {
            return false;
        } else {
            mDhcpRaFlagPollCount--;
            if (mDhcpRaFlagPollCount > 0) {
                sendMessageDelayed(CMD_POLL_CHECK, 1000);
            } else {
                Log.i(TAG, "No DHCPv6 support:" + raFlags);
                transitionTo(mStoppedState);
            }
        }
        return false;
    }

    class WaitBeforeStartState extends WaitBeforeOtherState {
        public WaitBeforeStartState(State otherState) {
            super();
            mOtherState = otherState;
        }
    }

    class WaitBeforeRenewalState extends WaitBeforeOtherState {
        public WaitBeforeRenewalState(State otherState) {
            super();
            mOtherState = otherState;
        }
    }

    class DhcpState extends LoggingState {
        @Override
        public void enter() {
            super.enter();
            clearDhcpState();
            if (initInterface() && initSockets()) {
                mReceiveThread = new ReceiveThread();
                mReceiveThread.start();
            } else {
                transitionTo(mStoppedState);
            }
        }

        @Override
        public void exit() {
            if (mReceiveThread != null) {
                mReceiveThread.halt();  // Also closes sockets.
                mReceiveThread = null;
            }
            clearDhcpState();
        }

        @Override
        public boolean processMessage(Message message) {
            super.processMessage(message);
            switch (message.what) {
                case CMD_STOP_DHCP:
                    transitionTo(mStoppedState);
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

   public boolean isValidPacket(Dhcp6Packet packet) {
        // TODO: check checksum.
        byte[] xid = packet.getTransactionId();
        if (!Arrays.equals(xid, mTransactionId)) {
            Log.d(TAG, "Unexpected transaction ID " + HexDump.toHexString(xid)
                        + ", expected " + HexDump.toHexString(mTransactionId));
            return false;
        }

        if (packet.getClientMac() == null ||
                !Arrays.equals(packet.getClientMac(), mHwAddr)) {
            Log.d(TAG, "MAC addr mismatch: got " +
                    HexDump.toHexString(packet.getClientMac()) + ", expected " +
                    HexDump.toHexString(mHwAddr));
            return false;
        }

        return true;
    }

    public void setDhcpLeaseExpiry(Dhcp6Packet packet) {
        long leaseTimeMillis = packet.getLeaseTimeMillis();
        mDhcpLeaseExpiry =
                (leaseTimeMillis > 0) ? SystemClock.elapsedRealtime() + leaseTimeMillis : 0;
    }

    /**
     * Retransmits packets using jittered exponential backoff with an optional timeout. Packet
     * transmission is triggered by CMD_KICK, which is sent by an AlarmManager alarm. If a subclass
     * sets mTimeout to a positive value, then timeout() is called by an AlarmManager alarm mTimeout
     * milliseconds after entering the state. Kicks and timeouts are cancelled when leaving the
     * state.
     *
     * Concrete subclasses must implement sendPacket, which is called when the alarm fires and a
     * packet needs to be transmitted, and receivePacket, which is triggered by CMD_RECEIVED_PACKET
     * sent by the receive thread. They may also set mTimeout and implement timeout.
     */
    abstract class PacketRetransmittingState extends LoggingState {

        private int mTimer;
        protected int mTimeout = 0;

        @Override
        public void enter() {
            super.enter();
            initTimer();
            maybeInitTimeout();
            sendMessage(CMD_KICK);
        }

        @Override
        public boolean processMessage(Message message) {
            super.processMessage(message);
            switch (message.what) {
                case CMD_KICK:
                    sendPacket();
                    scheduleKick();
                    return HANDLED;
                case CMD_RECEIVED_PACKET:
                    receivePacket((Dhcp6Packet) message.obj);
                    return HANDLED;
                case CMD_TIMEOUT:
                    timeout();
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }

        public void exit() {
            mKickAlarm.cancel();
            mTimeoutAlarm.cancel();
        }

        abstract protected boolean sendPacket();
        abstract protected void receivePacket(Dhcp6Packet packet);
        protected void timeout() {}

        protected void initTimer() {
            mTimer = FIRST_TIMEOUT_MS;
        }

        protected int jitterTimer(int baseTimer) {
            int maxJitter = baseTimer / 10;
            int jitter = mRandom.nextInt(2 * maxJitter) - maxJitter;
            return baseTimer + jitter;
        }

        protected void scheduleKick() {
            long now = SystemClock.elapsedRealtime();
            long timeout = jitterTimer(mTimer);
            long alarmTime = now + timeout;
            mKickAlarm.schedule(alarmTime);
            mTimer *= 2;
            if (mTimer > MAX_TIMEOUT_MS) {
                mTimer = MAX_TIMEOUT_MS;
            }
        }

        protected void maybeInitTimeout() {
            if (mTimeout > 0) {
                long alarmTime = SystemClock.elapsedRealtime() + mTimeout;
                mTimeoutAlarm.schedule(alarmTime);
            }
        }
    }

    class DhcpInitState extends PacketRetransmittingState {
        public DhcpInitState() {
            super();
        }

        @Override
        public void enter() {
            super.enter();
            startNewTransaction();
        }

        protected boolean sendPacket() {
            if (mDhcpServerType == STATEFUL_DHCPV6) {
                return sendSolicitPacket();
            } else if (mDhcpServerType == STATEFUL_DHCPV6) {
                return sendInfoRequestPacket();
            }
            return false;
        }

        protected void receivePacket(Dhcp6Packet packet) {
            if (!isValidPacket(packet)) return;

            if (mDhcpServerType == STATEFUL_DHCPV6) {
                if (!(packet instanceof Dhcp6AdvertisePacket)) return;
                mOffer = packet.toDhcpResults();
                if (mOffer != null) {
                    mServerIdentifier = packet.mServerIdentifier;
                    mServerIpAddress = packet.mServerAddress;
                    Log.d(TAG, "Got pending lease");
                    transitionTo(mDhcpRequestingState);
                }
            } else {
                if (!(packet instanceof Dhcp6ReplyPacket)) return;
                DhcpResults results = packet.toDhcpResults();
                if (results != null) {
                    mDhcpLease = results;
                    transitionTo(mDhcpBoundState);
                }
            }
        }
    }

    // Not implemented. We request the first offer we receive.
    class DhcpSelectingState extends LoggingState {
    }

    class DhcpRequestingState extends PacketRetransmittingState {
        public DhcpRequestingState() {
            super();
            mTimeout = DHCP_TIMEOUT_MS / 2;
        }

        protected boolean sendPacket() {
            return sendRequestPacket(
                    INADDR_ANY,                                     // ciaddr
                    (Inet6Address) mOffer.ipAddress.getAddress(),   // DHCP_REQUESTED_IP
                    mServerIpAddress,                               // DHCP_SERVER_IDENTIFIER
                    INADDR_BROADCAST_ROUTER);                       // packet destination address
        }

        protected void receivePacket(Dhcp6Packet packet) {
            if (!isValidPacket(packet)) return;
            if ((packet instanceof Dhcp6ReplyPacket)) {
                DhcpResults results = packet.toDhcpResults();
                if (results != null) {
                    mDhcpLease = results;
                    mOffer = null;
                    mServerIdentifier = packet.mServerIdentifier;
                    mServerIpAddress = packet.mServerAddress;
                    Log.d(TAG, "Confirmed lease: " + mDhcpLease);
                    setDhcpLeaseExpiry(packet);
                    transitionTo(mDhcpBoundState);
                }
            } else if (packet instanceof Dhcp6NakPacket) {
                Log.d(TAG, "Received NAK, returning to INIT");
                mOffer = null;
                transitionTo(mDhcpInitState);
            }
        }

        @Override
        protected void timeout() {
            // After sending REQUESTs unsuccessfully for a while, go back to init.
            transitionTo(mDhcpInitState);
        }
    }

    class DhcpHaveAddressState extends LoggingState {
        @Override
        public void enter() {
            super.enter();
            if (setIpAddress(mDhcpLease.ipAddress)) {
                if (DBG) Log.d(TAG, "Configured IPv6 address " + mDhcpLease.ipAddress);
                if (mDhcpLease.dnsServers != null) {
                    mController.sendMessage(
                        CMD_CONFIGURE_DNSV6, 0, 0, mDhcpLease.dnsServers);
                }
            } else {
                Log.e(TAG, "Failed to configure IPv6 address " + mDhcpLease.ipAddress);
                // There's likely no point in going into DhcpInitState here, we'll probably just
                // repeat the transaction, get the same IP address as before, and fail.
                transitionTo(mStoppedState);
            }
        }

        @Override
        public void exit() {
            if (DBG) Log.d(TAG, "Clearing IPv6 address");
        }
    }

    class DhcpBoundState extends LoggingState {
        @Override
        public void enter() {
            super.enter();
            // TODO: DhcpStateMachine only supports renewing at 50% of the lease time, and does not
            // support rebinding. Once the legacy DHCP client is gone, fix this.
            if (mDhcpServerType == STATEFUL_DHCPV6) {
                scheduleRenew();
            }
        }

        @Override
        public boolean processMessage(Message message) {
            super.processMessage(message);
            switch (message.what) {
                case CMD_RENEW_DHCP:
                    if (mRegisteredForPreDhcpNotification) {
                        transitionTo(mWaitBeforeRenewalState);
                    } else {
                        transitionTo(mDhcpRenewingState);
                    }
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    class DhcpRenewingState extends PacketRetransmittingState {
        public DhcpRenewingState() {
            super();
            mTimeout = DHCP_TIMEOUT_MS;
        }

        @Override
        public void enter() {
            super.enter();
            startNewTransaction();
        }

        protected boolean sendPacket() {
            return sendRequestPacket(
                    (Inet6Address) mDhcpLease.ipAddress.getAddress(),  // ciaddr
                    INADDR_ANY,                                        // DHCP_REQUESTED_IP
                    INADDR_ANY,                                        // DHCP_SERVER_IDENTIFIER
                    mServerIpAddress);          // packet destination address
        }

        protected void receivePacket(Dhcp6Packet packet) {
            if (!isValidPacket(packet)) return;
            if ((packet instanceof Dhcp6ReplyPacket)) {
                setDhcpLeaseExpiry(packet);
                transitionTo(mDhcpBoundState);
            } else if (packet instanceof Dhcp6NakPacket) {
                transitionTo(mDhcpInitState);
            }
        }

        @Override
        protected void timeout() {
            transitionTo(mStoppedState);
        }
    }

    // Not implemented. DhcpStateMachine does not implement it either.
    class DhcpRebindingState extends LoggingState {
    }

    class DhcpInitRebootState extends LoggingState {
    }

    class DhcpRebootingState extends LoggingState {
    }

    private static final byte[] intToByteArray(int value) {
        return new byte[] {
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value};
    }

    /**
        *     Get time stamp for DUID type 1 (link layer address plus time).
        *     The time stamp is started from 1/1 2000.
        *
        *     @return the seconds from 1/1 2000 UTC.
        **/
    public static byte[] getTimeStamp() {
        if (sTimeStamp == null) {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.set(2000, 0, 1, 0, 0, 0);
            Long index = cal.getTimeInMillis();
            Calendar cal2 = Calendar.getInstance();
            Long now = cal2.getTimeInMillis();
            Long offset =  ((now - index) / 1000) % 4294967296L;  // 2^32
            ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
            buffer.clear();
            buffer.order(ByteOrder.BIG_ENDIAN);
            buffer.putInt(offset.intValue());
            sTimeStamp = buffer.array();
        }
        return sTimeStamp;
    }

}
