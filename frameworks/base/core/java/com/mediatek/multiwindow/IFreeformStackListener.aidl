package com.mediatek.multiwindow;

/**
  * M: BMW
  * Listener for showing/hiding of the restore button.
  *
  * @hide
  */
oneway interface IFreeformStackListener {

    /**
     * Called when the App get focus.
     * @internal
     */
    void onShowRestoreButtonChanged(boolean exists);
}
