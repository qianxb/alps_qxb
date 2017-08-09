package com.mediatek.systemui.notification;

import android.app.Notification;
import android.content.Context;
import android.os.Parcel;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.test.AndroidTestCase;
import android.util.Log;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mediatek.sensorhub.ParcelableListInteger;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

public class StatusBarNotificationTest extends AndroidTestCase {

    UserHandle mUser = UserHandle.OWNER;
    @Mock Notification mNotification;
    Context mContext;
    StatusBarNotification mSbn;
    private static final String PKG = "pkg";
    private static final String BASE_PKG = "basePkg";
    private static final String TAG = "notification";
    private static final String GROUP = "group";
    private static final String SORT_KEY = "sortKey";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mContext = getContext();
        mSbn = null;
    }

    public void testStatusBarNotificationKey() {
        mSbn = new StatusBarNotification(PKG, BASE_PKG, 1, TAG, 0/*uid*/,
                0, 10, mNotification, mUser, 1/*postTime*/);
        assertEquals("0|pkg|1|notification|0", mSbn.getKey());
        assertEquals("0|pkg|1|notification|0", mSbn.getGroupKey());
        when(mNotification.getGroup()).thenReturn(GROUP);
        when(mNotification.getSortKey()).thenReturn(SORT_KEY);
        mSbn = new StatusBarNotification(PKG, BASE_PKG, 1, TAG, 0/*uid*/,
                0, 10, mNotification, mUser, 1/*postTime*/);
        assertEquals("0|pkg|g:group", mSbn.getGroupKey());
    }

    public void testParcelWriteAndRead() {
        mSbn = new StatusBarNotification(PKG, BASE_PKG, 1, TAG, 0/*uid*/,
                0, 10, mNotification, mUser, 1/*postTime*/);
        Parcel parcel = Parcel.obtain();
        mSbn.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        StatusBarNotification sbn2 = new StatusBarNotification(parcel);
        assertEquals(PKG, sbn2.getPackageName());
        assertEquals(BASE_PKG, sbn2.getOpPkg());
        assertEquals(1, sbn2.getId());
        assertEquals(TAG, sbn2.getTag());
        assertEquals(0, sbn2.getUid());
    }

    public void testCloneLight() {
        mSbn = new StatusBarNotification(PKG, BASE_PKG, 1, TAG, 0/*uid*/,
                0, 10, new Notification(), mUser, 1/*postTime*/);
        StatusBarNotification result = mSbn.cloneLight();
        assertEquals(PKG, result.getPackageName());
        assertEquals(BASE_PKG, result.getOpPkg());
        assertEquals(1, result.getId());
        assertEquals(TAG, result.getTag());
        assertEquals(0, result.getUid());
        assertEquals(1, result.getPostTime());
    }
}
