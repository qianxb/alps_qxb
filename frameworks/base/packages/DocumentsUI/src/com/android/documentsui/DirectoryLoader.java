/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
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
import static com.android.documentsui.State.SORT_ORDER_DISPLAY_NAME;
import static com.android.documentsui.State.SORT_ORDER_LAST_MODIFIED;
import static com.android.documentsui.State.SORT_ORDER_SIZE;

import android.content.AsyncTaskLoader;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.DeadObjectException;
import android.os.OperationCanceledException;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.util.Log;

import com.android.documentsui.dirlist.DirectoryFragment;
import com.android.documentsui.model.DocumentInfo;
import com.android.documentsui.model.RootInfo;

import libcore.io.IoUtils;

import java.io.FileNotFoundException;

/// M: Add to support drm
// import com.mediatek.drm.OmaDrmStore;
/// M: DRM refactory
import com.mediatek.omadrm.OmaDrmStore;


public class DirectoryLoader extends AsyncTaskLoader<DirectoryResult> {

    private static final String[] SEARCH_REJECT_MIMES = new String[] { Document.MIME_TYPE_DIR };

    private final ForceLoadContentObserver mObserver = new ForceLoadContentObserver();

    private final int mType;
    private final RootInfo mRoot;
    private final Uri mUri;
    private final int mUserSortOrder;
    private final boolean mSearchMode;

    private DocumentInfo mDoc;
    private CancellationSignal mSignal;
    private DirectoryResult mResult;

    /// M: add to support drm
    private int mDrmLevel;
    /// M: show previous loader's result @{
    private boolean mIsLoading = false;
    /// @}

    public DirectoryLoader(Context context, int type, RootInfo root, DocumentInfo doc, Uri uri,
            int userSortOrder, boolean inSearchMode) {
        super(context, ProviderExecutor.forAuthority(root.authority));
        mType = type;
        mRoot = root;
        mUri = uri;
        mUserSortOrder = userSortOrder;
        mDoc = doc;
        mSearchMode = inSearchMode;
        /// M: add to support drm
                try {
                        mDrmLevel = ((FilesActivity) context).getIntent().getIntExtra
                         (OmaDrmStore.DrmIntentExtra.EXTRA_DRM_LEVEL, -1);
                } catch (ClassCastException e) {
                        e.printStackTrace();
                        mDrmLevel = ((DocumentsActivity) context).getIntent().getIntExtra
                        (OmaDrmStore.DrmIntentExtra.EXTRA_DRM_LEVEL, -1);
                }
    }

    @Override
    public final DirectoryResult loadInBackground() {
        mIsLoading = true;
        synchronized (this) {
            if (isLoadInBackgroundCanceled()) {
                throw new OperationCanceledException();
            }
            mSignal = new CancellationSignal();
        }

        final ContentResolver resolver = getContext().getContentResolver();
        final String authority = mUri.getAuthority();

        final DirectoryResult result = new DirectoryResult();
        result.doc = mDoc;

        // Use default document when searching
        if (mSearchMode) {
            final Uri docUri = DocumentsContract.buildDocumentUri(
                    mRoot.authority, mRoot.documentId);
            try {
                mDoc = DocumentInfo.fromUri(resolver, docUri);
            } catch (FileNotFoundException e) {
                Log.w(TAG, "Failed to query", e);
                result.exception = e;
                return result;
            }
        }

        /// M: Move get custom mode after finishing get result from provider
        // Pick up any custom modes requested by user
        Cursor cursor = null;
        /*
        //M: mark google code
        try {
            final Uri stateUri = RecentsProvider.buildState(
                    mRoot.authority, mRoot.rootId, mDoc.documentId);
            cursor = resolver.query(stateUri, null, null, null, null);
            if (cursor.moveToFirst()) {
                userMode = getCursorInt(cursor, StateColumns.MODE);
            }
        } finally {
            IoUtils.closeQuietly(cursor);
        }

        if (userMode != State.MODE_UNKNOWN) {
            result.mode = userMode;
        } else {
            if ((mDoc.flags & Document.FLAG_DIR_PREFERS_GRID) != 0) {
                result.mode = State.MODE_GRID;
            } else {
                result.mode = State.MODE_LIST;
            }
        }
         //M end
         */

        if (mUserSortOrder != State.SORT_ORDER_UNKNOWN) {
            result.sortOrder = mUserSortOrder;
        } else {
            if ((mDoc.flags & Document.FLAG_DIR_PREFERS_LAST_MODIFIED) != 0) {
                result.sortOrder = State.SORT_ORDER_LAST_MODIFIED;
            } else {
                result.sortOrder = State.SORT_ORDER_DISPLAY_NAME;
            }
        }

        // Search always uses ranking from provider
        if (mSearchMode) {
            result.sortOrder = State.SORT_ORDER_UNKNOWN;
        }

        if (DEBUG)
            Log.d(TAG, "Loading directory for " + mUri + ", userSortOrder=" + mUserSortOrder
             + ", sortOrder=" + result.sortOrder + ", drmLevel=" + mDrmLevel);


        ContentProviderClient client = null;
        //Cursor cursor = null;
        try {
            client = DocumentsApplication.acquireUnstableProviderOrThrow(resolver, authority);
            cursor = client.query(
                    mUri, null, null, null, getQuerySortOrder(result.sortOrder), mSignal);
            if (cursor == null) {
                throw new RemoteException("Provider returned null");
            }

            cursor.registerContentObserver(mObserver);

            cursor = new RootCursorWrapper(mUri.getAuthority(), mRoot.rootId, cursor, -1);

            if (mSearchMode) {
                // Filter directories out of search results, for now
                cursor = new FilteringCursorWrapper(cursor, null, SEARCH_REJECT_MIMES);
            }
            else {
                /// M: Support DRM
                cursor = new FilteringCursorWrapper(cursor, mDrmLevel);
            }

            result.client = client;
            result.cursor = cursor;
        } catch (Exception e) {
            Log.w(TAG, "Failed to query", e);
            result.exception = e;
            ContentProviderClient.releaseQuietly(client);
        } finally {
            synchronized (this) {
                mSignal = null;
            }
        }

        /// M: Move get custom mode after finishing get result from provider, so that
        /// when user switch show mode(grid/list), loader can get the latest to return
        /// to fragment.
        try {
            final Uri stateUri = RecentsProvider.buildState(
                    mRoot.authority, mRoot.rootId, mDoc.documentId);
            cursor = resolver.query(stateUri, null, null, null, null);
            if (cursor.moveToFirst()) {
                //userMode = getCursorInt(cursor, StateColumns.MODE);
            }
        } finally {
            IoUtils.closeQuietly(cursor);
        }

        /*if (userMode != State.MODE_UNKNOWN) {
            result.mode = userMode;
        } else {
            if ((mDoc.flags & Document.FLAG_DIR_PREFERS_GRID) != 0) {
                result.mode = State.MODE_GRID;
            } else {
                result.mode = State.MODE_LIST;
            }
        }
        Log.d(TAG, "Loading directory finish for " + mUri + " with: userMode=" + userMode);*/

        mIsLoading = false;
        return result;
    }

