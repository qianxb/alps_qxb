/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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

package android.media;

import android.app.ActivityManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.database.Cursor;
import android.database.SQLException;
import android.drm.DrmManagerClient;
import android.graphics.BitmapFactory;
import android.mtp.MtpConstants;
import android.net.Uri;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.RemoteException;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.SystemProperties;
import android.os.storage.VolumeInfo;
import android.os.storage.DiskInfo;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Audio.Playlists;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.sax.Element;
import android.sax.ElementListener;
import android.sax.RootElement;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import dalvik.system.CloseGuard;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Internal service helper that no-one should use directly.
 *
 * The way the scan currently works is:
 * - The Java MediaScannerService creates a MediaScanner (this class), and calls
 *   MediaScanner.scanDirectories on it.
 * - scanDirectories() calls the native processDirectory() for each of the specified directories.
 * - the processDirectory() JNI method wraps the provided mediascanner client in a native
 *   'MyMediaScannerClient' class, then calls processDirectory() on the native MediaScanner
 *   object (which got created when the Java MediaScanner was created).
 * - native MediaScanner.processDirectory() calls
 *   doProcessDirectory(), which recurses over the folder, and calls
 *   native MyMediaScannerClient.scanFile() for every file whose extension matches.
 * - native MyMediaScannerClient.scanFile() calls back on Java MediaScannerClient.scanFile,
 *   which calls doScanFile, which after some setup calls back down to native code, calling
 *   MediaScanner.processFile().
 * - MediaScanner.processFile() calls one of several methods, depending on the type of the
 *   file: parseMP3, parseMP4, parseMidi, parseOgg or parseWMA.
 * - each of these methods gets metadata key/value pairs from the file, and repeatedly
 *   calls native MyMediaScannerClient.handleStringTag, which calls back up to its Java
 *   counterparts in this file.
 * - Java handleStringTag() gathers the key/value pairs that it's interested in.
 * - once processFile returns and we're back in Java code in doScanFile(), it calls
 *   Java MyMediaScannerClient.endFile(), which takes all the data that's been
 *   gathered and inserts an entry in to the database.
 *
 * In summary:
 * Java MediaScannerService calls
 * Java MediaScanner scanDirectories, which calls
 * Java MediaScanner processDirectory (native method), which calls
 * native MediaScanner processDirectory, which calls
 * native MyMediaScannerClient scanFile, which calls
 * Java MyMediaScannerClient scanFile, which calls
 * Java MediaScannerClient doScanFile, which calls
 * Java MediaScanner processFile (native method), which calls
 * native MediaScanner processFile, which calls
 * native parseMP3, parseMP4, parseMidi, parseOgg or parseWMA, which calls
 * native MyMediaScanner handleStringTag, which calls
 * Java MyMediaScanner handleStringTag.
 * Once MediaScanner processFile returns, an entry is inserted in to the database.
 *
 * The MediaScanner class is not thread-safe, so it should only be used in a single threaded manner.
 *
 * {@hide}
 */
public class MediaScanner implements AutoCloseable {
    static {
        System.loadLibrary("media_jni");
        native_init();
    }

