package com.mediatek.anrappframeworks;

import android.app.IActivityManager;
import android.os.IBinder;
/// M: ANR mechanism for Message History/Queue @{
import android.os.Looper;
import android.os.MessageQueue;
/// M: ANR mechanism for Message History/Queue @}
import android.os.ServiceManager;
/// M: ANR mechanism for Message History/Queue
import android.os.SystemClock;
import com.mediatek.anrappmanager.IFrameworks;

/**
 * System private API for talking with the ANRAppManager.  This
 * provides calls from the ANRAppManager to Framework.
 *
 * {@hide}
 */
public class ANRAppFrameworks implements IFrameworks {
    public String getActivityManagerDescriptor() {
        return IActivityManager.descriptor;
    }

    public IBinder serviceManagerGetService(String name) {
        return ServiceManager.getService(name);
    }
    /// M: ANR mechanism for Message History/Queue @{
    public long systemClockCurrentTimeMicro() {
        return SystemClock.currentTimeMicro();
    }

    public MessageQueue looperGetQueue(Looper looper) {
        return looper.getQueue();
    }

    public String messageQueueDumpMessageQueue(MessageQueue messageQueue) {
        return messageQueue.dumpMessageQueue();
    }
    /// M: ANR mechanism for Message History/Queue @}
}