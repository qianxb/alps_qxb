package com.mediatek.telecom;

import android.os.Message;
import android.os.SystemClock;
import android.telecom.Log;
import android.telecom.ParcelableCall;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * M: A debug helper for InCallService.
 */
public class InCallServiceMessageAnalyzer {
    private static final String TAG = InCallServiceMessageAnalyzer.class.getSimpleName();
    private static final int DUMP_THRESHOLD = 5;
    private static final int DUMP_RECOVER_THRESHOLD = 2;
    private static final int MSG_UPDATE_CALL = 3;

    Queue<Message> mMessageQueue = new ConcurrentLinkedQueue<Message>();
    private boolean mReadyForNextDump = true;
    private long mWaitingTimeStamp = 0;

    /**
     * M: called when message enqueue. This method is called in binder call.
     *
     * @param message the target message.
     */
    public void onMessageSent(Message message) {
        mMessageQueue.add(message);
    }

    /**
     * M: called when message dispatched and be ready for handleMessage.
     *
     * @param message the target message.
     */
    public void onStartHandleMessage(Message message) {
        if (message != mMessageQueue.peek()) {
            Log.w(TAG, "[onStartHandleMessage]The MessageAnalyzer " +
                    "works abnormal");
        }
        mWaitingTimeStamp = SystemClock.uptimeMillis();
    }

    /**
     * M: called when the message was handled, finished executing.
     *
     * @param message the target message.
     */
    public void onMessageHandled(Message message) {
        if (message != mMessageQueue.poll()) {
            Log.w(TAG, "[onMessageHandled]The MessageAnalyzer works abnormal");
        }
        long spentTime = SystemClock.uptimeMillis() - mWaitingTimeStamp;
        long waitTime = mWaitingTimeStamp - message.getWhen();
        Log.v(TAG, "[Msg handled]: " + parseMessageDetails(message)
                + " | " + String.format("spent: %4d, wait: %4d", spentTime, waitTime));

        //TODO: the #ConcurrentLinkedQueue has bad performance in size() method. Enhance it later.
        int queueSize = mMessageQueue.size();
        if (mReadyForNextDump && queueSize > DUMP_THRESHOLD) {
            Log.v(TAG, "Too many messages in queue, dump them:");
            mReadyForNextDump = false;
            dumpMessagesInQueue();
        }
        if (!mReadyForNextDump && queueSize <= DUMP_RECOVER_THRESHOLD) {
            mReadyForNextDump = true;
        }
    }

    private void dumpMessagesInQueue() {
        for (Message msg : mMessageQueue) {
            long waitTime = SystemClock.uptimeMillis() - msg.getWhen();
            Log.v(this, "    MsgDump: " + parseMessageDetails(msg)
                    + " | already wait: " + waitTime);
        }
    }

    private String parseMessageDetails(Message message) {
        if (message.what == MSG_UPDATE_CALL) {
            ParcelableCall call = (ParcelableCall) message.obj;
            return String.format("UPDATE_CALL %s: state: %d, capabilities: %08X, properties: %d",
                    call.getId(), call.getState(), call.getCapabilities(), call.getProperties());
        } else {
            return "NORMAL_MSG " + message.what;
        }
    }
}
