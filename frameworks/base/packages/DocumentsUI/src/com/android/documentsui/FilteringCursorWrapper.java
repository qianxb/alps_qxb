/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui;

import static com.android.documentsui.Shared.DEBUG;
import static com.android.documentsui.Shared.TAG;
import static com.android.documentsui.model.DocumentInfo.getCursorInt;
import static com.android.documentsui.model.DocumentInfo.getCursorLong;
import static com.android.documentsui.model.DocumentInfo.getCursorString;

import android.database.AbstractCursor;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.DocumentsContract.Document;
import android.provider.MediaStore;
import android.util.Log;

/// M: Add to support drm
import com.mediatek.omadrm.OmaDrmStore;


/**
 * Cursor wrapper that filters MIME types not matching given list.
 */
public class FilteringCursorWrapper extends AbstractCursor {
    private final Cursor mCursor;

    private final int[] mPosition;
    private int mCount;

    /// M: add to support drm, only show these drm files match given drm level.
    private int mDrmLevel = -1;

    public FilteringCursorWrapper(Cursor cursor, String[] acceptMimes) {
        this(cursor, acceptMimes, null, Long.MIN_VALUE);
    }

    public FilteringCursorWrapper(Cursor cursor, String[] acceptMimes, String[] rejectMimes) {
        this(cursor, acceptMimes, rejectMimes, Long.MIN_VALUE);
    }

    public FilteringCursorWrapper(
            Cursor cursor, String[] acceptMimes, String[] rejectMimes, long rejectBefore) {
        mCursor = cursor;

        final int count = cursor.getCount();
        mPosition = new int[count];

        cursor.moveToPosition(-1);
        while (cursor.moveToNext() && mCount < count) {
            final String mimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
            final long lastModified = getCursorLong(cursor, Document.COLUMN_LAST_MODIFIED);
            if (rejectMimes != null && MimePredicate.mimeMatches(rejectMimes, mimeType)) {
                continue;
            }
            if (lastModified < rejectBefore) {
                continue;
            }
            if (MimePredicate.mimeMatches(acceptMimes, mimeType)) {
                mPosition[mCount++] = cursor.getPosition();
            }
        }

        if (DEBUG && mCount != cursor.getCount()) {
            Log.d(TAG, "Before filtering " + cursor.getCount() + ", after " + mCount);
        }
    }

    /**
     * M: init FilteringCursorWrapper with given drm level.
     */
    public FilteringCursorWrapper(Cursor cursor, int drmLevel) {
        mCursor = cursor;
        mDrmLevel = drmLevel;

        final int count = cursor.getCount();
        mPosition = new int[count];

        /// M: Add to support drm. we only show match given drm level in intent extra,
        /// if don't limit drm level, we will show all drm files to user. {@
        int needShowDrmMethod = 0;
        if (DocumentsFeatureOption.IS_SUPPORT_DRM) {
            switch (mDrmLevel) {
                case OmaDrmStore.DrmIntentExtra.LEVEL_FL:
                    needShowDrmMethod = OmaDrmStore.Method.FL;
                    break;
                case OmaDrmStore.DrmIntentExtra.LEVEL_SD:
                    needShowDrmMethod = OmaDrmStore.Method.SD;
                    break;
                case OmaDrmStore.DrmIntentExtra.LEVEL_ALL:
                    needShowDrmMethod = OmaDrmStore.Method.FL | OmaDrmStore.Method.CD
                            | OmaDrmStore.Method.SD | OmaDrmStore.Method.FLSD;
                    break;
                default:
                    needShowDrmMethod = OmaDrmStore.Method.FL | OmaDrmStore.Method.CD
                            | OmaDrmStore.Method.SD | OmaDrmStore.Method.FLSD;
                    break;
            }
        }
        /// @}

        cursor.moveToPosition(-1);
        for (int i = 0; i < count; i++) {
            cursor.moveToNext();
            /// M: If it's no need show drmMethod, ignore it and make it hidden. {@
            if (DocumentsFeatureOption.IS_SUPPORT_DRM) {
                boolean isDrm = getCursorInt(cursor, MediaStore.MediaColumns.IS_DRM) > 0;
                int drmMethod = getCursorInt(cursor, MediaStore.MediaColumns.DRM_METHOD);
                /// M: If IS_DRM is true but drm_method is invalid(-1)
                /// with given need show drm level(mDrmLevel>0),
                /// this may happen when drm file has been deleted,
                /// we don't need show these drm files.
                if (isDrm && ((mDrmLevel > 0 && drmMethod < 0) ||
                 (needShowDrmMethod & drmMethod) == 0)) {
                    continue;
                }
            }
            mPosition[mCount] = cursor.getPosition();
            /// @}
            mCount++;
        }
    }


    @Override
    public Bundle getExtras() {
        return mCursor.getExtras();
    }

    @Override
    public void close() {
        super.close();
        mCursor.close();
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        return mCursor.moveToPosition(mPosition[newPosition]);
    }

    @Override
    public String[] getColumnNames() {
        return mCursor.getColumnNames();
    }

    @Override
    public int getCount() {
        return mCount;
    }

    @Override
    public double getDouble(int column) {
        return mCursor.getDouble(column);
    }

    @Override
    public float getFloat(int column) {
        return mCursor.getFloat(column);
    }

    @Override
    public int getInt(int column) {
        return mCursor.getInt(column);
    }

    @Override
    public long getLong(int column) {
        return mCursor.getLong(column);
    }

    @Override
    public short getShort(int column) {
        return mCursor.getShort(column);
    }

    @Override
    public String getString(int column) {
        return mCursor.getString(column);
    }

    @Override
    public int getType(int column) {
        return mCursor.getType(column);
    }

    @Override
    public boolean isNull(int column) {
        return mCursor.isNull(column);
    }
}
