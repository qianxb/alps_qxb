/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.mediatek.systemui.notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony.Threads;
import android.service.notification.StatusBarNotification;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.systemui.R;

public class NotificationManagerTest extends AndroidTestCase {
    final String TAG = NotificationManagerTest.class.getSimpleName();
    final boolean DEBUG = true;

    private NotificationManager mNotificationManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mNotificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        // clear the deck so that our getActiveNotifications results are predictable
        mNotificationManager.cancelAll();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mNotificationManager.cancelAll();
    }

    public void testNotify() {
        mNotificationManager.cancelAll();

        final int id = 1;
        sendNotification(id, R.drawable.icon_black);

        waiting(1000);
        // assume that sendNotification tested to make sure individual notifications were present
        StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        for (StatusBarNotification sbn : sbns) {
            if (sbn.getId() != id) {
                fail("we got back other notifications besides the one we posted: "
                        + sbn.getKey());
            }
        }
    }

    public void testCancel() {
        final int id = 9;
        sendNotification(id, R.drawable.icon_black);
        mNotificationManager.cancel(id);

        waiting(1000);
        StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        for (StatusBarNotification sbn : sbns) {
            assertFalse("canceled notification was still alive, id=" + id, sbn.getId() == id);
        }
    }

    public void testCancelAll() {
        sendNotification(1, R.drawable.icon_black);
        sendNotification(2, R.drawable.icon_blue);

        if (DEBUG) {
            Log.d(TAG, "posted 3 notifications, here they are: ");
            StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
            for (StatusBarNotification sbn : sbns) {
                Log.d(TAG, "  " + sbn);
            }
            Log.d(TAG, "about to cancel...");
        }
        mNotificationManager.cancelAll();

        waiting(1000);
        StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        assertTrue("notification list was not empty after cancelAll", sbns.length == 0);
    }

    private void sendNotification(final int id, final int icon) {
        final Intent intent = new Intent(Intent.ACTION_MAIN, Threads.CONTENT_URI);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(Intent.ACTION_MAIN);

        final PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        final Notification notification = new Notification.Builder(mContext)
                .setSmallIcon(icon)
                .setWhen(System.currentTimeMillis())
                .setContentTitle("notify#" + id)
                .setContentText("This is #" + id + "notification  ")
                .setContentIntent(pendingIntent)
                .build();
        mNotificationManager.notify(id, notification);

        waiting(1500);

        StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        for (StatusBarNotification sbn : sbns) {
            if (sbn.getId() == id) return;
        }
        fail("couldn't find posted notification id=" + id);
    }

    private void waiting(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            // ignore it
        }
    }
}
