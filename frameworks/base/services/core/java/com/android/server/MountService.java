/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.server;

import static com.android.internal.util.XmlUtils.readBooleanAttribute;
import static com.android.internal.util.XmlUtils.readIntAttribute;
import static com.android.internal.util.XmlUtils.readLongAttribute;
import static com.android.internal.util.XmlUtils.readStringAttribute;
import static com.android.internal.util.XmlUtils.writeBooleanAttribute;
import static com.android.internal.util.XmlUtils.writeIntAttribute;
import static com.android.internal.util.XmlUtils.writeLongAttribute;
import static com.android.internal.util.XmlUtils.writeStringAttribute;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import android.Manifest;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.IPackageMoveObserver;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.UserInfo;
import android.content.res.Configuration;
import android.content.res.ObbInfo;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Binder;
import android.os.DropBoxManager;
import android.os.Environment;
import android.os.Environment.UserEnvironment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.DiskInfo;
import android.os.storage.IMountService;
import android.os.storage.IMountServiceListener;
import android.os.storage.IMountShutdownObserver;
import android.os.storage.IObbActionListener;
import android.os.storage.MountServiceInternal;
import android.os.storage.OnObbStateChangeListener;
import android.os.storage.StorageManager;
import android.os.storage.StorageResultCode;
import android.os.storage.StorageVolume;
import android.os.storage.VolumeInfo;
import android.os.storage.VolumeRecord;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Slog;
import android.util.TimeUtils;
import android.util.Xml;
import android.widget.Toast;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.app.IMediaContainerService;
import com.android.internal.os.SomeArgs;
import com.android.internal.os.Zygote;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.HexDump;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.Preconditions;
import com.android.internal.widget.LockPatternUtils;
import com.android.server.NativeDaemonConnector.Command;
import com.android.server.NativeDaemonConnector.SensitiveArg;
import com.android.server.pm.PackageManagerService;

import libcore.io.IoUtils;
import libcore.util.EmptyArray;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.google.android.collect.Lists;

import com.mediatek.storage.StorageManagerEx;

/**
 * Service responsible for various storage media. Connects to {@code vold} to
 * watch for and manage dynamically added storage, such as SD cards and USB mass
 * storage. Also decides how storage should be presented to users on the device.
 */