    private final static String TAG = "MediaScanner";
    /// M: Add for control debug log, only default enable it on eng/userdebug load. @{
    private static final boolean LOGD = !"user".equals(Build.TYPE);
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG) || LOGD;

    private static final String[] FILES_PRESCAN_PROJECTION = new String[] {
            Files.FileColumns._ID, // 0
            Files.FileColumns.DATA, // 1
            Files.FileColumns.FORMAT, // 2
            Files.FileColumns.DATE_MODIFIED, // 3
    };

    private static final String[] ID_PROJECTION = new String[] {
            Files.FileColumns._ID,
    };

    private static final int FILES_PRESCAN_ID_COLUMN_INDEX = 0;
    private static final int FILES_PRESCAN_PATH_COLUMN_INDEX = 1;
    private static final int FILES_PRESCAN_FORMAT_COLUMN_INDEX = 2;
    private static final int FILES_PRESCAN_DATE_MODIFIED_COLUMN_INDEX = 3;

    private static final String[] PLAYLIST_MEMBERS_PROJECTION = new String[] {
            Audio.Playlists.Members.PLAYLIST_ID, // 0
     };

    private static final int ID_PLAYLISTS_COLUMN_INDEX = 0;
    private static final int PATH_PLAYLISTS_COLUMN_INDEX = 1;
    private static final int DATE_MODIFIED_PLAYLISTS_COLUMN_INDEX = 2;

    private static final String RINGTONES_DIR = "/ringtones/";
    private static final String NOTIFICATIONS_DIR = "/notifications/";
    private static final String ALARMS_DIR = "/alarms/";
    private static final String MUSIC_DIR = "/music/";
    private static final String PODCAST_DIR = "/podcasts/";

    private static final String EXTERNAL_PRIMARY_STORAGE_PATH_L = "/storage/sdcard0/";
    private static final String EXTERNAL_SECONDARY_STORAGE_PATH_L = "/storage/sdcard1/";

    private static final String[] ID3_GENRES = {
        // ID3v1 Genres
        "Blues",
        "Classic Rock",
        "Country",
        "Dance",
        "Disco",
        "Funk",
        "Grunge",
        "Hip-Hop",
        "Jazz",
        "Metal",
        "New Age",
        "Oldies",
        "Other",
        "Pop",
        "R&B",
        "Rap",
        "Reggae",
        "Rock",
        "Techno",
        "Industrial",
        "Alternative",
        "Ska",
        "Death Metal",
        "Pranks",
        "Soundtrack",
        "Euro-Techno",
        "Ambient",
        "Trip-Hop",
        "Vocal",
        "Jazz+Funk",
        "Fusion",
        "Trance",
        "Classical",
        "Instrumental",
        "Acid",
        "House",
        "Game",
        "Sound Clip",
        "Gospel",
        "Noise",
        "AlternRock",
        "Bass",
        "Soul",
        "Punk",
        "Space",
        "Meditative",
        "Instrumental Pop",
        "Instrumental Rock",
        "Ethnic",
        "Gothic",
        "Darkwave",
        "Techno-Industrial",
        "Electronic",
        "Pop-Folk",
        "Eurodance",
        "Dream",
        "Southern Rock",
        "Comedy",
        "Cult",
        "Gangsta",
        "Top 40",
        "Christian Rap",
        "Pop/Funk",
        "Jungle",
        "Native American",
        "Cabaret",
        "New Wave",
        "Psychadelic",
        "Rave",
        "Showtunes",
        "Trailer",
        "Lo-Fi",
        "Tribal",
        "Acid Punk",
        "Acid Jazz",
        "Polka",
        "Retro",
        "Musical",
        "Rock & Roll",
        "Hard Rock",
        // The following genres are Winamp extensions
        "Folk",
        "Folk-Rock",
        "National Folk",
        "Swing",
        "Fast Fusion",
        "Bebob",
        "Latin",
        "Revival",
        "Celtic",
        "Bluegrass",
        "Avantgarde",
        "Gothic Rock",
        "Progressive Rock",
        "Psychedelic Rock",
        "Symphonic Rock",
        "Slow Rock",
        "Big Band",
        "Chorus",
        "Easy Listening",
        "Acoustic",
        "Humour",
        "Speech",
        "Chanson",
        "Opera",
        "Chamber Music",
        "Sonata",
        "Symphony",
        "Booty Bass",
        "Primus",
        "Porn Groove",
        "Satire",
        "Slow Jam",
        "Club",
        "Tango",
        "Samba",
        "Folklore",
        "Ballad",
        "Power Ballad",
        "Rhythmic Soul",
        "Freestyle",
        "Duet",
        "Punk Rock",
        "Drum Solo",
        "A capella",
        "Euro-House",
        "Dance Hall",
        // The following ones seem to be fairly widely supported as well
        "Goa",
        "Drum & Bass",
        "Club-House",
        "Hardcore",
        "Terror",
        "Indie",
        "Britpop",
        null,
        "Polsk Punk",
        "Beat",
        "Christian Gangsta",
        "Heavy Metal",
        "Black Metal",
        "Crossover",
        "Contemporary Christian",
        "Christian Rock",
        "Merengue",
        "Salsa",
        "Thrash Metal",
        "Anime",
        "JPop",
        "Synthpop",
        // 148 and up don't seem to have been defined yet.
    };

    private long mNativeContext;
    private final Context mContext;
    private final String mPackageName;
    private final String mVolumeName;
    private final ContentProviderClient mMediaProvider;
    private final Uri mAudioUri;
    private final Uri mVideoUri;
    private final Uri mImagesUri;
    private final Uri mThumbsUri;
    private final Uri mVideoThumbsUri;
    private final Uri mPlaylistsUri;
    private final Uri mFilesUri;
    private final Uri mFilesUriNoNotify;
    private final boolean mProcessPlaylists;
    private final boolean mProcessGenres;
    private int mMtpObjectHandle;

    private final String mExternalStoragePath;
    private final boolean mExternalIsEmulated;

    private final AtomicBoolean mClosed = new AtomicBoolean();
    private final CloseGuard mCloseGuard = CloseGuard.get();

    /** whether to use bulk inserts or individual inserts for each item */
    private static final boolean ENABLE_BULK_INSERTS = true;

    // used when scanning the image database so we know whether we have to prune
    // old thumbnail files
    private int mOriginalCount;
    /// M: old video thumbnail files
    private int mOriginalVideoCount;
    /** Whether the database had any entries in it before the scan started */
    private boolean mWasEmptyPriorToScan = false;
    /** Whether the scanner has set a default sound for the ringer ringtone. */
    private boolean mDefaultRingtoneSet;
    /** Whether the scanner has set a default sound for the notification ringtone. */
    private boolean mDefaultNotificationSet;
    /** Whether the scanner has set a default sound for the alarm ringtone. */
    private boolean mDefaultAlarmSet;
    /** The filename for the default sound for the ringer ringtone. */
    private String mDefaultRingtoneFilename;
    /** The filename for the default sound for the notification ringtone. */
    private String mDefaultNotificationFilename;
    /** The filename for the default sound for the alarm ringtone. */
    private String mDefaultAlarmAlertFilename;
    /**
     * The prefix for system properties that define the default sound for
     * ringtones. Concatenate the name of the setting from Settings
     * to get the full system property.
     */
    private static final String DEFAULT_RINGTONE_PROPERTY_PREFIX = "ro.config.";

    private final BitmapFactory.Options mBitmapOptions = new BitmapFactory.Options();

    private static class FileEntry {
        long mRowId;
        String mPath;
        long mLastModified;
        int mFormat;
        boolean mLastModifiedChanged;

        FileEntry(long rowId, String path, long lastModified, int format) {
            mRowId = rowId;
            mPath = path;
            mLastModified = lastModified;
            mFormat = format;
            mLastModifiedChanged = false;
        }

        @Override
        public String toString() {
            return mPath + " mRowId: " + mRowId;
        }
    }

    private static class PlaylistEntry {
        String path;
        long bestmatchid;
        int bestmatchlevel;
    }

    private final ArrayList<PlaylistEntry> mPlaylistEntries = new ArrayList<>();
    private final ArrayList<FileEntry> mPlayLists = new ArrayList<>();

    private MediaInserter mMediaInserter;

    private DrmManagerClient mDrmManagerClient = null;
    /// M: Add 3 flags to identify if alarm, notification and ringtone is set or not. @{
    private static final String ALARM_SET = "alarm_set";
    private static final String NOTIFICATION_SET = "notification_set";
    private static final String RINGTONE_SET = "ringtone_set";
    /// @}
    /// M: limit bmp and gif file size, don't decode images if big over limit to avoid memory issue
    private long mLimitBmpFileSize = Long.MAX_VALUE;
    private long mLimitGifFileSize = Long.MAX_VALUE;

    public MediaScanner(Context c, String volumeName) {
        native_setup();
        mContext = c;
        mPackageName = c.getPackageName();
        mVolumeName = volumeName;

        mBitmapOptions.inSampleSize = 1;
        mBitmapOptions.inJustDecodeBounds = true;

        setDefaultRingtoneFileNames();

        mMediaProvider = mContext.getContentResolver()
                .acquireContentProviderClient(MediaStore.AUTHORITY);

        mAudioUri = Audio.Media.getContentUri(volumeName);
        mVideoUri = Video.Media.getContentUri(volumeName);
        mImagesUri = Images.Media.getContentUri(volumeName);
        mThumbsUri = Images.Thumbnails.getContentUri(volumeName);
        mVideoThumbsUri = Video.Thumbnails.getContentUri(volumeName);
        mFilesUri = Files.getContentUri(volumeName);
        mFilesUriNoNotify = mFilesUri.buildUpon().appendQueryParameter("nonotify", "1").build();

        if (!volumeName.equals("internal")) {
            // we only support playlists on external media
            mProcessPlaylists = true;
            mProcessGenres = true;
            mPlaylistsUri = Playlists.getContentUri(volumeName);
        } else {
            mProcessPlaylists = false;
            mProcessGenres = false;
            mPlaylistsUri = null;
        }

        final Locale locale = mContext.getResources().getConfiguration().locale;
        if (locale != null) {
            String language = locale.getLanguage();
            String country = locale.getCountry();
            if (language != null) {
                if (country != null) {
                    setLocale(language + "_" + country);
                } else {
                    setLocale(language);
                }
            }
        }

        mCloseGuard.open("close");
        mExternalStoragePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        mExternalIsEmulated = Environment.isExternalStorageEmulated();
        //mClient.testGenreNameConverter();

        /// M: set bmp and gif decode limit size same as Gallery
        final ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am.isLowRamDevice()) {
            mLimitBmpFileSize = 6 * TrafficStats.MB_IN_BYTES;
            mLimitGifFileSize = 10 * TrafficStats.MB_IN_BYTES;
        } else {
            mLimitBmpFileSize = 52 * TrafficStats.MB_IN_BYTES;
            mLimitGifFileSize = 20 * TrafficStats.MB_IN_BYTES;
        }
    }

    private void setDefaultRingtoneFileNames() {
        mDefaultRingtoneFilename = SystemProperties.get(DEFAULT_RINGTONE_PROPERTY_PREFIX
                + Settings.System.RINGTONE);
        mDefaultNotificationFilename = SystemProperties.get(DEFAULT_RINGTONE_PROPERTY_PREFIX
                + Settings.System.NOTIFICATION_SOUND);
        mDefaultAlarmAlertFilename = SystemProperties.get(DEFAULT_RINGTONE_PROPERTY_PREFIX
                + Settings.System.ALARM_ALERT);
        /// M: Adds log to debug setting ringtones.
        if (DEBUG) {
            Log.v(TAG, "setDefaultRingtoneFileNames: ringtone=" + mDefaultRingtoneFilename
                + ",notification=" + mDefaultNotificationFilename
                + ",alarm=" + mDefaultAlarmAlertFilename);
        }
    }

    private final MyMediaScannerClient mClient = new MyMediaScannerClient();

    private boolean isDrmEnabled() {
        String prop = SystemProperties.get("drm.service.enabled");
        return prop != null && prop.equals("true");
    }

    private class MyMediaScannerClient implements MediaScannerClient {

        private String mArtist;
        private String mAlbumArtist;    // use this if mArtist is missing
        private String mAlbum;
        private String mTitle;
        private String mComposer;
        private String mGenre;
        private String mMimeType;
        private int mFileType;
        private int mTrack;
        private int mYear;
        private int mDuration;
        private String mPath;
        private long mLastModified;
        private long mFileSize;
        private String mWriter;
        private int mCompilation;
        private boolean mIsDrm;
        private boolean mNoMedia;   // flag to suppress file from appearing in media tables
        private int mWidth;
        private int mHeight;
        /// M: add for mtk drm @{
        private String mDrmContentUr;
        private long mDrmOffset;
        private long mDrmDataLen;
        private String mDrmRightsIssuer;
        private String mDrmContentName;
        private String mDrmContentDescriptioin;
        private String mDrmContentVendor;
        private String mDrmIconUri;
        private long mDrmMethod;
        /// @}
        /// M: add for slow motion
        private String mSlowMotionSpeed;
        /// M: Add for fancy gallery homepage(Video get it's rotation)
        private int mOrientation;

        public FileEntry beginFile(String path, String mimeType, long lastModified,
                long fileSize, boolean isDirectory, boolean noMedia) {
            /// M: Directory or folder's mimeType must be null, because some special name folder(such as
            /// test.mp3) may give a wrong mimeType by MediaFile.getMimeTypeForFile(path).
            mMimeType = isDirectory ? null : mimeType;
            mFileType = 0;
            mFileSize = fileSize;
            mIsDrm = false;

            if (!isDirectory) {
                if (!noMedia && isNoMediaFile(path)) {
                    noMedia = true;
                }
                mNoMedia = noMedia;

                // try mimeType first, if it is specified
                if (mimeType != null) {
                    mFileType = MediaFile.getFileTypeForMimeType(mimeType);
                }

                /// M: OMA DRM v1: however, for those DCF file, normally when scanning it,
                // the input mime type should be "application/vnd.oma.drm.content";
                // however, there's case that the input mimetype is, for example, "image/*"
                // in these cases it will not call processFile() but processImageFile() instead.
                // for these cases, we change the {mFileType} back to ZERO,
                // and let Media.getFileType(path) to determine the type,
                // so that it can call processFile() as normal. @{
                if (MediaFile.isImageFileType(mFileType)) {
                    int lastDot = path.lastIndexOf(".");
                    if (lastDot > 0 && path.substring(lastDot + 1).toUpperCase().equals("DCF")) {
                        if (DEBUG) {
                            Log.v(TAG, "detect a *.DCF file with input mime type:" + mimeType);
                        }
                        mFileType = 0; // work around: change to ZERO
                    }
                }
                /// @}

                // if mimeType was not specified, compute file type based on file extension.
                if (mFileType == 0) {
                    MediaFile.MediaFileType mediaFileType = MediaFile.getFileType(path);
                    if (mediaFileType != null) {
                        mFileType = mediaFileType.fileType;
                        if (mMimeType == null || isValueslessMimeType(mMimeType)) {
                            mMimeType = mediaFileType.mimeType;
                        }
                    }
                }

                if (isDrmEnabled() && MediaFile.isDrmFileType(mFileType)) {
                    mFileType = getFileTypeFromDrm(path);
                }
                /// M: Add to get original mimetype from file for cta
                if (isDrmEnabled() && path.endsWith(".mudp")) {
                    if (mDrmManagerClient == null) {
                        mDrmManagerClient = new DrmManagerClient(mContext);
                    }
                    if (mDrmManagerClient.canHandle(path, null)) {
                        mMimeType = mDrmManagerClient.getOriginalMimeType(path);
                        mIsDrm = true;
                        if (DEBUG) {
                            Log.d(TAG, "get cta file " + path
                                + " with original mimetype " + mMimeType);
                        }
                    }
                }
            }

            FileEntry entry = makeEntryFor(path);
            // add some slack to avoid a rounding error
            long delta = (entry != null) ? (lastModified - entry.mLastModified) : 0;
            boolean wasModified = delta > 1 || delta < -1;
            if (entry == null || wasModified) {
                if (wasModified) {
                    entry.mLastModified = lastModified;
                } else {
                    entry = new FileEntry(0, path, lastModified,
                            (isDirectory ? MtpConstants.FORMAT_ASSOCIATION : 0));
                }
                entry.mLastModifiedChanged = true;
            }

            if (mProcessPlaylists && MediaFile.isPlayListFileType(mFileType)) {
                mPlayLists.add(entry);
                /// M: MediaScanner Performance turning {@
                /// Store playlist path and insert them in postScanAll
                mPlaylistFilePathList.add(path);
                /// @}
                // we don't process playlists in the main scan, so return null
                return null;
            }

            // clear all the metadata
            mArtist = null;
            mAlbumArtist = null;
            mAlbum = null;
            mTitle = null;
            mComposer = null;
            mGenre = null;
            mTrack = 0;
            mYear = 0;
            mDuration = 0;
            mPath = path;
            mLastModified = lastModified;
            mWriter = null;
            mCompilation = 0;
            mWidth = 0;
            mHeight = 0;
            /// M: add for mtk drm @{
            mDrmContentDescriptioin = null;
            mDrmContentName = null;
            mDrmContentUr = null;
            mDrmContentVendor = null;
            mDrmIconUri = null;
            mDrmRightsIssuer = null;
            mDrmDataLen = -1;
            mDrmOffset = -1;
            mDrmMethod = -1;
            /// @}
            /// M: add for slow motion
            mSlowMotionSpeed = "(0,0)x0";
            /// M: add for fancy gallery homepage
            mOrientation = 0;

            return entry;
        }

        @Override
        public void scanFile(String path, long lastModified, long fileSize,
                boolean isDirectory, boolean noMedia) {
            // This is the callback funtion from native codes.
            // Log.v(TAG, "scanFile: "+path);
            doScanFile(path, null, lastModified, fileSize, isDirectory, false, noMedia);
        }

        public Uri doScanFile(String path, String mimeType, long lastModified,
                long fileSize, boolean isDirectory, boolean scanAlways, boolean noMedia) {
            Uri result = null;
//            long t1 = System.currentTimeMillis();
            try {
                FileEntry entry = beginFile(path, mimeType, lastModified,
                        fileSize, isDirectory, noMedia);

                if (entry == null) {
                    return null;
                }

                // if this file was just inserted via mtp, set the rowid to zero
                // (even though it already exists in the database), to trigger
                // the correct code path for updating its entry
                if (mMtpObjectHandle != 0) {
                    entry.mRowId = 0;
                }

                if (entry.mPath != null &&
                        ((!mDefaultNotificationSet &&
                                doesPathHaveFilename(entry.mPath, mDefaultNotificationFilename))
                        || (!mDefaultRingtoneSet &&
                                doesPathHaveFilename(entry.mPath, mDefaultRingtoneFilename))
                        || (!mDefaultAlarmSet &&
                                doesPathHaveFilename(entry.mPath, mDefaultAlarmAlertFilename)))) {
                    if (DEBUG) {
                        Log.w(TAG, "forcing rescan of " + entry.mPath +
                            "since ringtone setting didn't finish");
                    }
                    scanAlways = true;
                }

                // rescan for metadata if file was modified since last scan
                if (entry != null && (entry.mLastModifiedChanged || scanAlways)) {
                    if (noMedia) {
                        result = endFile(entry, false, false, false, false, false);
                    } else {
                        String lowpath = path.toLowerCase(Locale.ROOT);
                        boolean ringtones = (lowpath.indexOf(RINGTONES_DIR) > 0);
                        boolean notifications = (lowpath.indexOf(NOTIFICATIONS_DIR) > 0);
                        boolean alarms = (lowpath.indexOf(ALARMS_DIR) > 0);
                        boolean podcasts = (lowpath.indexOf(PODCAST_DIR) > 0);
                        boolean music = (lowpath.indexOf(MUSIC_DIR) > 0) ||
                            (!ringtones && !notifications && !alarms && !podcasts);

                        boolean isaudio = MediaFile.isAudioFileType(mFileType);
                        boolean isvideo = MediaFile.isVideoFileType(mFileType);
                        boolean isimage = MediaFile.isImageFileType(mFileType);

                        /**
                         * M: TODO google use new method to generate path, we need test it later.
                         * because fork process can access external storage
                         * path since ALPS00453547.@{
                         */
                        if (isaudio || isvideo || isimage) {
                            path = Environment.maybeTranslateEmulatedPathToInternal(new File(path))
                                    .getAbsolutePath();
                        }

                        // we only extract metadata for audio and video files
                        if (isaudio || isvideo) {
                            processFile(path, mimeType, this);
                        }

                        if (isimage) {
                            processImageFile(path);
                        }

                        result = endFile(entry, ringtones, notifications, alarms, music, podcasts);
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in MediaScanner.scanFile()", e);
            }
//            long t2 = System.currentTimeMillis();
//            Log.v(TAG, "scanFile: " + path + " took " + (t2-t1));
            return result;
        }

        private int parseSubstring(String s, int start, int defaultValue) {
            int length = s.length();
            if (start == length) return defaultValue;

            char ch = s.charAt(start++);
            // return defaultValue if we have no integer at all
            if (ch < '0' || ch > '9') return defaultValue;

            int result = ch - '0';
            while (start < length) {
                ch = s.charAt(start++);
                if (ch < '0' || ch > '9') return result;
                result = result * 10 + (ch - '0');
            }

            return result;
        }

        public void handleStringTag(String name, String value) {
            if (DEBUG) {
                Log.v(TAG, "handleStringTag: name=" + name + ",value=" + value);
            }
            if (name.equalsIgnoreCase("title") || name.startsWith("title;")) {
                // Don't trim() here, to preserve the special \001 character
                // used to force sorting. The media provider will trim() before
                // inserting the title in to the database.
                mTitle = value;
            } else if (name.equalsIgnoreCase("artist") || name.startsWith("artist;")) {
                mArtist = value.trim();
            } else if (name.equalsIgnoreCase("albumartist") || name.startsWith("albumartist;")
                    || name.equalsIgnoreCase("band") || name.startsWith("band;")) {
                mAlbumArtist = value.trim();
            } else if (name.equalsIgnoreCase("album") || name.startsWith("album;")) {
                mAlbum = value.trim();
            } else if (name.equalsIgnoreCase("composer") || name.startsWith("composer;")) {
                mComposer = value.trim();
            } else if (mProcessGenres &&
                    (name.equalsIgnoreCase("genre") || name.startsWith("genre;"))) {
                mGenre = getGenreName(value);
            } else if (name.equalsIgnoreCase("year") || name.startsWith("year;")) {
                mYear = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("tracknumber") || name.startsWith("tracknumber;")) {
                // track number might be of the form "2/12"
                // we just read the number before the slash
                int num = parseSubstring(value, 0, 0);
                mTrack = (mTrack / 1000) * 1000 + num;
            } else if (name.equalsIgnoreCase("discnumber") ||
                    name.equals("set") || name.startsWith("set;")) {
                // set number might be of the form "1/3"
                // we just read the number before the slash
                int num = parseSubstring(value, 0, 0);
                mTrack = (num * 1000) + (mTrack % 1000);
            } else if (name.equalsIgnoreCase("duration")) {
                mDuration = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("writer") || name.startsWith("writer;")) {
                mWriter = value.trim();
            } else if (name.equalsIgnoreCase("compilation")) {
                mCompilation = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("isdrm")) {
                mIsDrm = (parseSubstring(value, 0, 0) == 1);
            } else if (name.equalsIgnoreCase("width")) {
                mWidth = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("height")) {
                mHeight = parseSubstring(value, 0, 0);
            /// M: add for MTK added feature. {@
            /// 1. DRM
            } else if (name.equalsIgnoreCase("drm_content_uri")) {
                mDrmContentUr = value.trim();
            } else if (name.equalsIgnoreCase("drm_offset")) {
                mDrmOffset = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("drm_dataLen")) {
                mDrmDataLen = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("drm_rights_issuer")) {
                mDrmRightsIssuer = value.trim();
            } else if (name.equalsIgnoreCase("drm_content_name")) {
                mDrmContentName = value.trim();
            } else if (name.equalsIgnoreCase("drm_content_description")) {
                mDrmContentDescriptioin = value.trim();
            } else if (name.equalsIgnoreCase("drm_content_vendor")) {
                mDrmContentVendor = value.trim();
            } else if (name.equalsIgnoreCase("drm_icon_uri")) {
                mDrmIconUri = value.trim();
            } else if (name.equalsIgnoreCase("drm_method")) {
                mDrmMethod = parseSubstring(value, 0, 0);
            /// 2. slow motion
            } else if (name.equalsIgnoreCase("SlowMotion_Speed_Value")) {
                mSlowMotionSpeed = "(0,0)x" + value;
            /// 3. fancy gallery homepage
            } else if (name.equalsIgnoreCase("rotation")) {
                mOrientation = parseSubstring(value, 0, 0);
            } else {
                //Log.v(TAG, "unknown tag: " + name + " (" + mProcessGenres + ")");
            }
            /// @}
        }

        private boolean convertGenreCode(String input, String expected) {
            String output = getGenreName(input);
            if (output.equals(expected)) {
                return true;
            } else {
                if (DEBUG) {
                    Log.d(TAG, "'" + input + "' -> '" + output
                        + "', expected '" + expected + "'");
                }
                return false;
            }
        }

        private void testGenreNameConverter() {
            convertGenreCode("2", "Country");
            convertGenreCode("(2)", "Country");
            convertGenreCode("(2", "(2");
            convertGenreCode("2 Foo", "Country");
            convertGenreCode("(2) Foo", "Country");
            convertGenreCode("(2 Foo", "(2 Foo");
            convertGenreCode("2Foo", "2Foo");
            convertGenreCode("(2)Foo", "Country");
            convertGenreCode("200 Foo", "Foo");
            convertGenreCode("(200) Foo", "Foo");
            convertGenreCode("200Foo", "200Foo");
            convertGenreCode("(200)Foo", "Foo");
            convertGenreCode("200)Foo", "200)Foo");
            convertGenreCode("200) Foo", "200) Foo");
        }

        public String getGenreName(String genreTagValue) {

            if (genreTagValue == null) {
                Log.e(TAG, "getGenreName: Null genreTag!");
                return null;
            }
            final int length = genreTagValue.length();

            if (length > 0) {
                boolean parenthesized = false;
                StringBuffer number = new StringBuffer();
                int i = 0;
                for (; i < length; ++i) {
                    char c = genreTagValue.charAt(i);
                    if (i == 0 && c == '(') {
                        parenthesized = true;
                    } else if (Character.isDigit(c)) {
                        number.append(c);
                    } else {
                        break;
                    }
                }
                char charAfterNumber = i < length ? genreTagValue.charAt(i) : ' ';
                if ((parenthesized && charAfterNumber == ')')
                        || !parenthesized && Character.isWhitespace(charAfterNumber)) {
                    try {
                        short genreIndex = Short.parseShort(number.toString());
                        if (genreIndex >= 0) {
                            if (genreIndex < ID3_GENRES.length && ID3_GENRES[genreIndex] != null) {
                                return ID3_GENRES[genreIndex];
                            } else if (genreIndex == 0xFF) {
                                Log.e(TAG, "getGenreName: genreIndex = 0xFF!");
                                return null;
                            } else if (genreIndex < 0xFF && (i + 1) < length) {
                                // genre is valid but unknown,
                                // if there is a string after the value we take it
                                if (parenthesized && charAfterNumber == ')') {
                                    i++;
                                }
                                String ret = genreTagValue.substring(i).trim();
                                if (ret.length() != 0) {
                                    return ret;
                                }
                            } else {
                                // else return the number, without parentheses
                                return number.toString();
                            }
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "getGenreName: invalidNum=" + number.toString(), e);
                    }
                }
            }

            return genreTagValue;
        }

        private void processImageFile(String path) {
            /// M: If decode bmp and gif over limit size, just return.
            long limitFileSize = Long.MAX_VALUE;
            if (MediaFile.FILE_TYPE_BMP == mFileType) {
                limitFileSize = mLimitBmpFileSize;
            } else if (MediaFile.FILE_TYPE_GIF == mFileType) {
                limitFileSize = mLimitGifFileSize;
            }
            if (mFileSize > limitFileSize) {
                if (DEBUG) {
                    Log.w(TAG, "processImageFile " + path + " over limit size " + limitFileSize);
                }
                return;
            }
            /// @}
            try {
                mBitmapOptions.outWidth = 0;
                mBitmapOptions.outHeight = 0;
                BitmapFactory.decodeFile(path, mBitmapOptions);
                mWidth = mBitmapOptions.outWidth;
                mHeight = mBitmapOptions.outHeight;
            } catch (Throwable th) {
                Log.e(TAG, "processImageFile: path=" + path, th);
            }
            if (DEBUG) {
                Log.v(TAG, "processImageFile: path = " + path
                    + ", width = " + mWidth + ", height = "
                    + mHeight + ", limitFileSize = " + limitFileSize);
            }
        }

        public void setMimeType(String mimeType) {
            if ("audio/mp4".equals(mMimeType) &&
                    mimeType.startsWith("video")) {
                // for feature parity with Donut, we force m4a files to keep the
                // audio/mp4 mimetype, even if they are really "enhanced podcasts"
                // with a video track
                return;
            }
            mMimeType = mimeType;
            mFileType = MediaFile.getFileTypeForMimeType(mimeType);
            if (DEBUG) {
                Log.v(TAG, "setMimeType: mMimeType = " + mMimeType);
            }
        }

        /**
         * Formats the data into a values array suitable for use with the Media
         * Content Provider.
         *
         * @return a map of values
         */
        private ContentValues toValues() {
            ContentValues map = new ContentValues();

            map.put(MediaStore.MediaColumns.DATA, mPath);
            map.put(MediaStore.MediaColumns.TITLE, mTitle);
            map.put(MediaStore.MediaColumns.DATE_MODIFIED, mLastModified);
            map.put(MediaStore.MediaColumns.SIZE, mFileSize);
            map.put(MediaStore.MediaColumns.MIME_TYPE, mMimeType);
            map.put(MediaStore.MediaColumns.IS_DRM, mIsDrm);

            String resolution = null;
            if (mWidth > 0 && mHeight > 0) {
                map.put(MediaStore.MediaColumns.WIDTH, mWidth);
                map.put(MediaStore.MediaColumns.HEIGHT, mHeight);
                resolution = mWidth + "x" + mHeight;
            }

            if (!mNoMedia) {
                if (MediaFile.isVideoFileType(mFileType)) {
                    map.put(Video.Media.ARTIST, (mArtist != null && mArtist.length() > 0
                            ? mArtist : MediaStore.UNKNOWN_STRING));
                    map.put(Video.Media.ALBUM, (mAlbum != null && mAlbum.length() > 0
                            ? mAlbum : MediaStore.UNKNOWN_STRING));
                    map.put(Video.Media.DURATION, mDuration);
                    if (resolution != null) {
                        map.put(Video.Media.RESOLUTION, resolution);
                    }
                    /// M: Add for slow motion
                    map.put(Video.Media.SLOW_MOTION_SPEED, mSlowMotionSpeed);
                    /// M: Add for fancy gallery homepage
                    map.put(Video.Media.ORIENTATION, mOrientation);
                } else if (MediaFile.isImageFileType(mFileType)) {
                    // FIXME - add DESCRIPTION
                } else if (MediaFile.isAudioFileType(mFileType)) {
                    map.put(Audio.Media.ARTIST, (mArtist != null && mArtist.length() > 0) ?
                            mArtist : MediaStore.UNKNOWN_STRING);
                    map.put(Audio.Media.ALBUM_ARTIST, (mAlbumArtist != null &&
                            mAlbumArtist.length() > 0) ? mAlbumArtist : null);
                    map.put(Audio.Media.ALBUM, (mAlbum != null && mAlbum.length() > 0) ?
                            mAlbum : MediaStore.UNKNOWN_STRING);
                    map.put(Audio.Media.COMPOSER, mComposer);
                    map.put(Audio.Media.GENRE, mGenre);
                    if (mYear != 0) {
                        map.put(Audio.Media.YEAR, mYear);
                    }
                    map.put(Audio.Media.TRACK, mTrack);
                    map.put(Audio.Media.DURATION, mDuration);
                    map.put(Audio.Media.COMPILATION, mCompilation);
                }
            }

            /// M: drm media file, add new column values.
            if (mIsDrm) {
                map.put(MediaStore.MediaColumns.DRM_CONTENT_DESCRIPTION, mDrmContentDescriptioin);
                map.put(MediaStore.MediaColumns.DRM_CONTENT_NAME, mDrmContentName);
                map.put(MediaStore.MediaColumns.DRM_CONTENT_URI, mDrmContentUr);
                map.put(MediaStore.MediaColumns.DRM_CONTENT_VENDOR, mDrmContentVendor);
                map.put(MediaStore.MediaColumns.DRM_DATA_LEN, mDrmDataLen);
                map.put(MediaStore.MediaColumns.DRM_ICON_URI, mDrmIconUri);
                map.put(MediaStore.MediaColumns.DRM_OFFSET, mDrmOffset);
                map.put(MediaStore.MediaColumns.DRM_RIGHTS_ISSUER, mDrmRightsIssuer);
                map.put(MediaStore.MediaColumns.DRM_METHOD, mDrmMethod);
            }
            /// @}
            return map;
        }

        private Uri endFile(FileEntry entry, boolean ringtones, boolean notifications,
                boolean alarms, boolean music, boolean podcasts)
                throws RemoteException {
            // update database

            // use album artist if artist is missing
            if (mArtist == null || mArtist.length() == 0) {
                mArtist = mAlbumArtist;
            }

            ContentValues values = toValues();
            String title = values.getAsString(MediaStore.MediaColumns.TITLE);
            if (title == null || TextUtils.isEmpty(title.trim())) {
                title = MediaFile.getFileTitle(values.getAsString(MediaStore.MediaColumns.DATA));
                values.put(MediaStore.MediaColumns.TITLE, title);
            }
            String album = values.getAsString(Audio.Media.ALBUM);
            if (MediaStore.UNKNOWN_STRING.equals(album)) {
                album = values.getAsString(MediaStore.MediaColumns.DATA);
                // extract last path segment before file name
                int lastSlash = album.lastIndexOf('/');
                if (lastSlash >= 0) {
                    int previousSlash = 0;
                    while (true) {
                        int idx = album.indexOf('/', previousSlash + 1);
                        if (idx < 0 || idx >= lastSlash) {
                            break;
                        }
                        previousSlash = idx;
                    }
                    if (previousSlash != 0) {
                        album = album.substring(previousSlash + 1, lastSlash);
                        values.put(Audio.Media.ALBUM, album);
                    }
                }
            }
            long rowId = entry.mRowId;
            if (DEBUG) {
                Log.d(TAG, "endFile() mFileType = " + mFileType);
            }
            if (MediaFile.isAudioFileType(mFileType) && (rowId == 0 || mMtpObjectHandle != 0)) {
                // Only set these for new entries. For existing entries, they
                // may have been modified later, and we want to keep the current
                // values so that custom ringtones still show up in the ringtone
                // picker.
                values.put(Audio.Media.IS_RINGTONE, ringtones);
                values.put(Audio.Media.IS_NOTIFICATION, notifications);
                values.put(Audio.Media.IS_ALARM, alarms);
                values.put(Audio.Media.IS_MUSIC, music);
                values.put(Audio.Media.IS_PODCAST, podcasts);
            /// M: MAV type MPO file need parse some info from exif
            } else if ((mFileType == MediaFile.FILE_TYPE_JPEG || mFileType == MediaFile.FILE_TYPE_MPO) && !mNoMedia) {
                ExifInterface exif = null;
                try {
                    exif = new ExifInterface(entry.mPath);
                } catch (IOException ex) {
                    // exif is null
                    Log.e(TAG, "endFile: Null ExifInterface!", ex);
                }
                if (exif != null) {
                    float[] latlng = new float[2];
                    if (exif.getLatLong(latlng)) {
                        values.put(Images.Media.LATITUDE, latlng[0]);
                        values.put(Images.Media.LONGITUDE, latlng[1]);
                    }

                    long time = exif.getGpsDateTime();
                    if (time != -1) {
                        values.put(Images.Media.DATE_TAKEN, time);
                    } else {
                        // If no time zone information is available, we should consider using
                        // EXIF local time as taken time if the difference between file time
                        // and EXIF local time is not less than 1 Day, otherwise MediaProvider
                        // will use file time as taken time.
                        time = exif.getDateTime();
                        if (time != -1 && Math.abs(mLastModified * 1000 - time) >= 86400000) {
                            values.put(Images.Media.DATE_TAKEN, time);
                        }
                    }

                    int orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, -1);
                    if (orientation != -1) {
                        // We only recognize a subset of orientation tag values.
                        int degree;
                        switch(orientation) {
                            case ExifInterface.ORIENTATION_ROTATE_90:
                                degree = 90;
                                break;
                            case ExifInterface.ORIENTATION_ROTATE_180:
                                degree = 180;
                                break;
                            case ExifInterface.ORIENTATION_ROTATE_270:
                                degree = 270;
                                break;
                            default:
                                degree = 0;
                                break;
                        }
                        values.put(Images.Media.ORIENTATION, degree);
                    }

                    /// M: Gets groupId and groupIndex for continuous shots images,
                    /// gets focus value for best shots images. @{
                    long groupId = 0L;
                    String groupIdStr = exif.getAttribute(ExifInterface.TAG_MTK_CONSHOT_GROUP_ID);
                    if (groupIdStr != null) {
                        try {
                            groupId = Long.parseLong(groupIdStr);
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "endFile: " + groupIdStr + " cannot be converted to long.");
                        }
                    }
                    int groupIndex = exif.getAttributeInt(ExifInterface.TAG_MTK_CONSHOT_PIC_INDEX, 0);
                    long focusHigh = exif.getAttributeInt(ExifInterface.TAG_MTK_CONSHOT_FOCUS_HIGH, 0);
                    long focusLow = exif.getAttributeInt(ExifInterface.TAG_MTK_CONSHOT_FOCUS_LOW, 0);
                    values.put(Images.Media.FOCUS_VALUE_HIGH, focusHigh);
                    values.put(Images.Media.FOCUS_VALUE_LOW, focusLow);
                    values.put(Images.Media.GROUP_ID, groupId);
                    values.put(Images.Media.GROUP_INDEX, groupIndex);
                    /// Add recocus for camera(2014/08/20)
                    int refocus = isStereoPhoto(entry.mPath) ? 1 : 0;
                    values.put(Images.Media.CAMERA_REFOCUS, refocus);
                    /// @}
                }
            }
            Uri tableUri = mFilesUri;
            MediaInserter inserter = mMediaInserter;
            if (!mNoMedia) {
                if (MediaFile.isVideoFileType(mFileType)) {
                    tableUri = mVideoUri;
                } else if (MediaFile.isImageFileType(mFileType)) {
                    tableUri = mImagesUri;
                } else if (MediaFile.isAudioFileType(mFileType)) {
                    tableUri = mAudioUri;
                }
            }
            Uri result = null;
            boolean needToSetSettings = false;
            if (rowId == 0) {
                if (mMtpObjectHandle != 0) {
                    values.put(MediaStore.MediaColumns.MEDIA_SCANNER_NEW_OBJECT_ID, mMtpObjectHandle);
                }
                if (tableUri == mFilesUri) {
                    int format = entry.mFormat;
                    if (format == 0) {
                        format = MediaFile.getFormatCode(entry.mPath, mMimeType);
                    }
                    values.put(Files.FileColumns.FORMAT, format);
                }
                // Setting a flag in order not to use bulk insert for the file related with
                // notifications, ringtones, and alarms, because the rowId of the inserted file is
                // needed.

                /// M: Set notification, ringtone and alarm if it wasn't set before. @{
                if (notifications && ((mWasEmptyPriorToScan && !mDefaultNotificationSet) ||
                        doesSettingEmpty(NOTIFICATION_SET))) {
                    if (TextUtils.isEmpty(mDefaultNotificationFilename) ||
                            doesPathHaveFilename(entry.mPath, mDefaultNotificationFilename)) {
                        needToSetSettings = true;
                        /// M: Adds log to debug setting ringtones.
                        if (DEBUG) {
                            Log.v(TAG, "endFile: needToSetNotification=true.");
                        }
                    }
                } else if (ringtones && ((mWasEmptyPriorToScan && !mDefaultRingtoneSet) ||
                        doesSettingEmpty(RINGTONE_SET))) {
                    if (TextUtils.isEmpty(mDefaultRingtoneFilename) ||
                            doesPathHaveFilename(entry.mPath, mDefaultRingtoneFilename)) {
                        needToSetSettings = true;
                        /// M: Adds log to debug setting ringtones.
                        if (DEBUG) {
                            Log.v(TAG, "endFile: needToSetRingtone=true.");
                        }
                    }
                } else if (alarms && ((mWasEmptyPriorToScan && !mDefaultAlarmSet) ||
                        doesSettingEmpty(ALARM_SET))) {
                    if (TextUtils.isEmpty(mDefaultAlarmAlertFilename) ||
                            doesPathHaveFilename(entry.mPath, mDefaultAlarmAlertFilename)) {
                        needToSetSettings = true;
                        /// M: Adds log to debug setting ringtones.
                        if (DEBUG) {
                            Log.v(TAG, "endFile: needToSetAlarm=true.");
                        }
                    }
                }
                /// @}

                // New file, insert it.
                // Directories need to be inserted before the files they contain, so they
                // get priority when bulk inserting.
                // If the rowId of the inserted file is needed, it gets inserted immediately,
                // bypassing the bulk inserter.
                if (inserter == null || needToSetSettings) {
                    if (inserter != null) {
                        inserter.flushAll();
                    }
                    result = mMediaProvider.insert(tableUri, values);
                } else if (entry.mFormat == MtpConstants.FORMAT_ASSOCIATION) {
                    inserter.insertwithPriority(tableUri, values);
                } else {
                    inserter.insert(tableUri, values);
                }

                if (result != null) {
                    rowId = ContentUris.parseId(result);
                    entry.mRowId = rowId;
                }
            } else {
                // updated file
                result = ContentUris.withAppendedId(tableUri, rowId);
                // path should never change, and we want to avoid replacing mixed cased paths
                // with squashed lower case paths
                values.remove(MediaStore.MediaColumns.DATA);

                int mediaType = 0;
                if (!MediaScanner.isNoMediaPath(entry.mPath)) {
                    int fileType = MediaFile.getFileTypeForMimeType(mMimeType);
                    if (MediaFile.isAudioFileType(fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_AUDIO;
                    } else if (MediaFile.isVideoFileType(fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_VIDEO;
                    } else if (MediaFile.isImageFileType(fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_IMAGE;
                    } else if (MediaFile.isPlayListFileType(fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_PLAYLIST;
                    }
                    values.put(FileColumns.MEDIA_TYPE, mediaType);
                }

                mMediaProvider.update(result, values, null, null);
            }
            ///M: Set ringtone if it wasn't set before. @{
            if(needToSetSettings) {
                if (notifications && doesSettingEmpty(NOTIFICATION_SET)) {
                    setSettingIfNotSet(Settings.System.NOTIFICATION_SOUND, tableUri, rowId);
                    mDefaultNotificationSet = true;
                    setSettingFlag(NOTIFICATION_SET);
                    if (DEBUG) {
                        Log.v(TAG, "endFile: set notification. uri="
                            + tableUri + ", rowId=" + rowId);
                    }
                } else if (ringtones && doesSettingEmpty(RINGTONE_SET)) {
                    setSettingIfNotSet(Settings.System.RINGTONE, tableUri, rowId);
                    mDefaultRingtoneSet = true;
                    setSettingFlag(RINGTONE_SET);
                    if (DEBUG) {
                        Log.v(TAG, "endFile: set ringtone. uri="
                            + tableUri + ", rowId=" + rowId);
                    }
                } else if (alarms && doesSettingEmpty(ALARM_SET)) {
                    setSettingIfNotSet(Settings.System.ALARM_ALERT, tableUri, rowId);
                    mDefaultAlarmSet = true;
                    setSettingFlag(ALARM_SET);
                }
            }
            /// @}
            return result;
        }

        private boolean doesPathHaveFilename(String path, String filename) {
            int pathFilenameStart = path.lastIndexOf(File.separatorChar) + 1;
            int filenameLength = filename.length();
            return path.regionMatches(pathFilenameStart, filename, 0, filenameLength) &&
                    pathFilenameStart + filenameLength == path.length();
        }

        /// M: Modify for ringtone list is empty @{
        private boolean doesSettingEmpty(String settingName) {
            String existingSettingValue = Settings.System.getString(mContext.getContentResolver(), settingName);
            if (TextUtils.isEmpty(existingSettingValue)) {
                return true;
            }
            return false;
        }

        private void setSettingFlag(String settingName) {
            final String VALUE = "yes";
            if (DEBUG) {
                Log.d(TAG, "setSettingFlag set:" + settingName);
            }
            Settings.System.putString(mContext.getContentResolver(), settingName, VALUE);
        }
        /// @}

        private void setSettingIfNotSet(String settingName, Uri uri, long rowId) {
            ContentResolver cr = mContext.getContentResolver();
            String existingSettingValue = Settings.System.getString(cr, settingName);
            if (TextUtils.isEmpty(existingSettingValue)) {
                final Uri settingUri = Settings.System.getUriFor(settingName);
                final Uri ringtoneUri = ContentUris.withAppendedId(uri, rowId);
                RingtoneManager.setActualDefaultRingtoneUri(mContext,
                        RingtoneManager.getDefaultType(settingUri), ringtoneUri);
                /// M: Adds log to debug setting ringtones.
                if (DEBUG) {
                    Log.v(TAG, "setSettingIfNotSet: name="
                        + settingName + ",value=" + rowId);
                }
            } else {
                Log.e(TAG, "setSettingIfNotSet: name=" + settingName
                        + " with value=" + existingSettingValue);
            }
        }

        private int getFileTypeFromDrm(String path) {
            if (!isDrmEnabled()) {
                return 0;
            }

            int resultFileType = 0;

            if (mDrmManagerClient == null) {
                mDrmManagerClient = new DrmManagerClient(mContext);
            }

            if (mDrmManagerClient.canHandle(path, null)) {
                mIsDrm = true;
                String drmMimetype = mDrmManagerClient.getOriginalMimeType(path);
                if (drmMimetype != null) {
                    mMimeType = drmMimetype;
                    resultFileType = MediaFile.getFileTypeForMimeType(drmMimetype);
                }
            }
            return resultFileType;
        }

    }; // end of anonymous MediaScannerClient instance

    private String settingSetIndicatorName(String base) {
        return base + "_set";
    }

    private boolean wasRingtoneAlreadySet(String name) {
        ContentResolver cr = mContext.getContentResolver();
        String indicatorName = settingSetIndicatorName(name);
        try {
            return Settings.System.getInt(cr, indicatorName) != 0;
        } catch (SettingNotFoundException e) {
            return false;
        }
    }

    private void prescan(String filePath, boolean prescanFiles) throws RemoteException {
        /// M: Adds log for debug.
        if (DEBUG) {
            Log.v(TAG, "prescan>>> filePath=" + filePath
                + ",prescanFiles=" + prescanFiles);
        }
        Cursor c = null;
        String where = null;
        String[] selectionArgs = null;

        mPlayLists.clear();

        if (filePath != null) {
            // query for only one file
            where = MediaStore.Files.FileColumns._ID + ">?" +
                " AND " + Files.FileColumns.DATA + "=?";
            selectionArgs = new String[] { "", filePath };
        } else {
            where = MediaStore.Files.FileColumns._ID + ">?";
            selectionArgs = new String[] { "" };
        }

        mDefaultRingtoneSet = wasRingtoneAlreadySet(Settings.System.RINGTONE);
        mDefaultNotificationSet = wasRingtoneAlreadySet(Settings.System.NOTIFICATION_SOUND);
        mDefaultAlarmSet = wasRingtoneAlreadySet(Settings.System.ALARM_ALERT);

        // Tell the provider to not delete the file.
        // If the file is truly gone the delete is unnecessary, and we want to avoid
        // accidentally deleting files that are really there (this may happen if the
        // filesystem is mounted and unmounted while the scanner is running).
        Uri.Builder builder = mFilesUri.buildUpon();
        builder.appendQueryParameter(MediaStore.PARAM_DELETE_DATA, "false");
        MediaBulkDeleter deleter = new MediaBulkDeleter(mMediaProvider, builder.build());

        /// M: Add to debug how many file scanned
        int audioCount = 0;
        long lastId = Long.MIN_VALUE;

        // Build the list of files from the content provider
        try {
            if (prescanFiles) {
                // First read existing files from the files table.
                // Because we'll be deleting entries for missing files as we go,
                // we need to query the database in small batches, to avoid problems
                // with CursorWindow positioning.
                ///M: Added 'force' parameter for forcing query external SD card related db items
                // while prescan under processing.
                Uri limitUri = mFilesUri.buildUpon()
                              .appendQueryParameter("limit", "1000")
                              .appendQueryParameter("force", "1")
                              .build();
                mWasEmptyPriorToScan = true;

                while (true) {
                    selectionArgs[0] = "" + lastId;
                    if (c != null) {
                        c.close();
                        c = null;
                    }
                    c = mMediaProvider.query(limitUri, FILES_PRESCAN_PROJECTION,
                            where, selectionArgs, MediaStore.Files.FileColumns._ID, null);
                    if (c == null) {
                        break;
                    }

                    int num = c.getCount();

                    if (num == 0) {
                        break;
                    }
                    mWasEmptyPriorToScan = false;
                    /**M:Added for resolving  issue that caused by storage path changed on M.@{**/
                    String externalPrimaryStoragePathOnM = null;
                    String externalSecondaryStoragePathOnM = null;
                    final boolean isSharedSdCardEanbled =
                      SystemProperties.getBoolean("ro.mtk_shared_sdcard", false);
                    StorageManager storageManager =
                      (StorageManager)mContext.getSystemService(Context.STORAGE_SERVICE);

                    for (VolumeInfo vol : storageManager.getVolumes()) {
                        if (VolumeInfo.ID_PRIVATE_INTERNAL.equals(vol.id)) continue;
                        if (isSharedSdCardEanbled) {
                            if (vol.isPrimary()) {
                               externalPrimaryStoragePathOnM = vol.getPath().getPath() + "/";
                            }
                            else if (vol.getDisk() != null  && vol.getDisk().isSd()) {
                               externalSecondaryStoragePathOnM = vol.getPath().getPath() + "/";
                            }
                        }
                        else {
                            if (vol.isPhoneStorage()) {
                               externalPrimaryStoragePathOnM = vol.getPath().getPath() + "/";
                            }
                            else if (vol.getDisk() != null  && vol.getDisk().isSd()) {
                               externalSecondaryStoragePathOnM = vol.getPath().getPath() + "/";
                            }
                        }
                    }

                    if (externalPrimaryStoragePathOnM == null) {
                       if(externalSecondaryStoragePathOnM != null) {
                           externalPrimaryStoragePathOnM = externalSecondaryStoragePathOnM;
                           externalSecondaryStoragePathOnM = null;
                       }
                    } else { // not null
                       if (externalPrimaryStoragePathOnM.startsWith("/storage/emulated/")) {
                           externalPrimaryStoragePathOnM = externalPrimaryStoragePathOnM
                                               + UserHandle.myUserId() + "/";
                       }
                    }
                    if (DEBUG) {
                        Log.v(TAG, "prescan>>> externalPrimaryStoragePathOnM="
                          + externalPrimaryStoragePathOnM
                          + ", externalSecondaryStoragePathOnM="
                          + externalSecondaryStoragePathOnM
                          + ", uid = " + UserHandle.myUserId());
                    }
                    /**@}**/

                    while (c.moveToNext()) {
                        long rowId = c.getLong(FILES_PRESCAN_ID_COLUMN_INDEX);
                        String path = c.getString(FILES_PRESCAN_PATH_COLUMN_INDEX);
                        int format = c.getInt(FILES_PRESCAN_FORMAT_COLUMN_INDEX);
                        long lastModified = c.getLong(FILES_PRESCAN_DATE_MODIFIED_COLUMN_INDEX);
                        lastId = rowId;

                        // Only consider entries with absolute path names.
                        // This allows storing URIs in the database without the
                        // media scanner removing them.
                        if (path != null && path.startsWith("/")) {
                            boolean exists = false;
                            try {
                               /**
                                *M:Added for resolving  issue that caused by storage path
                                *  changed on M.@{
                                **/
                                String newPath = null;
                                if (path.startsWith("/storage/sdcard")) {
                                    if(path.startsWith(EXTERNAL_PRIMARY_STORAGE_PATH_L)
                                              && externalPrimaryStoragePathOnM != null) {
                                        newPath = path.replace(EXTERNAL_PRIMARY_STORAGE_PATH_L,
                                                externalPrimaryStoragePathOnM);
                                    } else if(path.startsWith(EXTERNAL_SECONDARY_STORAGE_PATH_L)
                                              && externalSecondaryStoragePathOnM != null) {
                                        newPath = path.replace(EXTERNAL_SECONDARY_STORAGE_PATH_L,
                                                externalSecondaryStoragePathOnM);
                                    } else {
                                        newPath = null;
                                    }
                                    if(newPath != null) {
                                        if (DEBUG) {
                                            Log.v(TAG, "try to check if newPath exists, "+ newPath);
                                        }
                                        if(Os.access(newPath, android.system.OsConstants.F_OK)) {
                                            if (DEBUG) {
                                                Log.v(TAG, "update>>> path=" + path
                                                  + ", newPath=" + newPath);
                                            }
                                            exists = true;
                                            Uri realUri = ContentUris.withAppendedId(mFilesUri,
                                                                                     rowId);
                                            ContentValues values = new ContentValues();
                                            values.put(MediaStore.MediaColumns.DATA, newPath);
                                            mMediaProvider.update(realUri, values,null, null);
                                        }
                                    }
                                }

                                if (newPath == null) {
                                exists = Os.access(path, android.system.OsConstants.F_OK);
                                }
                                /**@}**/
                            } catch (ErrnoException e1) {
                                /// M: Adds log for debug.
                                if (DEBUG) {
                                    Log.e(TAG, "prescan: ErrnoException! path=" + path);
                                }
                            }
                            if (!exists && !MtpConstants.isAbstractObject(format)) {
                                // do not delete missing playlists, since they may have been
                                // modified by the user.
                                // The user can delete them in the media player instead.
                                // instead, clear the path and lastModified fields in the row
                                MediaFile.MediaFileType mediaFileType = MediaFile.getFileType(path);
                                int fileType = (mediaFileType == null ? 0 : mediaFileType.fileType);

                                if (!MediaFile.isPlayListFileType(fileType)) {
                                    deleter.delete(rowId);
                                    if (path.toLowerCase(Locale.US).endsWith("/.nomedia")) {
                                        deleter.flush();
                                        String parent = new File(path).getParent();
                                        mMediaProvider.call(MediaStore.UNHIDE_CALL, parent, null);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        finally {
            if (c != null) {
                c.close();
            }
            deleter.flush();
        }

        try {
            // compute original size of images
            mOriginalCount = 0;
           /**
            *M: Added 'force' parameter for forcing query external SD card
            *    related db items while prescan under processing.@{
            **/
            c = mMediaProvider
                .query(mImagesUri.buildUpon().appendQueryParameter("force", "1").build(),
                      ID_PROJECTION, null, null, null, null);
            /**@}**/
            if (c != null) {
                mOriginalCount = c.getCount();
                c.close();
                /// M: Make sure cursor not to be closed again in finally block.
                c = null;
            }
            mOriginalVideoCount = 0;
           /**
            *M: Added 'force' parameter for forcing query external SD card
            *    related db items while prescan under processing.@{
            **/
            c = mMediaProvider
                .query(mVideoUri.buildUpon().appendQueryParameter("force", "1").build(),
                       ID_PROJECTION, null, null, null, null);
            /**@}**/
            if (c != null) {
                mOriginalVideoCount = c.getCount();
                c.close();
                /// M: Make sure cursor not to be closed again in finally block.
                c = null;
            }
            /// M: log audio count in device
           /**
            *M: Added 'force' parameter for forcing query external SD card
            *    related db items while prescan under processing.@{
            **/
            c = mMediaProvider
                .query(mAudioUri.buildUpon().appendQueryParameter("force", "1").build(),
                       ID_PROJECTION, null, null, null, null);
            /**@}**/
            if (c != null) {
                audioCount = c.getCount();
                c.close();
                /// M: Make sure cursor not to be closed again in finally block.
                c = null;
            }
        /// M: Make sure cursor to be closed. @{
        } finally {
            if (null != c) {
                c.close();
            }
        /// @}
        }

        /// M: Adds log to debug setting ringtones and how many file scanned.
        if (DEBUG) {
            Log.v(TAG, "prescan<<< imageCount=" + mOriginalCount
                + ",videoCount=" + mOriginalVideoCount
                + ", audioCount=" + audioCount
                + ", lastId=" + lastId + ",isEmpty=" + mWasEmptyPriorToScan);
        }
    }

    private boolean inScanDirectory(String path, String[] directories) {
        for (int i = 0; i < directories.length; i++) {
            String directory = directories[i];
            if (path.startsWith(directory)) {
                return true;
            }
        }
        return false;
    }

    private void pruneDeadThumbnailFiles() {
        if (DEBUG) {
            Log.v(TAG, "pruneDeadThumbnailFiles>>>");
        }
        HashSet<String> existingFiles = new HashSet<String>();
        String directory = Environment.getExternalStorageDirectory().getPath() + "/"
                + MiniThumbFile.getMiniThumbFileDirectoryPath();
        String [] files = (new File(directory)).list();
        if (files == null) {
            files = new String[0];
        }
        for (int i = 0; i < files.length; i++) {
            String fullPathString = directory + "/" + files[i];
            existingFiles.add(fullPathString);
        }

        int imageThumbCount = 0;
        int videoThumbCount = 0;
        Cursor c = null;
        try {
            /// M: remove useful image thumbnail files
            c = mMediaProvider.query(
                    mThumbsUri,
                    new String [] { "_data" },
                    null,
                    null,
                    null, null);
            if (c != null && c.moveToFirst()) {
                imageThumbCount = c.getCount();
                do {
                    String fullPathString = c.getString(0);
                    existingFiles.remove(fullPathString);
                } while (c.moveToNext());
            }
            /// M: close image thumb query cursor
            if (c != null) {
                c.close();
                c = null;
            }
            /// M: remove useful video thumbnail files
            c = mMediaProvider.query(
                    mVideoThumbsUri,
                    new String [] { "_data" },
                    null,
                    null,
                    null, null);
            if (c != null && c.moveToFirst()) {
                videoThumbCount = c.getCount();
                do {
                    String fullPathString = c.getString(0);
                    existingFiles.remove(fullPathString);
                } while (c.moveToNext());
            }
            if (c != null) {
                c.close();
                c = null;
            }
            /// M: If exist image or video thumbnail, remove MiniThumbFiles
            if (imageThumbCount > 0 || videoThumbCount > 0) {
                String miniThumbFilePath = directory + "/" + MiniThumbFile.getMiniThumbFilePrefix();
                Iterator<String> iterator = existingFiles.iterator();
                while (iterator.hasNext()) {
                    String path = iterator.next();
                    if (path.startsWith(miniThumbFilePath)) {
                        iterator.remove();
                    }
                }
            }

            for (String fileToDelete : existingFiles) {
                if (DEBUG) {
                    Log.v(TAG, "delete dead thumbnail file " + fileToDelete);
                }
                try {
                    (new File(fileToDelete)).delete();
                } catch (SecurityException ex) {
                    /// M: Adds log for debug.
                    Log.e(TAG, "pruneDeadThumbnailFiles: path=" + fileToDelete, ex);
                }
            }
        } catch (RemoteException e) {
            // We will soon be killed...
            Log.e(TAG, "pruneDeadThumbnailFiles: RemoteException!", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        if (DEBUG) {
            Log.v(TAG, "pruneDeadThumbnailFiles<<< for " + directory);
        }
    }

    static class MediaBulkDeleter {
        StringBuilder whereClause = new StringBuilder();
        ArrayList<String> whereArgs = new ArrayList<String>(100);
        final ContentProviderClient mProvider;
        final Uri mBaseUri;

        public MediaBulkDeleter(ContentProviderClient provider, Uri baseUri) {
            mProvider = provider;
            mBaseUri = baseUri;
        }

        public void delete(long id) throws RemoteException {
            if (whereClause.length() != 0) {
                whereClause.append(",");
            }
            whereClause.append("?");
            whereArgs.add("" + id);
            if (whereArgs.size() > 100) {
                flush();
            }
        }

        public void flush() throws RemoteException {
            int size = whereArgs.size();
            if (size > 0) {
                String [] foo = new String [size];
                foo = whereArgs.toArray(foo);
                int numrows = mProvider.delete(mBaseUri,
                        MediaStore.MediaColumns._ID + " IN (" +
                        whereClause.toString() + ")", foo);
                //Log.i("@@@@@@@@@", "rows deleted: " + numrows);
                whereClause.setLength(0);
                whereArgs.clear();
            }
        }
    }

    private void postscan(final String[] directories) throws RemoteException {

        // handle playlists last, after we know what media files are on the storage.
        if (mProcessPlaylists) {
            processPlayLists();
        }

        if ((mOriginalCount == 0 || mOriginalVideoCount == 0)
                && mImagesUri.equals(Images.Media.getContentUri("external"))) {
            pruneDeadThumbnailFiles();
        }

        // allow GC to clean up
        mPlayLists.clear();
    }

    private void releaseResources() {
        // release the DrmManagerClient resources
        if (mDrmManagerClient != null) {
            mDrmManagerClient.close();
            mDrmManagerClient = null;
        }
    }

    public void scanDirectories(String[] directories) {
        try {
            long start = System.currentTimeMillis();
            prescan(null, true);
            long prescan = System.currentTimeMillis();

            if (ENABLE_BULK_INSERTS) {
                // create MediaInserter for bulk inserts
                mMediaInserter = new MediaInserter(mMediaProvider, 500);
            }

            for (int i = 0; i < directories.length; i++) {
                processDirectory(directories[i], mClient);
            }

            if (ENABLE_BULK_INSERTS) {
                // flush remaining inserts
                mMediaInserter.flushAll();
                mMediaInserter = null;
            }

            long scan = System.currentTimeMillis();
            postscan(directories);
            long end = System.currentTimeMillis();

            if (DEBUG) {
                Log.d(TAG, " prescan time: " + (prescan - start) + "ms\n");
                Log.d(TAG, "    scan time: " + (scan - prescan) + "ms\n");
                Log.d(TAG, "postscan time: " + (end - scan) + "ms\n");
                Log.d(TAG, "   total time: " + (end - start) + "ms\n");
            }
        } catch (SQLException e) {
            // this might happen if the SD card is removed while the media scanner is running
            Log.e(TAG, "SQLException in MediaScanner.scan()", e);
        } catch (UnsupportedOperationException e) {
            // this might happen if the SD card is removed while the media scanner is running
            Log.e(TAG, "UnsupportedOperationException in MediaScanner.scan()", e);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scan()", e);
        } finally {
            releaseResources();
        }
    }

    // this function is used to scan a single file
    public Uri scanSingleFile(String path, String mimeType) {
        try {
            prescan(path, true);

            File file = new File(path);
            if (!file.exists()) {
                Log.e(TAG, "scanSingleFile: Not exist path=" + path);
                return null;
            }

            // lastModified is in milliseconds on Files.
            long lastModifiedSeconds = file.lastModified() / 1000;

            // always scan the file, so we can return the content://media Uri for existing files
            return mClient.doScanFile(path, mimeType, lastModifiedSeconds, file.length(),
                    file.isDirectory(), true, isNoMediaPath(path));
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scanFile()", e);
            return null;
        } finally {
            releaseResources();
        }
    }

    private static boolean isNoMediaFile(String path) {
        File file = new File(path);
        if (file.isDirectory()) return false;

        // special case certain file names
        // I use regionMatches() instead of substring() below
        // to avoid memory allocation
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash + 2 < path.length()) {
            // ignore those ._* files created by MacOS
            if (path.regionMatches(lastSlash + 1, "._", 0, 2)) {
                return true;
            }

            // ignore album art files created by Windows Media Player:
            // Folder.jpg, AlbumArtSmall.jpg, AlbumArt_{...}_Large.jpg
            // and AlbumArt_{...}_Small.jpg
            if (path.regionMatches(true, path.length() - 4, ".jpg", 0, 4)) {
                if (path.regionMatches(true, lastSlash + 1, "AlbumArt_{", 0, 10) ||
                        path.regionMatches(true, lastSlash + 1, "AlbumArt.", 0, 9)) {
                    return true;
                }
                int length = path.length() - lastSlash - 1;
                if ((length == 17 && path.regionMatches(
                        true, lastSlash + 1, "AlbumArtSmall", 0, 13)) ||
                        (length == 10
                         && path.regionMatches(true, lastSlash + 1, "Folder", 0, 6))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static HashMap<String,String> mNoMediaPaths = new HashMap<String,String>();
    private static HashMap<String,String> mMediaPaths = new HashMap<String,String>();

    /* MediaProvider calls this when a .nomedia file is added or removed */
    public static void clearMediaPathCache(boolean clearMediaPaths, boolean clearNoMediaPaths) {
        synchronized (MediaScanner.class) {
            if (clearMediaPaths) {
                mMediaPaths.clear();
            }
            if (clearNoMediaPaths) {
                mNoMediaPaths.clear();
            }
        }
    }

    public static boolean isNoMediaPath(String path) {
        if (path == null) {
            return false;
        }
        // return true if file or any parent directory has name starting with a dot
        if (path.indexOf("/.") >= 0) {
            return true;
        }

        int firstSlash = path.lastIndexOf('/');
        if (firstSlash <= 0) {
            return false;
        }
        String parent = path.substring(0,  firstSlash);

        synchronized (MediaScanner.class) {
            if (mNoMediaPaths.containsKey(parent)) {
                return true;
            } else if (!mMediaPaths.containsKey(parent)) {
                // check to see if any parent directories have a ".nomedia" file
                // start from 1 so we don't bother checking in the root directory
                int offset = 1;
                while (offset >= 0) {
                    int slashIndex = path.indexOf('/', offset);
                    if (slashIndex > offset) {
                        slashIndex++; // move past slash
                        File file = new File(path.substring(0, slashIndex) + ".nomedia");
                        if (file.exists()) {
                            // we have a .nomedia in one of the parent directories
                            mNoMediaPaths.put(parent, "");
                            return true;
                        }
                    }
                    /// M: avoid loop in while@{
                    else if (slashIndex == offset) {
                        slashIndex++; //pass "//" case
                    }
                    /// @}
                    offset = slashIndex;
                }
                mMediaPaths.put(parent, "");
            }
        }

        return isNoMediaFile(path);
    }

    public void scanMtpFile(String path, int objectHandle, int format) {
        MediaFile.MediaFileType mediaFileType = MediaFile.getFileType(path);
        int fileType = (mediaFileType == null ? 0 : mediaFileType.fileType);
        File file = new File(path);
        long lastModifiedSeconds = file.lastModified() / 1000;

        if (!MediaFile.isAudioFileType(fileType) && !MediaFile.isVideoFileType(fileType) &&
            !MediaFile.isImageFileType(fileType) && !MediaFile.isPlayListFileType(fileType) &&
            !MediaFile.isDrmFileType(fileType)) {

            // no need to use the media scanner, but we need to update last modified and file size
            ContentValues values = new ContentValues();
            /// M: ALPS00670132, for WHQL ObjectSize folder case, folder file size must be 0
            values.put(Files.FileColumns.SIZE, format == MtpConstants.FORMAT_ASSOCIATION ? 0 : file.length());

            values.put(Files.FileColumns.DATE_MODIFIED, lastModifiedSeconds);
            try {
                String[] whereArgs = new String[] {  Integer.toString(objectHandle) };
                mMediaProvider.update(Files.getMtpObjectsUri(mVolumeName), values,
                        "_id=?", whereArgs);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in scanMtpFile", e);
            }
            return;
        }

        mMtpObjectHandle = objectHandle;
        Cursor fileList = null;
        try {
            if (MediaFile.isPlayListFileType(fileType)) {
                // build file cache so we can look up tracks in the playlist
                prescan(null, true);

                FileEntry entry = makeEntryFor(path);
                if (entry != null) {
                    fileList = mMediaProvider.query(mFilesUri,
                            FILES_PRESCAN_PROJECTION, null, null, null, null);
                    processPlayList(entry, fileList);
                }
            } else {
                // MTP will create a file entry for us so we don't want to do it in prescan
                prescan(path, false);

                // always scan the file, so we can return the content://media Uri for existing files
                mClient.doScanFile(path, mediaFileType.mimeType, lastModifiedSeconds, file.length(),
                    (format == MtpConstants.FORMAT_ASSOCIATION), true, isNoMediaPath(path));
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scanFile()", e);
        } finally {
            mMtpObjectHandle = 0;
            if (fileList != null) {
                fileList.close();
            }
            releaseResources();
        }
    }

    FileEntry makeEntryFor(String path) {
        String where;
        String[] selectionArgs;

        Cursor c = null;
        try {
            where = Files.FileColumns.DATA + "=?";
            selectionArgs = new String[] { path };
            c = mMediaProvider.query(mFilesUriNoNotify, FILES_PRESCAN_PROJECTION,
                    where, selectionArgs, null, null);
            if (c.moveToFirst()) {
                long rowId = c.getLong(FILES_PRESCAN_ID_COLUMN_INDEX);
                int format = c.getInt(FILES_PRESCAN_FORMAT_COLUMN_INDEX);
                long lastModified = c.getLong(FILES_PRESCAN_DATE_MODIFIED_COLUMN_INDEX);
                return new FileEntry(rowId, path, lastModified, format);
            }
        } catch (RemoteException e) {
            /// M: Adds log for debug.
            Log.e(TAG, "makeEntryFor: RemoteException! path=" + path, e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }

    // returns the number of matching file/directory names, starting from the right
    private int matchPaths(String path1, String path2) {
        int result = 0;
        int end1 = path1.length();
        int end2 = path2.length();

        while (end1 > 0 && end2 > 0) {
            int slash1 = path1.lastIndexOf('/', end1 - 1);
            int slash2 = path2.lastIndexOf('/', end2 - 1);
            int backSlash1 = path1.lastIndexOf('\\', end1 - 1);
            int backSlash2 = path2.lastIndexOf('\\', end2 - 1);
            int start1 = (slash1 > backSlash1 ? slash1 : backSlash1);
            int start2 = (slash2 > backSlash2 ? slash2 : backSlash2);
            if (start1 < 0) start1 = 0; else start1++;
            if (start2 < 0) start2 = 0; else start2++;
            int length = end1 - start1;
            if (end2 - start2 != length) break;
            if (path1.regionMatches(true, start1, path2, start2, length)) {
                result++;
                end1 = start1 - 1;
                end2 = start2 - 1;
            } else break;
        }

        return result;
    }

    private boolean matchEntries(long rowId, String data) {

        int len = mPlaylistEntries.size();
        boolean done = true;
        for (int i = 0; i < len; i++) {
            PlaylistEntry entry = mPlaylistEntries.get(i);
            if (entry.bestmatchlevel == Integer.MAX_VALUE) {
                continue; // this entry has been matched already
            }
            done = false;
            if (data.equalsIgnoreCase(entry.path)) {
                entry.bestmatchid = rowId;
                entry.bestmatchlevel = Integer.MAX_VALUE;
                continue; // no need for path matching
            }

            int matchLength = matchPaths(data, entry.path);
            if (matchLength > entry.bestmatchlevel) {
                entry.bestmatchid = rowId;
                entry.bestmatchlevel = matchLength;
            }
        }
        return done;
    }

    private void cachePlaylistEntry(String line, String playListDirectory) {
        PlaylistEntry entry = new PlaylistEntry();
        // watch for trailing whitespace
        int entryLength = line.length();
        while (entryLength > 0 && Character.isWhitespace(line.charAt(entryLength - 1))) entryLength--;
        // path should be longer than 3 characters.
        // avoid index out of bounds errors below by returning here.
        if (entryLength < 3) return;
        if (entryLength < line.length()) line = line.substring(0, entryLength);

        // does entry appear to be an absolute path?
        // look for Unix or DOS absolute paths
        char ch1 = line.charAt(0);
        boolean fullPath = (ch1 == '/' ||
                (Character.isLetter(ch1) && line.charAt(1) == ':' && line.charAt(2) == '\\'));
        // if we have a relative path, combine entry with playListDirectory
        if (!fullPath)
            line = playListDirectory + line;
        entry.path = line;
        //FIXME - should we look for "../" within the path?

        mPlaylistEntries.add(entry);
    }

    private void processCachedPlaylist(Cursor fileList, ContentValues values, Uri playlistUri) {
        fileList.moveToPosition(-1);
        while (fileList.moveToNext()) {
            long rowId = fileList.getLong(FILES_PRESCAN_ID_COLUMN_INDEX);
            String data = fileList.getString(FILES_PRESCAN_PATH_COLUMN_INDEX);
            if (matchEntries(rowId, data)) {
                break;
            }
        }

        int len = mPlaylistEntries.size();
        int index = 0;
        for (int i = 0; i < len; i++) {
            PlaylistEntry entry = mPlaylistEntries.get(i);
            if (entry.bestmatchlevel > 0) {
                try {
                    values.clear();
                    values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, Integer.valueOf(index));
                    values.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, Long.valueOf(entry.bestmatchid));
                    mMediaProvider.insert(playlistUri, values);
                    index++;
                } catch (RemoteException e) {
                    Log.e(TAG, "RemoteException in MediaScanner.processCachedPlaylist()", e);
                    return;
                }
            }
        }
        mPlaylistEntries.clear();
    }

    private void processM3uPlayList(String path, String playListDirectory, Uri uri,
            ContentValues values, Cursor fileList) {
        BufferedReader reader = null;
        try {
            File f = new File(path);
            if (f.exists()) {
                reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(f)), 8192);
                String line = reader.readLine();
                mPlaylistEntries.clear();
                while (line != null) {
                    // ignore comment lines, which begin with '#'
                    if (line.length() > 0 && line.charAt(0) != '#') {
                        cachePlaylistEntry(line, playListDirectory);
                    }
                    line = reader.readLine();
                }

                processCachedPlaylist(fileList, values, uri);
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException in MediaScanner.processM3uPlayList()", e);
        } finally {
            try {
                if (reader != null)
                    reader.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException in MediaScanner.processM3uPlayList()", e);
            }
        }
    }

    private void processPlsPlayList(String path, String playListDirectory, Uri uri,
            ContentValues values, Cursor fileList) {
        BufferedReader reader = null;
        try {
            File f = new File(path);
            if (f.exists()) {
                reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(f)), 8192);
                String line = reader.readLine();
                mPlaylistEntries.clear();
                while (line != null) {
                    // ignore comment lines, which begin with '#'
                    if (line.startsWith("File")) {
                        int equals = line.indexOf('=');
                        if (equals > 0) {
                            cachePlaylistEntry(line.substring(equals + 1), playListDirectory);
                        }
                    }
                    line = reader.readLine();
                }

                processCachedPlaylist(fileList, values, uri);
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException in MediaScanner.processPlsPlayList()", e);
        } finally {
            try {
                if (reader != null)
                    reader.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException in MediaScanner.processPlsPlayList()", e);
            }
        }
    }

    class WplHandler implements ElementListener {

        final ContentHandler handler;
        String playListDirectory;

        public WplHandler(String playListDirectory, Uri uri, Cursor fileList) {
            this.playListDirectory = playListDirectory;

            RootElement root = new RootElement("smil");
            Element body = root.getChild("body");
            Element seq = body.getChild("seq");
            Element media = seq.getChild("media");
            media.setElementListener(this);

            this.handler = root.getContentHandler();
        }

        @Override
        public void start(Attributes attributes) {
            String path = attributes.getValue("", "src");
            if (path != null) {
                cachePlaylistEntry(path, playListDirectory);
            }
        }

       @Override
       public void end() {
       }

        ContentHandler getContentHandler() {
            return handler;
        }
    }

    private void processWplPlayList(String path, String playListDirectory, Uri uri,
            ContentValues values, Cursor fileList) {
        FileInputStream fis = null;
        try {
            File f = new File(path);
            if (f.exists()) {
                fis = new FileInputStream(f);

                mPlaylistEntries.clear();
                Xml.parse(fis, Xml.findEncodingByName("UTF-8"),
                        new WplHandler(playListDirectory, uri, fileList).getContentHandler());

                processCachedPlaylist(fileList, values, uri);
            }
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null)
                    fis.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException in MediaScanner.processWplPlayList()", e);
            }
        }
    }

    private void processPlayList(FileEntry entry, Cursor fileList) throws RemoteException {
        String path = entry.mPath;
        ContentValues values = new ContentValues();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) throw new IllegalArgumentException("bad path " + path);
        Uri uri, membersUri;
        long rowId = entry.mRowId;

        // make sure we have a name
        String name = values.getAsString(MediaStore.Audio.Playlists.NAME);
        if (name == null) {
            name = values.getAsString(MediaStore.MediaColumns.TITLE);
            if (name == null) {
                // extract name from file name
                int lastDot = path.lastIndexOf('.');
                name = (lastDot < 0 ? path.substring(lastSlash + 1)
                        : path.substring(lastSlash + 1, lastDot));
            }
        }

        values.put(MediaStore.Audio.Playlists.NAME, name);
        values.put(MediaStore.Audio.Playlists.DATE_MODIFIED, entry.mLastModified);

        if (rowId == 0) {
            values.put(MediaStore.Audio.Playlists.DATA, path);
            uri = mMediaProvider.insert(mPlaylistsUri, values);
            rowId = ContentUris.parseId(uri);
            membersUri = Uri.withAppendedPath(uri, Playlists.Members.CONTENT_DIRECTORY);
        } else {
            uri = ContentUris.withAppendedId(mPlaylistsUri, rowId);
            mMediaProvider.update(uri, values, null, null);

            // delete members of existing playlist
            membersUri = Uri.withAppendedPath(uri, Playlists.Members.CONTENT_DIRECTORY);
            mMediaProvider.delete(membersUri, null, null);
        }

        String playListDirectory = path.substring(0, lastSlash + 1);
        MediaFile.MediaFileType mediaFileType = MediaFile.getFileType(path);
        int fileType = (mediaFileType == null ? 0 : mediaFileType.fileType);

        if (fileType == MediaFile.FILE_TYPE_M3U) {
            processM3uPlayList(path, playListDirectory, membersUri, values, fileList);
        } else if (fileType == MediaFile.FILE_TYPE_PLS) {
            processPlsPlayList(path, playListDirectory, membersUri, values, fileList);
        } else if (fileType == MediaFile.FILE_TYPE_WPL) {
            processWplPlayList(path, playListDirectory, membersUri, values, fileList);
        }
    }

    private void processPlayLists() throws RemoteException {
        Iterator<FileEntry> iterator = mPlayLists.iterator();
        Cursor fileList = null;
        try {
            // use the files uri and projection because we need the format column,
            // but restrict the query to just audio files
            fileList = mMediaProvider.query(mFilesUri, FILES_PRESCAN_PROJECTION,
                    "media_type=2", null, null, null);
            while (iterator.hasNext()) {
                FileEntry entry = iterator.next();
                // only process playlist files if they are new or have been modified since the last scan
                if (entry.mLastModifiedChanged) {
                    processPlayList(entry, fileList);
                }
            }
        } catch (RemoteException e1) {
            /// M: Adds log for debug.
            Log.e(TAG, "processPlayLists: RemoteException!", e1);
        } finally {
            if (fileList != null) {
                fileList.close();
            }
        }
    }

    private native void processDirectory(String path, MediaScannerClient client);
    private native void processFile(String path, String mimeType, MediaScannerClient client);
    private native void setLocale(String locale);

    public native byte[] extractAlbumArt(FileDescriptor fd);

    private static native final void native_init();
    private native final void native_setup();
    private native final void native_finalize();

    @Override
    public void close() {
        mCloseGuard.close();
        if (mClosed.compareAndSet(false, true)) {
            mMediaProvider.close();
            native_finalize();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            mCloseGuard.warnIfOpen();
            close();
        } finally {
            super.finalize();
        }
    }

    /// M: Checks out whether the mimetype is application/octet-stream. @(
    private static final String MIME_APPLICATION_OCTET_STREAM = "application/octet-stream";
    private boolean isValueslessMimeType(String mimetype) {
        boolean valueless = false;
        if (MIME_APPLICATION_OCTET_STREAM.equalsIgnoreCase(mimetype)) {
            valueless = true;
            if (DEBUG) {
                Log.v(TAG, "isValueslessMimeType: mimetype=" + mimetype);
            }
        }
        return valueless;
    }
    /// M: @}

    /// M: MediaScanner Performance turning {@
    /// Add some new api for MediaScanner performance enhancement feature,
    /// we use threadpool to scan every folder in directories.

    /**
     * M: Store playlist file path and return to MediaScannerService so that it will process them
     * when postScanAll called.
     */
    private ArrayList<String> mPlaylistFilePathList = new ArrayList<String>();

    /**
     * M: Pre-scan all, only call by this scanner created in thread pool.
     * @hide
     */
    public void preScanAll(String volume) {
        try {
            prescan(null, true);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scan()", e);
        }
    }

    /**
     * M: Post scan all, only call by this scanner created in thread pool.
     *
     * @param playlistPath playlist file path, process them to database.
     * @hide
     */
    public void postScanAll(ArrayList<String> playlistFilePathList) {
        try {
            /// handle playlists last, after we know what media files are on the storage.
            /// Restore path list to file entry list, then process these playlist
            if (mProcessPlaylists) {
                for (String path : playlistFilePathList) {
                    FileEntry entry = makeEntryFor(path);
                    File file = new File(path);
                    long lastModified = file.lastModified();
                    // add some slack to avoid a rounding error
                    long delta = (entry != null) ? (lastModified - entry.mLastModified) : 0;
                    boolean wasModified = delta > 1 || delta < -1;
                    if (entry == null || wasModified) {
                        if (wasModified) {
                            entry.mLastModified = lastModified;
                        } else {
                            entry = new FileEntry(0, path, lastModified, 0);
                        }
                        entry.mLastModifiedChanged = true;
                    }
                    mPlayLists.add(entry);
                }
                processPlayLists();
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scan()", e);
        }

        if ((mOriginalCount == 0 || mOriginalVideoCount == 0)
                && mImagesUri.equals(Images.Media.getContentUri("external"))) {
            pruneDeadThumbnailFiles();
        }

        if (DEBUG) {
            Log.v(TAG, "postScanAll");
        }
    }

    /**
     * M: Scan all given folder with right method. Single file and empty folder need scan special one by one.
     *
     * @param insertHanlder use to do entries insert
     * @param folders The folders given to scan.
     * @param volume External or internal
     * @param isSingelFile whether the given folders is single file
     *
     * @return playlist file path scan in these folders
     *
     * @hide
     */
    public ArrayList<String> scanFolders(Handler insertHanlder, String[] folders, String volume, boolean isSingelFile) {
        try {
            /// Init mPlaylist because we may insert playlist in begin file.
            mPlayLists.clear();

            if (ENABLE_BULK_INSERTS) {
                /// create MediaInserter for bulk inserts
                mMediaInserter = new MediaInserter(insertHanlder, 100);
            }
            /// Single file scan it directly and folder need scan all it's sub files.
            for (String path : folders) {
                if (isSingelFile) {
                    File file = new File(path);
                    long lastModifiedSeconds = file.lastModified() / 1000; // lastModified is in milliseconds on Files.
                    mClient.doScanFile(path, null, lastModifiedSeconds, file.length(),
                            file.isDirectory(), false, isNoMediaPath(path));
                } else {
                    processDirectory(path, mClient);
                }
            }
            if (ENABLE_BULK_INSERTS) {
                /// flush remaining inserts
                mMediaInserter.flushAll();
                mMediaInserter = null;
            }
        } catch (SQLException e) {
            /// this might happen if the SD card is removed while the media scanner is running
            Log.e(TAG, "SQLException in MediaScanner.scan()", e);
        } catch (UnsupportedOperationException e) {
            /// this might happen if the SD card is removed while the media scanner is running
            Log.e(TAG, "UnsupportedOperationException in MediaScanner.scan()", e);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scan()", e);
        }

        return mPlaylistFilePathList;
    }

    /**
     * M: Scan all given folder with right method. Single file and empty folder need scan special one by one.
     *
     * @param folders The folders given to scan.
     * @param volume External or internal
     * @param isSingelFileOrEmptyFolder whether the given folders is single file or empty folder
     *
     * @return playlist file path scan in these folders
     * @hide
     */
    public ArrayList<String> scanFolders(String[] folders, String volume, boolean isSingelFileOrEmptyFolder) {
        try {
            /// Init mPlaylist because we may insert playlist in begin file.
            mPlayLists.clear();

            if (ENABLE_BULK_INSERTS) {
                /// create MediaInserter for bulk inserts
                mMediaInserter = new MediaInserter(mMediaProvider, 500);
            }
            /// M: Call doScanFile to scan folder and use processDirecitory to scan subfolders.
            for (String folder : folders) {
                  File file = new File(folder);
                  if (file.exists()) {
                      // lastModified is in milliseconds on Files.
                      long lastModifiedSeconds = file.lastModified() / 1000;
                      mClient.doScanFile(folder, null, lastModifiedSeconds, file.length(),
                              file.isDirectory(), false, isNoMediaPath(folder));
                  }

                  if (!isSingelFileOrEmptyFolder) {
                    processDirectory(folder, mClient);
                  }
            }

            if (ENABLE_BULK_INSERTS) {
                /// flush remaining inserts
                mMediaInserter.flushAll();
                mMediaInserter = null;
            }
        } catch (SQLException e) {
            /// this might happen if the SD card is removed while the media scanner is running
            Log.e(TAG, "SQLException in MediaScanner.scan()", e);
        } catch (UnsupportedOperationException e) {
            /// this might happen if the SD card is removed while the media scanner is running
            Log.e(TAG, "UnsupportedOperationException in MediaScanner.scan()", e);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scan()", e);
        }

        return mPlaylistFilePathList;
    }
    /// @}

    /**M: Added for parsing stereo info.@{**/
    private static final String XMP_HEADER_START = "http://ns.adobe.com/xap/1.0/\0";
    private static final String XMP_EXT_MAIN_HEADER1 = "http://ns.adobe.com/xmp/extension/";
    private static final String NS_GDEPTH = "http://ns.google.com/photos/1.0/depthmap/";
    private static final String MTK_REFOCUS_PREFIX = "MRefocus";

    private static final int SOI = 0xFFD8;
    private static final int SOS = 0xFFDA;
    private static final int APP1 = 0xFFE1;
    private static final int APPXTAG_PLUS_LENGTHTAG_BYTE_COUNT = 4;

    /**
     * Check if current photo is stereo or not.
     * @param filePath
     *            file path of photo for checking
     * @return true if stereo photo, false if not stereo photo
     */
    public static boolean isStereoPhoto(String filePath) {
        if (filePath == null) {
            if (DEBUG) {
                Log.d(TAG, "<isStereoPhoto> filePath is null!!");
            }
            return false;
        }

        File srcFile = new File(filePath);
        if (!srcFile.exists()) {
            if (DEBUG) {
                Log.d(TAG, "<isStereoPhoto> " + filePath + " not exists!!!");
            }
            return false;
        }

        long start = System.currentTimeMillis();
        ArrayList<Section> sections = parseApp1Info(filePath);
        if (sections == null || sections.size() < 0) {
            if (DEBUG) {
                Log.d(TAG, "<isStereoPhoto> " + filePath + ", no app1 sections");
            }
            return false;
        }
        RandomAccessFile rafIn = null;
        try {
            rafIn = new RandomAccessFile(filePath, "r");
            for (int i = 0; i < sections.size(); i++) {
                Section section = sections.get(i);
                if (isStereo(section, rafIn)) {
                    if (DEBUG) {
                        Log.d(TAG, "<isStereoPhoto> " + filePath + " is stereo photo");
                    }
                    return true;
                }
            }
            if (DEBUG) {
                Log.d(TAG, "<isStereoPhoto> " + filePath + " is not stereo photo");
            }
            return false;
        } catch (FileNotFoundException e) {
            Log.e(TAG, "<isStereoPhoto> FileNotFoundException:", e);
            return false;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "<isStereoPhoto> IllegalArgumentException:", e);
            return false;
        } finally {
            try {
                if (rafIn != null) {
                    rafIn.close();
                    rafIn = null;
                }
            } catch (IOException e) {
                Log.e(TAG, "<isStereoPhoto> IOException:", e);
            }
            if (DEBUG) {
                Log.d(TAG, "<isStereoPhoto> <performance> costs(ms): "
                    + (System.currentTimeMillis() - start));
            }
        }
    }

    private static boolean isStereo(Section section, RandomAccessFile rafIn) {
        try {
            if (section.mIsXmpMain) {
                rafIn.seek(section.mOffset + 2);
                int len = rafIn.readUnsignedShort() - 2;
                rafIn.skipBytes(XMP_HEADER_START.length());
                byte[] xmpBuffer = new byte[len - XMP_HEADER_START.length()];
                rafIn.read(xmpBuffer, 0, xmpBuffer.length);
                String xmpContent = new String(xmpBuffer);
                if (xmpContent == null) {
                    if (DEBUG) {
                        Log.d(TAG, "<isStereo> xmpContent is null");
                    }
                    return false;
                }
                if (xmpContent.contains(MTK_REFOCUS_PREFIX)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            Log.e(TAG, "<isStereo> IOException:", e);
            return false;
        }
    }

    private static ArrayList<Section> parseApp1Info(String filePath) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(filePath, "r");
            int value = raf.readUnsignedShort();
            if (value != SOI) {
                if (DEBUG) {
                    Log.d(TAG, "<parseApp1Info> error, find no SOI");
                }
                return new ArrayList<Section>();
            }
            int marker = -1;
            long offset = -1;
            int length = -1;
            ArrayList<Section> sections = new ArrayList<Section>();

            while ((value = raf.readUnsignedShort()) != -1 && value != SOS) {
                marker = value;
                offset = raf.getFilePointer() - 2;
                length = raf.readUnsignedShort();
                if (value == APP1) {
                    Section section =
                            new Section(marker, offset, length);
                    long currentPos = raf.getFilePointer();
                    section = checkIfMainXmpInApp1(raf, section);
                    if (section != null && section.mIsXmpMain) {
                        sections.add(section);
                        break;
                    }
                    raf.seek(currentPos);
                }
                raf.skipBytes(length - 2);
            }

            return sections;
        } catch (IOException e) {
            Log.e(TAG, "<parseApp1Info> IOException, path " + filePath, e);
            return null;
        } finally {
            try {
                if (raf != null) {
                    raf.close();
                    raf = null;
                }
            } catch (IOException e) {
                Log.e(TAG, "<parseApp1Info> IOException, path " + filePath, e);
            }
        }
    }

    private static Section checkIfMainXmpInApp1(RandomAccessFile raf, Section section) {
        if (section == null) {
            if (DEBUG) {
                Log.d(TAG, "<checkIfMainXmpInApp1> section is null!!!");
            }
            return null;
        }
        byte[] buffer = null;
        String str = null;
        try {
            if (section.mMarker == APP1) {
                raf.seek(section.mOffset + APPXTAG_PLUS_LENGTHTAG_BYTE_COUNT);
                buffer = new byte[XMP_EXT_MAIN_HEADER1.length()];
                raf.read(buffer, 0, buffer.length);
                str = new String(buffer, 0, XMP_HEADER_START.length());
                if (XMP_HEADER_START.equals(str)) {
                    section.mIsXmpMain = true;
                }
            }
            return section;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "<checkIfMainXmpInApp1> UnsupportedEncodingException" + e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "<checkIfMainXmpInApp1> IOException" + e);
            return null;
        }
    }

    /**
     * APP Section.
     */
    private static class Section {
        // e.g. 0xffe1, exif
        public int mMarker;
        // marker offset from start of file
        public long mOffset;
        // app length, follow spec, include 2 length bytes
        public int mLength;
        public boolean mIsXmpMain;

        /**
          * Create a Section.
          * @param marker section mark
          * @param offset section address offset
          * @param length section length
          */
        public Section(int marker, long offset, int length) {
            mMarker = marker;
            mOffset = offset;
            mLength = length;
        }
    }
    /**@}**/
}
