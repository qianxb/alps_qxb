package com.mediatek.systemui.notification;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.android.server.notification.NotificationRecord;
import com.android.server.notification.ValidateNotificationPeople;
import com.android.server.notification.ZenModeFiltering;

import android.app.Notification;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.service.notification.StatusBarNotification;
import android.service.notification.ZenModeConfig;
import android.test.AndroidTestCase;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

public class ZenModeFilteringTest extends AndroidTestCase {

    private final String PKG = "pkg";
    private final String BASE_PKG = "basePkg";

    private Context mContext;
    private ZenModeFiltering mZenModeFiltering;
    @Mock ValidateNotificationPeople mValidator;
    UserHandle mUserHandle = UserHandle.OWNER;
    Bundle mPeopleExtras = new Bundle();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mContext = getContext();
        mZenModeFiltering = new ZenModeFiltering(mContext);
    }

    public void testMatchesFilterWithNullValidator() {
        ZenModeConfig zenModeConfig = getZenModeConfig(true, true);
        boolean result = mZenModeFiltering.matchesCallFilter(mContext,
                Global.ZEN_MODE_NO_INTERRUPTIONS, zenModeConfig, UserHandle.OWNER, mPeopleExtras,
                null, 1000, 1000);
        assertFalse(result);
        result = mZenModeFiltering.matchesCallFilter(mContext,
                Global.ZEN_MODE_ALARMS, zenModeConfig, UserHandle.OWNER, mPeopleExtras,
                null, 1000, 1000);
        assertFalse(result);
        result = mZenModeFiltering.matchesCallFilter(mContext,
                Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS, zenModeConfig, UserHandle.OWNER,
                mPeopleExtras, null, 1000, 1000);
        assertTrue(result);
    }

    public void testMatchesFilterWithDisallowCalls() {
        ValidateNotificationPeople tempValidator = new ValidateNotificationPeople();
        ZenModeConfig zenModeConfig = getZenModeConfig(false, false);
        boolean result = mZenModeFiltering.matchesCallFilter(mContext,
                Global.ZEN_MODE_NO_INTERRUPTIONS, zenModeConfig, UserHandle.OWNER, mPeopleExtras,
                tempValidator, 1000, 1000);
        assertFalse(result);
        result = mZenModeFiltering.matchesCallFilter(mContext,
                Global.ZEN_MODE_ALARMS, zenModeConfig, UserHandle.OWNER, mPeopleExtras,
                tempValidator, 1000, 1000);
        assertFalse(result);
        result = mZenModeFiltering.matchesCallFilter(mContext,
                Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS, zenModeConfig, UserHandle.OWNER,
                mPeopleExtras, tempValidator, 1000, 1000);
        assertFalse(result);
    }

    public void testMatchesFilterWithAllowAnyOne() {
        ZenModeConfig tempConfig = getZenModeConfig(true, false);
        // Allow the call from anyone.
        tempConfig.allowCallsFrom = ZenModeConfig.SOURCE_ANYONE;

        when(mValidator.getContactAffinity(mUserHandle, mPeopleExtras, 1000, 1000)).thenReturn(
                0f /*ValidateNotificationPeople.NONE*/);
        boolean result = mZenModeFiltering.matchesCallFilter(mContext,
                Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS, tempConfig, mUserHandle, mPeopleExtras,
                mValidator, 1000, 1000);
        assertTrue(result);

        when(mValidator.getContactAffinity(mUserHandle, mPeopleExtras, 1000, 1000)).thenReturn(
                0.5f /*ValidateNotificationPeople.VALID_CONTACT*/);
        result = mZenModeFiltering.matchesCallFilter(mContext,
                Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS, tempConfig, mUserHandle, mPeopleExtras,
                mValidator, 1000, 1000);
        assertTrue(result);

        when(mValidator.getContactAffinity(mUserHandle, mPeopleExtras, 1000, 1000)).thenReturn(
                1f /*ValidateNotificationPeople.STARRED_CONTACT*/);
        result = mZenModeFiltering.matchesCallFilter(mContext,
                Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS, tempConfig, mUserHandle, mPeopleExtras,
                mValidator, 1000, 1000);
        assertTrue(result);
    }

    public void testMatchesFilterWithOnlyContact() {
        ZenModeConfig tempConfig = getZenModeConfig(true, false);
        // Allow the call only from the contact in Phone.
        tempConfig.allowCallsFrom = ZenModeConfig.SOURCE_CONTACT;

        when(mValidator.getContactAffinity(mUserHandle, mPeopleExtras, 1000, 1000)).thenReturn(
                0f /*ValidateNotificationPeople.NONE*/);
        boolean result = mZenModeFiltering.matchesCallFilter(mContext,
                Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS, tempConfig, mUserHandle, mPeopleExtras,
                mValidator, 1000, 1000);
        assertFalse(result);

        when(mValidator.getContactAffinity(mUserHandle, mPeopleExtras, 1000, 1000)).thenReturn(
                0.5f /*ValidateNotificationPeople.VALID_CONTACT*/);
        result = mZenModeFiltering.matchesCallFilter(mContext,
                Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS, tempConfig, mUserHandle, mPeopleExtras,
                mValidator, 1000, 1000);
        assertTrue(result);

        when(mValidator.getContactAffinity(mUserHandle, mPeopleExtras, 1000, 1000)).thenReturn(
                1f /*ValidateNotificationPeople.STARRED_CONTACT*/);
        result = mZenModeFiltering.matchesCallFilter(mContext,
                Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS, tempConfig, mUserHandle, mPeopleExtras,
                mValidator, 1000, 1000);
        assertTrue(result);
    }

    public void testMatchesWithOnlyStarContact() {
        ZenModeConfig tempConfig = getZenModeConfig(true, false);
        // Allow the call only from the star contact.
        tempConfig.allowCallsFrom = ZenModeConfig.SOURCE_STAR;

        when(mValidator.getContactAffinity(mUserHandle, mPeopleExtras, 1000, 1000)).thenReturn(
                0f /*ValidateNotificationPeople.NONE*/);
        boolean result = mZenModeFiltering.matchesCallFilter(mContext,
                Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS, tempConfig, mUserHandle, mPeopleExtras,
                mValidator, 1000, 1000);
        assertFalse(result);

        when(mValidator.getContactAffinity(mUserHandle, mPeopleExtras, 1000, 1000)).thenReturn(
                0.5f /*ValidateNotificationPeople.VALID_CONTACT*/);
        result = mZenModeFiltering.matchesCallFilter(mContext,
                Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS, tempConfig, mUserHandle, mPeopleExtras,
                mValidator, 1000, 1000);
        assertFalse(result);

        when(mValidator.getContactAffinity(mUserHandle, mPeopleExtras, 1000, 1000)).thenReturn(
                1f /*ValidateNotificationPeople.STARRED_CONTACT*/);
        result = mZenModeFiltering.matchesCallFilter(mContext,
                Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS, tempConfig, mUserHandle, mPeopleExtras,
                mValidator, 1000, 1000);
        assertTrue(result);
    }

    public void testInterceptWithCategorySystem() {
        ZenModeConfig tempConfig = getZenModeConfig(true, true);
        NotificationRecord tempRecord = getNotificationRecord(Notification.CATEGORY_SYSTEM,
                Notification.PRIORITY_DEFAULT);
        boolean result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_NO_INTERRUPTIONS,
                tempConfig, tempRecord);
        assertFalse(result);

        result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_ALARMS, tempConfig,
                tempRecord);
        assertFalse(result);

        result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS,
                tempConfig, tempRecord);
        assertFalse(result);
    }

    public void testInterceptWithCategoryAlarm() {
        ZenModeConfig tempConfig = getZenModeConfig(false, false);
        NotificationRecord tempRecord = getNotificationRecord(Notification.CATEGORY_ALARM,
                Notification.PRIORITY_DEFAULT);

        boolean result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_NO_INTERRUPTIONS,
                tempConfig, tempRecord);
        assertTrue(true);

        result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_ALARMS, tempConfig,
                tempRecord);
        assertFalse(result);

        result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS,
                tempConfig, tempRecord);
        assertFalse(result);
    }

    public void testInterceptWithCategoryCall() {
        ZenModeConfig tempConfig = getZenModeConfig(true, true);
        tempConfig.allowCallsFrom = ZenModeConfig.SOURCE_ANYONE;
        NotificationRecord tempRecord = getNotificationRecord(Notification.CATEGORY_CALL,
                Notification.PRIORITY_DEFAULT);

        boolean result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_NO_INTERRUPTIONS,
                tempConfig, tempRecord);
        assertTrue(result);

        result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_ALARMS, tempConfig,
                tempRecord);
        assertTrue(result);

        result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS,
                tempConfig, tempRecord);
        assertFalse(result);
    }

    public void testInterceptWithCategoryMessage() {
        ZenModeConfig tempConfig = getZenModeConfig(true, true);
        tempConfig.allowMessages = true;
        tempConfig.allowMessagesFrom = ZenModeConfig.SOURCE_ANYONE;
        NotificationRecord tempRecord = getNotificationRecord(Notification.CATEGORY_MESSAGE,
                Notification.PRIORITY_DEFAULT);

        boolean result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_NO_INTERRUPTIONS,
                tempConfig, tempRecord);
        assertTrue(result);

        result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_ALARMS, tempConfig,
                tempRecord);
        assertTrue(result);

        result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS,
                tempConfig, tempRecord);
        assertFalse(result);
    }

    public void testInterceptWithCategoryEvent() {
        ZenModeConfig tempConfig = getZenModeConfig(true, true);
        tempConfig.allowEvents = true;
        NotificationRecord tempRecord = getNotificationRecord(Notification.CATEGORY_EVENT,
                Notification.PRIORITY_DEFAULT);

        boolean result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_NO_INTERRUPTIONS,
                tempConfig, tempRecord);
        assertTrue(result);

        result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_ALARMS, tempConfig,
                tempRecord);
        assertTrue(result);

        result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS,
                tempConfig, tempRecord);
        assertFalse(result);
    }

    public void testInterceptWithCategoryReminder() {
        ZenModeConfig tempConfig = getZenModeConfig(true, true);
        tempConfig.allowReminders = true;
        NotificationRecord tempRecord = getNotificationRecord(Notification.CATEGORY_REMINDER,
                Notification.PRIORITY_DEFAULT);

        boolean result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_NO_INTERRUPTIONS,
                tempConfig, tempRecord);
        assertTrue(result);

        result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_ALARMS, tempConfig,
                tempRecord);
        assertTrue(result);

        result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS,
                tempConfig, tempRecord);
        assertFalse(result);
    }

    public void testInterceptWithMaxPriority() {
        ZenModeConfig tempConfig = getZenModeConfig(true, true);
        NotificationRecord tempRecord = getNotificationRecord(Notification.CATEGORY_REMINDER,
                Notification.PRIORITY_MAX);

        boolean result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_NO_INTERRUPTIONS,
                tempConfig, tempRecord);
        assertTrue(result);

        result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_ALARMS, tempConfig,
                tempRecord);
        assertTrue(result);

        result = mZenModeFiltering.shouldIntercept(Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS,
                tempConfig, tempRecord);
        assertFalse(result);
    }

    private NotificationRecord getNotificationRecord(String category, int priority) {
        Notification notification = new Notification.Builder(mContext)
                .setContentTitle("A")
                .setCategory(category)
                .build();
        return new NotificationRecord(mContext, new StatusBarNotification(
                PKG, BASE_PKG, 1, null, 1, 1, 10, notification, mUserHandle, 1));
    }

    private ZenModeConfig getZenModeConfig(boolean allowCalls, boolean allowRepeatCallers) {
        ZenModeConfig zenModeConfig = new ZenModeConfig();
        zenModeConfig.allowCalls = allowCalls;
        zenModeConfig.allowRepeatCallers = allowRepeatCallers;
        return zenModeConfig;
    }
}
