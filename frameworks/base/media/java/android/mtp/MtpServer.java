/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.mtp;

/**
 * Java wrapper for MTP/PTP support as USB responder.
 * {@hide}
 */
public class MtpServer implements Runnable {

    private long mNativeContext; // accessed by native methods
    private boolean mServerEndup = false;
    private final MtpDatabase mDatabase;

    static {
        System.loadLibrary("media_jni");
    }

    public MtpServer(MtpDatabase database, boolean usePtp) {
        mServerEndup = false;
        mDatabase = database;
        native_setup(database, usePtp);
        database.setServer(this);
    }

    public void start() {
        mServerEndup = false;
        Thread thread = new Thread(this, "MtpServer");
        thread.start();
    }

    @Override
    public void run() {
        native_run();
        native_cleanup();
        mServerEndup = true;
        mDatabase.close();
    }

    public void sendObjectAdded(int handle) {
        native_send_object_added(handle);
    }

    public void sendObjectRemoved(int handle) {
        native_send_object_removed(handle);
    }

    public void sendDevicePropertyChanged(int property) {
        native_send_device_property_changed(property);
    }

    public void addStorage(MtpStorage storage) {
        native_add_storage(storage);
    }

    public void removeStorage(MtpStorage storage) {
        native_remove_storage(storage.getStorageId());
    }

    /**
     * Added for Storage Update and send StorageInfoChanged event
     * @hide
     * @internal
     */
    public void updateStorage(MtpStorage storage) {
        native_update_storage(storage);
    }
    /**
     * for send StorageInfoChanged event
     * @hide
     * @internal
     */
    public void sendStorageInfoChanged(MtpStorage storage) {
        native_send_storage_infoChanged(storage.getStorageId());
    }

    /**
     * dummy
     * @hide
     * @internal
     */
    public void endSession() {
        //do nothing
    }

    /**
     * check thread run status
     * @hide
     * @internal
     */
    public boolean getStatus() {
        return mServerEndup;
    }

    /**
     * update object, send ObjectInfoChanged event
     * @hide
     * @internal
     */
    public void sendObjectInfoChanged(int handle) {
        native_send_object_infoChanged(handle);
    }

    private native final void native_setup(MtpDatabase database, boolean usePtp);
    private native final void native_run();
    private native final void native_cleanup();
    private native final void native_send_object_added(int handle);
    private native final void native_send_object_removed(int handle);
    private native final void native_send_device_property_changed(int property);
    private native final void native_add_storage(MtpStorage storage);
    private native final void native_remove_storage(int storageId);
    // for sorage update
    private native final void native_update_storage(MtpStorage storage);
    private native final void native_send_storage_infoChanged(int storageId);
    private native final void native_send_object_infoChanged(int handle);
}