class MountService extends IMountService.Stub
        implements INativeDaemonConnectorCallbacks, Watchdog.Monitor {

    // Static direct instance pointer for the tightly-coupled idle service to use
    static MountService sSelf = null;

    public static class Lifecycle extends SystemService {
        private MountService mMountService;
        private String oldDefaultPath = "";

        public Lifecycle(Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            Slog.d(TAG, "MountService onStart");
            sSelf.isBootingPhase = true;
            mMountService = new MountService(getContext());
            publishBinderService("mount", mMountService);
            mMountService.start();
            oldDefaultPath = sSelf.getDefaultPath();
            Slog.d(TAG, "get Default path onStart default path=" + oldDefaultPath);
        }

        @Override
        public void onBootPhase(int phase) {
            Slog.d(TAG, "MountService onBootPhase");
            if (phase == SystemService.PHASE_ACTIVITY_MANAGER_READY) {
                mMountService.systemReady();
            } else if (phase == SystemService.PHASE_BOOT_COMPLETED) {
                mMountService.bootCompleted();
            }
            if (phase == SystemService.PHASE_BOOT_COMPLETED) {
                Slog.d(TAG, "MountService onBootPhase: PHASE_BOOT_COMPLETED");
                sSelf.isBootingPhase = false;
                if (!oldDefaultPath.contains("emulated") && !"".equals(oldDefaultPath)) {
                    Slog.d(TAG, "set defaut path to " + oldDefaultPath);
                    sSelf.setDefaultPath(oldDefaultPath);
                    sSelf.updateDefaultPathIfNeed();
                }
            }
        }

        @Override
        public void onSwitchUser(int userHandle) {
            mMountService.mCurrentUserId = userHandle;
        }

        @Override
        public void onUnlockUser(int userHandle) {
            Slog.d(TAG, "MountService onUnlockUser, userHandle=" + userHandle);
            mMountService.onUnlockUser(userHandle);
        }

        @Override
        public void onCleanupUser(int userHandle) {
            Slog.d(TAG, "MountService onCleanupUser, userHandle=" + userHandle);
            mMountService.onCleanupUser(userHandle);
        }
    }

    private static final boolean DEBUG_EVENTS = true;
    private static final boolean DEBUG_OBB = true;

    // Disable this since it messes up long-running cryptfs operations.
    private static final boolean WATCHDOG_ENABLE = false;

    private static final String TAG = "MountService";

    private static final String TAG_STORAGE_BENCHMARK = "storage_benchmark";
    private static final String TAG_STORAGE_TRIM = "storage_trim";

    private static final String VOLD_TAG = "VoldConnector";
    private static final String CRYPTD_TAG = "CryptdConnector";

    /** Maximum number of ASEC containers allowed to be mounted. */
    private static final int MAX_CONTAINERS = 250;

    /** Magic value sent by MoveTask.cpp */
    private static final int MOVE_STATUS_COPY_FINISHED = 82;

    /*
     * Internal vold response code constants
     */
    class VoldResponseCode {
        /*
         * 100 series - Requestion action was initiated; expect another reply
         *              before proceeding with a new command.
         */
        public static final int VolumeListResult               = 110;
        public static final int AsecListResult                 = 111;
        public static final int StorageUsersListResult         = 112;
        public static final int CryptfsGetfieldResult          = 113;

        /*
         * 200 series - Requestion action has been successfully completed.
         */
        public static final int ShareStatusResult              = 210;
        public static final int AsecPathResult                 = 211;
        public static final int ShareEnabledResult             = 212;

        /*
         * 400 series - Command was accepted, but the requested action
         *              did not take place.
         */
        public static final int OpFailedNoMedia                = 401;
        public static final int OpFailedMediaBlank             = 402;
        public static final int OpFailedMediaCorrupt           = 403;
        public static final int OpFailedVolNotMounted          = 404;
        public static final int OpFailedStorageBusy            = 405;
        public static final int OpFailedStorageNotFound        = 406;

        /*
         * 600 series - Unsolicited broadcasts.
         */
        public static final int DISK_CREATED = 640;
        public static final int DISK_SIZE_CHANGED = 641;
        public static final int DISK_LABEL_CHANGED = 642;
        public static final int DISK_SCANNED = 643;
        public static final int DISK_SYS_PATH_CHANGED = 644;
        public static final int DISK_DESTROYED = 649;

        public static final int VOLUME_CREATED = 650;
        public static final int VOLUME_STATE_CHANGED = 651;
        public static final int VOLUME_FS_TYPE_CHANGED = 652;
        public static final int VOLUME_FS_UUID_CHANGED = 653;
        public static final int VOLUME_FS_LABEL_CHANGED = 654;
        public static final int VOLUME_PATH_CHANGED = 655;
        public static final int VOLUME_INTERNAL_PATH_CHANGED = 656;
        public static final int VOLUME_DESTROYED = 659;

        public static final int MOVE_STATUS = 660;
        public static final int BENCHMARK_RESULT = 661;
        public static final int TRIM_RESULT = 662;
    }

    private static final int VERSION_INIT = 1;
    private static final int VERSION_ADD_PRIMARY = 2;
    private static final int VERSION_FIX_PRIMARY = 3;

    private static final String TAG_VOLUMES = "volumes";
    private static final String ATTR_VERSION = "version";
    private static final String ATTR_PRIMARY_STORAGE_UUID = "primaryStorageUuid";
    private static final String ATTR_FORCE_ADOPTABLE = "forceAdoptable";
    private static final String TAG_VOLUME = "volume";
    private static final String ATTR_TYPE = "type";
    private static final String ATTR_FS_UUID = "fsUuid";
    private static final String ATTR_PART_GUID = "partGuid";
    private static final String ATTR_NICKNAME = "nickname";
    private static final String ATTR_USER_FLAGS = "userFlags";
    private static final String ATTR_CREATED_MILLIS = "createdMillis";
    private static final String ATTR_LAST_TRIM_MILLIS = "lastTrimMillis";
    private static final String ATTR_LAST_BENCH_MILLIS = "lastBenchMillis";

    private final AtomicFile mSettingsFile;

    /**
     * <em>Never</em> hold the lock while performing downcalls into vold, since
     * unsolicited events can suddenly appear to update data structures.
     */
    private final Object mLock = new Object();

    /** Set of users that we know are unlocked. */
    @GuardedBy("mLock")
    private int[] mLocalUnlockedUsers = EmptyArray.INT;
    /** Set of users that system knows are unlocked. */
    @GuardedBy("mLock")
    private int[] mSystemUnlockedUsers = EmptyArray.INT;

    /** Map from disk ID to disk */
    @GuardedBy("mLock")
    private ArrayMap<String, DiskInfo> mDisks = new ArrayMap<>();
    /** Map from volume ID to disk */
    @GuardedBy("mLock")
    private final ArrayMap<String, VolumeInfo> mVolumes = new ArrayMap<>();

    /** Map from UUID to record */
    @GuardedBy("mLock")
    private ArrayMap<String, VolumeRecord> mRecords = new ArrayMap<>();
    @GuardedBy("mLock")
    private String mPrimaryStorageUuid;
    @GuardedBy("mLock")
    private boolean mForceAdoptable;

    /** Map from disk ID to latches */
    @GuardedBy("mLock")
    private ArrayMap<String, CountDownLatch> mDiskScanLatches = new ArrayMap<>();

    @GuardedBy("mLock")
    private IPackageMoveObserver mMoveCallback;
    @GuardedBy("mLock")
    private String mMoveTargetUuid;

    private volatile int mCurrentUserId = UserHandle.USER_SYSTEM;

    private VolumeInfo findVolumeByIdOrThrow(String id) {
        synchronized (mLock) {
            final VolumeInfo vol = mVolumes.get(id);
            if (vol != null) {
                return vol;
            }
        }
        throw new IllegalArgumentException("No volume found for ID " + id);
    }

    private String findVolumeIdForPathOrThrow(String path) {
        synchronized (mLock) {
            for (int i = 0; i < mVolumes.size(); i++) {
                final VolumeInfo vol = mVolumes.valueAt(i);
                if (vol.path != null && path.startsWith(vol.path)) {
                    return vol.id;
                }
            }
        }
        throw new IllegalArgumentException("No volume found for path " + path);
    }

    private VolumeRecord findRecordForPath(String path) {
        synchronized (mLock) {
            for (int i = 0; i < mVolumes.size(); i++) {
                final VolumeInfo vol = mVolumes.valueAt(i);
                if (vol.path != null && path.startsWith(vol.path)) {
                    return mRecords.get(vol.fsUuid);
                }
            }
        }
        return null;
    }

    private String scrubPath(String path) {
        if (path.startsWith(Environment.getDataDirectory().getAbsolutePath())) {
            return "internal";
        }
        final VolumeRecord rec = findRecordForPath(path);
        if (rec == null || rec.createdMillis == 0) {
            return "unknown";
        } else {
            return "ext:" + (int) ((System.currentTimeMillis() - rec.createdMillis)
                    / DateUtils.WEEK_IN_MILLIS) + "w";
        }
    }

    private @Nullable VolumeInfo findStorageForUuid(String volumeUuid) {
        final StorageManager storage = mContext.getSystemService(StorageManager.class);
        if (Objects.equals(StorageManager.UUID_PRIVATE_INTERNAL, volumeUuid)) {
            return storage.findVolumeById(VolumeInfo.ID_EMULATED_INTERNAL);
        } else if (Objects.equals(StorageManager.UUID_PRIMARY_PHYSICAL, volumeUuid)) {
            return storage.getPrimaryPhysicalVolume();
        } else {
            return storage.findEmulatedForPrivate(storage.findVolumeByUuid(volumeUuid));
        }
    }

    private boolean shouldBenchmark() {
        final long benchInterval = Settings.Global.getLong(mContext.getContentResolver(),
                Settings.Global.STORAGE_BENCHMARK_INTERVAL, DateUtils.WEEK_IN_MILLIS);
        if (benchInterval == -1) {
            return false;
        } else if (benchInterval == 0) {
            return true;
        }

        synchronized (mLock) {
            for (int i = 0; i < mVolumes.size(); i++) {
                final VolumeInfo vol = mVolumes.valueAt(i);
                final VolumeRecord rec = mRecords.get(vol.fsUuid);
                if (vol.isMountedWritable() && rec != null) {
                    final long benchAge = System.currentTimeMillis() - rec.lastBenchMillis;
                    if (benchAge >= benchInterval) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private CountDownLatch findOrCreateDiskScanLatch(String diskId) {
        synchronized (mLock) {
            CountDownLatch latch = mDiskScanLatches.get(diskId);
            if (latch == null) {
                latch = new CountDownLatch(1);
                mDiskScanLatches.put(diskId, latch);
            }
            return latch;
        }
    }

    private static String escapeNull(String arg) {
        if (TextUtils.isEmpty(arg)) {
            return "!";
        } else {
            if (arg.indexOf('\0') != -1 || arg.indexOf(' ') != -1) {
                throw new IllegalArgumentException(arg);
            }
            return arg;
        }
    }

    /** List of crypto types.
      * These must match CRYPT_TYPE_XXX in cryptfs.h AND their
      * corresponding commands in CommandListener.cpp */
    public static final String[] CRYPTO_TYPES
        = { "password", "default", "pattern", "pin" };

    private final Context mContext;

    private final NativeDaemonConnector mConnector;
    private final NativeDaemonConnector mCryptConnector;

    private final Thread mConnectorThread;
    private final Thread mCryptConnectorThread;

    private volatile boolean mSystemReady = false;
    private volatile boolean mBootCompleted = false;
    private volatile boolean mDaemonConnected = false;

    private PackageManagerService mPms;

    private final Callbacks mCallbacks;
    private final LockPatternUtils mLockPatternUtils;

    // Two connectors - mConnector & mCryptConnector
    private final CountDownLatch mConnectedSignal = new CountDownLatch(2);
    private final CountDownLatch mAsecsScanned = new CountDownLatch(1);

    private final Object mUnmountLock = new Object();
    @GuardedBy("mUnmountLock")
    private CountDownLatch mUnmountSignal;

    /**
     * Private hash of currently mounted secure containers.
     * Used as a lock in methods to manipulate secure containers.
     */
    final private HashSet<String> mAsecMountSet = new HashSet<String>();

    /**
     * The size of the crypto algorithm key in bits for OBB files. Currently
     * Twofish is used which takes 128-bit keys.
     */
    private static final int CRYPTO_ALGORITHM_KEY_SIZE = 128;

    /**
     * The number of times to run SHA1 in the PBKDF2 function for OBB files.
     * 1024 is reasonably secure and not too slow.
     */
    private static final int PBKDF2_HASH_ROUNDS = 1024;

    /**
     * Mounted OBB tracking information. Used to track the current state of all
     * OBBs.
     */
    final private Map<IBinder, List<ObbState>> mObbMounts = new HashMap<IBinder, List<ObbState>>();

    /** Map from raw paths to {@link ObbState}. */
    final private Map<String, ObbState> mObbPathToStateMap = new HashMap<String, ObbState>();

    // Not guarded by a lock.
    private final MountServiceInternalImpl mMountServiceInternal = new MountServiceInternalImpl();

    class ObbState implements IBinder.DeathRecipient {
        public ObbState(String rawPath, String canonicalPath, int callingUid,
                IObbActionListener token, int nonce) {
            this.rawPath = rawPath;
            this.canonicalPath = canonicalPath;

            this.ownerGid = UserHandle.getSharedAppGid(callingUid);
            this.token = token;
            this.nonce = nonce;
        }

        final String rawPath;
        final String canonicalPath;

        final int ownerGid;

        // Token of remote Binder caller
        final IObbActionListener token;

        // Identifier to pass back to the token
        final int nonce;

        public IBinder getBinder() {
            return token.asBinder();
        }

        @Override
        public void binderDied() {
            ObbAction action = new UnmountObbAction(this, true);
            mObbActionHandler.sendMessage(mObbActionHandler.obtainMessage(OBB_RUN_ACTION, action));
        }

        public void link() throws RemoteException {
            getBinder().linkToDeath(this, 0);
        }

        public void unlink() {
            getBinder().unlinkToDeath(this, 0);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("ObbState{");
            sb.append("rawPath=").append(rawPath);
            sb.append(",canonicalPath=").append(canonicalPath);
            sb.append(",ownerGid=").append(ownerGid);
            sb.append(",token=").append(token);
            sb.append(",binder=").append(getBinder());
            sb.append('}');
            return sb.toString();
        }
    }

    // OBB Action Handler
    final private ObbActionHandler mObbActionHandler;

    // OBB action handler messages
    private static final int OBB_RUN_ACTION = 1;
    private static final int OBB_MCS_BOUND = 2;
    private static final int OBB_MCS_UNBIND = 3;
    private static final int OBB_MCS_RECONNECT = 4;
    private static final int OBB_FLUSH_MOUNT_STATE = 5;

    /*
     * Default Container Service information
     */
    static final ComponentName DEFAULT_CONTAINER_COMPONENT = new ComponentName(
            "com.android.defcontainer", "com.android.defcontainer.DefaultContainerService");

    final private DefaultContainerConnection mDefContainerConn = new DefaultContainerConnection();

    class DefaultContainerConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG_OBB)
                Slog.i(TAG, "onServiceConnected");
            IMediaContainerService imcs = IMediaContainerService.Stub.asInterface(service);
            mObbActionHandler.sendMessage(mObbActionHandler.obtainMessage(OBB_MCS_BOUND, imcs));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG_OBB)
                Slog.i(TAG, "onServiceDisconnected");
        }
    };

    // Used in the ObbActionHandler
    private IMediaContainerService mContainerService = null;

    // Last fstrim operation tracking
    private static final String LAST_FSTRIM_FILE = "last-fstrim";
    private final File mLastMaintenanceFile;
    private long mLastMaintenance;

    // Handler messages
    private static final int H_SYSTEM_READY = 1;
    private static final int H_DAEMON_CONNECTED = 2;
    private static final int H_SHUTDOWN = 3;
    private static final int H_FSTRIM = 4;
    private static final int H_VOLUME_MOUNT = 5;
    private static final int H_VOLUME_BROADCAST = 6;
    private static final int H_INTERNAL_BROADCAST = 7;
    private static final int H_VOLUME_UNMOUNT = 8;
    private static final int H_PARTITION_FORGET = 9;
    private static final int H_RESET = 10;

    class MountServiceHandler extends Handler {
        public MountServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case H_SYSTEM_READY: {
                    Slog.i(TAG, "handleMessage:H_SYSTEM_READY");
                    handleSystemReady();
                    break;
                }
                case H_DAEMON_CONNECTED: {
                    Slog.i(TAG, "H_DAEMON_CONNECTED");
                    handleDaemonConnected();
                    break;
                }
                case H_FSTRIM: {
                    Slog.i(TAG, "H_FSTRIM");
                    if (!isReady()) {
                        Slog.i(TAG, "fstrim requested, but no daemon connection yet; trying again");
                        sendMessageDelayed(obtainMessage(H_FSTRIM, msg.obj),
                                DateUtils.SECOND_IN_MILLIS);
                        break;
                    }

                    Slog.i(TAG, "Running fstrim idle maintenance");

                    // Remember when we kicked it off
                    try {
                        mLastMaintenance = System.currentTimeMillis();
                        mLastMaintenanceFile.setLastModified(mLastMaintenance);
                    } catch (Exception e) {
                        Slog.e(TAG, "Unable to record last fstrim!");
                    }

                    final boolean shouldBenchmark = shouldBenchmark();
                    try {
                        // This method must be run on the main (handler) thread,
                        // so it is safe to directly call into vold.
                        mConnector.execute("fstrim", shouldBenchmark ? "dotrimbench" : "dotrim");
                    } catch (NativeDaemonConnectorException ndce) {
                        Slog.e(TAG, "Failed to run fstrim!");
                    }

                    // invoke the completion callback, if any
                    // TODO: fstrim is non-blocking, so remove this useless callback
                    Runnable callback = (Runnable) msg.obj;
                    if (callback != null) {
                        callback.run();
                    }
                    break;
                }
                case H_SHUTDOWN: {
                    Slog.i(TAG, "H_SHUTDOWN");
                    isShuttingDown = true;
                    final IMountShutdownObserver obs = (IMountShutdownObserver) msg.obj;
                    boolean success = false;
                    try {
                        success = mConnector.execute("volume", "shutdown").isClassOk();
                    } catch (NativeDaemonConnectorException ignored) {
                    }
                    if (obs != null) {
                        try {
                            obs.onShutDownComplete(success ? 0 : -1);
                        } catch (RemoteException ignored) {
                        }
                    }
                    Slog.i(TAG, "finsh shut down");
                    isShuttingDown = false;
                    break;
                }
                case H_VOLUME_MOUNT: {
                    Slog.i(TAG, "H_VOLUME_MOUNT");
                    final VolumeInfo vol = (VolumeInfo) msg.obj;
                    if (isMountDisallowed(vol)) {
                        Slog.i(TAG, "Ignoring mount " + vol.getId() + " due to policy");
                        break;
                    }
                    int rc = StorageResultCode.OperationSucceeded;
                    try {
                        mConnector.execute("volume", "mount", vol.id, vol.mountFlags,
                                vol.mountUserId);
                    } catch (NativeDaemonConnectorException ignored) {
                        rc = ignored.getCode();
                        Slog.w(TAG, "mount volume fail, ignored=" + ignored);
                    }
                    if (rc == StorageResultCode.OperationSucceeded) {
                        VolumeInfo curVol = null;
                        synchronized (mLock) {
                            curVol = mVolumes.get(vol.getId());
                        }
                        if (isShowDefaultPathDialog(curVol)) {
                            showDefaultPathDialog(curVol);
                        }
                    } else {
                        // if mount fail, for other volume mount operation
                        // should not marked as disk insert
                        isDiskInsert = false;
                        Slog.w(TAG, "mount volume fail, vol=" + vol
                                + ", return code=" + rc);
                    }
                    break;
                }
                case H_VOLUME_UNMOUNT: {
                    final VolumeInfo vol = (VolumeInfo) msg.obj;
                    unmount(vol.getId());
                    break;
                }
                case H_VOLUME_BROADCAST: {
                    Slog.i(TAG, "H_VOLUME_BROADCAST");
                    final StorageVolume userVol = (StorageVolume) msg.obj;
                    final String envState = userVol.getState();
                    Slog.d(TAG, "Volume " + userVol.getId() + " broadcasting " + envState + " to "
                            + userVol.getOwner());

                    final String action = VolumeInfo.getBroadcastForEnvironment(envState);
                    if (action != null) {
                        final Intent intent = new Intent(action,
                                Uri.fromFile(userVol.getPathFile()));
                        intent.putExtra(StorageVolume.EXTRA_STORAGE_VOLUME, userVol);
                        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                        Slog.i(TAG, "sendBroadcastAsUser, intent=" + intent
                                + ", userVol=" + userVol);
                        mContext.sendBroadcastAsUser(intent, userVol.getOwner());
                    }
                    break;
                }
                case H_INTERNAL_BROADCAST: {
                    // Internal broadcasts aimed at system components, not for
                    // third-party apps.
                    final Intent intent = (Intent) msg.obj;
                    mContext.sendBroadcastAsUser(intent, UserHandle.ALL,
                            android.Manifest.permission.WRITE_MEDIA_STORAGE);
                    break;
                }
                case H_PARTITION_FORGET: {
                    final String partGuid = (String) msg.obj;
                    forgetPartition(partGuid);
                    break;
                }
                case H_RESET: {
                    resetIfReadyAndConnected();
                    break;
                }
            }
        }
    }

    private final Handler mHandler;

    private BroadcastReceiver mUserReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
            Preconditions.checkArgument(userId >= 0);

            try {
                if (Intent.ACTION_USER_ADDED.equals(action)) {
                    Slog.i(TAG, "onReceive:ACTION_USER_ADDED");
                    final UserManager um = mContext.getSystemService(UserManager.class);
                    final int userSerialNumber = um.getUserSerialNumber(userId);
                    mConnector.execute("volume", "user_added", userId, userSerialNumber);
                } else if (Intent.ACTION_USER_REMOVED.equals(action)) {
                    synchronized (mVolumes) {
                        final int size = mVolumes.size();
                        for (int i = 0; i < size; i++) {
                            final VolumeInfo vol = mVolumes.valueAt(i);
                            if (vol.mountUserId == userId) {
                                vol.mountUserId = UserHandle.USER_NULL;
                                mHandler.obtainMessage(H_VOLUME_UNMOUNT, vol).sendToTarget();
                            }
                        }
                    }
                    Slog.i(TAG, "onReceive:ACTION_USER_REMOVED");
                    mConnector.execute("volume", "user_removed", userId);
                } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                    Slog.i(TAG, "ACTION_USER_SWITCHED");
                    mCurrentUserId = userId;
                    updateDefaultPathForUserSwitch();
                }
            } catch (NativeDaemonConnectorException e) {
                Slog.w(TAG, "Failed to send user details to vold", e);
            }
        }
    };

    @Override
    public void waitForAsecScan() {
        waitForLatch(mAsecsScanned, "mAsecsScanned");
    }

    private void waitForReady() {
        waitForLatch(mConnectedSignal, "mConnectedSignal");
    }

    private void waitForLatch(CountDownLatch latch, String condition) {
        try {
            waitForLatch(latch, condition, -1);
        } catch (TimeoutException ignored) {
        }
    }

    private void waitForLatch(CountDownLatch latch, String condition, long timeoutMillis)
            throws TimeoutException {
        final long startMillis = SystemClock.elapsedRealtime();
        while (true) {
            try {
                if (latch.await(5000, TimeUnit.MILLISECONDS)) {
                    return;
                } else {
                    Slog.w(TAG, "Thread " + Thread.currentThread().getName()
                            + " still waiting for " + condition + "...");
                }
            } catch (InterruptedException e) {
                Slog.w(TAG, "Interrupt while waiting for " + condition);
            }
            if (timeoutMillis > 0 && SystemClock.elapsedRealtime() > startMillis + timeoutMillis) {
                throw new TimeoutException("Thread " + Thread.currentThread().getName()
                        + " gave up waiting for " + condition + " after " + timeoutMillis + "ms");
            }
        }
    }

    private boolean isReady() {
        try {
            return mConnectedSignal.await(0, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    private void handleSystemReady() {
        initIfReadyAndConnected();
        resetIfReadyAndConnected();

        /*
         * If UMS was connected on boot
         * send the connected broadcast when system ready
         */
        if (mSendUmsConnectedOnBoot) {
            sendUmsIntent(true);
            mSendUmsConnectedOnBoot = false;
        }

        // Start scheduling nominally-daily fstrim operations
        MountServiceIdler.scheduleIdlePass(mContext);
    }

    /**
     * MediaProvider has a ton of code that makes assumptions about storage
     * paths never changing, so we outright kill them to pick up new state.
     */
    @Deprecated
    private void killMediaProvider(List<UserInfo> users) {
        if (users == null) return;

        final long token = Binder.clearCallingIdentity();
        try {
            for (UserInfo user : users) {
                // System user does not have media provider, so skip.
                if (user.isSystemOnly()) continue;

                final ProviderInfo provider = mPms.resolveContentProvider(MediaStore.AUTHORITY,
                        PackageManager.MATCH_DIRECT_BOOT_AWARE
                                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                        user.id);
                if (provider != null) {
                    final IActivityManager am = ActivityManagerNative.getDefault();
                    try {
                        am.killApplication(provider.applicationInfo.packageName,
                                UserHandle.getAppId(provider.applicationInfo.uid),
                                UserHandle.USER_ALL, "vold reset");
                        // We only need to run this once. It will kill all users' media processes.
                        break;
                    } catch (RemoteException e) {
                    }
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void addInternalVolumeLocked() {
        // Create a stub volume that represents internal storage
        final VolumeInfo internal = new VolumeInfo(VolumeInfo.ID_PRIVATE_INTERNAL,
                VolumeInfo.TYPE_PRIVATE, null, null);
        internal.state = VolumeInfo.STATE_MOUNTED;
        internal.path = Environment.getDataDirectory().getAbsolutePath();
        mVolumes.put(internal.id, internal);
    }

    private void initIfReadyAndConnected() {
        Slog.d(TAG, "Thinking about init, mSystemReady=" + mSystemReady
                + ", mDaemonConnected=" + mDaemonConnected);
        if (mSystemReady && mDaemonConnected
                && !StorageManager.isFileEncryptedNativeOnly()) {
            // When booting a device without native support, make sure that our
            // user directories are locked or unlocked based on the current
            // emulation status.
            final boolean initLocked = StorageManager.isFileEncryptedEmulatedOnly();
            Slog.d(TAG, "Setting up emulation state, initlocked=" + initLocked);
            final List<UserInfo> users = mContext.getSystemService(UserManager.class).getUsers();
            for (UserInfo user : users) {
                try {
                    if (initLocked) {
                        mCryptConnector.execute("cryptfs", "lock_user_key", user.id);
                    } else {
                        mCryptConnector.execute("cryptfs", "unlock_user_key", user.id,
                                user.serialNumber, "!", "!");
                    }
                } catch (NativeDaemonConnectorException e) {
                    Slog.w(TAG, "Failed to init vold", e);
                }
            }
        }
    }

    private void resetIfReadyAndConnected() {
        Slog.d(TAG, "Thinking about reset, mSystemReady=" + mSystemReady
                + ", mDaemonConnected=" + mDaemonConnected);
        if (mSystemReady && mDaemonConnected) {
            final List<UserInfo> users = mContext.getSystemService(UserManager.class).getUsers();
            killMediaProvider(users);

            final int[] systemUnlockedUsers;
            synchronized (mLock) {
                systemUnlockedUsers = mSystemUnlockedUsers;

                mDisks.clear();
                mVolumes.clear();

                addInternalVolumeLocked();
            }

            try {
                mConnector.execute("volume", "reset");

                // Tell vold about all existing and started users
                for (UserInfo user : users) {
                    mConnector.execute("volume", "user_added", user.id, user.serialNumber);
                }
                for (int userId : systemUnlockedUsers) {
                    mConnector.execute("volume", "user_started", userId);
                }
            } catch (NativeDaemonConnectorException e) {
                Slog.w(TAG, "Failed to reset vold", e);
            }
        }
    }

    private void onUnlockUser(int userId) {
        Slog.d(TAG, "onUnlockUser " + userId);

        // We purposefully block here to make sure that user-specific
        // staging area is ready so it's ready for zygote-forked apps to
        // bind mount against.
        try {
            mConnector.execute("volume", "user_started", userId);
        } catch (NativeDaemonConnectorException ignored) {
        }

        // Record user as started so newly mounted volumes kick off events
        // correctly, then synthesize events for any already-mounted volumes.
        synchronized (mVolumes) {
            for (int i = 0; i < mVolumes.size(); i++) {
                final VolumeInfo vol = mVolumes.valueAt(i);
                if (vol.isVisibleForRead(userId) && vol.isMountedReadable()) {
                    final StorageVolume userVol = vol.buildStorageVolume(mContext, userId, false);
                    mHandler.obtainMessage(H_VOLUME_BROADCAST, userVol).sendToTarget();

                    final String envState = VolumeInfo.getEnvironmentForState(vol.getState());
                    mCallbacks.notifyStorageStateChanged(userVol.getPath(), envState, envState);
                }
            }
            mSystemUnlockedUsers = ArrayUtils.appendInt(mSystemUnlockedUsers, userId);
        }
    }

    private void onCleanupUser(int userId) {
        Slog.d(TAG, "onCleanupUser " + userId);

        try {
            mConnector.execute("volume", "user_stopped", userId);
        } catch (NativeDaemonConnectorException ignored) {
        }

        synchronized (mVolumes) {
            mSystemUnlockedUsers = ArrayUtils.removeInt(mSystemUnlockedUsers, userId);
        }
    }

    void runIdleMaintenance(Runnable callback) {
        mHandler.sendMessage(mHandler.obtainMessage(H_FSTRIM, callback));
    }

    // Binder entry point for kicking off an immediate fstrim
    @Override
    public void runMaintenance() {
        enforcePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);
        runIdleMaintenance(null);
    }

    @Override
    public long lastMaintenance() {
        return mLastMaintenance;
    }

    /**
     * Callback from NativeDaemonConnector
     */
    @Override
    public void onDaemonConnected() {
        mDaemonConnected = true;
        mHandler.obtainMessage(H_DAEMON_CONNECTED).sendToTarget();
    }

    private void handleDaemonConnected() {
        Slog.i(TAG, "handleDaemonConnected");
        initIfReadyAndConnected();
        resetIfReadyAndConnected();

        /*
         * Now that we've done our initialization, release
         * the hounds!
         */
        mConnectedSignal.countDown();
        if (mConnectedSignal.getCount() != 0) {
            // More daemons need to connect
            return;
        }

        // On an encrypted device we can't see system properties yet, so pull
        // the system locale out of the mount service.
        if ("".equals(SystemProperties.get("vold.encrypt_progress"))) {
            copyLocaleFromMountService();
        }

        // Let package manager load internal ASECs.
        mPms.scanAvailableAsecs();

        // Notify people waiting for ASECs to be scanned that it's done.
        mAsecsScanned.countDown();
    }

    private void copyLocaleFromMountService() {
        String systemLocale;
        try {
            systemLocale = getField(StorageManager.SYSTEM_LOCALE_KEY);
        } catch (RemoteException e) {
            return;
        }
        if (TextUtils.isEmpty(systemLocale)) {
            return;
        }

        Slog.d(TAG, "Got locale " + systemLocale + " from mount service");
        Locale locale = Locale.forLanguageTag(systemLocale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        try {
            ActivityManagerNative.getDefault().updatePersistentConfiguration(config);
        } catch (RemoteException e) {
            Slog.e(TAG, "Error setting system locale from mount service", e);
        }

        // Temporary workaround for http://b/17945169.
        Slog.d(TAG, "Setting system properties to " + systemLocale + " from mount service");
        SystemProperties.set("persist.sys.locale", locale.toLanguageTag());
    }

    /**
     * Callback from NativeDaemonConnector
     */
    @Override
    public boolean onCheckHoldWakeLock(int code) {
        return false;
    }

    /**
     * Callback from NativeDaemonConnector
     */
    @Override
    public boolean onEvent(int code, String raw, String[] cooked) {
        synchronized (mLock) {
            return onEventLocked(code, raw, cooked);
        }
    }

    private boolean onEventLocked(int code, String raw, String[] cooked) {
        switch (code) {
            case VoldResponseCode.DISK_CREATED: {
                Slog.d(TAG, "DISK_CREATED");
                if (cooked.length != 3) break;
                final String id = cooked[1];
                int flags = Integer.parseInt(cooked[2]);
                if (SystemProperties.getBoolean(StorageManager.PROP_FORCE_ADOPTABLE, false)
                        || mForceAdoptable) {
                    flags |= DiskInfo.FLAG_ADOPTABLE;
                }
                mDisks.put(id, new DiskInfo(id, flags));
                Slog.d(TAG, "create diskInfo=" + mDisks.get(id));
                isDiskInsert = true;
                break;
            }
            case VoldResponseCode.DISK_SIZE_CHANGED: {
                Slog.d(TAG, "DISK_SIZE_CHANGED");
                if (cooked.length != 3) break;
                final DiskInfo disk = mDisks.get(cooked[1]);
                if (disk != null) {
                    Slog.d(TAG, "disk size change from + " + disk.size
                            + " to " + Long.parseLong(cooked[2]));
                    disk.size = Long.parseLong(cooked[2]);
                }
                break;
            }
            case VoldResponseCode.DISK_LABEL_CHANGED: {
                Slog.d(TAG, "DISK_LABEL_CHANGED");
                final DiskInfo disk = mDisks.get(cooked[1]);
                if (disk != null) {
                    final StringBuilder builder = new StringBuilder();
                    for (int i = 2; i < cooked.length; i++) {
                        builder.append(cooked[i]).append(' ');
                    }
                    disk.label = builder.toString().trim();
                    Slog.d(TAG, "DISK_LABEL_CHANGED, new label = " + disk.label +
                            ", diskInfo=" + disk);
                }
                break;
            }
            case VoldResponseCode.DISK_SCANNED: {
                Slog.d(TAG, "DISK_SCANNED");
                if (cooked.length != 2) break;
                final DiskInfo disk = mDisks.get(cooked[1]);
                if (disk != null) {
                    onDiskScannedLocked(disk);
                }
                break;
            }
            case VoldResponseCode.DISK_SYS_PATH_CHANGED: {
                if (cooked.length != 3) break;
                final DiskInfo disk = mDisks.get(cooked[1]);
                if (disk != null) {
                    disk.sysPath = cooked[2];
                }
                break;
            }
            case VoldResponseCode.DISK_DESTROYED: {
                Slog.d(TAG, "DISK_DESTROYED");
                if (cooked.length != 2) break;
                final DiskInfo disk = mDisks.remove(cooked[1]);
                if (disk != null) {
                    mCallbacks.notifyDiskDestroyed(disk);
                }
                updateDefaultPathIfNeed();
                break;
            }

            case VoldResponseCode.VOLUME_CREATED: {
                Slog.d(TAG, "VOLUME_CREATED");
                final String id = cooked[1];
                final int type = Integer.parseInt(cooked[2]);
                final String diskId = TextUtils.nullIfEmpty(cooked[3]);
                final String partGuid = TextUtils.nullIfEmpty(cooked[4]);

                final DiskInfo disk = mDisks.get(diskId);
                final VolumeInfo vol = new VolumeInfo(id, type, disk, partGuid);
                mVolumes.put(id, vol);
                onVolumeCreatedLocked(vol);
                Slog.d(TAG, "create volumeInfo=" + mVolumes.get(id));
                break;
            }
            case VoldResponseCode.VOLUME_STATE_CHANGED: {
                Slog.d(TAG, "VOLUME_STATE_CHANGED");
                if (cooked.length != 3) break;
                final VolumeInfo vol = mVolumes.get(cooked[1]);
                if (vol != null) {
                    final int oldState = vol.state;
                    final int newState = Integer.parseInt(cooked[2]);
                    vol.state = newState;
                    onVolumeStateChangedLocked(vol, oldState, newState);
                }
                break;
            }
            case VoldResponseCode.VOLUME_FS_TYPE_CHANGED: {
                Slog.d(TAG, "VOLUME_FS_TYPE_CHANGED");
                if (cooked.length != 3) break;
                final VolumeInfo vol = mVolumes.get(cooked[1]);
                if (vol != null) {
                    vol.fsType = cooked[2];
                    Slog.d(TAG, "new fsType=" + vol.fsType
                            + ", volumeInfo=" + vol);
                }
                break;
            }
            case VoldResponseCode.VOLUME_FS_UUID_CHANGED: {
                Slog.d(TAG, "VOLUME_FS_UUID_CHANGED");
                if (cooked.length != 3) break;
                final VolumeInfo vol = mVolumes.get(cooked[1]);
                if (vol != null) {
                    vol.fsUuid = cooked[2];
                    Slog.d(TAG, "new fsUuid=" + vol.fsUuid
                            + ", volumeInfo=" + vol);
                }
                break;
            }
            case VoldResponseCode.VOLUME_FS_LABEL_CHANGED: {
                Slog.d(TAG, "VOLUME_FS_LABEL_CHANGED");
                final VolumeInfo vol = mVolumes.get(cooked[1]);
                if (vol != null) {
                    final StringBuilder builder = new StringBuilder();
                    for (int i = 2; i < cooked.length; i++) {
                        builder.append(cooked[i]).append(' ');
                    }
                    vol.fsLabel = builder.toString().trim();
                    Slog.d(TAG, "new fsLabel=" + vol.fsLabel
                            + ", volumeInfo=" + vol);
                }
                // TODO: notify listeners that label changed
                break;
            }
            case VoldResponseCode.VOLUME_PATH_CHANGED: {
                Slog.d(TAG, "VOLUME_PATH_CHANGED");
                if (cooked.length != 3) break;
                final VolumeInfo vol = mVolumes.get(cooked[1]);
                if (vol != null) {
                    vol.path = cooked[2];
                    Slog.d(TAG, "new path= " + vol.path
                            + ", volumeInfo=" + vol);
                }
                break;
            }
            case VoldResponseCode.VOLUME_INTERNAL_PATH_CHANGED: {
                Slog.d(TAG, "VOLUME_INTERNAL_PATH_CHANGED");
                if (cooked.length != 3) break;
                final VolumeInfo vol = mVolumes.get(cooked[1]);
                if (vol != null) {
                    vol.internalPath = cooked[2];
                    Slog.d(TAG, "new internal path= " + vol.internalPath
                            + ", volumeInfo=" + vol);
                }
                break;
            }
            case VoldResponseCode.VOLUME_DESTROYED: {
                Slog.d(TAG, "VOLUME_DESTROYED");
                if (cooked.length != 2) break;
                Slog.d(TAG, "destroyed volumeInfo=" + mVolumes.get(cooked[1]));
                mVolumes.remove(cooked[1]);
                break;
            }

            case VoldResponseCode.MOVE_STATUS: {
                Slog.d(TAG, "MOVE_STATUS");
                final int status = Integer.parseInt(cooked[1]);
                onMoveStatusLocked(status);
                break;
            }
            case VoldResponseCode.BENCHMARK_RESULT: {
                if (cooked.length != 7) break;
                final String path = cooked[1];
                final String ident = cooked[2];
                final long create = Long.parseLong(cooked[3]);
                final long drop = Long.parseLong(cooked[4]);
                final long run = Long.parseLong(cooked[5]);
                final long destroy = Long.parseLong(cooked[6]);

                final DropBoxManager dropBox = mContext.getSystemService(DropBoxManager.class);
                dropBox.addText(TAG_STORAGE_BENCHMARK, scrubPath(path)
                        + " " + ident + " " + create + " " + run + " " + destroy);

                final VolumeRecord rec = findRecordForPath(path);
                if (rec != null) {
                    rec.lastBenchMillis = System.currentTimeMillis();
                    writeSettingsLocked();
                }

                break;
            }
            case VoldResponseCode.TRIM_RESULT: {
                if (cooked.length != 4) break;
                final String path = cooked[1];
                final long bytes = Long.parseLong(cooked[2]);
                final long time = Long.parseLong(cooked[3]);

                final DropBoxManager dropBox = mContext.getSystemService(DropBoxManager.class);
                dropBox.addText(TAG_STORAGE_TRIM, scrubPath(path)
                        + " " + bytes + " " + time);

                final VolumeRecord rec = findRecordForPath(path);
                if (rec != null) {
                    rec.lastTrimMillis = System.currentTimeMillis();
                    writeSettingsLocked();
                }

                break;
            }

            default: {
                Slog.d(TAG, "Unhandled vold event " + code);
            }
        }

        return true;
    }

    private void onDiskScannedLocked(DiskInfo disk) {
        Slog.d(TAG, "onDiskScannedLocked, diskInfo=" + disk);
        int volumeCount = 0;
        for (int i = 0; i < mVolumes.size(); i++) {
            final VolumeInfo vol = mVolumes.valueAt(i);
            if (Objects.equals(disk.id, vol.getDiskId())) {
                volumeCount++;
            }
        }
        Slog.d(TAG, "this disk has " + volumeCount + " volumes");

        final Intent intent = new Intent(DiskInfo.ACTION_DISK_SCANNED);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        intent.putExtra(DiskInfo.EXTRA_DISK_ID, disk.id);
        intent.putExtra(DiskInfo.EXTRA_VOLUME_COUNT, volumeCount);
        Slog.d(TAG, "sendBroadcastAsUser, intent=" + intent
                + ", disk.id=" + disk.id
                + ", volumeCount=" + volumeCount);
        mHandler.obtainMessage(H_INTERNAL_BROADCAST, intent).sendToTarget();

        final CountDownLatch latch = mDiskScanLatches.remove(disk.id);
        if (latch != null) {
            latch.countDown();
        }

        disk.volumeCount = volumeCount;
        mCallbacks.notifyDiskScanned(disk, volumeCount);
    }

    private void onVolumeCreatedLocked(VolumeInfo vol) {
        if (mPms.isOnlyCoreApps()) {
            Slog.d(TAG, "System booted in core-only mode; ignoring volume " + vol.getId());
            return;
        }
        Slog.d(TAG, "onVolumeCreatedLocked, volumeInfo=" + vol);
        if (vol.type == VolumeInfo.TYPE_EMULATED) {
            final StorageManager storage = mContext.getSystemService(StorageManager.class);
            final VolumeInfo privateVol = storage.findPrivateForEmulated(vol);

            Slog.d(TAG, "privateVol=" + privateVol);
            if (Objects.equals(StorageManager.UUID_PRIVATE_INTERNAL, mPrimaryStorageUuid)
                    && VolumeInfo.ID_PRIVATE_INTERNAL.equals(privateVol.id)) {
                Slog.v(TAG, "Found primary storage at " + vol);
                vol.mountFlags |= VolumeInfo.MOUNT_FLAG_PRIMARY;
                vol.mountFlags |= VolumeInfo.MOUNT_FLAG_VISIBLE;
                mHandler.obtainMessage(H_VOLUME_MOUNT, vol).sendToTarget();

            } else if (Objects.equals(privateVol.fsUuid, mPrimaryStorageUuid)) {
                Slog.v(TAG, "Found primary storage at " + vol);
                vol.mountFlags |= VolumeInfo.MOUNT_FLAG_PRIMARY;
                vol.mountFlags |= VolumeInfo.MOUNT_FLAG_VISIBLE;
                mHandler.obtainMessage(H_VOLUME_MOUNT, vol).sendToTarget();
            }

        } else if (vol.type == VolumeInfo.TYPE_PUBLIC) {
            // TODO: only look at first public partition
            if (Objects.equals(StorageManager.UUID_PRIMARY_PHYSICAL, mPrimaryStorageUuid)
                    && vol.disk.isDefaultPrimary()) {
                Slog.v(TAG, "Found primary storage at " + vol);
                vol.mountFlags |= VolumeInfo.MOUNT_FLAG_PRIMARY;
                vol.mountFlags |= VolumeInfo.MOUNT_FLAG_VISIBLE;
            }

            // Adoptable public disks are visible to apps, since they meet
            // public API requirement of being in a stable location.
            if (vol.disk.isAdoptable() || vol.isPhoneStorage()) {
                vol.mountFlags |= VolumeInfo.MOUNT_FLAG_VISIBLE;
            }

            vol.mountUserId = mCurrentUserId;
            mHandler.obtainMessage(H_VOLUME_MOUNT, vol).sendToTarget();

        } else if (vol.type == VolumeInfo.TYPE_PRIVATE) {
            mHandler.obtainMessage(H_VOLUME_MOUNT, vol).sendToTarget();

        } else {
            Slog.d(TAG, "Skipping automatic mounting of " + vol);
        }
    }

    private boolean isBroadcastWorthy(VolumeInfo vol) {
        //Slog.d(TAG, "isBroadcastWorthy, volumeInfo=" + vol);
        switch (vol.getType()) {
            case VolumeInfo.TYPE_PRIVATE:
            case VolumeInfo.TYPE_PUBLIC:
            case VolumeInfo.TYPE_EMULATED:
                break;
            default:
                return false;
        }

        switch (vol.getState()) {
            case VolumeInfo.STATE_MOUNTED:
            case VolumeInfo.STATE_MOUNTED_READ_ONLY:
            case VolumeInfo.STATE_EJECTING:
            case VolumeInfo.STATE_UNMOUNTED:
            case VolumeInfo.STATE_UNMOUNTABLE:
            case VolumeInfo.STATE_BAD_REMOVAL:
                break;
            default:
                return false;
        }

        return true;
    }

    private void onVolumeStateChangedLocked(VolumeInfo vol, int oldState, int newState) {
        Slog.d(TAG, "onVolumeStateChangedLocked"
                + ", oldState=" + VolumeInfo.getEnvironmentForState(oldState)
                + ", newState=" + VolumeInfo.getEnvironmentForState(newState)
                + ", volumeInfo=" + vol);
        // Remember that we saw this volume so we're ready to accept user
        // metadata, or so we can annoy them when a private volume is ejected
        if (vol.isMountedReadable() && !TextUtils.isEmpty(vol.fsUuid)) {
            VolumeRecord rec = mRecords.get(vol.fsUuid);
            if (rec == null) {
                rec = new VolumeRecord(vol.type, vol.fsUuid);
                rec.partGuid = vol.partGuid;
                rec.createdMillis = System.currentTimeMillis();
                if (vol.type == VolumeInfo.TYPE_PRIVATE) {
                    rec.nickname = vol.disk.getDescription();
                }
                mRecords.put(rec.fsUuid, rec);
                writeSettingsLocked();
            } else {
                // Handle upgrade case where we didn't store partition GUID
                if (TextUtils.isEmpty(rec.partGuid)) {
                    rec.partGuid = vol.partGuid;
                    writeSettingsLocked();
                }
            }
        }

        mCallbacks.notifyVolumeStateChanged(vol, oldState, newState);

        // Do not broadcast before boot has completed to avoid launching the
        // processes that receive the intent unnecessarily.
        if (mBootCompleted && isBroadcastWorthy(vol)) {
            final Intent intent = new Intent(VolumeInfo.ACTION_VOLUME_STATE_CHANGED);
            intent.putExtra(VolumeInfo.EXTRA_VOLUME_ID, vol.id);
            intent.putExtra(VolumeInfo.EXTRA_VOLUME_STATE, newState);
            intent.putExtra(VolumeRecord.EXTRA_FS_UUID, vol.fsUuid);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                    | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            Slog.d(TAG, "sendBroadcastAsUser, intent=" + intent
                    + ", vol.id=" + vol.id
                    + ", newState=" + newState
                    + ", vol.fsUuid=" + vol.fsUuid
                    + ", flags=FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT");
            mHandler.obtainMessage(H_INTERNAL_BROADCAST, intent).sendToTarget();
        }

        final String oldStateEnv = VolumeInfo.getEnvironmentForState(oldState);
        final String newStateEnv = VolumeInfo.getEnvironmentForState(newState);

        if (!Objects.equals(oldStateEnv, newStateEnv)) {
            // Kick state changed event towards all started users. Any users
            // started after this point will trigger additional
            // user-specific broadcasts.
            for (int userId : mSystemUnlockedUsers) {
                if (vol.isVisibleForRead(userId)) {
                    final StorageVolume userVol = vol.buildStorageVolume(mContext, userId, false);
                    mHandler.obtainMessage(H_VOLUME_BROADCAST, userVol).sendToTarget();

                    Slog.d(TAG, "notify callbacks StorageStateChanged, storageVolume=" + userVol);
                    mCallbacks.notifyStorageStateChanged(userVol.getPath(), oldStateEnv,
                            newStateEnv);
                }
            }
        }

        if (vol.type == VolumeInfo.TYPE_PUBLIC && vol.state == VolumeInfo.STATE_EJECTING) {
            // TODO: this should eventually be handled by new ObbVolume state changes
            /*
             * Some OBBs might have been unmounted when this volume was
             * unmounted, so send a message to the handler to let it know to
             * remove those from the list of mounted OBBS.
             */
            mObbActionHandler.sendMessage(mObbActionHandler.obtainMessage(
                    OBB_FLUSH_MOUNT_STATE, vol.path));
        }

        updateDefaultPathIfNeed();
    }

    private void onMoveStatusLocked(int status) {
        if (mMoveCallback == null) {
            Slog.w(TAG, "Odd, status but no move requested");
            return;
        }

        // TODO: estimate remaining time
        try {
            mMoveCallback.onStatusChanged(-1, status, -1);
        } catch (RemoteException ignored) {
        }

        // We've finished copying and we're about to clean up old data, so
        // remember that move was successful if we get rebooted
        if (status == MOVE_STATUS_COPY_FINISHED) {
            Slog.d(TAG, "Move to " + mMoveTargetUuid + " copy phase finshed; persisting");

            mPrimaryStorageUuid = mMoveTargetUuid;
            writeSettingsLocked();
        }

        if (PackageManager.isMoveStatusFinished(status)) {
            Slog.d(TAG, "Move to " + mMoveTargetUuid + " finished with status " + status);

            mMoveCallback = null;
            mMoveTargetUuid = null;
        }
    }

    private void enforcePermission(String perm) {
        mContext.enforceCallingOrSelfPermission(perm, perm);
    }

    /**
     * Decide if volume is mountable per device policies.
     */
    private boolean isMountDisallowed(VolumeInfo vol) {
        if (vol.type == VolumeInfo.TYPE_PUBLIC || vol.type == VolumeInfo.TYPE_PRIVATE) {
            final UserManager userManager = mContext.getSystemService(UserManager.class);
            return userManager.hasUserRestriction(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
                    Binder.getCallingUserHandle());
        } else {
            return false;
        }
    }

    private void enforceAdminUser() {
        UserManager um = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        final int callingUserId = UserHandle.getCallingUserId();
        boolean isAdmin;
        long token = Binder.clearCallingIdentity();
        try {
            isAdmin = um.getUserInfo(callingUserId).isAdmin();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        if (!isAdmin) {
            throw new SecurityException("Only admin users can adopt sd cards");
        }
    }

    /**
     * Constructs a new MountService instance
     *
     * @param context  Binder context for this service
     */
    public MountService(Context context) {
        sSelf = this;

        mContext = context;
        mCallbacks = new Callbacks(FgThread.get().getLooper());
        mLockPatternUtils = new LockPatternUtils(mContext);

        // XXX: This will go away soon in favor of IMountServiceObserver
        mPms = (PackageManagerService) ServiceManager.getService("package");

        HandlerThread hthread = new HandlerThread(TAG);
        hthread.start();
        mHandler = new MountServiceHandler(hthread.getLooper());

        // Add OBB Action Handler to MountService thread.
        mObbActionHandler = new ObbActionHandler(IoThread.get().getLooper());

        // Initialize the last-fstrim tracking if necessary
        File dataDir = Environment.getDataDirectory();
        File systemDir = new File(dataDir, "system");
        mLastMaintenanceFile = new File(systemDir, LAST_FSTRIM_FILE);
        if (!mLastMaintenanceFile.exists()) {
            // Not setting mLastMaintenance here means that we will force an
            // fstrim during reboot following the OTA that installs this code.
            try {
                (new FileOutputStream(mLastMaintenanceFile)).close();
            } catch (IOException e) {
                Slog.e(TAG, "Unable to create fstrim record " + mLastMaintenanceFile.getPath());
            }
        } else {
            mLastMaintenance = mLastMaintenanceFile.lastModified();
        }

        mSettingsFile = new AtomicFile(
                new File(Environment.getDataSystemDirectory(), "storage.xml"));

        synchronized (mLock) {
            readSettingsLocked();
        }

        LocalServices.addService(MountServiceInternal.class, mMountServiceInternal);

        /*
         * Create the connection to vold with a maximum queue of twice the
         * amount of containers we'd ever expect to have. This keeps an
         * "asec list" from blocking a thread repeatedly.
         */

        mConnector = new NativeDaemonConnector(this, "vold", MAX_CONTAINERS * 2, VOLD_TAG, 25,
                null);
        mConnector.setDebug(true);
        mConnector.setWarnIfHeld(mLock);
        mConnectorThread = new Thread(mConnector, VOLD_TAG);

        // Reuse parameters from first connector since they are tested and safe
        mCryptConnector = new NativeDaemonConnector(this, "cryptd",
                MAX_CONTAINERS * 2, CRYPTD_TAG, 25, null);
        mCryptConnector.setDebug(true);
        mCryptConnectorThread = new Thread(mCryptConnector, CRYPTD_TAG);

        final IntentFilter userFilter = new IntentFilter();
        userFilter.addAction(Intent.ACTION_USER_ADDED);
        userFilter.addAction(Intent.ACTION_USER_REMOVED);
        userFilter.addAction(Intent.ACTION_USER_SWITCHED);
        mContext.registerReceiver(mUserReceiver, userFilter, null, mHandler);

        synchronized (mLock) {
            addInternalVolumeLocked();
        }

        // Add ourself to the Watchdog monitors if enabled.
        if (WATCHDOG_ENABLE) {
            Watchdog.getInstance().addMonitor(this);
        }

        initMTKFeature();
    }

    private void start() {
        mConnectorThread.start();
        mCryptConnectorThread.start();
    }

    private void systemReady() {
        mSystemReady = true;
        mHandler.obtainMessage(H_SYSTEM_READY).sendToTarget();
    }

    private void bootCompleted() {
        mBootCompleted = true;
    }

    private String getDefaultPrimaryStorageUuid() {
        if (SystemProperties.getBoolean(StorageManager.PROP_PRIMARY_PHYSICAL, false)) {
            return StorageManager.UUID_PRIMARY_PHYSICAL;
        } else {
            return StorageManager.UUID_PRIVATE_INTERNAL;
        }
    }

    private void readSettingsLocked() {
        Slog.i(TAG, "readSettingsLocked");
        mRecords.clear();
        mPrimaryStorageUuid = getDefaultPrimaryStorageUuid();
        mForceAdoptable = false;

        FileInputStream fis = null;
        try {
            fis = mSettingsFile.openRead();
            final XmlPullParser in = Xml.newPullParser();
            in.setInput(fis, StandardCharsets.UTF_8.name());

            int type;
            while ((type = in.next()) != END_DOCUMENT) {
                if (type == START_TAG) {
                    final String tag = in.getName();
                    if (TAG_VOLUMES.equals(tag)) {
                        final int version = readIntAttribute(in, ATTR_VERSION, VERSION_INIT);
                        final boolean primaryPhysical = SystemProperties.getBoolean(
                                StorageManager.PROP_PRIMARY_PHYSICAL, false);
                        final boolean validAttr = (version >= VERSION_FIX_PRIMARY)
                                || (version >= VERSION_ADD_PRIMARY && !primaryPhysical);
                        if (validAttr) {
                            mPrimaryStorageUuid = readStringAttribute(in,
                                    ATTR_PRIMARY_STORAGE_UUID);
                        }
                        mForceAdoptable = readBooleanAttribute(in, ATTR_FORCE_ADOPTABLE, false);

                        Slog.i(TAG, "read start tag: version=" + version
                                + ", primaryPhysical=" + primaryPhysical
                                + ", mPrimaryStorageUuid=" + mPrimaryStorageUuid
                                + ", mForceAdoptable=" + mForceAdoptable);

                    } else if (TAG_VOLUME.equals(tag)) {
                        final VolumeRecord rec = readVolumeRecord(in);
                        Slog.i(TAG, "read volume tag: volumeRecode=" + rec);
                        mRecords.put(rec.fsUuid, rec);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            // Missing metadata is okay, probably first boot
        } catch (IOException e) {
            Slog.wtf(TAG, "Failed reading metadata", e);
        } catch (XmlPullParserException e) {
            Slog.wtf(TAG, "Failed reading metadata", e);
        } finally {
            IoUtils.closeQuietly(fis);
        }
    }

    private void writeSettingsLocked() {
        Slog.i(TAG, "writeSettingsLocked");
        FileOutputStream fos = null;
        try {
            fos = mSettingsFile.startWrite();

            XmlSerializer out = new FastXmlSerializer();
            out.setOutput(fos, StandardCharsets.UTF_8.name());
            out.startDocument(null, true);
            out.startTag(null, TAG_VOLUMES);
            writeIntAttribute(out, ATTR_VERSION, VERSION_FIX_PRIMARY);
            writeStringAttribute(out, ATTR_PRIMARY_STORAGE_UUID, mPrimaryStorageUuid);
            writeBooleanAttribute(out, ATTR_FORCE_ADOPTABLE, mForceAdoptable);
            Slog.i(TAG, "write start tag: version=" + VERSION_FIX_PRIMARY
                    + ", mPrimaryStorageUuid=" + mPrimaryStorageUuid
                    + ", mForceAdoptable=" + mForceAdoptable);
            final int size = mRecords.size();
            for (int i = 0; i < size; i++) {
                final VolumeRecord rec = mRecords.valueAt(i);
                Slog.i(TAG, "write volume record: " + rec);
                writeVolumeRecord(out, rec);
            }
            out.endTag(null, TAG_VOLUMES);
            out.endDocument();

            mSettingsFile.finishWrite(fos);
        } catch (IOException e) {
            if (fos != null) {
                mSettingsFile.failWrite(fos);
            }
        }
    }

    public static VolumeRecord readVolumeRecord(XmlPullParser in) throws IOException {
        final int type = readIntAttribute(in, ATTR_TYPE);
        final String fsUuid = readStringAttribute(in, ATTR_FS_UUID);
        final VolumeRecord meta = new VolumeRecord(type, fsUuid);
        meta.partGuid = readStringAttribute(in, ATTR_PART_GUID);
        meta.nickname = readStringAttribute(in, ATTR_NICKNAME);
        meta.userFlags = readIntAttribute(in, ATTR_USER_FLAGS);
        meta.createdMillis = readLongAttribute(in, ATTR_CREATED_MILLIS);
        meta.lastTrimMillis = readLongAttribute(in, ATTR_LAST_TRIM_MILLIS);
        meta.lastBenchMillis = readLongAttribute(in, ATTR_LAST_BENCH_MILLIS);
        return meta;
    }

    public static void writeVolumeRecord(XmlSerializer out, VolumeRecord rec) throws IOException {
        out.startTag(null, TAG_VOLUME);
        writeIntAttribute(out, ATTR_TYPE, rec.type);
        writeStringAttribute(out, ATTR_FS_UUID, rec.fsUuid);
        writeStringAttribute(out, ATTR_PART_GUID, rec.partGuid);
        writeStringAttribute(out, ATTR_NICKNAME, rec.nickname);
        writeIntAttribute(out, ATTR_USER_FLAGS, rec.userFlags);
        writeLongAttribute(out, ATTR_CREATED_MILLIS, rec.createdMillis);
        writeLongAttribute(out, ATTR_LAST_TRIM_MILLIS, rec.lastTrimMillis);
        writeLongAttribute(out, ATTR_LAST_BENCH_MILLIS, rec.lastBenchMillis);
        out.endTag(null, TAG_VOLUME);
    }

    /**
     * Exposed API calls below here
     */

    @Override
    public void registerListener(IMountServiceListener listener) {
        mCallbacks.register(listener);
    }

    @Override
    public void unregisterListener(IMountServiceListener listener) {
        mCallbacks.unregister(listener);
    }

    @Override
    public void shutdown(final IMountShutdownObserver observer) {
        enforcePermission(android.Manifest.permission.SHUTDOWN);

        Slog.i(TAG, "Shutting down");
        waitMTKNetlogStopped();
        mHandler.obtainMessage(H_SHUTDOWN, observer).sendToTarget();
    }

    @Override
    public boolean isUsbMassStorageConnected() {
        Slog.i(TAG, "isUsbMassStorageConnected");
        waitForReady();

        if (getUmsEnabling()) {
            Slog.i(TAG, "isUsbMassStorageConnected return true");
            return true;
        }

        Slog.i(TAG, "isUsbMassStorageConnected return " + mIsUsbConnected);
        return mIsUsbConnected;
    }

    @Override
    public void setUsbMassStorageEnabled(boolean enable) {
        Slog.d(TAG, "setUsbMassStorageEnabled, enable=" + enable);
        waitForReady();
        enforcePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);
        validateUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER);

        // erternal sd card can also share to pc when the primary is emulated.
        int userId = mCurrentUserId;
        mIsTurnOnOffUsb = true;
        synchronized (mLock) {
            for (int i = 0; i < mVolumes.size(); i++) {
                final VolumeInfo vol = mVolumes.valueAt(i);
                if (!vol.isAllowUsbMassStorage(userId)) {
                    Slog.d(TAG, "no need share, skip volume=" + vol);
                    continue;
                }

                if (enable) {
                    setUmsEnabling(true);
                    if (vol.getState() == VolumeInfo.STATE_MOUNTED
                            || vol.getState() == VolumeInfo.STATE_MOUNTED_READ_ONLY) {
                        Slog.d(TAG, "setUsbMassStorageEnabled, first unmount volume="
                            + vol);
                        unmount(vol.getId());
                        Slog.d(TAG, "setUsbMassStorageEnabled, second share volume");
                        doShareUnshareVolume(vol.getId(), true);
                    } else if (vol.getState() == VolumeInfo.STATE_UNMOUNTED) {
                        Slog.d(TAG, "setUsbMassStorageEnabled, just share volume");
                        doShareUnshareVolume(vol.getId(), true);
                    }
                    setUmsEnabling(false);
                } else {
                    if (vol.getState() != VolumeInfo.STATE_REMOVED
                        && vol.getState() != VolumeInfo.STATE_BAD_REMOVAL) {
                        Slog.d(TAG, "setUsbMassStorageEnabled, first unshare volume="
                                + vol);
                        doShareUnshareVolume(vol.getId(), false);
                        Slog.d(TAG, "setUsbMassStorageEnabled, second mount volume");
                        mount(vol.getId());
                    }
                }
            }
        }
        mIsTurnOnOffUsb = false;
    }

    @Override
    public boolean isUsbMassStorageEnabled() {
        Slog.i(TAG, "isUsbMassStorageEnabled");
        waitForReady();
        // if there is any one storage can be share, return true
        // otherwise return false.
        boolean result = false;

        synchronized (mVolumes) {
            for (int i = 0; i < mVolumes.size(); i++) {
                final VolumeInfo vol = mVolumes.valueAt(i);
                if (isVolumeSharedEnable(vol)) {
                    result = true;
                    break;
                }
            }
        }

        Slog.i(TAG, "isUsbMassStorageEnabled return + " + result);
        return result;
    }

    @Override
    public String getVolumeState(String mountPoint) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isExternalStorageEmulated() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int mountVolume(String path) {
        Slog.i(TAG, "mountVolume, path=" + path);
        mount(findVolumeIdForPathOrThrow(path));
        return 0;
    }

    @Override
    public void unmountVolume(String path, boolean force, boolean removeEncryption) {
        Slog.i(TAG, "unmountVolume, path=" + path);
        unmount(findVolumeIdForPathOrThrow(path));
    }

    @Override
    public int formatVolume(String path) {
        Slog.i(TAG, "formatVolume, path=" + path);
        format(findVolumeIdForPathOrThrow(path));
        return 0;
    }

    @Override
    public void mount(String volId) {
        enforcePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);
        waitForReady();

        final VolumeInfo vol = findVolumeByIdOrThrow(volId);
        Slog.i(TAG, "mount, volId=" + volId + ", volumeInfo=" + vol);
        if (isMountDisallowed(vol)) {
            throw new SecurityException("Mounting " + volId + " restricted by policy");
        }
        try {
            mConnector.execute("volume", "mount", vol.id, vol.mountFlags, vol.mountUserId);
        } catch (NativeDaemonConnectorException e) {
            //throw e.rethrowAsParcelableException();
            Slog.e(TAG, "mount" + vol + "ERROR!!");
        }
    }

    @Override
    public void unmount(String volId) {
        enforcePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);
        waitForReady();

        final VolumeInfo vol = findVolumeByIdOrThrow(volId);
        Slog.i(TAG, "unmount, volId=" + volId + ", volumeInfo=" + vol);

        // TODO: expand PMS to know about multiple volumes
        if (vol.isPrimaryPhysical()) {
            final long ident = Binder.clearCallingIdentity();
            try {
                synchronized (mUnmountLock) {
                    mUnmountSignal = new CountDownLatch(1);
                    mPms.updateExternalMediaStatus(false, true);
                    waitForLatch(mUnmountSignal, "mUnmountSignal");
                    mUnmountSignal = null;
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        try {
            mConnector.execute("volume", "unmount", vol.id);
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
        updateDefaultPathIfNeed();
    }

    @Override
    public void format(String volId) {
        enforcePermission(android.Manifest.permission.MOUNT_FORMAT_FILESYSTEMS);
        waitForReady();

        final VolumeInfo vol = findVolumeByIdOrThrow(volId);
        Slog.i(TAG, "format, volId=" + volId + ", volumeInfo=" + vol);
        try {
            mConnector.execute("volume", "format", vol.id, "auto");
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
    }

    @Override
    public long benchmark(String volId) {
        Slog.i(TAG, "benchmark, volId=" + volId);
        enforcePermission(android.Manifest.permission.MOUNT_FORMAT_FILESYSTEMS);
        waitForReady();

        try {
            // TODO: make benchmark async so we don't block other commands
            final NativeDaemonEvent res = mConnector.execute(3 * DateUtils.MINUTE_IN_MILLIS,
                    "volume", "benchmark", volId);
            return Long.parseLong(res.getMessage());
        } catch (NativeDaemonTimeoutException e) {
            return Long.MAX_VALUE;
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
    }

    @Override
    public void partitionPublic(String diskId) {
        Slog.i(TAG, "partitionPublic, diskId=" + diskId);
        enforcePermission(android.Manifest.permission.MOUNT_FORMAT_FILESYSTEMS);
        waitForReady();

        final CountDownLatch latch = findOrCreateDiskScanLatch(diskId);
        try {
            mConnector.execute("volume", "partition", diskId, "public");
            waitForLatch(latch, "partitionPublic", 3 * DateUtils.MINUTE_IN_MILLIS);
        } catch (NativeDaemonConnectorException e) {
            Slog.i(TAG, "partitionPublic NativeDaemonConnectorException, e=" + e.getMessage());
            popFormatFailToast();
            throw e.rethrowAsParcelableException();
        } catch (TimeoutException e) {
            Slog.i(TAG, "partitionPublic timeout exception, e=" + e.getMessage());
            popFormatFailToast();
            throw new IllegalStateException(e);
        }
        Slog.i(TAG, "partitionPublic return");
    }

    @Override
    public void partitionPrivate(String diskId) {
        Slog.i(TAG, "partitionPrivate, diskId=" + diskId);
        enforcePermission(android.Manifest.permission.MOUNT_FORMAT_FILESYSTEMS);
        enforceAdminUser();
        waitForReady();

        final CountDownLatch latch = findOrCreateDiskScanLatch(diskId);
        try {
            mConnector.execute("volume", "partition", diskId, "private");
            waitForLatch(latch, "partitionPrivate", 3 * DateUtils.MINUTE_IN_MILLIS);
        } catch (NativeDaemonConnectorException e) {
            Slog.i(TAG, "partitionPrivate NativeDaemonConnectorException, e=" + e.getMessage());
            popFormatFailToast();
            throw e.rethrowAsParcelableException();
        } catch (TimeoutException e) {
            Slog.i(TAG, "partitionPrivate timeout exception, e=" + e.getMessage());
            popFormatFailToast();
            throw new IllegalStateException(e);
        }
        Slog.i(TAG, "partitionPrivate return");
    }

    @Override
    public void partitionMixed(String diskId, int ratio) {
        Slog.i(TAG, "partitionMixed, diskId=" + diskId + ", ratio=" + ratio);
        enforcePermission(android.Manifest.permission.MOUNT_FORMAT_FILESYSTEMS);
        enforceAdminUser();
        waitForReady();

        final CountDownLatch latch = findOrCreateDiskScanLatch(diskId);
        try {
            mConnector.execute("volume", "partition", diskId, "mixed", ratio);
            waitForLatch(latch, "partitionMixed", 3 * DateUtils.MINUTE_IN_MILLIS);
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        } catch (TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void setVolumeNickname(String fsUuid, String nickname) {
        Slog.i(TAG, "setVolumeNickname, fsUuid=" + fsUuid
                + ", nickname=" + nickname);
        enforcePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);
        waitForReady();

        Preconditions.checkNotNull(fsUuid);
        synchronized (mLock) {
            final VolumeRecord rec = mRecords.get(fsUuid);
            rec.nickname = nickname;
            mCallbacks.notifyVolumeRecordChanged(rec);
            writeSettingsLocked();
        }
    }

    @Override
    public void setVolumeUserFlags(String fsUuid, int flags, int mask) {
        Slog.i(TAG, "setVolumeUserFlags, fsUuid=" + fsUuid
                + ", flags=" + flags + ", mask=" + mask);
        enforcePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);
        waitForReady();

        Preconditions.checkNotNull(fsUuid);
        synchronized (mLock) {
            final VolumeRecord rec = mRecords.get(fsUuid);
            rec.userFlags = (rec.userFlags & ~mask) | (flags & mask);
            mCallbacks.notifyVolumeRecordChanged(rec);
            writeSettingsLocked();
        }
    }

    @Override
    public void forgetVolume(String fsUuid) {
        Slog.i(TAG, "forgetVolume, fsUuid=" + fsUuid);
        enforcePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);
        waitForReady();

        Preconditions.checkNotNull(fsUuid);

        synchronized (mLock) {
            final VolumeRecord rec = mRecords.remove(fsUuid);
            if (rec != null && !TextUtils.isEmpty(rec.partGuid)) {
                mHandler.obtainMessage(H_PARTITION_FORGET, rec.partGuid).sendToTarget();
            }
            mCallbacks.notifyVolumeForgotten(fsUuid);

            // If this had been primary storage, revert back to internal and
            // reset vold so we bind into new volume into place.
            if (Objects.equals(mPrimaryStorageUuid, fsUuid)) {
                mPrimaryStorageUuid = getDefaultPrimaryStorageUuid();
                mHandler.obtainMessage(H_RESET).sendToTarget();
            }

            writeSettingsLocked();
        }
    }

    @Override
    public void forgetAllVolumes() {
        Slog.i(TAG, "forgetAllVolumes");
        enforcePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);
        waitForReady();

        synchronized (mLock) {
            for (int i = 0; i < mRecords.size(); i++) {
                final String fsUuid = mRecords.keyAt(i);
                final VolumeRecord rec = mRecords.valueAt(i);
                if (!TextUtils.isEmpty(rec.partGuid)) {
                    mHandler.obtainMessage(H_PARTITION_FORGET, rec.partGuid).sendToTarget();
                }
                mCallbacks.notifyVolumeForgotten(fsUuid);
            }
            mRecords.clear();

            if (!Objects.equals(StorageManager.UUID_PRIVATE_INTERNAL, mPrimaryStorageUuid)) {
                mPrimaryStorageUuid = getDefaultPrimaryStorageUuid();
            }

            writeSettingsLocked();
            /// M: ALPS02841558 aysnc H_RESET msg cause AdoptableHostTest fail@{
            resetIfReadyAndConnected();
            /// M: ALPS02841558 aysnc H_RESET msg cause AdoptableHostTest fail@}
        }
    }

    private void forgetPartition(String partGuid) {
        Slog.i(TAG, "forgetPartition, partGuid=" + partGuid);
        try {
            mConnector.execute("volume", "forget_partition", partGuid);
        } catch (NativeDaemonConnectorException e) {
            Slog.w(TAG, "Failed to forget key for " + partGuid + ": " + e);
        }
    }

    private void remountUidExternalStorage(int uid, int mode) {
        waitForReady();

        String modeName = "none";
        switch (mode) {
            case Zygote.MOUNT_EXTERNAL_DEFAULT: {
                modeName = "default";
            } break;

            case Zygote.MOUNT_EXTERNAL_READ: {
                modeName = "read";
            } break;

            case Zygote.MOUNT_EXTERNAL_WRITE: {
                modeName = "write";
            } break;
        }

        try {
            mConnector.execute("volume", "remount_uid", uid, modeName);
        } catch (NativeDaemonConnectorException e) {
            Slog.w(TAG, "Failed to remount UID " + uid + " as " + modeName + ": " + e);
        }
    }

    @Override
    public void setDebugFlags(int flags, int mask) {
        Slog.i(TAG, "setDebugFlags, flags=" + flags + ", mask=" + mask);
        enforcePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);
        waitForReady();

        if ((mask & StorageManager.DEBUG_EMULATE_FBE) != 0) {
            if (StorageManager.isFileEncryptedNativeOnly()) {
                throw new IllegalStateException(
                        "Emulation not available on device with native FBE");
            }
            if (mLockPatternUtils.isCredentialRequiredToDecrypt(false)) {
                throw new IllegalStateException(
                        "Emulation requires disabling 'Secure start-up' in Settings > Security");
            }

            final long token = Binder.clearCallingIdentity();
            try {
                final boolean emulateFbe = (flags & StorageManager.DEBUG_EMULATE_FBE) != 0;
                SystemProperties.set(StorageManager.PROP_EMULATE_FBE, Boolean.toString(emulateFbe));

                // Perform hard reboot to kick policy into place
                mContext.getSystemService(PowerManager.class).reboot(null);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        if ((mask & StorageManager.DEBUG_FORCE_ADOPTABLE) != 0) {
            synchronized (mLock) {
                mForceAdoptable = (flags & StorageManager.DEBUG_FORCE_ADOPTABLE) != 0;

                writeSettingsLocked();
                mHandler.obtainMessage(H_RESET).sendToTarget();
            }
        }

        if ((mask & (StorageManager.DEBUG_SDCARDFS_FORCE_ON
                | StorageManager.DEBUG_SDCARDFS_FORCE_OFF)) != 0) {
            final String value;
            if ((flags & StorageManager.DEBUG_SDCARDFS_FORCE_ON) != 0) {
                value = "force_on";
            } else if ((flags & StorageManager.DEBUG_SDCARDFS_FORCE_OFF) != 0) {
                value = "force_off";
            } else {
                value = "";
            }

            final long token = Binder.clearCallingIdentity();
            try {
                SystemProperties.set(StorageManager.PROP_SDCARDFS, value);

                // Reset storage to kick new setting into place
                mHandler.obtainMessage(H_RESET).sendToTarget();
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    @Override
    public String getPrimaryStorageUuid() {
        enforcePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);
        waitForReady();

        synchronized (mLock) {
            Slog.i(TAG, "getPrimaryStorageUuid, mPrimaryStorageUuid=" + mPrimaryStorageUuid);
            return mPrimaryStorageUuid;
        }
    }

    @Override
    public void setPrimaryStorageUuid(String volumeUuid, IPackageMoveObserver callback) {
        Slog.i(TAG, "setPrimaryStorageUuid, volumeUuid=" + volumeUuid);
        enforcePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);
        waitForReady();

        final VolumeInfo from;
        final VolumeInfo to;

        synchronized (mLock) {
            if (Objects.equals(mPrimaryStorageUuid, volumeUuid)) {
                throw new IllegalArgumentException("Primary storage already at " + volumeUuid);
            }

            if (mMoveCallback != null) {
                throw new IllegalStateException("Move already in progress");
            }
            mMoveCallback = callback;
            mMoveTargetUuid = volumeUuid;

            // When moving to/from primary physical volume, we probably just nuked
            // the current storage location, so we have nothing to move.
            if (Objects.equals(StorageManager.UUID_PRIMARY_PHYSICAL, mPrimaryStorageUuid)
                    || Objects.equals(StorageManager.UUID_PRIMARY_PHYSICAL, volumeUuid)) {
                Slog.d(TAG, "Skipping move to/from primary physical");
                onMoveStatusLocked(MOVE_STATUS_COPY_FINISHED);
                onMoveStatusLocked(PackageManager.MOVE_SUCCEEDED);
                mHandler.obtainMessage(H_RESET).sendToTarget();
                return;

            } else {
                from = findStorageForUuid(mPrimaryStorageUuid);
                to = findStorageForUuid(volumeUuid);

                if (from == null) {
                    Slog.w(TAG, "Failing move due to missing from volume " + mPrimaryStorageUuid);
                    onMoveStatusLocked(PackageManager.MOVE_FAILED_INTERNAL_ERROR);
                    return;
                } else if (to == null) {
                    Slog.w(TAG, "Failing move due to missing to volume " + volumeUuid);
                    onMoveStatusLocked(PackageManager.MOVE_FAILED_INTERNAL_ERROR);
                    return;
                }
            }
        }

        try {
            mConnector.execute("volume", "move_storage", from.id, to.id);
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
    }

    @Override
    public int[] getStorageUsers(String path) {
        Slog.i(TAG, "getStorageUsers, path=" + path);
        enforcePermission(android.Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS);
        waitForReady();
        try {
            final String[] r = NativeDaemonEvent.filterMessageList(
                    mConnector.executeForList("storage", "users", path),
                    VoldResponseCode.StorageUsersListResult);

            // FMT: <pid> <process name>
            int[] data = new int[r.length];
            for (int i = 0; i < r.length; i++) {
                String[] tok = r[i].split(" ");
                try {
                    data[i] = Integer.parseInt(tok[0]);
                } catch (NumberFormatException nfe) {
                    Slog.e(TAG, String.format("Error parsing pid %s", tok[0]));
                    return new int[0];
                }
            }
            return data;
        } catch (NativeDaemonConnectorException e) {
            Slog.e(TAG, "Failed to retrieve storage users list", e);
            return new int[0];
        }
    }

    private void warnOnNotMounted() {
        synchronized (mLock) {
            for (int i = 0; i < mVolumes.size(); i++) {
                final VolumeInfo vol = mVolumes.valueAt(i);
                if (vol.isPrimary() && vol.isMountedWritable()) {
                    // Cool beans, we have a mounted primary volume
                    return;
                }
            }
        }

        Slog.w(TAG, "No primary storage mounted!");
    }

    public String[] getSecureContainerList() {
        enforcePermission(android.Manifest.permission.ASEC_ACCESS);
        waitForReady();
        warnOnNotMounted();

        try {
            return NativeDaemonEvent.filterMessageList(
                    mConnector.executeForList("asec", "list"), VoldResponseCode.AsecListResult);
        } catch (NativeDaemonConnectorException e) {
            return new String[0];
        }
    }

    public int createSecureContainer(String id, int sizeMb, String fstype, String key,
            int ownerUid, boolean external) {
        Slog.i(TAG, "createSecureContainer, id=" + id + ", sizeMb="
            + sizeMb + ", fstype=" + fstype + ", key=" + key);
        enforcePermission(android.Manifest.permission.ASEC_CREATE);
        waitForReady();
        warnOnNotMounted();

        int rc = StorageResultCode.OperationSucceeded;
        try {
            mConnector.execute("asec", "create", id, sizeMb, fstype, new SensitiveArg(key),
                    ownerUid, external ? "1" : "0");
        } catch (NativeDaemonConnectorException e) {
            rc = StorageResultCode.OperationFailedInternalError;
        }

        if (rc == StorageResultCode.OperationSucceeded) {
            synchronized (mAsecMountSet) {
                mAsecMountSet.add(id);
            }
        }
        return rc;
    }

    @Override
    public int resizeSecureContainer(String id, int sizeMb, String key) {
        Slog.i(TAG, "resizeSecureContainer, id=" + id
                + ", sizeMb=" + sizeMb + ", key=" + key);
        enforcePermission(android.Manifest.permission.ASEC_CREATE);
        waitForReady();
        warnOnNotMounted();

        int rc = StorageResultCode.OperationSucceeded;
        try {
            mConnector.execute("asec", "resize", id, sizeMb, new SensitiveArg(key));
        } catch (NativeDaemonConnectorException e) {
            rc = StorageResultCode.OperationFailedInternalError;
        }
        return rc;
    }

    public int finalizeSecureContainer(String id) {
        Slog.i(TAG, "finalizeSecureContainer, id=" + id);
        enforcePermission(android.Manifest.permission.ASEC_CREATE);
        warnOnNotMounted();

        int rc = StorageResultCode.OperationSucceeded;
        try {
            mConnector.execute("asec", "finalize", id);
            /*
             * Finalization does a remount, so no need
             * to update mAsecMountSet
             */
        } catch (NativeDaemonConnectorException e) {
            rc = StorageResultCode.OperationFailedInternalError;
        }
        return rc;
    }

    public int fixPermissionsSecureContainer(String id, int gid, String filename) {
        Slog.i(TAG, "fixPermissionsSecureContainer, id=" + id + ", gid=" + gid
                + ", filename=" + filename);
        enforcePermission(android.Manifest.permission.ASEC_CREATE);
        warnOnNotMounted();

        int rc = StorageResultCode.OperationSucceeded;
        try {
            mConnector.execute("asec", "fixperms", id, gid, filename);
            /*
             * Fix permissions does a remount, so no need to update
             * mAsecMountSet
             */
        } catch (NativeDaemonConnectorException e) {
            rc = StorageResultCode.OperationFailedInternalError;
        }
        return rc;
    }

    public int destroySecureContainer(String id, boolean force) {
        Slog.i(TAG, "destroySecureContainer, id=" + id + ", force=" + force);
        enforcePermission(android.Manifest.permission.ASEC_DESTROY);
        waitForReady();
        warnOnNotMounted();

        /*
         * Force a GC to make sure AssetManagers in other threads of the
         * system_server are cleaned up. We have to do this since AssetManager
         * instances are kept as a WeakReference and it's possible we have files
         * open on the external storage.
         */
        Runtime.getRuntime().gc();

        int rc = StorageResultCode.OperationSucceeded;
        try {
            final Command cmd = new Command("asec", "destroy", id);
            if (force) {
                cmd.appendArg("force");
            }
            mConnector.execute(cmd);
        } catch (NativeDaemonConnectorException e) {
            int code = e.getCode();
            if (code == VoldResponseCode.OpFailedStorageBusy) {
                rc = StorageResultCode.OperationFailedStorageBusy;
            } else {
                rc = StorageResultCode.OperationFailedInternalError;
            }
        }

        if (rc == StorageResultCode.OperationSucceeded) {
            synchronized (mAsecMountSet) {
                if (mAsecMountSet.contains(id)) {
                    mAsecMountSet.remove(id);
                }
            }
        }

        return rc;
    }

    public int mountSecureContainer(String id, String key, int ownerUid, boolean readOnly) {
        Slog.i(TAG, "mountSecureContainer, id=" + id + ", key=" + key
                + ", ownerUid=" + ownerUid + ", readOnly=" + readOnly);
        enforcePermission(android.Manifest.permission.ASEC_MOUNT_UNMOUNT);
        waitForReady();
        warnOnNotMounted();

        synchronized (mAsecMountSet) {
            if (mAsecMountSet.contains(id)) {
                return StorageResultCode.OperationFailedStorageMounted;
            }
        }

        int rc = StorageResultCode.OperationSucceeded;
        try {
            mConnector.execute("asec", "mount", id, new SensitiveArg(key), ownerUid,
                    readOnly ? "ro" : "rw");
        } catch (NativeDaemonConnectorException e) {
            int code = e.getCode();
            if (code != VoldResponseCode.OpFailedStorageBusy) {
                rc = StorageResultCode.OperationFailedInternalError;
            }
        }

        if (rc == StorageResultCode.OperationSucceeded) {
            synchronized (mAsecMountSet) {
                mAsecMountSet.add(id);
            }
        }
        return rc;
    }

    public int unmountSecureContainer(String id, boolean force) {
        Slog.i(TAG, "unmountSecureContainer, id=" + id
                + ", force=" + force);
        enforcePermission(android.Manifest.permission.ASEC_MOUNT_UNMOUNT);
        waitForReady();
        warnOnNotMounted();

        synchronized (mAsecMountSet) {
            if (!mAsecMountSet.contains(id)) {
                Slog.i(TAG, "OperationFailedStorageNotMounted");
                return StorageResultCode.OperationFailedStorageNotMounted;
            }
         }

        /*
         * Force a GC to make sure AssetManagers in other threads of the
         * system_server are cleaned up. We have to do this since AssetManager
         * instances are kept as a WeakReference and it's possible we have files
         * open on the external storage.
         */
        Runtime.getRuntime().gc();
        System.runFinalization();

        int rc = StorageResultCode.OperationSucceeded;
        try {
            final Command cmd = new Command("asec", "unmount", id);
            if (force) {
                cmd.appendArg("force");
            }
            mConnector.execute(cmd);
        } catch (NativeDaemonConnectorException e) {
            int code = e.getCode();
            if (code == VoldResponseCode.OpFailedStorageBusy) {
                rc = StorageResultCode.OperationFailedStorageBusy;
            } else {
                rc = StorageResultCode.OperationFailedInternalError;
            }
        }

        if (rc == StorageResultCode.OperationSucceeded) {
            synchronized (mAsecMountSet) {
                mAsecMountSet.remove(id);
            }
        }
        return rc;
    }

    public boolean isSecureContainerMounted(String id) {
        enforcePermission(android.Manifest.permission.ASEC_ACCESS);
        waitForReady();
        warnOnNotMounted();

        synchronized (mAsecMountSet) {
            return mAsecMountSet.contains(id);
        }
    }

    public int renameSecureContainer(String oldId, String newId) {
        Slog.i(TAG, "renameSecureContainer, oldId=" + oldId
                + ", newId=" + newId);
        enforcePermission(android.Manifest.permission.ASEC_RENAME);
        waitForReady();
        warnOnNotMounted();

        synchronized (mAsecMountSet) {
            /*
             * Because a mounted container has active internal state which cannot be
             * changed while active, we must ensure both ids are not currently mounted.
             */
            if (mAsecMountSet.contains(oldId) || mAsecMountSet.contains(newId)) {
                return StorageResultCode.OperationFailedStorageMounted;
            }
        }

        int rc = StorageResultCode.OperationSucceeded;
        try {
            mConnector.execute("asec", "rename", oldId, newId);
        } catch (NativeDaemonConnectorException e) {
            rc = StorageResultCode.OperationFailedInternalError;
        }

        return rc;
    }

    public String getSecureContainerPath(String id) {
        Slog.i(TAG, "getSecureContainerPath, id=" + id);
        enforcePermission(android.Manifest.permission.ASEC_ACCESS);
        waitForReady();
        warnOnNotMounted();

        final NativeDaemonEvent event;
        try {
            event = mConnector.execute("asec", "path", id);
            event.checkCode(VoldResponseCode.AsecPathResult);
            return event.getMessage();
        } catch (NativeDaemonConnectorException e) {
            int code = e.getCode();
            if (code == VoldResponseCode.OpFailedStorageNotFound) {
                Slog.i(TAG, String.format("Container '%s' not found", id));
                return null;
            } else {
                throw new IllegalStateException(String.format("Unexpected response code %d", code));
            }
        }
    }

    public String getSecureContainerFilesystemPath(String id) {
        Slog.i(TAG, "getSecureContainerFilesystemPath, id=" + id);
        enforcePermission(android.Manifest.permission.ASEC_ACCESS);
        waitForReady();
        warnOnNotMounted();

        final NativeDaemonEvent event;
        try {
            event = mConnector.execute("asec", "fspath", id);
            event.checkCode(VoldResponseCode.AsecPathResult);
            return event.getMessage();
        } catch (NativeDaemonConnectorException e) {
            int code = e.getCode();
            if (code == VoldResponseCode.OpFailedStorageNotFound) {
                Slog.i(TAG, String.format("Container '%s' not found", id));
                return null;
            } else {
                throw new IllegalStateException(String.format("Unexpected response code %d", code));
            }
        }
    }

    @Override
    public void finishMediaUpdate() {
        Slog.i(TAG, "finishMediaUpdate");
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("no permission to call finishMediaUpdate()");
        }
        if (mUnmountSignal != null) {
            mUnmountSignal.countDown();
        } else {
            Slog.w(TAG, "Odd, nobody asked to unmount?");
        }
    }

    private boolean isUidOwnerOfPackageOrSystem(String packageName, int callerUid) {
        if (callerUid == android.os.Process.SYSTEM_UID) {
            return true;
        }

        if (packageName == null) {
            return false;
        }

        final int packageUid = mPms.getPackageUid(packageName,
                PackageManager.MATCH_DEBUG_TRIAGED_MISSING, UserHandle.getUserId(callerUid));

        if (DEBUG_OBB) {
            Slog.d(TAG, "packageName = " + packageName + ", packageUid = " +
                    packageUid + ", callerUid = " + callerUid);
        }

        return callerUid == packageUid;
    }

    public String getMountedObbPath(String rawPath) {
        Slog.i(TAG, "getMountedObbPath, rawPath=" + rawPath);
        Preconditions.checkNotNull(rawPath, "rawPath cannot be null");

        waitForReady();
        warnOnNotMounted();

        final ObbState state;
        synchronized (mObbMounts) {
            state = mObbPathToStateMap.get(rawPath);
        }
        if (state == null) {
            Slog.w(TAG, "Failed to find OBB mounted at " + rawPath);
            return null;
        }

        final NativeDaemonEvent event;
        try {
            event = mConnector.execute("obb", "path", state.canonicalPath);
            event.checkCode(VoldResponseCode.AsecPathResult);
            return event.getMessage();
        } catch (NativeDaemonConnectorException e) {
            int code = e.getCode();
            if (code == VoldResponseCode.OpFailedStorageNotFound) {
                return null;
            } else {
                throw new IllegalStateException(String.format("Unexpected response code %d", code));
            }
        }
    }

    @Override
    public boolean isObbMounted(String rawPath) {
        Preconditions.checkNotNull(rawPath, "rawPath cannot be null");
        synchronized (mObbMounts) {
            return mObbPathToStateMap.containsKey(rawPath);
        }
    }

    @Override
    public void mountObb(
            String rawPath, String canonicalPath, String key, IObbActionListener token, int nonce) {
        Preconditions.checkNotNull(rawPath, "rawPath cannot be null");
        Preconditions.checkNotNull(canonicalPath, "canonicalPath cannot be null");
        Preconditions.checkNotNull(token, "token cannot be null");

        final int callingUid = Binder.getCallingUid();
        final ObbState obbState = new ObbState(rawPath, canonicalPath, callingUid, token, nonce);
        final ObbAction action = new MountObbAction(obbState, key, callingUid);
        mObbActionHandler.sendMessage(mObbActionHandler.obtainMessage(OBB_RUN_ACTION, action));

        if (DEBUG_OBB)
            Slog.i(TAG, "Send to OBB handler: " + action.toString());
    }

    @Override
    public void unmountObb(String rawPath, boolean force, IObbActionListener token, int nonce) {
        Preconditions.checkNotNull(rawPath, "rawPath cannot be null");

        final ObbState existingState;
        synchronized (mObbMounts) {
            existingState = mObbPathToStateMap.get(rawPath);
        }

        if (existingState != null) {
            // TODO: separate state object from request data
            final int callingUid = Binder.getCallingUid();
            final ObbState newState = new ObbState(
                    rawPath, existingState.canonicalPath, callingUid, token, nonce);
            final ObbAction action = new UnmountObbAction(newState, force);
            mObbActionHandler.sendMessage(mObbActionHandler.obtainMessage(OBB_RUN_ACTION, action));

            if (DEBUG_OBB)
                Slog.i(TAG, "Send to OBB handler: " + action.toString());
        } else {
            Slog.w(TAG, "Unknown OBB mount at " + rawPath);
        }
    }

    @Override
    public int getEncryptionState() {
        Slog.i(TAG, "getEncryptionState");
        mContext.enforceCallingOrSelfPermission(Manifest.permission.CRYPT_KEEPER,
                "no permission to access the crypt keeper");

        waitForReady();

        final NativeDaemonEvent event;
        try {
            event = mCryptConnector.execute("cryptfs", "cryptocomplete");
            return Integer.parseInt(event.getMessage());
        } catch (NumberFormatException e) {
            // Bad result - unexpected.
            Slog.w(TAG, "Unable to parse result from cryptfs cryptocomplete");
            return ENCRYPTION_STATE_ERROR_UNKNOWN;
        } catch (NativeDaemonConnectorException e) {
            // Something bad happened.
            Slog.w(TAG, "Error in communicating with cryptfs in validating");
            return ENCRYPTION_STATE_ERROR_UNKNOWN;
        }
    }

    @Override
    public int decryptStorage(String password) {
        Slog.i(TAG, "decryptStorage, password=" + password);
        if (TextUtils.isEmpty(password)) {
            throw new IllegalArgumentException("password cannot be empty");
        }

        mContext.enforceCallingOrSelfPermission(Manifest.permission.CRYPT_KEEPER,
                "no permission to access the crypt keeper");

        waitForReady();

        if (DEBUG_EVENTS) {
            Slog.i(TAG, "decrypting storage...");
        }

        final NativeDaemonEvent event;
        try {
            event = mCryptConnector.execute("cryptfs", "checkpw", new SensitiveArg(password));

            final int code = Integer.parseInt(event.getMessage());
            if (code == 0) {
                // Decrypt was successful. Post a delayed message before restarting in order
                // to let the UI to clear itself
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        try {
                            mCryptConnector.execute("cryptfs", "restart");
                        } catch (NativeDaemonConnectorException e) {
                            Slog.e(TAG, "problem executing in background", e);
                        }
                    }
                }, 1000); // 1 second
            }

            return code;
        } catch (NativeDaemonConnectorException e) {
            // Decryption failed
            return e.getCode();
        }
    }

    public int encryptStorage(int type, String password) {
        Slog.i(TAG, "encryptStorage, type=" + CRYPTO_TYPES[type]
                + ", password=" + password);
        if (TextUtils.isEmpty(password) && type != StorageManager.CRYPT_TYPE_DEFAULT) {
            throw new IllegalArgumentException("password cannot be empty");
        }

        mContext.enforceCallingOrSelfPermission(Manifest.permission.CRYPT_KEEPER,
            "no permission to access the crypt keeper");

        waitForReady();

        if (DEBUG_EVENTS) {
            Slog.i(TAG, "encrypting storage...");
        }

        waitMtkLogStopped();
        try {
            if (type == StorageManager.CRYPT_TYPE_DEFAULT) {
                mCryptConnector.execute("cryptfs", "enablecrypto", "inplace",
                                CRYPTO_TYPES[type]);
            } else {
                mCryptConnector.execute("cryptfs", "enablecrypto", "inplace",
                                CRYPTO_TYPES[type], new SensitiveArg(password));
            }
        } catch (NativeDaemonConnectorException e) {
            // Encryption failed
            return e.getCode();
        }

        return 0;
    }

    /** Set the password for encrypting the master key.
     *  @param type One of the CRYPTO_TYPE_XXX consts defined in StorageManager.
     *  @param password The password to set.
     */
    public int changeEncryptionPassword(int type, String password) {
        Slog.i(TAG, "changeEncryptionPassword, type=" + CRYPTO_TYPES[type]
                + ", password=" + password);
        mContext.enforceCallingOrSelfPermission(Manifest.permission.CRYPT_KEEPER,
            "no permission to access the crypt keeper");

        waitForReady();

        if (DEBUG_EVENTS) {
            Slog.i(TAG, "changing encryption password...");
        }

        if (mOldEncryptionType == -1) {
            mOldEncryptionType = getPasswordType();
        }

        try {
            NativeDaemonEvent event = mCryptConnector.execute("cryptfs", "changepw", CRYPTO_TYPES[type],
                        new SensitiveArg(password));

            if (type != mOldEncryptionType) {
                Slog.i(TAG, "Encryption type changed from " + mOldEncryptionType + " to " + type);
                mOldEncryptionType = type;
                sendEncryptionTypeIntent();
            }

            return Integer.parseInt(event.getMessage());
        } catch (NativeDaemonConnectorException e) {
            // Encryption failed
            return e.getCode();
        }
    }

    /**
     * Validate a user-supplied password string with cryptfs
     */
    @Override
    public int verifyEncryptionPassword(String password) throws RemoteException {
        Slog.i(TAG, "verifyEncryptionPassword, password=" + password);
        // Only the system process is permitted to validate passwords
        if (Binder.getCallingUid() != android.os.Process.SYSTEM_UID) {
            throw new SecurityException("no permission to access the crypt keeper");
        }

        mContext.enforceCallingOrSelfPermission(Manifest.permission.CRYPT_KEEPER,
            "no permission to access the crypt keeper");

        if (TextUtils.isEmpty(password)) {
            throw new IllegalArgumentException("password cannot be empty");
        }

        waitForReady();

        if (DEBUG_EVENTS) {
            Slog.i(TAG, "validating encryption password...");
        }

        final NativeDaemonEvent event;
        try {
            event = mCryptConnector.execute("cryptfs", "verifypw", new SensitiveArg(password));
            Slog.i(TAG, "cryptfs verifypw => " + event.getMessage());
            return Integer.parseInt(event.getMessage());
        } catch (NativeDaemonConnectorException e) {
            // Encryption failed
            return e.getCode();
        }
    }

    /**
     * Get the type of encryption used to encrypt the master key.
     * @return The type, one of the CRYPT_TYPE_XXX consts from StorageManager.
     */
    @Override
    public int getPasswordType() {
        Slog.i(TAG, "getPasswordType");
        mContext.enforceCallingOrSelfPermission(Manifest.permission.STORAGE_INTERNAL,
            "no permission to access the crypt keeper");

        waitForReady();

        final NativeDaemonEvent event;
        try {
            event = mCryptConnector.execute("cryptfs", "getpwtype");
            for (int i = 0; i < CRYPTO_TYPES.length; ++i) {
                Slog.i(TAG, "CRYPTO_TYPES[" + i + "]=" + CRYPTO_TYPES[i]
                        + ", event.getMessage()=" + event.getMessage());
                if (CRYPTO_TYPES[i].equals(event.getMessage())) {
                    Slog.i(TAG, "return CRYPTO_TYPES=" + CRYPTO_TYPES[i]);
                    return i;
                }
            }

            throw new IllegalStateException("unexpected return from cryptfs");
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
    }

    /**
     * Set a field in the crypto header.
     * @param field field to set
     * @param contents contents to set in field
     */
    @Override
    public void setField(String field, String contents) throws RemoteException {
        Slog.i(TAG, "setField, field=" + field + ", contens=" + contents);
        mContext.enforceCallingOrSelfPermission(Manifest.permission.STORAGE_INTERNAL,
            "no permission to access the crypt keeper");

        waitForReady();

        final NativeDaemonEvent event;
        try {
            event = mCryptConnector.execute("cryptfs", "setfield", field, contents);
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
    }

    /**
     * Gets a field from the crypto header.
     * @param field field to get
     * @return contents of field
     */
    @Override
    public String getField(String field) throws RemoteException {
        Slog.i(TAG, "getField, field=" + field);
        mContext.enforceCallingOrSelfPermission(Manifest.permission.STORAGE_INTERNAL,
            "no permission to access the crypt keeper");

        waitForReady();

        final NativeDaemonEvent event;
        try {
            final String[] contents = NativeDaemonEvent.filterMessageList(
                    mCryptConnector.executeForList("cryptfs", "getfield", field),
                    VoldResponseCode.CryptfsGetfieldResult);
            String result = new String();
            for (String content : contents) {
                result += content;
            }
            Slog.i(TAG, "getField, return " + result);
            return result;
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
    }

    /**
     * Is userdata convertible to file based encryption?
     * @return non zero for convertible
     */
    @Override
    public boolean isConvertibleToFBE() throws RemoteException {
        mContext.enforceCallingOrSelfPermission(Manifest.permission.STORAGE_INTERNAL,
            "no permission to access the crypt keeper");

        waitForReady();

        final NativeDaemonEvent event;
        try {
            event = mCryptConnector.execute("cryptfs", "isConvertibleToFBE");
            return Integer.parseInt(event.getMessage()) != 0;
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
    }

    @Override
    public String getPassword() throws RemoteException {
        Slog.i(TAG, "getPassword");
        mContext.enforceCallingOrSelfPermission(Manifest.permission.ACCESS_KEYGUARD_SECURE_STORAGE,
                "only keyguard can retrieve password");
        mContext.enforceCallingOrSelfPermission(Manifest.permission.STORAGE_INTERNAL,
                "no permission to access the crypt keeper");
        if (!isReady()) {
            Slog.i(TAG, "not ready, reutn null");
            return new String();
        }

        final NativeDaemonEvent event;
        try {
            event = mCryptConnector.execute("cryptfs", "getpw");
            if ("-1".equals(event.getMessage())) {
                // -1 equals no password
                Slog.i(TAG, "no password, reutn null");
                return null;
            }
            return event.getMessage();
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        } catch (IllegalArgumentException e) {
            Slog.e(TAG, "Invalid response to getPassword");
            return null;
        }
    }

    @Override
    public void clearPassword() throws RemoteException {
        Slog.i(TAG, "clearPassword");
        mContext.enforceCallingOrSelfPermission(Manifest.permission.STORAGE_INTERNAL,
                "only keyguard can clear password");
        if (!isReady()) {
            return;
        }

        final NativeDaemonEvent event;
        try {
            event = mCryptConnector.execute("cryptfs", "clearpw");
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
    }

    @Override
    public void createUserKey(int userId, int serialNumber, boolean ephemeral) {
        enforcePermission(android.Manifest.permission.STORAGE_INTERNAL);
        waitForReady();

        try {
            mCryptConnector.execute("cryptfs", "create_user_key", userId, serialNumber,
                ephemeral ? 1 : 0);
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
    }

    @Override
    public void destroyUserKey(int userId) {
        enforcePermission(android.Manifest.permission.STORAGE_INTERNAL);
        waitForReady();

        try {
            mCryptConnector.execute("cryptfs", "destroy_user_key", userId);
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
    }

    private SensitiveArg encodeBytes(byte[] bytes) {
        if (ArrayUtils.isEmpty(bytes)) {
            return new SensitiveArg("!");
        } else {
            return new SensitiveArg(HexDump.toHexString(bytes));
        }
    }

    @Override
    public void changeUserKey(int userId, int serialNumber,
            byte[] token, byte[] oldSecret, byte[] newSecret) {
        enforcePermission(android.Manifest.permission.STORAGE_INTERNAL);
        waitForReady();

        try {
            mCryptConnector.execute("cryptfs", "change_user_key", userId, serialNumber,
                encodeBytes(token), encodeBytes(oldSecret), encodeBytes(newSecret));
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
    }

    /*
     * Add this token/secret pair to the set of ways we can recover a disk encryption key.
     * Changing the token/secret for a disk encryption key is done in two phases: first, adding
     * a new token/secret pair with this call, then delting all other pairs with
     * fixateNewestUserKeyAuth. This allows other places where a credential is used, such as
     * Gatekeeper, to be updated between the two calls.
     */
    @Override
    public void addUserKeyAuth(int userId, int serialNumber, byte[] token, byte[] secret) {
        enforcePermission(android.Manifest.permission.STORAGE_INTERNAL);
        waitForReady();

        try {
            mCryptConnector.execute("cryptfs", "add_user_key_auth", userId, serialNumber,
                encodeBytes(token), encodeBytes(secret));
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
    }

    /*
     * Delete all disk encryption token/secret pairs except the most recently added one
     */
    @Override
    public void fixateNewestUserKeyAuth(int userId) {
        enforcePermission(android.Manifest.permission.STORAGE_INTERNAL);
        waitForReady();

        try {
            mCryptConnector.execute("cryptfs", "fixate_newest_user_key_auth", userId);
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
    }

    @Override
    public void unlockUserKey(int userId, int serialNumber, byte[] token, byte[] secret) {
        enforcePermission(android.Manifest.permission.STORAGE_INTERNAL);
        waitForReady();

        if (StorageManager.isFileEncryptedNativeOrEmulated()) {
            // When a user has secure lock screen, require a challenge token to
            // actually unlock. This check is mostly in place for emulation mode.
            if (mLockPatternUtils.isSecure(userId) && ArrayUtils.isEmpty(token)) {
                throw new IllegalStateException("Token required to unlock secure user " + userId);
            }

            try {
                mCryptConnector.execute("cryptfs", "unlock_user_key", userId, serialNumber,
                        encodeBytes(token), encodeBytes(secret));
            } catch (NativeDaemonConnectorException e) {
                throw e.rethrowAsParcelableException();
            }
        }

        synchronized (mLock) {
            mLocalUnlockedUsers = ArrayUtils.appendInt(mLocalUnlockedUsers, userId);
        }
    }

    @Override
    public void lockUserKey(int userId) {
        enforcePermission(android.Manifest.permission.STORAGE_INTERNAL);
        waitForReady();

        try {
            mCryptConnector.execute("cryptfs", "lock_user_key", userId);
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }

        synchronized (mLock) {
            mLocalUnlockedUsers = ArrayUtils.removeInt(mLocalUnlockedUsers, userId);
        }
    }

    @Override
    public boolean isUserKeyUnlocked(int userId) {
        synchronized (mLock) {
            return ArrayUtils.contains(mLocalUnlockedUsers, userId);
        }
    }

    @Override
    public void prepareUserStorage(String volumeUuid, int userId, int serialNumber, int flags) {
        enforcePermission(android.Manifest.permission.STORAGE_INTERNAL);
        waitForReady();

        try {
            mCryptConnector.execute("cryptfs", "prepare_user_storage", escapeNull(volumeUuid),
                    userId, serialNumber, flags);
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
    }

    @Override
    public void destroyUserStorage(String volumeUuid, int userId, int flags) {
        enforcePermission(android.Manifest.permission.STORAGE_INTERNAL);
        waitForReady();

        try {
            mCryptConnector.execute("cryptfs", "destroy_user_storage", escapeNull(volumeUuid),
                    userId, flags);
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        }
    }

    @Override
    public ParcelFileDescriptor mountAppFuse(final String name) throws RemoteException {
        try {
            final int uid = Binder.getCallingUid();
            final int pid = Binder.getCallingPid();
            final NativeDaemonEvent event =
                    mConnector.execute("appfuse", "mount", uid, pid, name);
            if (event.getFileDescriptors() == null) {
                throw new RemoteException("AppFuse FD from vold is null.");
            }
            return ParcelFileDescriptor.fromFd(
                    event.getFileDescriptors()[0],
                    mHandler,
                    new ParcelFileDescriptor.OnCloseListener() {
                        @Override
                        public void onClose(IOException e) {
                            try {
                                final NativeDaemonEvent event = mConnector.execute(
                                        "appfuse", "unmount", uid, pid, name);
                            } catch (NativeDaemonConnectorException unmountException) {
                                Log.e(TAG, "Failed to unmount appfuse.");
                            }
                        }
                    });
        } catch (NativeDaemonConnectorException e) {
            throw e.rethrowAsParcelableException();
        } catch (IOException e) {
            throw new RemoteException(e.getMessage());
        }
    }

    @Override
    public int mkdirs(String callingPkg, String appPath) {
        Slog.i(TAG, "mkdirs, callingPkg=" + callingPkg
                + ", appPath=" + appPath);
        final int userId = UserHandle.getUserId(Binder.getCallingUid());
        final UserEnvironment userEnv = new UserEnvironment(userId);

        // Validate that reported package name belongs to caller
        final AppOpsManager appOps = (AppOpsManager) mContext.getSystemService(
                Context.APP_OPS_SERVICE);
        appOps.checkPackage(Binder.getCallingUid(), callingPkg);

        File appFile = null;
        try {
            appFile = new File(appPath).getCanonicalFile();
        } catch (IOException e) {
            Slog.e(TAG, "Failed to resolve " + appPath + ": " + e);
            return -1;
        }

        // Try translating the app path into a vold path, but require that it
        // belong to the calling package.
        if (FileUtils.contains(userEnv.buildExternalStorageAppDataDirs(callingPkg), appFile) ||
                FileUtils.contains(userEnv.buildExternalStorageAppObbDirs(callingPkg), appFile) ||
                FileUtils.contains(userEnv.buildExternalStorageAppMediaDirs(callingPkg), appFile)) {
            appPath = appFile.getAbsolutePath();
            if (!appPath.endsWith("/")) {
                appPath = appPath + "/";
            }

            try {
                mConnector.execute("volume", "mkdirs", appPath);
                return 0;
            } catch (NativeDaemonConnectorException e) {
                return e.getCode();
            }
        }

        throw new SecurityException("Invalid mkdirs path: " + appFile);
    }

    @Override
    public StorageVolume[] getVolumeList(int uid, String packageName, int flags) {
        final int userId = UserHandle.getUserId(uid);

        final boolean forWrite = (flags & StorageManager.FLAG_FOR_WRITE) != 0;
        final boolean realState = (flags & StorageManager.FLAG_REAL_STATE) != 0;
        final boolean includeInvisible = (flags & StorageManager.FLAG_INCLUDE_INVISIBLE) != 0;

        final boolean userKeyUnlocked;
        final boolean storagePermission;
        final long token = Binder.clearCallingIdentity();
        try {
            userKeyUnlocked = isUserKeyUnlocked(userId);
            storagePermission = mMountServiceInternal.hasExternalStorage(uid, packageName);
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        boolean foundPrimary = false;

        final ArrayList<StorageVolume> res = new ArrayList<>();
        synchronized (mLock) {
            for (int i = 0; i < mVolumes.size(); i++) {
                final VolumeInfo vol = mVolumes.valueAt(i);
                switch (vol.getType()) {
                    case VolumeInfo.TYPE_PUBLIC:
                    case VolumeInfo.TYPE_EMULATED:
                        break;
                    default:
                        continue;
                }

                boolean match = false;
                if (forWrite) {
                    match = vol.isVisibleForWrite(userId);
                } else {
                    match = vol.isVisibleForRead(userId)
                            || (includeInvisible && vol.getPath() != null);
                }
                if (!match) continue;

                boolean reportUnmounted = false;
                if ((vol.getType() == VolumeInfo.TYPE_EMULATED) && !userKeyUnlocked) {
                    reportUnmounted = true;
                } else if (!storagePermission && !realState) {
                    reportUnmounted = true;
                }

                final StorageVolume userVol = vol.buildStorageVolume(mContext, userId,
                        reportUnmounted);
                if (vol.isPrimary()) {
                    res.add(0, userVol);
                    foundPrimary = true;
                } else {
                    res.add(userVol);
                }
            }
        }

        if (!foundPrimary) {
            Log.w(TAG, "No primary storage defined yet; hacking together a stub");

            final boolean primaryPhysical = SystemProperties.getBoolean(
                    StorageManager.PROP_PRIMARY_PHYSICAL, false);

            final String id = "stub_primary";
            final File path = Environment.getLegacyExternalStorageDirectory();
            final String description = mContext.getString(android.R.string.unknownName);
            final boolean primary = true;
            final boolean removable = primaryPhysical;
            final boolean emulated = !primaryPhysical;
            final long mtpReserveSize = 0L;
            final boolean allowMassStorage = false;
            final long maxFileSize = 0L;
            final UserHandle owner = new UserHandle(userId);
            final String uuid = null;
            final String state = Environment.MEDIA_REMOVED;

            res.add(0, new StorageVolume(id, StorageVolume.STORAGE_ID_INVALID, path,
                    description, primary, removable, emulated, mtpReserveSize,
                    allowMassStorage, maxFileSize, owner, uuid, state));
        }

        return res.toArray(new StorageVolume[res.size()]);
    }

    @Override
    public DiskInfo[] getDisks() {
        synchronized (mLock) {
            final DiskInfo[] res = new DiskInfo[mDisks.size()];
            for (int i = 0; i < mDisks.size(); i++) {
                res[i] = mDisks.valueAt(i);
            }
            return res;
        }
    }

    @Override
    public VolumeInfo[] getVolumes(int flags) {
        synchronized (mLock) {
            final VolumeInfo[] res = new VolumeInfo[mVolumes.size()];
            for (int i = 0; i < mVolumes.size(); i++) {
                res[i] = mVolumes.valueAt(i);
            }
            return res;
        }
    }

    @Override
    public VolumeRecord[] getVolumeRecords(int flags) {
        synchronized (mLock) {
            final VolumeRecord[] res = new VolumeRecord[mRecords.size()];
            for (int i = 0; i < mRecords.size(); i++) {
                res[i] = mRecords.valueAt(i);
            }
            return res;
        }
    }

    private void addObbStateLocked(ObbState obbState) throws RemoteException {
        final IBinder binder = obbState.getBinder();
        List<ObbState> obbStates = mObbMounts.get(binder);

        if (obbStates == null) {
            obbStates = new ArrayList<ObbState>();
            mObbMounts.put(binder, obbStates);
        } else {
            for (final ObbState o : obbStates) {
                if (o.rawPath.equals(obbState.rawPath)) {
                    throw new IllegalStateException("Attempt to add ObbState twice. "
                            + "This indicates an error in the MountService logic.");
                }
            }
        }

        obbStates.add(obbState);
        try {
            obbState.link();
        } catch (RemoteException e) {
            /*
             * The binder died before we could link it, so clean up our state
             * and return failure.
             */
            obbStates.remove(obbState);
            if (obbStates.isEmpty()) {
                mObbMounts.remove(binder);
            }

            // Rethrow the error so mountObb can get it
            throw e;
        }

        mObbPathToStateMap.put(obbState.rawPath, obbState);
    }

    private void removeObbStateLocked(ObbState obbState) {
        final IBinder binder = obbState.getBinder();
        final List<ObbState> obbStates = mObbMounts.get(binder);
        if (obbStates != null) {
            if (obbStates.remove(obbState)) {
                obbState.unlink();
            }
            if (obbStates.isEmpty()) {
                mObbMounts.remove(binder);
            }
        }

        mObbPathToStateMap.remove(obbState.rawPath);
    }

    private class ObbActionHandler extends Handler {
        private boolean mBound = false;
        private final List<ObbAction> mActions = new LinkedList<ObbAction>();

        ObbActionHandler(Looper l) {
            super(l);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case OBB_RUN_ACTION: {
                    final ObbAction action = (ObbAction) msg.obj;

                    if (DEBUG_OBB)
                        Slog.i(TAG, "OBB_RUN_ACTION: " + action.toString());

                    // If a bind was already initiated we don't really
                    // need to do anything. The pending install
                    // will be processed later on.
                    if (!mBound) {
                        // If this is the only one pending we might
                        // have to bind to the service again.
                        if (!connectToService()) {
                            Slog.e(TAG, "Failed to bind to media container service");
                            action.handleError();
                            return;
                        }
                    }

                    mActions.add(action);
                    break;
                }
                case OBB_MCS_BOUND: {
                    if (DEBUG_OBB)
                        Slog.i(TAG, "OBB_MCS_BOUND");
                    if (msg.obj != null) {
                        mContainerService = (IMediaContainerService) msg.obj;
                    }
                    if (mContainerService == null) {
                        // Something seriously wrong. Bail out
                        Slog.e(TAG, "Cannot bind to media container service");
                        for (ObbAction action : mActions) {
                            // Indicate service bind error
                            action.handleError();
                        }
                        mActions.clear();
                    } else if (mActions.size() > 0) {
                        final ObbAction action = mActions.get(0);
                        if (action != null) {
                            action.execute(this);
                        }
                    } else {
                        // Should never happen ideally.
                        Slog.w(TAG, "Empty queue");
                    }
                    break;
                }
                case OBB_MCS_RECONNECT: {
                    if (DEBUG_OBB)
                        Slog.i(TAG, "OBB_MCS_RECONNECT");
                    if (mActions.size() > 0) {
                        if (mBound) {
                            disconnectService();
                        }
                        if (!connectToService()) {
                            Slog.e(TAG, "Failed to bind to media container service");
                            for (ObbAction action : mActions) {
                                // Indicate service bind error
                                action.handleError();
                            }
                            mActions.clear();
                        }
                    }
                    break;
                }
                case OBB_MCS_UNBIND: {
                    if (DEBUG_OBB)
                        Slog.i(TAG, "OBB_MCS_UNBIND");

                    // Delete pending install
                    if (mActions.size() > 0) {
                        mActions.remove(0);
                    }
                    if (mActions.size() == 0) {
                        if (mBound) {
                            disconnectService();
                        }
                    } else {
                        // There are more pending requests in queue.
                        // Just post MCS_BOUND message to trigger processing
                        // of next pending install.
                        mObbActionHandler.sendEmptyMessage(OBB_MCS_BOUND);
                    }
                    break;
                }
                case OBB_FLUSH_MOUNT_STATE: {
                    final String path = (String) msg.obj;

                    if (DEBUG_OBB)
                        Slog.i(TAG, "Flushing all OBB state for path " + path);

                    synchronized (mObbMounts) {
                        final List<ObbState> obbStatesToRemove = new LinkedList<ObbState>();

                        final Iterator<ObbState> i = mObbPathToStateMap.values().iterator();
                        while (i.hasNext()) {
                            final ObbState state = i.next();

                            /*
                             * If this entry's source file is in the volume path
                             * that got unmounted, remove it because it's no
                             * longer valid.
                             */
                            if (state.canonicalPath.startsWith(path)) {
                                obbStatesToRemove.add(state);
                            }
                        }

                        for (final ObbState obbState : obbStatesToRemove) {
                            if (DEBUG_OBB)
                                Slog.i(TAG, "Removing state for " + obbState.rawPath);

                            removeObbStateLocked(obbState);

                            try {
                                obbState.token.onObbResult(obbState.rawPath, obbState.nonce,
                                        OnObbStateChangeListener.UNMOUNTED);
                            } catch (RemoteException e) {
                                Slog.i(TAG, "Couldn't send unmount notification for  OBB: "
                                        + obbState.rawPath);
                            }
                        }
                    }
                    break;
                }
            }
        }

        private boolean connectToService() {
            if (DEBUG_OBB)
                Slog.i(TAG, "Trying to bind to DefaultContainerService");

            Intent service = new Intent().setComponent(DEFAULT_CONTAINER_COMPONENT);
            if (mContext.bindServiceAsUser(service, mDefContainerConn, Context.BIND_AUTO_CREATE,
                    UserHandle.SYSTEM)) {
                mBound = true;
                return true;
            }
            return false;
        }

        private void disconnectService() {
            mContainerService = null;
            mBound = false;
            mContext.unbindService(mDefContainerConn);
        }
    }

    abstract class ObbAction {
        private static final int MAX_RETRIES = 3;
        private int mRetries;

        ObbState mObbState;

        ObbAction(ObbState obbState) {
            mObbState = obbState;
        }

        public void execute(ObbActionHandler handler) {
            try {
                if (DEBUG_OBB)
                    Slog.i(TAG, "Starting to execute action: " + toString());
                mRetries++;
                if (mRetries > MAX_RETRIES) {
                    Slog.w(TAG, "Failed to invoke remote methods on default container service. Giving up");
                    mObbActionHandler.sendEmptyMessage(OBB_MCS_UNBIND);
                    handleError();
                } else {
                    handleExecute();
                    if (DEBUG_OBB)
                        Slog.i(TAG, "Posting install MCS_UNBIND");
                    mObbActionHandler.sendEmptyMessage(OBB_MCS_UNBIND);
                }
            } catch (RemoteException e) {
                if (DEBUG_OBB)
                    Slog.i(TAG, "Posting install MCS_RECONNECT");
                mObbActionHandler.sendEmptyMessage(OBB_MCS_RECONNECT);
            } catch (Exception e) {
                if (DEBUG_OBB)
                    Slog.d(TAG, "Error handling OBB action", e);
                handleError();
                mObbActionHandler.sendEmptyMessage(OBB_MCS_UNBIND);
            }
        }

        abstract void handleExecute() throws RemoteException, IOException;
        abstract void handleError();

        protected ObbInfo getObbInfo() throws IOException {
            ObbInfo obbInfo;
            try {
                obbInfo = mContainerService.getObbInfo(mObbState.canonicalPath);
            } catch (RemoteException e) {
                Slog.d(TAG, "Couldn't call DefaultContainerService to fetch OBB info for "
                        + mObbState.canonicalPath);
                obbInfo = null;
            }
            if (obbInfo == null) {
                throw new IOException("Couldn't read OBB file: " + mObbState.canonicalPath);
            }
            return obbInfo;
        }

        protected void sendNewStatusOrIgnore(int status) {
            if (mObbState == null || mObbState.token == null) {
                return;
            }

            try {
                mObbState.token.onObbResult(mObbState.rawPath, mObbState.nonce, status);
            } catch (RemoteException e) {
                Slog.w(TAG, "MountServiceListener went away while calling onObbStateChanged");
            }
        }
    }

    class MountObbAction extends ObbAction {
        private final String mKey;
        private final int mCallingUid;

        MountObbAction(ObbState obbState, String key, int callingUid) {
            super(obbState);
            mKey = key;
            mCallingUid = callingUid;
        }

        @Override
        public void handleExecute() throws IOException, RemoteException {
            waitForReady();
            warnOnNotMounted();

            final ObbInfo obbInfo = getObbInfo();

            if (!isUidOwnerOfPackageOrSystem(obbInfo.packageName, mCallingUid)) {
                Slog.w(TAG, "Denied attempt to mount OBB " + obbInfo.filename
                        + " which is owned by " + obbInfo.packageName);
                sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_PERMISSION_DENIED);
                return;
            }

            final boolean isMounted;
            synchronized (mObbMounts) {
                isMounted = mObbPathToStateMap.containsKey(mObbState.rawPath);
            }
            if (isMounted) {
                Slog.w(TAG, "Attempt to mount OBB which is already mounted: " + obbInfo.filename);
                sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_ALREADY_MOUNTED);
                return;
            }

            final String hashedKey;
            if (mKey == null) {
                hashedKey = "none";
            } else {
                try {
                    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");

                    KeySpec ks = new PBEKeySpec(mKey.toCharArray(), obbInfo.salt,
                            PBKDF2_HASH_ROUNDS, CRYPTO_ALGORITHM_KEY_SIZE);
                    SecretKey key = factory.generateSecret(ks);
                    BigInteger bi = new BigInteger(key.getEncoded());
                    hashedKey = bi.toString(16);
                } catch (NoSuchAlgorithmException e) {
                    Slog.e(TAG, "Could not load PBKDF2 algorithm", e);
                    sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_INTERNAL);
                    return;
                } catch (InvalidKeySpecException e) {
                    Slog.e(TAG, "Invalid key spec when loading PBKDF2 algorithm", e);
                    sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_INTERNAL);
                    return;
                }
            }

            int rc = StorageResultCode.OperationSucceeded;
            try {
                mConnector.execute("obb", "mount", mObbState.canonicalPath, new SensitiveArg(hashedKey),
                        mObbState.ownerGid);
            } catch (NativeDaemonConnectorException e) {
                int code = e.getCode();
                if (code != VoldResponseCode.OpFailedStorageBusy) {
                    rc = StorageResultCode.OperationFailedInternalError;
                }
            }

            if (rc == StorageResultCode.OperationSucceeded) {
                if (DEBUG_OBB)
                    Slog.d(TAG, "Successfully mounted OBB " + mObbState.canonicalPath);

                synchronized (mObbMounts) {
                    addObbStateLocked(mObbState);
                }

                sendNewStatusOrIgnore(OnObbStateChangeListener.MOUNTED);
            } else {
                Slog.e(TAG, "Couldn't mount OBB file: " + rc);

                sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_COULD_NOT_MOUNT);
            }
        }

        @Override
        public void handleError() {
            sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_INTERNAL);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("MountObbAction{");
            sb.append(mObbState);
            sb.append('}');
            return sb.toString();
        }
    }

    class UnmountObbAction extends ObbAction {
        private final boolean mForceUnmount;

        UnmountObbAction(ObbState obbState, boolean force) {
            super(obbState);
            mForceUnmount = force;
        }

        @Override
        public void handleExecute() throws IOException {
            waitForReady();
            warnOnNotMounted();

            final ObbState existingState;
            synchronized (mObbMounts) {
                existingState = mObbPathToStateMap.get(mObbState.rawPath);
            }

            if (existingState == null) {
                sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_NOT_MOUNTED);
                return;
            }

            if (existingState.ownerGid != mObbState.ownerGid) {
                Slog.w(TAG, "Permission denied attempting to unmount OBB " + existingState.rawPath
                        + " (owned by GID " + existingState.ownerGid + ")");
                sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_PERMISSION_DENIED);
                return;
            }

            int rc = StorageResultCode.OperationSucceeded;
            try {
                final Command cmd = new Command("obb", "unmount", mObbState.canonicalPath);
                if (mForceUnmount) {
                    cmd.appendArg("force");
                }
                mConnector.execute(cmd);
            } catch (NativeDaemonConnectorException e) {
                int code = e.getCode();
                if (code == VoldResponseCode.OpFailedStorageBusy) {
                    rc = StorageResultCode.OperationFailedStorageBusy;
                } else if (code == VoldResponseCode.OpFailedStorageNotFound) {
                    // If it's not mounted then we've already won.
                    rc = StorageResultCode.OperationSucceeded;
                } else {
                    rc = StorageResultCode.OperationFailedInternalError;
                }
            }

            if (rc == StorageResultCode.OperationSucceeded) {
                synchronized (mObbMounts) {
                    removeObbStateLocked(existingState);
                }

                sendNewStatusOrIgnore(OnObbStateChangeListener.UNMOUNTED);
            } else {
                Slog.w(TAG, "Could not unmount OBB: " + existingState);
                sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_COULD_NOT_UNMOUNT);
            }
        }

        @Override
        public void handleError() {
            sendNewStatusOrIgnore(OnObbStateChangeListener.ERROR_INTERNAL);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("UnmountObbAction{");
            sb.append(mObbState);
            sb.append(",force=");
            sb.append(mForceUnmount);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class Callbacks extends Handler {
        private static final int MSG_STORAGE_STATE_CHANGED = 1;
        private static final int MSG_VOLUME_STATE_CHANGED = 2;
        private static final int MSG_VOLUME_RECORD_CHANGED = 3;
        private static final int MSG_VOLUME_FORGOTTEN = 4;
        private static final int MSG_DISK_SCANNED = 5;
        private static final int MSG_DISK_DESTROYED = 6;
        private static final int MSG_UMS_CONNECTION_CHANGED = 7;

        private final RemoteCallbackList<IMountServiceListener>
                mCallbacks = new RemoteCallbackList<>();

        public Callbacks(Looper looper) {
            super(looper);
        }

        public void register(IMountServiceListener callback) {
            mCallbacks.register(callback);
        }

        public void unregister(IMountServiceListener callback) {
            mCallbacks.unregister(callback);
        }

        @Override
        public void handleMessage(Message msg) {
            final SomeArgs args = (SomeArgs) msg.obj;
            final int n = mCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                final IMountServiceListener callback = mCallbacks.getBroadcastItem(i);
                try {
                    invokeCallback(callback, msg.what, args);
                } catch (RemoteException ignored) {
                }
            }
            mCallbacks.finishBroadcast();
            args.recycle();
        }

        private void invokeCallback(IMountServiceListener callback, int what, SomeArgs args)
                throws RemoteException {
            switch (what) {
                case MSG_STORAGE_STATE_CHANGED: {
                    callback.onStorageStateChanged((String) args.arg1, (String) args.arg2,
                            (String) args.arg3);
                    break;
                }
                case MSG_VOLUME_STATE_CHANGED: {
                    callback.onVolumeStateChanged((VolumeInfo) args.arg1, args.argi2, args.argi3);
                    break;
                }
                case MSG_VOLUME_RECORD_CHANGED: {
                    callback.onVolumeRecordChanged((VolumeRecord) args.arg1);
                    break;
                }
                case MSG_VOLUME_FORGOTTEN: {
                    callback.onVolumeForgotten((String) args.arg1);
                    break;
                }
                case MSG_DISK_SCANNED: {
                    callback.onDiskScanned((DiskInfo) args.arg1, args.argi2);
                    break;
                }
                case MSG_DISK_DESTROYED: {
                    callback.onDiskDestroyed((DiskInfo) args.arg1);
                    break;
                }
                case MSG_UMS_CONNECTION_CHANGED: {
                    callback.onUsbMassStorageConnectionChanged((boolean)args.arg1);
                    break;
                }
            }
        }

        private void notifyStorageStateChanged(String path, String oldState, String newState) {
            final SomeArgs args = SomeArgs.obtain();
            args.arg1 = path;
            args.arg2 = oldState;
            args.arg3 = newState;
            obtainMessage(MSG_STORAGE_STATE_CHANGED, args).sendToTarget();
        }

        private void notifyVolumeStateChanged(VolumeInfo vol, int oldState, int newState) {
            final SomeArgs args = SomeArgs.obtain();
            args.arg1 = vol.clone();
            args.argi2 = oldState;
            args.argi3 = newState;
            obtainMessage(MSG_VOLUME_STATE_CHANGED, args).sendToTarget();
        }

        private void notifyVolumeRecordChanged(VolumeRecord rec) {
            final SomeArgs args = SomeArgs.obtain();
            args.arg1 = rec.clone();
            obtainMessage(MSG_VOLUME_RECORD_CHANGED, args).sendToTarget();
        }

        private void notifyVolumeForgotten(String fsUuid) {
            final SomeArgs args = SomeArgs.obtain();
            args.arg1 = fsUuid;
            obtainMessage(MSG_VOLUME_FORGOTTEN, args).sendToTarget();
        }

        private void notifyDiskScanned(DiskInfo disk, int volumeCount) {
            final SomeArgs args = SomeArgs.obtain();
            args.arg1 = disk.clone();
            args.argi2 = volumeCount;
            obtainMessage(MSG_DISK_SCANNED, args).sendToTarget();
        }

        private void notifyDiskDestroyed(DiskInfo disk) {
            final SomeArgs args = SomeArgs.obtain();
            args.arg1 = disk.clone();
            obtainMessage(MSG_DISK_DESTROYED, args).sendToTarget();
        }

        private void onUsbMassStorageConnectionChanged(boolean connected) {
            final SomeArgs args = SomeArgs.obtain();
            args.arg1 = connected;
            obtainMessage(MSG_UMS_CONNECTION_CHANGED, args).sendToTarget();
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DUMP, TAG);

        final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ", 160);
        synchronized (mLock) {
            pw.println("Disks:");
            pw.increaseIndent();
            for (int i = 0; i < mDisks.size(); i++) {
                final DiskInfo disk = mDisks.valueAt(i);
                disk.dump(pw);
            }
            pw.decreaseIndent();

            pw.println();
            pw.println("Volumes:");
            pw.increaseIndent();
            for (int i = 0; i < mVolumes.size(); i++) {
                final VolumeInfo vol = mVolumes.valueAt(i);
                if (VolumeInfo.ID_PRIVATE_INTERNAL.equals(vol.id)) continue;
                vol.dump(pw);
            }
            pw.decreaseIndent();

            pw.println();
            pw.println("Records:");
            pw.increaseIndent();
            for (int i = 0; i < mRecords.size(); i++) {
                final VolumeRecord note = mRecords.valueAt(i);
                note.dump(pw);
            }
            pw.decreaseIndent();

            pw.println();
            pw.println("Primary storage UUID: " + mPrimaryStorageUuid);
            pw.println("Force adoptable: " + mForceAdoptable);
            pw.println();
            pw.println("Local unlocked users: " + Arrays.toString(mLocalUnlockedUsers));
            pw.println("System unlocked users: " + Arrays.toString(mSystemUnlockedUsers));
        }

        synchronized (mObbMounts) {
            pw.println();
            pw.println("mObbMounts:");
            pw.increaseIndent();
            final Iterator<Entry<IBinder, List<ObbState>>> binders = mObbMounts.entrySet()
                    .iterator();
            while (binders.hasNext()) {
                Entry<IBinder, List<ObbState>> e = binders.next();
                pw.println(e.getKey() + ":");
                pw.increaseIndent();
                final List<ObbState> obbStates = e.getValue();
                for (final ObbState obbState : obbStates) {
                    pw.println(obbState);
                }
                pw.decreaseIndent();
            }
            pw.decreaseIndent();

            pw.println();
            pw.println("mObbPathToStateMap:");
            pw.increaseIndent();
            final Iterator<Entry<String, ObbState>> maps = mObbPathToStateMap.entrySet().iterator();
            while (maps.hasNext()) {
                final Entry<String, ObbState> e = maps.next();
                pw.print(e.getKey());
                pw.print(" -> ");
                pw.println(e.getValue());
            }
            pw.decreaseIndent();
        }

        pw.println();
        pw.println("mConnector:");
        pw.increaseIndent();
        mConnector.dump(fd, pw, args);
        pw.decreaseIndent();

        pw.println();
        pw.println("mCryptConnector:");
        pw.increaseIndent();
        mCryptConnector.dump(fd, pw, args);
        pw.decreaseIndent();

        pw.println();
        pw.print("Last maintenance: ");
        pw.println(TimeUtils.formatForLogging(mLastMaintenance));
    }

    /** {@inheritDoc} */
    @Override
    public void monitor() {
        if (mConnector != null) {
            mConnector.monitor();
        }
        if (mCryptConnector != null) {
            mCryptConnector.monitor();
        }
    }

    private final class MountServiceInternalImpl extends MountServiceInternal {
        // Not guarded by a lock.
        private final CopyOnWriteArrayList<ExternalStorageMountPolicy> mPolicies =
                new CopyOnWriteArrayList<>();

        @Override
        public void addExternalStoragePolicy(ExternalStorageMountPolicy policy) {
            // No locking - CopyOnWriteArrayList
            mPolicies.add(policy);
        }

        @Override
        public void onExternalStoragePolicyChanged(int uid, String packageName) {
            final int mountMode = getExternalStorageMountMode(uid, packageName);
            remountUidExternalStorage(uid, mountMode);
        }

        @Override
        public int getExternalStorageMountMode(int uid, String packageName) {
            // No locking - CopyOnWriteArrayList
            int mountMode = Integer.MAX_VALUE;
            for (ExternalStorageMountPolicy policy : mPolicies) {
                final int policyMode = policy.getMountMode(uid, packageName);
                if (policyMode == Zygote.MOUNT_EXTERNAL_NONE) {
                    return Zygote.MOUNT_EXTERNAL_NONE;
                }
                mountMode = Math.min(mountMode, policyMode);
            }
            if (mountMode == Integer.MAX_VALUE) {
                return Zygote.MOUNT_EXTERNAL_NONE;
            }
            return mountMode;
        }

        public boolean hasExternalStorage(int uid, String packageName) {
            // No need to check for system uid. This avoids a deadlock between
            // PackageManagerService and AppOpsService.
            if (uid == Process.SYSTEM_UID) {
                return true;
            }
            // No locking - CopyOnWriteArrayList
            for (ExternalStorageMountPolicy policy : mPolicies) {
                final boolean policyHasStorage = policy.hasExternalStorage(uid, packageName);
                if (!policyHasStorage) {
                    return false;
                }
            }
            return true;
        }
    }

/////////////////////////MTK feature/////////////////////////////////////////

    /**
     * register receiver for MTK feature
     */
    private void initMTKFeature() {
        // for DM APP feature
        registerDMAPPReceiver();

        // for Privacy Protection feature
        registerPrivacyProtectionReceiver();

        // for UMS feature
        registerUsbStateReceiver();

        // for IPO feature
        registerBootIPOReceiver();
    }

    private void popFormatFailToast() {
        mHandler.post(new Runnable() {
            public void run() {
                Toast.makeText(mContext, mContext.getString(
                        com.mediatek.internal.R.string.format_error),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * for mtk multi-log feature
     * when to encrypt phone storage, send eject broadcast and wait mtklog
     * stopped, if stopped, then do encrypt
     */
    private void waitMtkLogStopped() {
        Slog.i(TAG, "waitMtkLogStopped...");
        VolumeInfo emulatedVolume = null;
        synchronized (mLock) {
            for (int i = 0; i < mVolumes.size(); i++) {
                final VolumeInfo vol = mVolumes.valueAt(i);
                if (vol.getType() == VolumeInfo.TYPE_EMULATED
                        && vol.getState() == VolumeInfo.STATE_MOUNTED) {
                    emulatedVolume = vol;
                    break;
                }
            }
        }
        if (emulatedVolume == null) {
            Slog.i(TAG, "cannot find emulated volume, return");
            return;
        }

        final StorageVolume userVol = emulatedVolume.buildStorageVolume(
                mContext, mCurrentUserId, false);
        final Intent intent = new Intent(Intent.ACTION_MEDIA_EJECT,
                Uri.fromFile(userVol.getPathFile()));
        intent.putExtra(StorageVolume.EXTRA_STORAGE_VOLUME, userVol);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        Slog.i(TAG, "sendBroadcastAsUser, intent=" + intent
                + ", userVol=" + userVol);
        mContext.sendBroadcastAsUser(intent, userVol.getOwner());

        int tryCount = 0;
        final String netlog = "debug.mtklog.netlog.Running";
        final String mdlogger = "debug.mdlogger.Running";
        final String MBlog = "debug.MB.running";
        final String GPSlog = "debug.gpsdbglog.enable";
        while (!SystemProperties.get(netlog, "0").equals("0")
                || !SystemProperties.get(mdlogger, "0").equals("0")
                || !SystemProperties.get(MBlog, "0").equals("0")
                || !SystemProperties.get(GPSlog, "0").equals("0")) {
            if (tryCount == 60) {
                Slog.i(TAG, "try count = 60, break");
                break;
            }
            try {
                Slog.i(TAG, netlog + "=" + SystemProperties.get(netlog));
                Slog.i(TAG, mdlogger + "=" + SystemProperties.get(mdlogger));
                Slog.i(TAG, MBlog + "=" + SystemProperties.get(MBlog));
                Slog.i(TAG, GPSlog + "=" + SystemProperties.get(GPSlog));
                Thread.sleep(500);
                tryCount++;
            } catch (Exception e) {
            }
        }
        if (tryCount != 60) {
            try {
                Thread.sleep(3000);
            } catch (Exception e) {
            }
        }
        Slog.i(TAG, "waitMtkLogStopped done");
    }

/////////////////////////default write path/////////////////

    private static final String PROP_VOLD_DECRYPT = "vold.decrypt";
    private static final String INSERT_OTG = "insert_otg";
    private boolean isDiskInsert = false;
    private static boolean isBootingPhase = false;
    private static boolean isShuttingDown = false;
    //private int mCurrentUserId = 0;

    /**
     * set default path for APP to storage data.
     */
    public void setDefaultPath(String path) {
        if (path == null) {
            Slog.e(TAG, "setDefaultPath error! path=null");
            return;
        }
        try {
            SystemProperties.set(StorageManagerEx.PROP_SD_DEFAULT_PATH, path);
            Slog.e(TAG, "setDefaultPath new path=" + path);
        } catch (IllegalArgumentException e) {
            Slog.e(TAG, "IllegalArgumentException when set default path:", e);
        }
    }

    private void updateDefaultPathForUserSwitch() {
        Slog.i(TAG, "updateDefaultPathForUserSwitch");
        String defaultPath = getDefaultPath();
        Slog.i(TAG, "current default path = " + defaultPath);
        if (defaultPath.contains("emulated")) {
            defaultPath = "/storage/emulated/" + mCurrentUserId;
            // no need to pop default path changed toast
            Slog.i(TAG, "change default path to " + defaultPath);
            setDefaultPath(defaultPath);
        } else {
            updateDefaultPathIfNeed();
        }
    }

    /**
     * If old default path is not mounted, choose the first mounted
     * and visible volume as default path
     */
    private void updateDefaultPathIfNeed() {
        Slog.i(TAG, "updateDefaultPathIfNeed");

        if (isBootingPhase) {
            Slog.i(TAG, "In booting phase, don't update default path");
            return;
        }

        if (isIPOBooting) {
            Slog.i(TAG, "In IPO booting phase, don't update default path");
            return;
        }

        if (isShuttingDown) {
            Slog.i(TAG, "In shutting down, don't update default path");
            return;
        }

        String defaultPath = getDefaultPath();
        Slog.i(TAG, "current default path = " + defaultPath);
        String newPath = "";
        boolean needChange = false;
        boolean isFindCurrentDefaultPathVolume = false;

        int userId = mCurrentUserId;
        synchronized(mLock) {
            for (int i = 0; i < mVolumes.size(); i++) {
                final VolumeInfo vol = mVolumes.valueAt(i);
                File pathFile = vol.getPathForUser(userId);
                if (pathFile != null && pathFile.getAbsolutePath().equals(defaultPath)) {
                    Slog.i(TAG, "find default path volume= " + vol);
                    isFindCurrentDefaultPathVolume = true;
                    if (vol.getState() != VolumeInfo.STATE_MOUNTED) {
                        Slog.i(TAG, "old default path is not mounted");
                        needChange = true;
                        break;
                    } else {
                        if (!vol.isVisibleForWrite(userId)) {
                            Slog.i(TAG, "old default path is not visible for write, userId="
                                   + userId);
                            needChange = true;
                            break;
                        } else {
                            Slog.i(TAG, "old default path is visible for write, userId=" + userId);
                        }
                    }
                }
            }
        }

        if (needChange || !isFindCurrentDefaultPathVolume) {
            Slog.i(TAG, "need change default path " + defaultPath);
            synchronized(mLock) {
                for (int i = 0; i < mVolumes.size(); i++) {
                    final VolumeInfo vol = mVolumes.valueAt(i);
                    if (vol.getState() == VolumeInfo.STATE_MOUNTED
                            && vol.isVisibleForWrite(userId)) {
                        newPath = vol.getPathForUser(userId).getAbsolutePath();
                        Slog.i(TAG, "updateDefaultPathIfNeed from " + defaultPath
                                + " to " + newPath);
                        setDefaultPath(newPath);
                        popDefaultPathChangedToast();
                        break;
                    }
                }
            }

            if (newPath.equals("")) {
                Slog.i(TAG, "not find mounted and visible volume, keep old default path:"
                       + defaultPath);
            }
        } else {
            Slog.i(TAG, "no need change default path, keep default path:" + defaultPath);
        }
    }

    private String getDefaultPath() {
        StorageManagerEx sm = new StorageManagerEx();
        return sm.getDefaultPath();
    }

    /**
     * check all storage state
     * if there is more than one storage in MOUNTED state
     * we should show the dialog after insert and mount succeed
     */
    private boolean isShowDefaultPathDialog(VolumeInfo curVol) {
        Slog.i(TAG, "isShowDefaultPathDialog, curVol=" + curVol);

        if (curVol == null) {
            Slog.i(TAG, "curVolume is null, skip it.");
            return false;
        }

        if(isBootingPhase) {
            Slog.i(TAG, "in booting phase, not show defaultPathDialog, skip it.");
            return false;
        }

        if (isIPOBooting) {
            Slog.i(TAG, "in IPO booting phase, not show defaultPathDialog, skip it.");
            return false;
        }

        if (curVol.getState() != VolumeInfo.STATE_MOUNTED
                && curVol.getState() != VolumeInfo.STATE_CHECKING) {
            Slog.i(TAG, "this volume state is not mounted/checking, skip it.");
            return false;
        }

        if (!isDiskInsert) {
            Slog.i(TAG, "not disk insert, no need show dialog, return false");
            return false;
        }

        isDiskInsert = false;
        int mountCount = 0;
        if(SystemProperties.get(PROP_VOLD_DECRYPT).equals("trigger_restart_min_framework")) {
            Slog.i(TAG, "PROP_VOLD_DECRYPT=trigger_restart_min_framework, return false");
            return false;
        }

        // when emulated volume is mounted, no need to show default path dialog
        if (curVol.getType() == VolumeInfo.TYPE_EMULATED && curVol.getDiskId() != null) {
            Slog.i(TAG, "isShowDefaultPathDialog, emulated volume, return false");
            return false;
        }

        int userId = mCurrentUserId;
        synchronized (mLock) {
            for (int i = 0; i < mVolumes.size(); i++) {
                final VolumeInfo vol = mVolumes.valueAt(i);
                if (vol.getState() == VolumeInfo.STATE_MOUNTED && vol.isVisibleForWrite(userId)) {
                    Slog.i(TAG, "find a visibe & mounted volume, volumeId=" + vol.getId());
                    mountCount++;
                }
            }
            Slog.i(TAG, "mount and visible volumes count=" + mountCount);
        }
        return (mountCount > 1);
    }

    /**
     * check all storage, if there is one internal storage
     * we should show the toast when plug out sd card
     */
    private void popDefaultPathChangedToast() {
        mHandler.post(new Runnable() {
            public void run() {
                Toast.makeText(mContext, mContext.getString(
                        com.mediatek.internal.R.string.sdcard_default_path_change),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * open setting activity about Storage & USB
     *
     */
    private void showDefaultPathDialog(VolumeInfo vol) {
        Slog.i(TAG, "showDefaultPathDialog, vol=" + vol);
        int userId = mCurrentUserId;
        if (!vol.isVisibleForWrite(userId)) {
            Slog.i(TAG, "showDefaultPathDialog,but vol is not visible to userID="
                    + userId + ", volumeInfo=" + vol);
            return;
        }

        Intent intent = new Intent("com.mediatek.storage.StorageDefaultPathDialog");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (vol.isUSBOTG()) {
            intent.putExtra(INSERT_OTG, true);
        }
        mContext.startActivity(intent);
    }

/////////////////////////DM APP//////////////////////////////////////////////////

    private static final String PROP_DM_APP = "ro.mtk_dm_app";
    private static final String OMADM_USB_ENABLE =  "com.mediatek.dm.LAWMO_UNLOCK";
    private static final String OMADM_USB_DISABLE = "com.mediatek.dm.LAWMO_LOCK";
    private static final String OMADM_SD_FORMAT =   "com.mediatek.dm.LAWMO_WIPE";
    private static final Object OMADM_SYNC_LOCK = new Object();
    /**
     * register DM_APP receiver if need
     */
    private void registerDMAPPReceiver() {
        if (SystemProperties.get(PROP_DM_APP).equals("1")) {
            final IntentFilter DMFilter = new IntentFilter();
            DMFilter.addAction(OMADM_USB_ENABLE);
            DMFilter.addAction(OMADM_USB_DISABLE);
            DMFilter.addAction(OMADM_SD_FORMAT);
            mContext.registerReceiver(mDMReceiver, DMFilter, null, mHandler);
        }
    }

    private void enableUSBFuction(boolean enable) {
        waitForReady();
        try {
            mConnector.execute("USB", enable ? "enable" : "disable");
        } catch (NativeDaemonConnectorException e) {
            Slog.e(TAG, "enableUSBFunction failed, ", e);
        }
    }

    private final BroadcastReceiver mDMReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(OMADM_USB_ENABLE)) {
                Slog.d(TAG, "mDMReceiver USB enable");
                new Thread() {
                    public void run() {
                        synchronized (OMADM_SYNC_LOCK) {
                            enableUSBFuction(true);
                        }
                    }
                } .start();
            } else if (action.equals(OMADM_USB_DISABLE)) {
                Slog.d(TAG, "mDMReceiver USB disable");
                new Thread() {
                    public void run() {
                        synchronized (OMADM_SYNC_LOCK) {
                           enableUSBFuction(false);
                        }
                    }
                } .start();
            } else if (action.equals(OMADM_SD_FORMAT)) {
                Slog.d(TAG, "mDMReceiver format SD");
                new Thread() {
                    public void run() {
                        VolumeInfo[] volumes = getVolumes(0);
                        synchronized (OMADM_SYNC_LOCK) {
                            int userId = mCurrentUserId;
                            for (int i = 0; i < volumes.length; i++) {
                                final VolumeInfo vol = volumes[i];
                                if (!vol.isVisible()
                                        || vol.getType() != VolumeInfo.TYPE_PUBLIC) {
                                    Slog.d(TAG, "no need format, skip volume=" + vol);
                                    continue;
                                }
                                try {
                                    if (vol.getState() == VolumeInfo.STATE_MOUNTED) {
                                        unmount(vol.getId());
                                        //wait for unmount succeed...
                                        for (int j = 0; j < 20; j++) {
                                            sleep(1000);
                                            if (vol.getState() == VolumeInfo.STATE_UNMOUNTED) {
                                                Slog.d(TAG, "Unmount Succeeded, volume=" + vol);
                                                break;
                                            }
                                        }
                                    }
                                    else if (vol.getState() == VolumeInfo.STATE_MEDIA_SHARED) {
                                        Slog.d(TAG, "volume is shared, unshared firstly, volume="
                                               + vol);
                                        doShareUnshareVolume(vol.getId(), false);
                                    }

                                    format(vol.getId());
                                    Slog.d(TAG, "format Succeed! volume=" + vol);
                                } catch (InterruptedException e) {
                                    Slog.e(TAG, "SD format exception", e);
                                } catch (IllegalArgumentException e) {
                                    Slog.e(TAG, "SD format exception", e);
                                } catch (SecurityException e) {
                                    Slog.e(TAG, "SD format exception", e);
                                } catch (NullPointerException e) {
                                    Slog.e(TAG, "SD format exception", e);
                                }
                            }
                        }
                    }
                } .start();
            }
        }
    };

/////////////////////////Privacy Protection feature////////////////////////////////////////////

    private static final String PRIVACY_PROTECTION_LOCK = "com.mediatek.ppl.NOTIFY_LOCK";
    private static final String PRIVACY_PROTECTION_UNLOCK = "com.mediatek.ppl.NOTIFY_UNLOCK";
    private static final String PRIVACY_PROTECTION_WIPE
      = "com.mediatek.ppl.NOTIFY_MOUNT_SERVICE_WIPE";
    private static final String PRIVACY_PROTECTION_WIPE_DONE
      = "com.mediatek.ppl.MOUNT_SERVICE_WIPE_RESPONSE";
    private static final Object FORMAT_LOCK = new Object();

    private void registerPrivacyProtectionReceiver() {
        final IntentFilter privacyProtectionFilter = new IntentFilter();
        privacyProtectionFilter.addAction(PRIVACY_PROTECTION_LOCK);
        privacyProtectionFilter.addAction(PRIVACY_PROTECTION_UNLOCK);
        privacyProtectionFilter.addAction(PRIVACY_PROTECTION_WIPE);
        mContext.registerReceiver(mPrivacyProtectionReceiver,
                                  privacyProtectionFilter,
                                  null,
                                  mHandler);
    }

    private ArrayList<VolumeInfo> findVolumeListNeedFormat() {
        Slog.i(TAG, "findVolumeListNeedFormat");
        ArrayList<VolumeInfo> tempVolumes = Lists.newArrayList();
        synchronized (mLock) {
            for (int i = 0; i < mVolumes.size(); i++) {
                final VolumeInfo vol = mVolumes.valueAt(i);
                // if external sd card is formatted as internal storage
                // it shoule be formatted, but don't format data partition
                // which will be formatted by factory reset
                if ((!vol.isUSBOTG() && vol.isVisible()
                        && vol.getType() == VolumeInfo.TYPE_PUBLIC)
                        || (vol.getType() == VolumeInfo.TYPE_PRIVATE
                        && vol.getDiskId() != null)) {
                    tempVolumes.add(vol);
                    Slog.i(TAG, "i will try to format volume= " + vol);
                }
            }
        }
        return tempVolumes;
    }

    private void formatPhoneStorageAndExternalSDCard() {

        final ArrayList<VolumeInfo> tempVolumes = findVolumeListNeedFormat();

        new Thread() {
            public void run() {
                synchronized (FORMAT_LOCK) {
                    int userId = mCurrentUserId;
                    for (int i = 0; i < tempVolumes.size(); i++) {
                        final VolumeInfo vol = tempVolumes.get(i);
                        // if external sd card is formatted as internal storage
                        // we just format it as public storage
                        if (vol.getType() == VolumeInfo.TYPE_PRIVATE
                                && vol.getDiskId() != null) {
                            Slog.i(TAG, "use partition public to format, volume= " + vol);
                            partitionPublic(vol.getDiskId());
                            if (vol.getFsUuid() != null) {
                                forgetVolume(vol.getFsUuid());
                            }
                            continue;
                        }

                        // first need to wait checking state if needed
                        if (vol.getState() == VolumeInfo.STATE_CHECKING) {
                            Slog.i(TAG, "volume is checking, wait..");
                            for (int j = 0; j < 30; j++) {
                                try {
                                    sleep(1000);
                                } catch (InterruptedException ex) {
                                    Slog.e(TAG, "Exception when wait!", ex);
                                }
                                if (vol.getState() != VolumeInfo.STATE_CHECKING) {
                                    Slog.i(TAG, "volume wait checking done!");
                                    break;
                                }
                            }
                        }
                        // then unmount if needed
                        if (vol.getState() == VolumeInfo.STATE_MOUNTED) {
                            Slog.i(TAG, "volume is mounted, unmount firstly"
                                    + ", volume=" + vol);
                            unmount(vol.getId());
                            for (int j = 0; j < 30; j++) {
                                try {
                                    sleep(1000);
                                } catch (InterruptedException ex) {
                                    Slog.e(TAG, "Exception when wait!", ex);
                                }
                                if (vol.getState() == VolumeInfo.STATE_UNMOUNTED) {
                                    Slog.i(TAG, "wait unmount done!");
                                    break;
                                }
                            }
                        }
                        // then unshare if needed
                        if (vol.getState() == VolumeInfo.STATE_MEDIA_SHARED) {
                            Slog.i(TAG, "volume is shared, unshared firstly"
                                    + " volume=" + vol);
                            doShareUnshareVolume(vol.getId(), false);
                            for (int j = 0; j < 30; j++) {
                                try {
                                    sleep(1000);
                                } catch (InterruptedException ex) {
                                    Slog.e(TAG, "Exception when wait!", ex);
                                }
                                if (vol.getState() == VolumeInfo.STATE_UNMOUNTED) {
                                    Slog.i(TAG, "wait unshare done!");
                                    break;
                                }
                            }
                        }

                        format(vol.getId());
                        Slog.d(TAG, "format Succeed! volume=" + vol);
                    }

                    // notify Privacy Protection that format done
                    Intent intent = new Intent(PRIVACY_PROTECTION_WIPE_DONE);
                    mContext.sendBroadcast(intent);
                    Slog.d(TAG, "Privacy Protection wipe: send " + intent);
                }
            }
        } .start();
    }

    private final BroadcastReceiver mPrivacyProtectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(PRIVACY_PROTECTION_UNLOCK)) {
                Slog.i(TAG, "Privacy Protection unlock!");
                new Thread() {
                    public void run() {
                        enableUSBFuction(true);
                    }
                } .start();
            } else if (action.equals(PRIVACY_PROTECTION_LOCK)) {
                Slog.i(TAG, "Privacy Protection lock!");
                new Thread() {
                    public void run() {
                        enableUSBFuction(false);
                    }
                } .start();
            } else if (action.equals(PRIVACY_PROTECTION_WIPE)) {
                Slog.i(TAG, "Privacy Protection wipe!");
                formatPhoneStorageAndExternalSDCard();
            }
        }
    };

///////////////////USB Mass Storage feature///////////////////////////

    private boolean mUmsEnabling;
    // this will set to true when user is turning on UMS or turning off UMS
    private boolean mIsTurnOnOffUsb = false;
    private boolean mIsUsbConnected = false;
    private boolean mSendUmsConnectedOnBoot = false;
    private int mUMSCount = 0;
    private static final Object TURNONUSB_SYNC_LOCK = new Object();

    private void registerUsbStateReceiver() {
        mContext.registerReceiver(
                mUsbReceiver,
                new IntentFilter(UsbManager.ACTION_USB_STATE),
                null,
                mHandler);
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Slog.i(TAG, "mUsbReceiver onReceive, intent=" + intent);
            boolean isConnected =
                    intent.getBooleanExtra(UsbManager.USB_CONNECTED, false) &&
                    intent.getBooleanExtra(UsbManager.USB_FUNCTION_MASS_STORAGE, false) &&
                    !intent.getBooleanExtra("SettingUsbCharging", false);
            mIsUsbConnected = isConnected;
            notifyShareAvailabilityChange(isConnected);
        }
    };

    private void notifyShareAvailabilityChange(boolean isConnected) {
        Slog.i(TAG, "notifyShareAvailabilityChange, isConnected=" + isConnected);
        mCallbacks.onUsbMassStorageConnectionChanged(isConnected);

        if (mSystemReady == true) {
            sendUmsIntent(isConnected);
        } else {
            mSendUmsConnectedOnBoot = isConnected;
        }

        if (!isConnected) {
            // M: two case need turn off
            // 1. is turning on UMS
            // 2. there is any storage at SHARED status
            boolean needTurnOff = false;
            if (mIsTurnOnOffUsb) {
                needTurnOff = true;
            } else {
                synchronized (mLock) {
                    for (int i = 0; i < mVolumes.size(); i++) {
                        final VolumeInfo vol = mVolumes.valueAt(i);
                        if (vol.getState() == VolumeInfo.STATE_MEDIA_SHARED) {
                            needTurnOff = true;
                            break;
                        }
                    }
                }
            }
            /*
             * USB mass storage disconnected while enabled
            */
            if (needTurnOff) {
                new Thread("MountService#turnOffUMS") {
                    @Override
                    public void run() {
                        synchronized (TURNONUSB_SYNC_LOCK) {
                            setUsbMassStorageEnabled(false);
                        }
                    }
                } .start();
            }
        }
    }

    private void doShareUnshareVolume(String volId, boolean enable) {
        Slog.i(TAG, "doShareUnshareVolume, volId=" + volId
                + ", enable=" + enable);
        final VolumeInfo vol = findVolumeByIdOrThrow(volId);
        Slog.i(TAG, "doShareUnshareVolume, find volumeInfo=" + vol);
        if (vol.getType() == VolumeInfo.TYPE_EMULATED) {
            Slog.i(TAG, "emulated storage no need to share/unshare");
            return;
        }

        try {
            mConnector.execute("volume", enable ? "share" : "unshare", volId, "ums");
        } catch (NativeDaemonConnectorException e) {
            Slog.e(TAG, "Failed to share/unshare", e);
        }
    }

    private boolean getUmsEnabling() {
        return mUmsEnabling;
    }

    private void setUmsEnabling(boolean enable) {
        mUmsEnabling = enable;
    }

    private void sendUmsIntent(boolean c) {
        mContext.sendBroadcastAsUser(
                new Intent((c ? Intent.ACTION_UMS_CONNECTED : Intent.ACTION_UMS_DISCONNECTED)),
                UserHandle.ALL);
    }

    /**
     * Just check if the volume is enable to be share
     * @param vol
     * @return
     */
    private boolean isVolumeSharedEnable(VolumeInfo vol) {
        int userId = mCurrentUserId;
        // only public and visible is sharedable volume
        if (!vol.isAllowUsbMassStorage(userId)) {
            Slog.i(TAG, "not able to shared Volume=" + vol);
            return false;
        }

        boolean result = doGetVolumeShared(vol.getId());
        Slog.i(TAG, "isVolumeSharedEnable return " + result);
        return result;
    }

    private boolean doGetVolumeShared(String volId) {
        Slog.i(TAG, "doGetVolumeShared volId=" + volId);

        final NativeDaemonEvent event;
        try {
            event = mConnector.execute("volume", "shared", volId, "ums");
        } catch (NativeDaemonConnectorException ex) {
            Slog.e(TAG, "Failed to read response to volume shared " + volId + " ums");
            return false;
        }

        if (event.getCode() == VoldResponseCode.ShareEnabledResult) {
            return event.getMessage().endsWith("enabled");
        } else {
            return false;
        }
    }

    private void validateUserRestriction(String restriction) {
        UserManager um = (UserManager) mContext.getSystemService(Context.USER_SERVICE);

        if (um != null
                && um.hasUserRestriction(restriction, Binder.getCallingUserHandle())) {
            throw new SecurityException("User has restriction " + restriction);
        }
    }

///////////////////IPO feature///////////////////////////
    private static final String BOOT_IPO = "android.intent.action.ACTION_BOOT_IPO";
    private static boolean isIPOBooting = false;

    private void registerBootIPOReceiver() {
        final IntentFilter bootIPOFilter = new IntentFilter();
        bootIPOFilter.addAction(BOOT_IPO);
        mContext.registerReceiver(mBootIPOReceiver, bootIPOFilter, null, mHandler);
    }

    private final BroadcastReceiver mBootIPOReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            new Thread() {
                public void run() {
                    Slog.d(TAG, "MountService BOOT_IPO");
                    isIPOBooting = true;
                    try {
                        Slog.d(TAG, "Notify VOLD IPO startup");
                        mConnector.execute("volume", "ipo", "startup");
                    } catch (NativeDaemonConnectorException e) {
                        Slog.e(TAG, "Error reinit SD card while IPO", e);
                    }

                    waitAllVolumeMounted();
                    Slog.d(TAG, "MountService BOOT_IPO finish");
                    isIPOBooting = false;
                    updateDefaultPathIfNeed();
                }
            } .start();
        }
    };

    /**
     * wait all volume mounted, when ipo startup
     * @return
     */
    private void waitAllVolumeMounted() {
        try {
            Slog.d(TAG, "waitAllVolumeMounted when ipo startup");
            int retryCount = 0;
            while(retryCount < 10) {
                boolean isNeedWait = false;
                VolumeInfo[] volumes = getVolumes(0);
                for (int i = 0; i < volumes.length; i++) {
                    final VolumeInfo vol = volumes[i];
                    if (vol.getType() == VolumeInfo.TYPE_PUBLIC
                            || vol.getType() == VolumeInfo.TYPE_EMULATED) {
                        if (vol.isVisibleForWrite(mCurrentUserId) &&
                                vol.getState() != VolumeInfo.STATE_MOUNTED) {
                            Slog.i(TAG, "volume is not mounted, wait...");
                            isNeedWait = true;
                            retryCount++;
                            Thread.sleep(1000);
                            break;
                        }
                    }
                }
                if (!isNeedWait) {
                    Slog.i(TAG, "all visible volume is mounted");
                    break;
                }
            }
        } catch (Exception e) {
        }
    }

    /**
     * wait net log write tcpdump_xxx.cap finished before unmount volume
     */
    private void waitMTKNetlogStopped() {
        Slog.i(TAG, "waitMTKNetlogStopped...");

        int tryCount = 0;
        final String netlog = "debug.mtklog.netlog.Running";
        while (!SystemProperties.get(netlog, "0").equals("0")) {
            if (tryCount == 60) {
                Slog.i(TAG, "try count = 60, break");
                break;
            }
            try {
                Slog.i(TAG, netlog + "=" + SystemProperties.get(netlog));
                Thread.sleep(500);
                tryCount++;
            } catch (Exception e) {
            }
        }
        Slog.i(TAG, "waitMTKNetlogStopped done");
    }

//////////////////encrypt storage///////////////////////
    public static final String ACTION_ENCRYPTION_TYPE_CHANGED =
            "com.mediatek.intent.extra.ACTION_ENCRYPTION_TYPE_CHANGED";
    private int mOldEncryptionType = -1;

    private void sendEncryptionTypeIntent() {
        mContext.sendBroadcastAsUser(new Intent(ACTION_ENCRYPTION_TYPE_CHANGED), UserHandle.ALL);
    }

    // for setting check if set primary storage Uuid is finished or not
    public boolean isSetPrimaryStorageUuidFinished() {
        synchronized (mLock) {
            return this.mMoveCallback == null ? true : false;
        }
    }
}
