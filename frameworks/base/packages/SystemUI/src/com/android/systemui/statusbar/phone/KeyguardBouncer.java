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
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import android.app.ActivityManager;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Slog;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;

import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardHostView;
import com.android.keyguard.KeyguardSecurityModel;
import com.android.keyguard.KeyguardSecurityView;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.keyguard.R;
import com.android.keyguard.ViewMediatorCallback;

import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.DejankUtils;
import com.android.systemui.classifier.FalsingManager;

import com.mediatek.keyguard.PowerOffAlarm.PowerOffAlarmManager;

import static com.android.keyguard.KeyguardHostView.OnDismissAction;
import static com.android.keyguard.KeyguardSecurityModel.SecurityMode;

/**
 * A class which manages the bouncer on the lockscreen.
 */
public class KeyguardBouncer {

    private final String TAG = "KeyguardBouncer";
    private final boolean DEBUG = true;

    private Context mContext;
    private ViewMediatorCallback mCallback;
    private LockPatternUtils mLockPatternUtils;
    private ViewGroup mContainer;
    private StatusBarWindowManager mWindowManager;
    protected KeyguardHostView mKeyguardView;
    protected ViewGroup mRoot;
    private boolean mShowingSoon;
    private int mBouncerPromptReason;
    private FalsingManager mFalsingManager;
    private KeyguardUpdateMonitorCallback mUpdateMonitorCallback =
            new KeyguardUpdateMonitorCallback() {
                @Override
                public void onStrongAuthStateChanged(int userId) {
                    mBouncerPromptReason = mCallback.getBouncerPromptReason();
                }
            };

    /// M: use securityModel to check if it needs to show full screen mode
    private KeyguardSecurityModel mSecurityModel;

    private ViewGroup mNotificationPanel ;

