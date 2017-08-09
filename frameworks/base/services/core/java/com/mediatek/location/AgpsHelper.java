/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.location;

import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.LocalSocketAddress.Namespace;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PowerManager;
import android.os.UserHandle;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.mediatek.location.Agps2FrameworkInterface.Agps2FrameworkInterfaceReceiver;
import com.mediatek.location.Framework2AgpsInterface.Framework2AgpsInterfaceSender;
import com.mediatek.socket.base.SocketUtils.UdpServerInterface;
import com.mediatek.socket.base.UdpClient;
import com.mediatek.socket.base.UdpServer;
import java.lang.Thread;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A worker which will help mtk_agpsd to call Android APIs
 * for features which are not available in native APIs.
 *
 * {@hide}
 */
public class AgpsHelper extends Thread {
    private static final boolean DEBUG = true; //Log.isLoggable(TAG, Log.DEBUG);

    // Handler messages
    private static final int CMD_REQUEST_NET = 100;
    private static final int CMD_QUERY_DNS   = 101;
    private static final int CMD_NET_TIMEOUT = 102;
    private static final int CMD_RELEASE_NET = 103;
    private static final int CMD_REQUEST_GPS_ICON = 104;
    private static final int CMD_REMOVE_GPS_ICON = 105;

    private static final String TAG = "MtkAgpsHelper";
    private static final String CHANNEL_OUT = "mtk_framework2agps"; // reply to mtk_agpsd
    private static final String CHANNEL_IN  = "mtk_agps2framework"; // get cmd from mtk_agpsd

    private static final String WAKELOCK_KEY = "MtkAgps";
    private static final long NET_REQ_TIMEOUT = 10000;

    private final LocationExt mLocExt;
    private final ConnectivityManager mConnManager;
    private final Context mContext;

    private final ArrayList<AgpsNetReq> mAgpsNetReqs = new ArrayList<AgpsNetReq>(2);
    private final byte [] mEmptyIpv6 = new byte[16];

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private UdpServer mNodeIn;
    private UdpClient mNodeOut;
    private Framework2AgpsInterfaceSender mFwkToAgps;
    private NetworkRequest mNetReqSupl;
    private NetworkRequest mNetReqEmergency;
    private NetworkRequest mNetReqIms;

    public static void log(String msg) {
        Log.d(TAG, msg);
    }

    public AgpsHelper(LocationExt locExt, Context context, ConnectivityManager connMgr) {
        if (DEBUG) log("AgpsHelper constructor");
        mLocExt = locExt;
        mContext = context;
        mConnManager = connMgr;

        new Thread("MtkAgpsSocket") {
            public void run() {
                if (DEBUG) log("SocketThread.run()");
                waitForAgpsCommands();
            }
        }.start();
    }

    private Agps2FrameworkInterfaceReceiver mReceiver
            = new Agps2FrameworkInterfaceReceiver() {
        @Override
        public void isExist() {
            if (DEBUG) log("isExist()");
        }

        @Override
        public void acquireWakeLock() {
            if (DEBUG) log("acquireWakeLock()");
            mWakeLock.acquire();
        }

        @Override
        public void releaseWakeLock() {
            if (DEBUG) log("releaseWakeLock()");
            mWakeLock.release();
        }

        @Override
        public void requestDedicatedApnAndDnsQuery(
                String fqdn, boolean isEsupl, boolean isSuplApn) {
            if (DEBUG) {
                    log("requestDedicatedApnAndDnsQuery() fqdn=" + fqdn +
                        " isEsupl=" + isEsupl +
                        " isSuplApn=" + isSuplApn);
            }
            AgpsNetReq agpsNetReq = new AgpsNetReq(fqdn, isEsupl, isSuplApn);
            sendMessage(CMD_REQUEST_NET, agpsNetReq);
        }

        @Override
        public void releaseDedicatedApn() {
            if (DEBUG) log("releaseDedicatedApn()");
            sendMessage(CMD_RELEASE_NET, null);
        }

        @Override
        public void requestGpsIcon(){
            if (DEBUG) log("requestGpsIcon");
            sendMessage(CMD_REQUEST_GPS_ICON, null);
        }

        @Override
        public void removeGpsIcon(){
            if (DEBUG) log("removeGpsIcon()");
            sendMessage(CMD_REMOVE_GPS_ICON, null);
        }
    };

