package com.mediatek.usp;

import android.content.Context;
import android.util.Log;

import com.android.server.SystemService;

/**
 * Usp Service.
 */
public class UspService extends SystemService {

    private final String TAG = "UspService";
    private final boolean DEBUG = true;
    final UspServiceImpl mImpl;
    /**
     * constructor of Usp service.
     * @param context from system server
     */
    public UspService(Context context) {
        super(context);
        mImpl = new UspServiceImpl(context);
        Log.i(TAG, "UspServiceImpl" + mImpl.toString());
    }

    @Override
    public void onStart() {
        Log.i(TAG, "Registering service " + Context.USP_SERVICE);
        publishBinderService(Context.USP_SERVICE, mImpl);
        mImpl.start();
    }

    @Override
    public void onBootPhase(int phase) {
        Log.i(TAG, "phase " + phase);
        if (phase == SystemService.PHASE_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed: unfreezed");
            mImpl.unfreezeScreen();
        }
    }
}

