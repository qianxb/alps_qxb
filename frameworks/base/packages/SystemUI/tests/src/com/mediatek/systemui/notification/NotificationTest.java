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
import android.app.Notification.Action;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.widget.RemoteViews;

import com.android.systemui.R;

public class NotificationTest extends AndroidTestCase {

    private Notification mNotification;
    private Context mContext;

    private static final String TICKER_TEXT = "tickerText";
    private static final String CONTENT_TITLE = "contentTitle";
    private static final String CONTENT_TEXT = "contentText";
    private static final String CONTENT_INFO = "contentInfo";
    private static final String URI_STRING = "uriString";
    private static final int TOLERANCE = 200;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getContext();
        mNotification = new Notification();
    }

    public void testConstructor() {
        mNotification = null;
        mNotification = new Notification();
        assertNotNull(mNotification);
        assertTrue(System.currentTimeMillis() - mNotification.when < TOLERANCE);

        mNotification = null;
        final int notificationTime = 200;
        mNotification = new Notification(0, TICKER_TEXT, notificationTime);
        assertEquals(notificationTime, mNotification.when);
        assertEquals(0, mNotification.icon);
        assertEquals(TICKER_TEXT, mNotification.tickerText);
    }

    public void testDescribeContents() {
        final int expected = 0;
        mNotification = new Notification();
        assertEquals(expected, mNotification.describeContents());
    }

    public void testWriteToParcel() {
        mNotification = new Notification();
        mNotification.icon = 0;
        mNotification.number = 1;
        final Intent intent = new Intent();
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        mNotification.contentIntent = pendingIntent;
        final Intent deleteIntent = new Intent();
        final PendingIntent delPendingIntent = PendingIntent.getBroadcast(
                mContext, 0, deleteIntent, 0);
        mNotification.deleteIntent = delPendingIntent;
        mNotification.tickerText = TICKER_TEXT;

        final RemoteViews contentView = new RemoteViews(mContext.getPackageName(),
                android.R.layout.simple_list_item_1);
        mNotification.contentView = contentView;
        mNotification.defaults = 0;
        mNotification.flags = 0;
        final Uri uri = Uri.parse(URI_STRING);
        mNotification.sound = uri;
        mNotification.audioStreamType = 0;
        final long[] longArray = { 1l, 2l, 3l };
        mNotification.vibrate = longArray;
        mNotification.ledARGB = 0;
        mNotification.ledOnMS = 0;
        mNotification.ledOffMS = 0;
        mNotification.iconLevel = 0;
        Parcel parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        // Test Notification(Parcel)
        Notification result = new Notification(parcel);
        assertEquals(mNotification.icon, result.icon);
        assertEquals(mNotification.when, result.when);
        assertEquals(mNotification.number, result.number);
        assertNotNull(result.contentIntent);
        assertNotNull(result.deleteIntent);
        assertEquals(mNotification.tickerText, result.tickerText);
        assertNotNull(result.contentView);
        assertEquals(mNotification.defaults, result.defaults);
        assertEquals(mNotification.flags, result.flags);
        assertNotNull(result.sound);
        assertEquals(mNotification.audioStreamType, result.audioStreamType);
        assertEquals(mNotification.vibrate[0], result.vibrate[0]);
        assertEquals(mNotification.vibrate[1], result.vibrate[1]);
        assertEquals(mNotification.vibrate[2], result.vibrate[2]);
        assertEquals(mNotification.ledARGB, result.ledARGB);
        assertEquals(mNotification.ledOnMS, result.ledOnMS);
        assertEquals(mNotification.ledOffMS, result.ledOffMS);
        assertEquals(mNotification.iconLevel, result.iconLevel);

        mNotification.contentIntent = null;
        parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        result = new Notification(parcel);
        assertNull(result.contentIntent);

        mNotification.deleteIntent = null;
        parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        result = new Notification(parcel);
        assertNull(result.deleteIntent);

        mNotification.tickerText = null;
        parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        result = new Notification(parcel);
        assertNull(result.tickerText);

        mNotification.contentView = null;
        parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        result = new Notification(parcel);
        assertNull(result.contentView);

        mNotification.sound = null;
        parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        result = new Notification(parcel);
        assertNull(result.sound);
    }

    public void testBuilder() {
        final Intent intent = new Intent();
        final PendingIntent contentIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        mNotification = new Notification.Builder(mContext)
                .setSmallIcon(1)
                .setContentTitle(CONTENT_TITLE)
                .setContentText(CONTENT_TEXT)
                .setContentIntent(contentIntent)
                .build();
        assertEquals(CONTENT_TEXT, mNotification.extras.getString(Notification.EXTRA_TEXT));
        assertEquals(CONTENT_TITLE, mNotification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(1, mNotification.icon);
        assertEquals(contentIntent, mNotification.contentIntent);
    }

    public void testToString() {
        mNotification = new Notification();
        assertNotNull(mNotification.toString());
    }

    public void testNotificationCategory() {
        Notification.Builder builder = new Notification.Builder(mContext);
        builder.setContentTitle(CONTENT_TITLE);
        builder.setContentText(CONTENT_TEXT);

        builder.setCategory(Notification.CATEGORY_CALL);
        mNotification = builder.build();
        assertEquals(Notification.CATEGORY_CALL, mNotification.category);

        builder.setCategory(Notification.CATEGORY_EMAIL);
        mNotification = builder.build();
        assertEquals(Notification.CATEGORY_EMAIL, mNotification.category);

        builder.setCategory(Notification.CATEGORY_MESSAGE);
        mNotification = builder.build();
        assertEquals(Notification.CATEGORY_MESSAGE, mNotification.category);

        builder.setCategory(Notification.CATEGORY_EVENT);
        mNotification = builder.build();
        assertEquals(Notification.CATEGORY_EVENT, mNotification.category);

        builder.setCategory(Notification.CATEGORY_ALARM);
        mNotification = builder.build();
        assertEquals(Notification.CATEGORY_ALARM, mNotification.category);
    }

    public void testBuilderActions() {
        Notification.Builder builder = new Notification.Builder(mContext);
        builder.setContentTitle(CONTENT_TITLE);
        builder.setContentText(CONTENT_TEXT);
        Intent intent = new Intent();
        PendingIntent actionIntent1 = PendingIntent.getBroadcast(mContext,
                1, intent, 0);
        builder.addAction(R.drawable.icon_black,
                "action1",
                actionIntent1);
        PendingIntent actionIntent2 = PendingIntent.getBroadcast(mContext,
                1, intent, 0);
        builder.addAction(R.drawable.icon_blue,
                "",
                actionIntent2);
        mNotification = builder.build();

        Notification.Action[] actions = mNotification.actions;
        assertNotNull(actions);
        assertEquals(2, actions.length);
        assertEquals(actionIntent1, actions[0].actionIntent);
        assertEquals("action1", actions[0].title);
    }

    public void testNotificationExtras() {
        final Intent intent = new Intent();
        final PendingIntent contentIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        Notification.Builder builder = new Notification.Builder(mContext);
        builder.setContentTitle(CONTENT_TITLE);
        builder.setContentText(CONTENT_TEXT);
        builder.setContentInfo(CONTENT_INFO);
        builder.setContentIntent(contentIntent);
        builder.setWhen(1);
        builder.setProgress(100, 50, false);
        builder.setUsesChronometer(true);
        builder.addPerson(URI_STRING);
        mNotification = builder.build();

        assertEquals(CONTENT_TITLE, mNotification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(CONTENT_TEXT, mNotification.extras.getString(Notification.EXTRA_TEXT));
        assertEquals(CONTENT_INFO, mNotification.extras.getString(Notification.EXTRA_INFO_TEXT));
        assertEquals(true, mNotification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN));
        assertEquals(50, mNotification.extras.getInt(Notification.EXTRA_PROGRESS));
        assertEquals(false,
                mNotification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE));
        assertEquals(100, mNotification.extras.getInt(Notification.EXTRA_PROGRESS_MAX));
        assertEquals(true, mNotification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER));
        assertNotNull(mNotification.extras.getStringArray(Notification.EXTRA_PEOPLE));
    }

    public void testNotificationStyles() {
        Notification.Builder builder = new Notification.Builder(mContext);
        builder.setContentTitle(CONTENT_TITLE);
        builder.setContentText(CONTENT_TEXT);

        builder.setStyle(new Notification.BigPictureStyle());
        mNotification = builder.build();
        assertEquals(Notification.BigPictureStyle.class.getName(),
                mNotification.extras.getString(Notification.EXTRA_TEMPLATE));

        builder.setStyle(new Notification.InboxStyle());
        mNotification = builder.build();
        assertEquals(Notification.InboxStyle.class.getName(),
                mNotification.extras.getString(Notification.EXTRA_TEMPLATE));

        builder.setStyle(new Notification.BigTextStyle());
        mNotification = builder.build();
        assertEquals(Notification.BigTextStyle.class.getName(),
                mNotification.extras.getString(Notification.EXTRA_TEMPLATE));
    }
}