    public KeyguardBouncer(Context context, ViewMediatorCallback callback,
            LockPatternUtils lockPatternUtils, StatusBarWindowManager windowManager,
            ViewGroup container) {
        mContext = context;
        mCallback = callback;
        mLockPatternUtils = lockPatternUtils;
        mContainer = container;
        mWindowManager = windowManager;
        mNotificationPanel = (ViewGroup) mContainer.findViewById(R.id.notification_panel);

        mSecurityModel = new KeyguardSecurityModel(mContext);
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mUpdateMonitorCallback);
        mFalsingManager = FalsingManager.getInstance(mContext);
    }

    ///M:
    public void show(boolean resetSecuritySelection) {
        final int keyguardUserId = KeyguardUpdateMonitor.getCurrentUser();
        if (keyguardUserId == UserHandle.USER_SYSTEM && UserManager.isSplitSystemUser()) {
            // In split system user mode, we never unlock system user.
            return;
        }
        mFalsingManager.onBouncerShown();
        ensureView();
        show(resetSecuritySelection, false) ;
    }

    /**
     * show bouncer.
     * @param resetSecuritySelection need to reset.
     * @param authenticated authenticated or not.
     */
    public void show(boolean resetSecuritySelection, boolean authenticated) {
        if (DEBUG) {
            Log.d(TAG, "show(resetSecuritySelection = " + resetSecuritySelection);
        }

        if (PowerOffAlarmManager.isAlarmBoot()) {
            Log.d(TAG, "show() - this is alarm boot, just re-inflate.") ;
            /// M: fix ALPS01865324, we should call KeyguardPasswordView.onPause() to hide IME
            ///    before the KeyguardPasswordView is gone.
            if (mKeyguardView != null && mRoot != null) {
                Log.d(TAG, "show() - before re-inflate, we should pause current view.") ;
                mKeyguardView.onPause();
            }
            // force to remove views.
            inflateView() ;
        } else {
            ensureView();
        }

        if (resetSecuritySelection) {
            // showPrimarySecurityScreen() updates the current security method. This is needed in
            // case we are already showing and the current security method changed.
            mKeyguardView.showPrimarySecurityScreen();
        }
        if (mRoot.getVisibility() == View.VISIBLE || mShowingSoon) {
            return;
        }

        final int activeUserId = ActivityManager.getCurrentUser();
        final int keyguardUserId = KeyguardUpdateMonitor.getCurrentUser();
        final boolean allowDismissKeyguard =
                !(UserManager.isSplitSystemUser() && activeUserId == UserHandle.USER_SYSTEM)
                && activeUserId == keyguardUserId;
        // If allowed, try to dismiss the Keyguard. If no security auth (password/pin/pattern) is
        // set, this will dismiss the whole Keyguard. Otherwise, show the bouncer.
        if (allowDismissKeyguard && mKeyguardView.dismiss()) {
            return;
        }
        // This condition may indicate an error on Android, so log it.
        if (!allowDismissKeyguard) {
            Slog.w(TAG, "User can't dismiss keyguard: " + activeUserId + " != " + keyguardUserId);
        }
        // Try to dismiss the Keyguard. If no security pattern is set, this will dismiss the whole
        // Keyguard. If we need to authenticate, show the bouncer.
        if (!mKeyguardView.dismiss(authenticated)) {
            if (DEBUG) {
                Log.d(TAG, "show() - try to dismiss \"Bouncer\" directly.") ;
            }

            mShowingSoon = true;

            // Split up the work over multiple frames.
            DejankUtils.postAfterTraversal(mShowRunnable);
        }
    }

    private final Runnable mShowRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "mShowRunnable.run() is called.") ;
            mRoot.setVisibility(View.VISIBLE);
            mKeyguardView.onResume();
            showPromptReason(mBouncerPromptReason);
            if (mKeyguardView.getHeight() != 0) {
                mKeyguardView.startAppearAnimation();
            } else {
                mKeyguardView.getViewTreeObserver().addOnPreDrawListener(
                        new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                mKeyguardView.getViewTreeObserver().removeOnPreDrawListener(this);
                                mKeyguardView.startAppearAnimation();
                                return true;
                            }
                        });
                mKeyguardView.requestLayout();
            }
            mShowingSoon = false;
            mKeyguardView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }
    };

    /**
     * Show a string explaining why the security view needs to be solved.
     *
     * @param reason a flag indicating which string should be shown, see
     *               {@link KeyguardSecurityView#PROMPT_REASON_NONE}
     *               and {@link KeyguardSecurityView#PROMPT_REASON_RESTART}
     */
    public void showPromptReason(int reason) {
        mKeyguardView.showPromptReason(reason);
    }

    public void showMessage(String message, int color) {
        mKeyguardView.showMessage(message, color);
    }

    private void cancelShowRunnable() {
        DejankUtils.removeCallbacks(mShowRunnable);
        mShowingSoon = false;
    }

    public void showWithDismissAction(OnDismissAction r) {
        ensureView();
        mKeyguardView.setOnDismissAction(r);
        show(false /* resetSecuritySelection */);
    }

    public void showWithDismissAction(OnDismissAction r, Runnable cancelAction) {
        if (DEBUG) Log.d(TAG, "showWithDismissAction() is called.");
        ensureView();
        mKeyguardView.setOnDismissAction(r, cancelAction);
        show(false /* resetSecuritySelection */);
    }

    public void hide(boolean destroyView) {
        mFalsingManager.onBouncerHidden();
        if (DEBUG) {
            Log.d(TAG, "hide() is called, destroyView = " + destroyView) ;
        }

        cancelShowRunnable();
        if (mKeyguardView != null) {
            mKeyguardView.cancelDismissAction();
            mKeyguardView.cleanUp();
        }
        if (destroyView) {
            if (DEBUG) Log.d(TAG, "call removeView()") ;
            removeView();
        } else if (mRoot != null) {
            if (DEBUG) Log.d(TAG, "just set keyguard Invisible.") ;
            mRoot.setVisibility(View.INVISIBLE);
        }

        /// M: [ALPS01748966] true place that user has left keyguard.
        // If the alternate unlock was suppressed, it can now be safely
        // enabled because the user has left keyguard.
        Log.d(TAG, "hide() - user has left keyguard, setAlternateUnlockEnabled(true)") ;
        KeyguardUpdateMonitor.getInstance(mContext).setAlternateUnlockEnabled(true);
    }

    /**
     * See {@link StatusBarKeyguardViewManager#startPreHideAnimation}.
     */
    public void startPreHideAnimation(Runnable runnable) {
        if (mKeyguardView != null) {
            mKeyguardView.startDisappearAnimation(runnable);
        } else if (runnable != null) {
            runnable.run();
        }
    }

    /**
     * Reset the state of the view.
     */
    public void reset() {
        cancelShowRunnable();
        inflateView();
        mFalsingManager.onBouncerHidden();
    }

    public void onScreenTurnedOff() {
        if (mKeyguardView != null && mRoot != null && mRoot.getVisibility() == View.VISIBLE) {
            mKeyguardView.onScreenTurnedOff();
            mKeyguardView.onPause();
        }
    }

    public long getUserActivityTimeout() {
        /*if (mKeyguardView != null) {
            long timeout = mKeyguardView.getUserActivityTimeout();
            if (timeout >= 0) {
                return timeout;
            }
        }*/
        return KeyguardViewMediator.AWAKE_INTERVAL_DEFAULT_MS;
    }

    public boolean isShowing() {
        return mShowingSoon || (mRoot != null && mRoot.getVisibility() == View.VISIBLE);
    }

    public void prepare() {
        boolean wasInitialized = mRoot != null;
        ensureView();
        if (wasInitialized) {
            mKeyguardView.showPrimarySecurityScreen();
        }
        mBouncerPromptReason = mCallback.getBouncerPromptReason();
    }

    protected void ensureView() {
        if (mRoot == null) {
            inflateView();
        }
    }

    protected void inflateView() {
        if (DEBUG) {
            Log.d(TAG, "inflateView() is called, we force to re-inflate the \"Bouncer\" view.");
        }

        removeView();
        mRoot = (ViewGroup) LayoutInflater.from(mContext).inflate(R.layout.keyguard_bouncer, null);
        mKeyguardView = (KeyguardHostView) mRoot.findViewById(R.id.keyguard_host_view);
        mKeyguardView.setLockPatternUtils(mLockPatternUtils);
        mKeyguardView.setViewMediatorCallback(mCallback);
        mKeyguardView.setNotificationPanelView(mNotificationPanel) ;
        mContainer.addView(mRoot, mContainer.getChildCount());
        mRoot.setVisibility(View.INVISIBLE);
        mRoot.setSystemUiVisibility(View.STATUS_BAR_DISABLE_HOME);
    }

    protected void removeView() {
        if (mRoot != null && mRoot.getParent() == mContainer) {

            Log.d(TAG, "removeView() - really remove all views.") ;

            mContainer.removeView(mRoot);
            mRoot = null;
        }
    }

    public boolean onBackPressed() {
        return mKeyguardView != null && mKeyguardView.handleBackKey();
    }

    /**
     * @return True if and only if the security method should be shown before showing the
     * notifications on Keyguard, like SIM PIN/PUK.
     */
    public boolean needsFullscreenBouncer() {
        ensureView();
        /*if (mKeyguardView != null) {
            SecurityMode mode = mKeyguardView.getSecurityMode();
            return mode == SecurityMode.SimPin || mode == SecurityMode.SimPuk;
        }
        return false; */
        SecurityMode mode = mSecurityModel.getSecurityMode();
        return mode == SecurityMode.SimPinPukMe1
                || mode == SecurityMode.SimPinPukMe2
                || mode == SecurityMode.SimPinPukMe3
                || mode == SecurityMode.SimPinPukMe4
                || mode == SecurityMode.AntiTheft
                || mode == SecurityMode.AlarmBoot;
    }

    /**
     * Like {@link #needsFullscreenBouncer}, but uses the currently visible security method, which
     * makes this method much faster.
     */
    public boolean isFullscreenBouncer() {
        if (mKeyguardView != null) {
            SecurityMode mode = mKeyguardView.getCurrentSecurityMode();
            return mode == SecurityMode.SimPinPukMe1
                || mode == SecurityMode.SimPinPukMe2
                || mode == SecurityMode.SimPinPukMe3
                || mode == SecurityMode.SimPinPukMe4
                || mode == SecurityMode.AntiTheft
                || mode == SecurityMode.AlarmBoot;
        }
        return false;
    }

    /**
     * WARNING: This method might cause Binder calls.
     */
    public boolean isSecure() {
        return mKeyguardView == null || mKeyguardView.getSecurityMode() != SecurityMode.None;
    }

    public boolean shouldDismissOnMenuPressed() {
        return mKeyguardView.shouldEnableMenuKey();
    }

    public boolean interceptMediaKey(KeyEvent event) {
        ensureView();
        return mKeyguardView.interceptMediaKey(event);
    }

    public void notifyKeyguardAuthenticated(boolean strongAuth) {
        ensureView();
        mKeyguardView.finish(strongAuth);
    }
}