    protected void setup() {
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_KEY);
        mWakeLock.setReferenceCounted(true);
        NetworkRequest.Builder nrBuilder = new NetworkRequest.Builder();
        mNetReqEmergency = nrBuilder
                .addCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)
                .build();
        mNetReqIms = nrBuilder
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .build();
        mNetReqSupl = nrBuilder
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_SUPL)
                .build();
        mHandlerThread = new HandlerThread("MtkAgpsHandler");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case CMD_REQUEST_NET:
                        handleRequestNet((AgpsNetReq)msg.obj);
                        break;
                    case CMD_QUERY_DNS:
                        handleDnsQuery((AgpsNetReq)msg.obj);
                        break;
                    case CMD_NET_TIMEOUT:
                        handleNetTimeout((AgpsNetReq)msg.obj);
                        break;
                    case CMD_RELEASE_NET:
                        handleReleaseNet((AgpsNetReq)msg.obj);
                        break;
                    case CMD_REQUEST_GPS_ICON:
                        handleRequestGpsIcon();
                        break;
                    case CMD_REMOVE_GPS_ICON:
                        handleRemoveGpsIcon();
                        break;
                }
            }
        };
    }

    protected void waitForAgpsCommands() {
        setup();

        try {
            int buffSizeOut = 35;
            int buffSizeIn  = 271;
            mNodeOut = new UdpClient(CHANNEL_OUT, Namespace.ABSTRACT, buffSizeOut);
            mNodeIn = new UdpServer(CHANNEL_IN, Namespace.ABSTRACT, buffSizeIn);
            mFwkToAgps = new Framework2AgpsInterfaceSender();
            while (true) {
                mReceiver.readAndDecode(mNodeIn);
            }
        } catch (Exception e) {
            log(e.toString());
        } finally {
            if (mNodeIn != null) {
                mNodeIn.close();
                mNodeIn = null;
            }
            mReceiver = null;
        }
    }

    void sendMessage(int what, Object obj) {
        mHandler.obtainMessage(what, 0, 0, obj).sendToTarget();
    }

    void sendMessageDelayed(int what, Object obj, long delayMillis) {
        Message msg = mHandler.obtainMessage(what, 0, 0, obj);
        mHandler.sendMessageDelayed(msg, delayMillis);
    }

    void removeMessages(int what, Object obj) {
        mHandler.removeMessages(what, obj);
    }

    void doReleaseNet(AgpsNetReq req) {
        if (DEBUG) log("doReleaseNet");
        mAgpsNetReqs.remove(req);
        req.releaseNet();
    }

    void handleRequestNet(AgpsNetReq req) {
        if (DEBUG) log("handleRequestNet");
        while (mAgpsNetReqs.size() >= 2) {
            // mtk_agpsd may crash due to bug or watch dog
            if (DEBUG) log("remove potential leak of AgpsNetReq");
            doReleaseNet(mAgpsNetReqs.get(0));
        }
        mAgpsNetReqs.add(req);
        req.requestNet();
    }

    void handleDnsQuery(AgpsNetReq req) {
        if (DEBUG) log("handleDnsQuery");
        req.queryDns();
    }

    void handleNetTimeout(AgpsNetReq req) {
        if (DEBUG) log("handleNetTimeout");
        req.queryDns();
    }

    void handleReleaseNet(AgpsNetReq req) {
        if (DEBUG) log("handleReleaseNet");
        if (null != req) {
            doReleaseNet(req);
        } else {
            if (!mAgpsNetReqs.isEmpty()) {
                doReleaseNet(mAgpsNetReqs.get(0));
            }
        }
    }

    void handleRequestGpsIcon() {
        Intent intent = new Intent(LocationManager.HIGH_POWER_REQUEST_CHANGE_ACTION);
        intent.putExtra("requestGpsByNi", true);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    void handleRemoveGpsIcon() {
        Intent intent = new Intent(LocationManager.HIGH_POWER_REQUEST_CHANGE_ACTION);
        intent.putExtra("requestGpsByNi", false);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    class AgpsNetReq {
        String mFqdn;
        boolean mIsEsupl;
        boolean mIsSuplApn;
        boolean mIsQueried = false;
        int mRouteType = ConnectivityManager.TYPE_NONE;
        NetworkRequest mNetReq = null;
        Network mNet = null;
        ConnectivityManager.NetworkCallback mNetworkCallback = null;

        AgpsNetReq(String fqdn, boolean isEsupl, boolean isSuplApn) {
            mFqdn = fqdn;
            mIsEsupl = isEsupl;
            mIsSuplApn = isSuplApn;
        }
        void decideRoute() {
            Network netEmergemcy = null;
            Network netIms = null;
            Network netSupl = null;
            Network [] nets = mConnManager.getAllNetworks();
            if (null != nets) {
                for (Network net : nets) {
                    NetworkCapabilities netCap = mConnManager.getNetworkCapabilities(net);
                    if (DEBUG) log("checking net=" + net + " cap=" + netCap);
                    if (null == netEmergemcy && null != netCap &&
                            netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)) {
                        netEmergemcy = net;
                        if (DEBUG) log("NetEmergemcy");
                    }
                    if (null == netIms && null != netCap &&
                            netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS)) {
                        netIms = net;
                        if (DEBUG) log("NetIms");
                    }
                    if (null == netSupl && null != netCap &&
                            netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_SUPL)) {
                        netSupl = net;
                        if (DEBUG) log("NetSupl");
                    }
                }
            }
            if (mIsEsupl) {
                if (null != netEmergemcy) {
                    if (DEBUG) log("to use NetEmergemcy");
                    mRouteType = ConnectivityManager.TYPE_MOBILE_EMERGENCY;
                    mNet = netEmergemcy;
                    mNetReq = mNetReqEmergency;
                    return;
                } else if (null != netIms) {
                    if (DEBUG) log("to use NetIms");
                    mRouteType = ConnectivityManager.TYPE_MOBILE_IMS;
                    mNet = netIms;
                    mNetReq = mNetReqIms;
                    return;
                }
            }
            if (mIsSuplApn && mLocExt.hasIccCard() && !mLocExt.isAirplaneModeOn()) {
                if (DEBUG) log("try to use NetSupl");
                mRouteType = ConnectivityManager.TYPE_MOBILE_SUPL;
                mNet = netSupl;
                mNetReq = mNetReqSupl;
            }
        }

        void requestNet() {
            boolean isDirectDns = false;
            decideRoute();
            if (null != mNetReq) {
                mNetworkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network net) {
                        if (DEBUG) log("onAvailable: network=" + net);
                        synchronized (AgpsNetReq.this) {
                            if (null == mNet) {
                                mNet = net;
                                removeMessages(CMD_NET_TIMEOUT, AgpsNetReq.this);
                                sendMessage(CMD_QUERY_DNS, AgpsNetReq.this);
                            }
                        }
                    }

                    @Override
                    public void onLost(Network net) {
                        if (DEBUG) log("onLost: network=" + net);
                        //sendMessageDelayed(CMD_RELEASE_NET, AgpsNetReq.this, NET_REQ_TIMEOUT);
                    }
                };
                synchronized (this) {
                    if (DEBUG) log("request net:" + mNetReq);
                    mConnManager.requestNetwork(mNetReq, mNetworkCallback);
                    if (null == mNet) {
                        if (DEBUG) log("wait for net callback");
                        sendMessageDelayed(CMD_NET_TIMEOUT, this, NET_REQ_TIMEOUT);
                    } else {
                        //sendMessage(CMD_QUERY_DNS, this);
                        isDirectDns = true;
                    }
                }
            } else {
                //sendMessage(CMD_QUERY_DNS, this);
                isDirectDns = true;
            }
            if (isDirectDns) {
                queryDns();
            }
        }

        void getDefaultNet() {
            Network net = mConnManager.getActiveNetwork();
            if (null != net) {
                mNet = net;
                NetworkCapabilities netCap = mConnManager.getNetworkCapabilities(net);
                if (DEBUG) log("default network=" + net + " cap=" + netCap);
                if (netCap != null && netCap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    if (DEBUG) log("to use NetWiFi");
                    mRouteType = ConnectivityManager.TYPE_WIFI;
                } else {
                    if (DEBUG) log("to use NetMobile");
                    mRouteType = ConnectivityManager.TYPE_MOBILE;
                }
            }
        }

        void queryDns() {
            if (mIsQueried) return;
            mIsQueried = true;

            boolean hasIpv4 = false;
            boolean hasIpv6 = false;
            int ipv4 = 0;
            byte [] ipv6 = mEmptyIpv6;
            try {
                InetAddress[] ias;
                if (null != mNet) {
                    ias = mNet.getAllByName(mFqdn);
                } else {
                    getDefaultNet();
                    ias = InetAddress.getAllByName(mFqdn);
                }
                for (InetAddress ia : ias) {
                    byte [] addr = ia.getAddress();
                    log("ia=" + ia.toString() + " bytes=" +
                            Arrays.toString(addr) + " network=" + mNet);
                    if (addr.length == 4 && !hasIpv4) {
                        hasIpv4 = true;
                        ipv4 = addr[3] & 0xFF;
                        ipv4 = (ipv4 << 8) | (addr[2] & 0xFF);
                        ipv4 = (ipv4 << 8) | (addr[1] & 0xFF);
                        ipv4 = (ipv4 << 8) | (addr[0] & 0xFF);
                        requestRoute(ia);
                    } else if (addr.length == 16 && !hasIpv6) {
                        hasIpv6 = true;
                        ipv6 = addr;
                        requestRoute(ia);
                    }
                }
            } catch (UnknownHostException e) {
                log("UnknownHostException for fqdn=" + mFqdn);
            }
            boolean isSuccess = hasIpv4 || hasIpv6;
            boolean ret = mFwkToAgps.DnsQueryResult(mNodeOut, isSuccess, hasIpv4, ipv4,
                    hasIpv6, ipv6);
            if (DEBUG) log("DnsQueryResult() fqdn=" + mFqdn +
                        " isSuccess=" + isSuccess +
                        " hasIpv4=" + hasIpv4 +
                        " ipv4=" + Integer.toHexString(ipv4) +
                        " hasIpv6=" + hasIpv6 +
                        " ipv6=" + Arrays.toString(ipv6) +
                        " ret=" + ret);
            if (!isSuccess) {
                // mtk_agpsd will not ask to release this failed request
                doReleaseNet(this);
            }
        }

        void requestRoute(InetAddress ia) {
            if (ConnectivityManager.TYPE_NONE != mRouteType) {
                boolean result = mConnManager.requestRouteToHostAddress(
                        mRouteType, ia);
                if (!result) {
                    log("Error requesting route (" + mRouteType + ") to host: " + ia);
                } else if (DEBUG) {
                    log("Requesting route (" + mRouteType + ") to host: " + ia);
                }
            }
        }

        synchronized void releaseNet() {
            if (DEBUG) log("releaseNet() fqdn=" + mFqdn + " eSupl=" + mIsEsupl +
                    " suplApn=" + mIsSuplApn);
            if (null != mNetworkCallback) {
                if (DEBUG) log("remove net callback");
                mConnManager.unregisterNetworkCallback(mNetworkCallback);
                mNetworkCallback = null;
                removeMessages(CMD_NET_TIMEOUT, AgpsNetReq.this);
                //removeMessages(CMD_RELEASE_NET, this);
            }
            mIsQueried = true;
            mNetReq = null;
            mNet = null;
            mFqdn = null;
        }
    }
}
