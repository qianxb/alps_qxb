package com.android.settingslib.applications;

import android.app.Application;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import com.android.settingslib.BaseTest;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.ApplicationsState.Callbacks;
import com.android.settingslib.applications.ApplicationsState.MainHandler;
import com.android.settingslib.applications.ApplicationsState.Session;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class ApplicationsStateTest extends BaseTest {

    private static final String TAG = "ApplicationsStateTest";
    private static final String PACKAGE_NAME = "com.android.settings";
    private int mUserId = UserHandle.USER_OWNER;
    private ApplicationsState mApplicationsState;
    // A session is like Settings Apps module
    private Session mSession;
    private Callbacks mCallbacks;
    private HandlerThread mThread;
    private MainHandler mMainHandler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Log.d(TAG, "setUp");
        mApplicationsState = ApplicationsState.getInstance((Application) mContext
                .getApplicationContext());
        mCallbacks = Mockito.mock(Callbacks.class);
        mSession = mApplicationsState.newSession(mCallbacks);
        if (mSession == null) {
            Log.d(TAG, "mSession is null");
        }
    }

    @Override
    protected void tearDown() throws Exception {
        Log.d(TAG, "tearDown");
        mSession.release();
        mMainHandler = null;
        if (mThread != null) {
            mThread.quitSafely();
            mThread = null;
        }
        super.tearDown();
    }

    public void testAddSenssion() {
        assertTrue(mApplicationsState.mSessions.contains(mSession));
        addSession();
        assertTrue(mApplicationsState.mActiveSessions.contains(mSession));
    }

    public void testReleaseSenssion() {
        addSession();
        mSession.release();
        assertFalse(mApplicationsState.mSessions.contains(mSession));
    }

    public void testAddPackage() {
        setMainHandlerWithThread();
        addSession();
        addPackage();
        waitForThreads();

        Mockito.verify(mCallbacks, Mockito.atLeastOnce()).onPackageListChanged();
    }

    public void testRemovePackage() {
        setMainHandlerWithThread();
        addSession();
        addPackage();
        mApplicationsState.removePackage(PACKAGE_NAME, mUserId);
        waitForThreads();

        Mockito.verify(mCallbacks, Mockito.atLeastOnce()).onPackageListChanged();
    }

    public void testSessionResume() {
        setMainHandlerWithThread();
        mSession.resume();
        waitForThreads();

        Mockito.verify(mCallbacks, Mockito.atLeastOnce()).onLoadEntriesCompleted();
    }

    public void testSessionPause() {
        setMainHandlerWithThread();
        mSession.resume();
        waitForThreads();

        mSession.pause();
        assertFalse(mSession.mResumed);
        assertFalse(mApplicationsState.mResumed);
    }

    public void testPackageSizeChanged() {
        setMainHandlerWithThread();
        addSession();
        Message msg = mApplicationsState.mMainHandler.obtainMessage(
                MainHandler.MSG_PACKAGE_SIZE_CHANGED, PACKAGE_NAME);
        mApplicationsState.mMainHandler.sendMessage(msg);
        waitForThreads();

        ArgumentCaptor<String> packageName = ArgumentCaptor.forClass(String.class);
        Mockito.verify(mCallbacks, Mockito.atLeastOnce()).onPackageSizeChanged(
                packageName.capture());
        assertEquals(PACKAGE_NAME, (String) packageName.getValue());
    }

    public void testRunningStateChanged() {
        setMainHandlerWithThread();
        addSession();
        Message msg = mApplicationsState.mMainHandler.obtainMessage(
                MainHandler.MSG_RUNNING_STATE_CHANGED, 0);
        mApplicationsState.mMainHandler.sendMessage(msg);
        waitForThreads();

        ArgumentCaptor<Boolean> state = ArgumentCaptor.forClass(Boolean.class);
        Mockito.verify(mCallbacks, Mockito.atLeastOnce()).onRunningStateChanged(
                state.capture());
        Log.d(TAG, "testRunningStateChanged " + state.getValue());
        assertFalse((boolean) state.getValue());
    }

    private void addSession() {
        mApplicationsState.mSessionsChanged = true;
        mSession.mResumed = true;
        mApplicationsState.rebuildActiveSessions();
    }

    private void addPackage() {
        mApplicationsState.mResumed = true;
        mApplicationsState.addPackage(PACKAGE_NAME, mUserId);
    }

    private void setMainHandlerWithThread() {
        mThread = new HandlerThread("ApplicationsState.tester", Process.THREAD_PRIORITY_BACKGROUND);
        mThread.start();
        mMainHandler = mApplicationsState.getMainHandler(mThread.getLooper());
        mApplicationsState.mMainHandler = mMainHandler;
    }

    private void waitForThreads() {
        try {
            Thread.sleep(1000); // sleep 1s
        } catch (InterruptedException e) {
        }
    }
}
