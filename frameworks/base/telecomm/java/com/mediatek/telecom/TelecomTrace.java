package com.mediatek.telecom;

import android.os.Trace;
import android.telecom.Log;

/**
 * Trace utils.
 * @hide
 */
public class TelecomTrace {
    private static final String LOG_TAG = "TelecomTrace";
    private static final long TRACE_TAG = Trace.TRACE_TAG_PERF;

    /**
     * begin the performance trace.
     * @param tag the tag shows in the report.
     * @hide
     */
    public static void begin(String tag) {
        if (Trace.isTagEnabled(TRACE_TAG)) {
            Log.d(LOG_TAG, "[begin]" + tag);
        }
        Trace.traceBegin(TRACE_TAG, tag);
    }

    /**
     * end the performance trace.
     * @param tag the tag shows in the report.
     * @hide
     */
    public static void end(String tag) {
        Trace.traceEnd(TRACE_TAG);
        if (Trace.isTagEnabled(TRACE_TAG)) {
            Log.d(LOG_TAG, "[end]" + tag);
        }
    }
}
