/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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

package android.telecom;

import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.telecom.InCallService.VideoCall;
import android.view.Surface;

import com.android.internal.os.SomeArgs;
import com.android.internal.telecom.IVideoCallback;
import com.android.internal.telecom.IVideoProvider;

/**
 * Implementation of a Video Call, which allows InCallUi to communicate commands to the underlying
 * {@link Connection.VideoProvider}, and direct callbacks from the
 * {@link Connection.VideoProvider} to the appropriate {@link VideoCall.Listener}.
 *
 * {@hide}
 */
public class VideoCallImpl extends VideoCall {

    private final IVideoProvider mVideoProvider;
    private final VideoCallListenerBinder mBinder;
    private VideoCall.Callback mCallback;
    private int mVideoQuality = VideoProfile.QUALITY_UNKNOWN;
    private int mVideoState = VideoProfile.STATE_AUDIO_ONLY;

    private IBinder.DeathRecipient mDeathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            mVideoProvider.asBinder().unlinkToDeath(this, 0);
        }
    };

    /**
     * IVideoCallback stub implementation.
     */
    private final class VideoCallListenerBinder extends IVideoCallback.Stub {
        @Override
        public void receiveSessionModifyRequest(VideoProfile videoProfile) {
            if (mHandler == null) {
                /// M: add log for debugging.
                logv("receiveSessionModifyRequest");
                return;
            }
            mHandler.obtainMessage(MessageHandler.MSG_RECEIVE_SESSION_MODIFY_REQUEST,
                    videoProfile).sendToTarget();

        }

        @Override
        public void receiveSessionModifyResponse(int status, VideoProfile requestProfile,
                VideoProfile responseProfile) {
            if (mHandler == null) {
                /// M: add log for debugging.
                logv("receiveSessionModifyResponse");
                return;
            }
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = status;
            args.arg2 = requestProfile;
            args.arg3 = responseProfile;
            mHandler.obtainMessage(MessageHandler.MSG_RECEIVE_SESSION_MODIFY_RESPONSE, args)
                    .sendToTarget();
        }

        @Override
        public void handleCallSessionEvent(int event) {
            if (mHandler == null) {
                /// M: add log for debugging.
                logv("handleCallSessionEvent");
                return;
            }
            mHandler.obtainMessage(MessageHandler.MSG_HANDLE_CALL_SESSION_EVENT, event)
                    .sendToTarget();
        }

        @Override
        public void changePeerDimensions(int width, int height) {
            if (mHandler == null) {
                /// M: add log for debugging.
                logv("changePeerDimensions");
                return;
            }
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = width;
            args.arg2 = height;
            mHandler.obtainMessage(MessageHandler.MSG_CHANGE_PEER_DIMENSIONS, args).sendToTarget();
        }

        /* M: ViLTE part start */
        /* Different from AOSP, additional parameter "rotation" is added. */
        @Override
        public void changePeerDimensionsWithAngle(int width, int height, int rotation) {
            if (mHandler == null) {
                logv("changePeerDimensionsWithAngle");
                return;
            }
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = width;
            args.arg2 = height;
            args.arg3 = rotation;
            mHandler.obtainMessage(MessageHandler.MSG_CHANGE_PEER_DIMENSIONS_WITH_ANGLE, args)
                    .sendToTarget();
        }
        /* M: ViLTE part end */

        @Override
        public void changeVideoQuality(int videoQuality) {
            if (mHandler == null) {
                /// M: add log for debugging.
                logv("changeVideoQuality");
                return;
            }
            mHandler.obtainMessage(MessageHandler.MSG_CHANGE_VIDEO_QUALITY, videoQuality, 0)
                    .sendToTarget();
        }

        @Override
        public void changeCallDataUsage(long dataUsage) {
            if (mHandler == null) {
                /// M: add log for debugging.
                logv("changeCallDataUsage");
                return;
            }
            mHandler.obtainMessage(MessageHandler.MSG_CHANGE_CALL_DATA_USAGE, dataUsage)
                    .sendToTarget();
        }

        @Override
        public void changeCameraCapabilities(VideoProfile.CameraCapabilities cameraCapabilities) {
            if (mHandler == null) {
                /// M: add log for debugging.
                logv("changeCameraCapabilities");
                return;
            }
            mHandler.obtainMessage(MessageHandler.MSG_CHANGE_CAMERA_CAPABILITIES,
                    cameraCapabilities).sendToTarget();
        }
    }

    /** Default handler used to consolidate binder method calls onto a single thread. */
    private final class MessageHandler extends Handler {
        private static final int MSG_RECEIVE_SESSION_MODIFY_REQUEST = 1;
        private static final int MSG_RECEIVE_SESSION_MODIFY_RESPONSE = 2;
        private static final int MSG_HANDLE_CALL_SESSION_EVENT = 3;
        private static final int MSG_CHANGE_PEER_DIMENSIONS = 4;
        private static final int MSG_CHANGE_CALL_DATA_USAGE = 5;
        private static final int MSG_CHANGE_CAMERA_CAPABILITIES = 6;
        private static final int MSG_CHANGE_VIDEO_QUALITY = 7;
        /* M: ViLTE part start */
        private static final int MSG_MTK_BASE = 100;
        private static final int MSG_CHANGE_PEER_DIMENSIONS_WITH_ANGLE = MSG_MTK_BASE;
        /* M: ViLTE part end */

        public MessageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (mCallback == null) {
                return;
            }

            SomeArgs args;
            switch (msg.what) {
                case MSG_RECEIVE_SESSION_MODIFY_REQUEST:
                    mCallback.onSessionModifyRequestReceived((VideoProfile) msg.obj);
                    break;
                case MSG_RECEIVE_SESSION_MODIFY_RESPONSE:
                    args = (SomeArgs) msg.obj;
                    try {
                        int status = (int) args.arg1;
                        VideoProfile requestProfile = (VideoProfile) args.arg2;
                        VideoProfile responseProfile = (VideoProfile) args.arg3;

                        mCallback.onSessionModifyResponseReceived(
                                status, requestProfile, responseProfile);
                    } finally {
                        args.recycle();
                    }
                    break;
                case MSG_HANDLE_CALL_SESSION_EVENT:
                    mCallback.onCallSessionEvent((int) msg.obj);
                    break;
                case MSG_CHANGE_PEER_DIMENSIONS:
                    args = (SomeArgs) msg.obj;
                    try {
                        int width = (int) args.arg1;
                        int height = (int) args.arg2;
                        mCallback.onPeerDimensionsChanged(width, height);
                    } finally {
                        args.recycle();
                    }
                    break;
                /* M: ViLTE part start */
                /* Different from AOSP, additional parameter "rotation" is added. */
                case MSG_CHANGE_PEER_DIMENSIONS_WITH_ANGLE:
                    args = (SomeArgs) msg.obj;
                    try {
                        int width = (int) args.arg1;
                        int height = (int) args.arg2;
                        int rotation = (int) args.arg3;
                        mCallback.onPeerDimensionsWithAngleChanged(width, height, rotation);
                    } finally {
                        args.recycle();
                    }
                    break;
                /* M: ViLTE part end */
                case MSG_CHANGE_CALL_DATA_USAGE:
                    mCallback.onCallDataUsageChanged((long) msg.obj);
                    break;
                case MSG_CHANGE_CAMERA_CAPABILITIES:
                    mCallback.onCameraCapabilitiesChanged(
                            (VideoProfile.CameraCapabilities) msg.obj);
                    break;
                case MSG_CHANGE_VIDEO_QUALITY:
                    mVideoQuality = msg.arg1;
                    mCallback.onVideoQualityChanged(msg.arg1);
                    break;
                default:
                    break;
            }
        }
    };

    private Handler mHandler;

    VideoCallImpl(IVideoProvider videoProvider) throws RemoteException {
        mVideoProvider = videoProvider;
        mVideoProvider.asBinder().linkToDeath(mDeathRecipient, 0);

        mBinder = new VideoCallListenerBinder();
        mVideoProvider.addVideoCallback(mBinder);
        /// M: add log for debugging.
        logv("[VideoCallImpl]mBinder=" + mBinder);
    }

    public void destroy() {
        /// M: add log for debugging.
        logv("[destroy]");
        unregisterCallback(mCallback);
    }

    /** {@inheritDoc} */
    public void registerCallback(VideoCall.Callback callback) {
        /// M: add log for debugging.
        logv("[registerCallback]");
        registerCallback(callback, null);
    }

    /** {@inheritDoc} */
    public void registerCallback(VideoCall.Callback callback, Handler handler) {
        mCallback = callback;
        if (handler == null) {
            mHandler = new MessageHandler(Looper.getMainLooper());
        } else {
            mHandler = new MessageHandler(handler.getLooper());
        }
    }

    /** {@inheritDoc} */
    public void unregisterCallback(VideoCall.Callback callback) {
        /// M: add log for debugging.
        logv("[unregisterCallback]");
        if (callback != mCallback) {
            return;
        }

        mCallback = null;
        try {
            mVideoProvider.removeVideoCallback(mBinder);
        } catch (RemoteException e) {
        }
    }

    /** {@inheritDoc} */
    public void setCamera(String cameraId) {
        /// M: add log for debugging.
        logv("[setCamera]cameraId = " + cameraId);
        try {
            mVideoProvider.setCamera(cameraId);
        } catch (RemoteException e) {
        }
    }

    /** {@inheritDoc} */
    public void setPreviewSurface(Surface surface) {
        /// M: add log for debugging.
        logv("[setPreviewSurface]preview = " + surface);
        try {
            mVideoProvider.setPreviewSurface(surface);
        } catch (RemoteException e) {
        }
    }

    /** {@inheritDoc} */
    public void setDisplaySurface(Surface surface) {
        /// M: add log for debugging.
        logv("[setDisplaySurface]display = " + surface);
        try {
            mVideoProvider.setDisplaySurface(surface);
        } catch (RemoteException e) {
        }
    }

    /** {@inheritDoc} */
    public void setDeviceOrientation(int rotation) {
        /// M: add log for debugging.
        logv("[setDeviceOrientation]rotation = " + rotation);
        try {
            mVideoProvider.setDeviceOrientation(rotation);
        } catch (RemoteException e) {
        }
    }

    /** {@inheritDoc} */
    public void setZoom(float value) {
        /// M: add log for debugging.
        logv("[setZoom]value = " + value);
        try {
            mVideoProvider.setZoom(value);
        } catch (RemoteException e) {
        }
    }

    /**
     * Sends a session modification request to the video provider.
     * <p>
     * The {@link InCallService} will create the {@code requestProfile} based on the current
     * video state (i.e. {@link Call.Details#getVideoState()}).  It is, however, possible that the
     * video state maintained by the {@link InCallService} could get out of sync with what is known
     * by the {@link android.telecom.Connection.VideoProvider}.  To remove ambiguity, the
     * {@link VideoCallImpl} passes along the pre-modify video profile to the {@code VideoProvider}
     * to ensure it has full context of the requested change.
     *
     * @param requestProfile The requested video profile.
     */
    public void sendSessionModifyRequest(VideoProfile requestProfile) {
        try {
            VideoProfile originalProfile = new VideoProfile(mVideoState, mVideoQuality);

            /// M: add log for debugging.
            logv("[sendSessionModifyRequest]current: " + originalProfile
                    + ", requesting: " + requestProfile);
            mVideoProvider.sendSessionModifyRequest(originalProfile, requestProfile);
        } catch (RemoteException e) {
        }
    }

    /** {@inheritDoc} */
    public void sendSessionModifyResponse(VideoProfile responseProfile) {
        /// M: add log for debugging.
        logv("[sendSessionModifyResponse]response: " + responseProfile);
        try {
            mVideoProvider.sendSessionModifyResponse(responseProfile);
        } catch (RemoteException e) {
        }
    }

    /** {@inheritDoc} */
    public void requestCameraCapabilities() {
        /// M: add log for debugging.
        logv("[requestCameraCapabilities]");
        try {
            mVideoProvider.requestCameraCapabilities();
        } catch (RemoteException e) {
        }
    }

    /** {@inheritDoc} */
    public void requestCallDataUsage() {
        /// M: add log for debugging.
        logv("[requestCallDataUsage]");
        try {
            mVideoProvider.requestCallDataUsage();
        } catch (RemoteException e) {
        }
    }

    /** {@inheritDoc} */
    public void setPauseImage(Uri uri) {
        /// M: add log for debugging.
        logv("[setPauseImage]uri: " + uri);
        try {
            mVideoProvider.setPauseImage(uri);
        } catch (RemoteException e) {
        }
    }

    /**
     * Sets the video state for the current video call.
     * @param videoState the new video state.
     */
    public void setVideoState(int videoState) {
        mVideoState = videoState;
    }

    /* M: ViLTE part start */
    /** {@inheritDoc} */
    public void setUIMode(int mode) {
        logv("[setUIMode]mode: " + mode);
        try {
            mVideoProvider.setUIMode(mode);
        } catch (RemoteException e) {
        }
    }
    /* M: ViLTE part end */

    /// M: log utility function. @{
    private void logv(String msg) {
        Log.v(this, msg + ", " + this);
    }
    /// @}
}