    @Override
    public void cancelLoadInBackground() {
        super.cancelLoadInBackground();

        synchronized (this) {
            if (mSignal != null) {
                mSignal.cancel();
            }
        }
    }

    @Override
    public void deliverResult(DirectoryResult result) {
        if (isReset()) {
            IoUtils.closeQuietly(result);
            return;
        }
        /// M: If the given result has exception with DeadObjectException type, it means
        /// client has died, we need load directory it again.
        if (isStarted() && result != null && result.exception != null
                && (result.exception instanceof DeadObjectException)) {
            Log.d(TAG, "deliverResult with client has dead, reload directory again");
            IoUtils.closeQuietly(result);
            forceLoad();
            return;
        }

        DirectoryResult oldResult = mResult;
        mResult = result;

        if (isStarted()) {
            super.deliverResult(result);
        }

        if (oldResult != null && oldResult != result) {
            IoUtils.closeQuietly(oldResult);
        }
    }

    @Override
    protected void onStartLoading() {
        /// M: show previous loader's result @{
        boolean contentChanged = takeContentChanged();
        if (mResult != null) {
            /// Check current contentprovider client, if the server has died, we need reload
            /// to register observer. @{
            try {
                mResult.client.canonicalize(mUri);
                deliverResult(mResult);
            } catch (Exception e) {
                contentChanged = true;
                Log.d(TAG, "onStartLoading with client has dead, reload to register obsever. " + e);
            }
            /// @}
        }
        Log.d(TAG, "onStartLoading contentChanged: " + contentChanged + ", mIsLoading: "
                + mIsLoading + ", mResult: " + mResult);
        if (!contentChanged && mIsLoading) {
            return;
        }
        if (contentChanged || mResult == null) {
            forceLoad();
        }
        /// @}
    }

    @Override
    protected void onStopLoading() {
        cancelLoad();
    }

    @Override
    public void onCanceled(DirectoryResult result) {
        /// M: show previous loader's result @{
        if (result == null) {
            return;
        }
        if (result.exception != null && (result.exception instanceof OperationCanceledException)) {
            IoUtils.closeQuietly(result);
            Log.d(TAG, "DirectoryLoader: loading has been canceled, no deliver result");
            return;
        }
        if (!isReset() && (mResult == null)) {
            deliverResult(result);
            Log.d(TAG, "DirectoryLoader show result when onCanceled");
        } else {
            IoUtils.closeQuietly(result);
        }
        /// @}
    }

    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();

        IoUtils.closeQuietly(mResult);
        mResult = null;

        getContext().getContentResolver().unregisterContentObserver(mObserver);
    }

    public static String getQuerySortOrder(int sortOrder) {
        switch (sortOrder) {
            case SORT_ORDER_DISPLAY_NAME:
                return Document.COLUMN_DISPLAY_NAME + " ASC";
            case SORT_ORDER_LAST_MODIFIED:
                return Document.COLUMN_LAST_MODIFIED + " DESC";
            case SORT_ORDER_SIZE:
                return Document.COLUMN_SIZE + " DESC";
            default:
                return null;
        }
    }
}
