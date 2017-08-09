package com.mediatek.socket.base;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.LocalSocketAddress.Namespace;

import com.mediatek.socket.base.SocketUtils.BaseBuffer;

public class UdpClient {

    private BaseBuffer mBuff;
    private boolean mIsLocalSocket;

    // Network
    private String mHost;
    private int mPort;
    private InetAddress mInetAddress;
    private DatagramSocket mNetSocket;
    private DatagramPacket mPacket;

    // Local
    private String mChannelName;
    private Namespace mNamespace;
    private LocalSocket mLocalSocket;
    private DataOutputStream mOut;

    public UdpClient(String host, int port, int sendBuffSize) {
        mIsLocalSocket = false;
        mBuff = new BaseBuffer(sendBuffSize);
        mHost = host;
        mPort = port;
    }

    public UdpClient(String channelName, Namespace namesapce, int sendBuffSize) {
        mIsLocalSocket = true;
        mBuff = new BaseBuffer(sendBuffSize);
        mChannelName = channelName;
        mNamespace = namesapce;
    }

    public boolean connect() {
        if (mIsLocalSocket) {
            try {
                mLocalSocket = new LocalSocket(LocalSocket.SOCKET_DGRAM);
                mLocalSocket.connect(new LocalSocketAddress(mChannelName,
                        mNamespace));
                mOut = new DataOutputStream(mLocalSocket.getOutputStream());
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                mNetSocket = new DatagramSocket();
                if (mInetAddress == null) {
                    mInetAddress = InetAddress.getByName(mHost);
                }
                mPacket = new DatagramPacket(mBuff.getBuff(),
                        mBuff.getBuff().length, mInetAddress, mPort);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public BaseBuffer getBuff() {
        return mBuff;
    }

    public boolean write() {
        if (mIsLocalSocket) {
            try {
                mOut.write(mBuff.getBuff(), 0, mBuff.getOffset());
                mBuff.setOffset(0);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                mPacket.setLength(mBuff.getOffset());
                mNetSocket.send(mPacket);
                mBuff.setOffset(0);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public void close() {
        if (mIsLocalSocket) {
            try {
                if (mLocalSocket != null) {
                    mLocalSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            if (mNetSocket != null) {
                mNetSocket.close();
            }
        }
    }

}
