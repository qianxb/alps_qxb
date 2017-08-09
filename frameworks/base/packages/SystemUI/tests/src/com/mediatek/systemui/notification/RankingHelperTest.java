/*
 * Copyright (C) 2014 The Android Open Source Project
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

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.android.server.notification.NotificationRecord;
import com.android.server.notification.NotificationUsageStats;
import com.android.server.notification.RankingHelper;
import com.android.server.notification.ValidateNotificationPeople;
import com.android.systemui.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;

public class RankingHelperTest extends AndroidTestCase {
    @Mock NotificationUsageStats mUsageStats;

    private Notification mNotiGroupGSortA;
    private Notification mNotiGroupGSortB;
    private Notification mNotiNoGroup;
    private Notification mNotiNoGroup2;
    private Notification mNotiNoGroupSortA;
    private Notification mNotiWithMaxPri;
    private Notification mNotiWithHighPri;
    private Notification mNotiWithDefaultPri;
    private Notification mNotiWithLowPri;
    private Notification mNotiWithMinPri;
    private Notification mNotiWithAffinity;
    private Notification mNotiWithAffinity2;
    private Notification mNotiWithPkgPri;
    private NotificationRecord mRecordGroupGSortA;
    private NotificationRecord mRecordGroupGSortB;
    private NotificationRecord mRecordNoGroup;
    private NotificationRecord mRecordNoGroup2;
    private NotificationRecord mRecordNoGroupSortA;
    private NotificationRecord mRecordWithMaxPri;
    private NotificationRecord mRecordWithHighPri;
    private NotificationRecord mRecordWithDefaultPri;
    private NotificationRecord mRecordWithLowPri;
    private NotificationRecord mRecordWithMinPri;
    private NotificationRecord mRecordWithAffinity;
    private NotificationRecord mRecordWithAffinity2;
    private NotificationRecord mRecordWithPkgPri;
    private RankingHelper mHelper;

    @Override
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        UserHandle user = UserHandle.ALL;

        mHelper = new RankingHelper(getContext(), null, mUsageStats, new String[0]);

        mNotiGroupGSortA = new Notification.Builder(getContext())
                .setContentTitle("A")
                .setGroup("G")
                .setSortKey("A")
                .setWhen(1205)
                .build();
        mRecordGroupGSortA = new NotificationRecord(getContext(), new StatusBarNotification(
                "package", "package", 1, null, 0, 0, 0, mNotiGroupGSortA, user));

        mNotiGroupGSortB = new Notification.Builder(getContext())
                .setContentTitle("B")
                .setGroup("G")
                .setSortKey("B")
                .setWhen(1200)
                .build();
        mRecordGroupGSortB = new NotificationRecord(getContext(), new StatusBarNotification(
                "package", "package", 1, null, 0, 0, 0, mNotiGroupGSortB, user));

        mNotiNoGroup = new Notification.Builder(getContext())
                .setContentTitle("C")
                .setWhen(1201)
                .build();
        mRecordNoGroup = new NotificationRecord(getContext(), new StatusBarNotification(
                "package", "package", 1, null, 0, 0, 0, mNotiNoGroup, user));

        mNotiNoGroup2 = new Notification.Builder(getContext())
                .setContentTitle("D")
                .setWhen(1202)
                .build();
        mRecordNoGroup2 = new NotificationRecord(getContext(), new StatusBarNotification(
                "package", "package", 1, null, 0, 0, 0, mNotiNoGroup2, user));

        mNotiNoGroupSortA = new Notification.Builder(getContext())
                .setContentTitle("E")
                .setWhen(1201)
                .setSortKey("A")
                .build();
        mRecordNoGroupSortA = new NotificationRecord(getContext(), new StatusBarNotification(
                "package", "package", 1, null, 0, 0, 0, mNotiNoGroupSortA, user));

        mNotiWithMaxPri = new Notification.Builder(getContext())
                .setContentTitle("H")
                .setWhen(1201)
                .setPriority(Notification.PRIORITY_MAX)
                .build();
        mRecordWithMaxPri = new NotificationRecord(getContext(), new StatusBarNotification(
                "package", "package", 1, null, 0, 0, Notification.PRIORITY_MAX,
                mNotiWithMaxPri, user));

        mNotiWithHighPri = new Notification.Builder(getContext())
                .setContentTitle("I")
                .setWhen(1202)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();
        mRecordWithHighPri = new NotificationRecord(getContext(), new StatusBarNotification(
                "package", "package", 1, null, 0, 0, Notification.PRIORITY_HIGH,
                mNotiWithHighPri, user));

        mNotiWithDefaultPri = new Notification.Builder(getContext())
                .setContentTitle("J")
                .setWhen(1203)
                .build();
        mRecordWithDefaultPri = new NotificationRecord(getContext(), new StatusBarNotification(
                "package", "package", 1, null, 0, 0, Notification.PRIORITY_DEFAULT,
                mNotiWithDefaultPri, user));

        mNotiWithLowPri = new Notification.Builder(getContext())
                .setContentTitle("K")
                .setPriority(Notification.PRIORITY_LOW)
                .setWhen(1204)
                .build();
        mRecordWithLowPri = new NotificationRecord(getContext(), new StatusBarNotification(
                "package", "package", 1, null, 0, 0, Notification.PRIORITY_LOW,
                mNotiWithLowPri, user));

        mNotiWithMinPri = new Notification.Builder(getContext())
                .setContentTitle("L")
                .setPriority(Notification.PRIORITY_MIN)
                .setWhen(1205)
                .build();
        mRecordWithMinPri = new NotificationRecord(getContext(), new StatusBarNotification(
                "package", "package", 1, null, 0, 0, Notification.PRIORITY_MIN,
                mNotiWithMinPri, user));

        mNotiWithAffinity = new Notification.Builder(getContext())
        .setContentTitle("M")
        .setWhen(1200)
        .build();
        mRecordWithAffinity = new NotificationRecord(getContext(), new StatusBarNotification(
                "package", "package", 1, null, 0, 0, 0, mNotiWithAffinity, user));
        mRecordWithAffinity.setContactAffinity(1f);

        mNotiWithAffinity2 = new Notification.Builder(getContext())
        .setContentTitle("N")
        .setWhen(1200)
        .build();
        mRecordWithAffinity2 = new NotificationRecord(getContext(), new StatusBarNotification(
                "package", "package", 1, null, 0, 0, 0, mNotiWithAffinity2, user));
        mRecordWithAffinity.setContactAffinity(0.5f);

        mNotiWithPkgPri = new Notification.Builder(getContext())
        .setContentTitle("O")
        .setSortKey("A")
        .setWhen(1200)
        .build();
        mRecordWithPkgPri = new NotificationRecord(getContext(), new StatusBarNotification(
                "package", "package", 1, null, 0, 0, 0, mNotiWithPkgPri, user));
        mRecordWithPkgPri.setPackagePriority(Notification.PRIORITY_MAX);
    }

    @SmallTest
    public void testFindAfterRankingWithASplitGroup() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>(3);
        notificationList.add(mRecordGroupGSortA);
        notificationList.add(mRecordGroupGSortB);
        notificationList.add(mRecordNoGroup);
        notificationList.add(mRecordNoGroupSortA);
        mHelper.sort(notificationList);
        assertTrue(mHelper.indexOf(notificationList, mRecordGroupGSortA) == 2);
        assertTrue(mHelper.indexOf(notificationList, mRecordGroupGSortB) == 3);
        assertTrue(mHelper.indexOf(notificationList, mRecordNoGroup) == 0);
        assertTrue(mHelper.indexOf(notificationList, mRecordNoGroupSortA) == 1);
    }

    @SmallTest
    public void testSortShouldNotThrowWithPlainNotifications() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>(2);
        notificationList.add(mRecordNoGroup);
        notificationList.add(mRecordNoGroup2);
        mHelper.sort(notificationList);
        assertTrue(mHelper.indexOf(notificationList, mRecordNoGroup) == 1);
        assertTrue(mHelper.indexOf(notificationList, mRecordNoGroup2) == 0);
    }

    @SmallTest
    public void testSortShouldNotThrowOneSorted() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>(2);
        notificationList.add(mRecordNoGroup);
        notificationList.add(mRecordNoGroupSortA);
        mHelper.sort(notificationList);
        assertTrue(mHelper.indexOf(notificationList, mRecordNoGroup) == 0);
        assertTrue(mHelper.indexOf(notificationList, mRecordNoGroupSortA) == 1);
    }

    @SmallTest
    public void testSortShouldNotThrowOneNotification() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>(1);
        notificationList.add(mRecordNoGroup);
        mHelper.sort(notificationList);
        assertTrue(mHelper.indexOf(notificationList, mRecordNoGroup) >= 0);
    }

    @SmallTest
    public void testSortShouldNotThrowOneSortKey() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>(1);
        notificationList.add(mRecordGroupGSortB);
        mHelper.sort(notificationList);
        assertTrue(mHelper.indexOf(notificationList, mRecordGroupGSortB) >= 0);
    }

    @SmallTest
    public void testSortShouldNotThrowOnEmptyList() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>();
        mHelper.sort(notificationList);
        assertTrue(notificationList.size() == 0);
    }

    @SmallTest
    public void testRankingWithPriority() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>();
        notificationList.add(mRecordWithLowPri);
        notificationList.add(mRecordWithMinPri);
        notificationList.add(mRecordWithDefaultPri);
        notificationList.add(mRecordWithMaxPri);
        notificationList.add(mRecordWithHighPri);
        mHelper.sort(notificationList);
        assertTrue(mHelper.indexOf(notificationList, mRecordWithMaxPri) == 0);
        assertTrue(mHelper.indexOf(notificationList, mRecordWithHighPri) == 1);
        assertTrue(mHelper.indexOf(notificationList, mRecordWithDefaultPri) == 2);
        assertTrue(mHelper.indexOf(notificationList, mRecordWithLowPri) == 3);
        assertTrue(mHelper.indexOf(notificationList, mRecordWithMinPri) == 4);
    }

    @SmallTest
    public void testRankingWithAffinity() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>();
        notificationList.add(mRecordWithAffinity2);
        notificationList.add(mRecordWithAffinity);
        mHelper.sort(notificationList);
        assertTrue(mHelper.indexOf(notificationList, mRecordWithAffinity2) == 1);
        assertTrue(mHelper.indexOf(notificationList, mRecordWithAffinity) == 0);
    }

    @SmallTest
    public void testRankingWithWhenAndAffinity() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>(2);
        notificationList.add(mRecordNoGroup);
        notificationList.add(mRecordNoGroup2);
        notificationList.add(mRecordWithAffinity);
        mHelper.sort(notificationList);
        assertTrue(mHelper.indexOf(notificationList, mRecordWithAffinity) == 0);
        assertTrue(mHelper.indexOf(notificationList, mRecordNoGroup2) == 1);
        assertTrue(mHelper.indexOf(notificationList, mRecordNoGroup) == 2);
    }

    @SmallTest
    public void testRankingWithPackagePriority() throws Exception {
        ArrayList<NotificationRecord> notificationList = new ArrayList<NotificationRecord>(2);
        notificationList.add(mRecordNoGroup);
        notificationList.add(mRecordWithPkgPri);
        mHelper.sort(notificationList);
        assertTrue(mHelper.indexOf(notificationList, mRecordWithPkgPri) == 0);
        assertTrue(mHelper.indexOf(notificationList, mRecordNoGroup) == 1);
    }
}
