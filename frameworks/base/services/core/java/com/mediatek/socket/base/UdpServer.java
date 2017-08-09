package com.mediatek.socket.base;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.LocalSocketAddress.Namespace;

import com.mediatek.socket.base.SocketUtils.BaseBuffer;
import com.mediatek.socket.base.SocketUtils.UdpServerInterface;

public class UdpServer implements UdpServerInterface {

    private BaseBuffer mBuff;
    private boolean mIsLocalSocket;

    // Network
    private int mPort;
    private DatagramSocket mNetSocket;
    private DatagramPacket mPacket;

    // Local
    private LocalSocket mLocalSocket;
    private DataInputStream mIn;
    private String mChannelName;
    private Namespace mNamespace;

    public UdpServer(int port, int recvBuffSize) {
        mIsLocalSocket = false;
        mBuff = new BaseBuffer(recvBuffSize);
        mPort = port;
        if (!bind()) {
            throw new RuntimeException("bind() fail");
        }
    }

    public UdpServer(String channelName, Namespace namespace, int recvBuffSize) {
        mIsLocalSocket = true;
        mBuff = new BaseBuffer(recvBuffSize);
        mChannelName = channelName;
        mNamespace = namespace;
        if (!bind()) {
            throw new RuntimeException("bind() fail");
        }
    }

    public boolean bind() {
        for (int i = 0; i < 5; i++) {
            if (mIsLocalSocket) {
                try {
                    mLocalSocket = new LocalSocket(LocalSocket.SOCKET_DGRAM);
                    mLocalSocket.bind(new LocalSocketAddress(mChannelName,
                            mNamespace));
                    mIn = new DataInputStream(mLocalSocket.getInputStream());
                    return true;
                } catch (IOException e) {
                    if (i == 4) {
                        throw new RuntimeException(e);
                    }
                    msleep(200);
                }
            } else {
                try {
                    mNetSocket = new DatagramSocket(mPort);
                    mPacket = new DatagramPacket(mBuff.getBuff(),
                            mBuff.getBuff().length);
                    return true;
                } catch (SocketException e) {
                    msleep(200);
                    if (i == 4) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean read() {
        mBuff.clear();
        if (mIsLocalSocket) {
            try {
                if (mIn.read(mBuff.getBuff()) < 8) {
                    return false;
                }
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                mNetSocket.receive(mPacket);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public BaseBuffer getBuff() {
        return mBuff;
    }

    public void close() {
        if (mIsLocalSocket) {
            try {
                // send a invalid message to trigger the close event
                UdpClient client = new UdpClient(mChannelName, mNamespace, 128);
                client.connect();
                client.getBuff().putInt(0xffffffff);
                client.write();
                client.close();
                mLocalSocket.close();
                mIn.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            mNetSocket.close();
        }
    }

    public int available() {
        if (mIsLocalSocket) {
            try {
                return mIn.available();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                throw new RuntimeException(
                        "Network Type does not support available() API");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    public boolean setSoTimeout(int timeout) {
        if (mIsLocalSocket) {
            try {
                mLocalSocket.setSoTimeout(timeout);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                mNetSocket.setSoTimeout(timeout);
                return true;
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private void msleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
