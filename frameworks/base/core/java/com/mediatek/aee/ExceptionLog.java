/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.aee;

import android.util.Log;

import com.mediatek.am.AMEventHookData;
import com.mediatek.am.AMEventHookData.EndOfErrorDumpThread;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import java.io.IOException;

public class ExceptionLog {
  public static final String TAG = "AES";
    public static final byte AEE_WARNING_JNI = 0;
    public static final byte AEE_EXCEPTION_JNI = 1;

    static {
        Log.i(TAG, "load Exception Log jni");
        System.loadLibrary("mediatek_exceptionlog");
    }

    /// M: Use AMEventHook @{
    public void onEndOfErrorDumpThread(
        AMEventHookData.EndOfErrorDumpThread data) {

        String type = data.getString(EndOfErrorDumpThread.Index.type);
        String info = data.getString(EndOfErrorDumpThread.Index.info);
        String pid = data.getString(EndOfErrorDumpThread.Index.pid);

        Log.d(TAG, "onEndOfErrorDumpThread: " + type + " " + info + " " + pid);
        handle(type, info, pid);
    }
    /// M: Use AMEventHook @}

    public void handle(String type, String info, String pid) {
        Log.w(TAG, "Exception Log handling...");

        String proc = "";
        String pkgs = "";
        String traceback = "";
        String cause = "";
        String detail = "";
        long lpid = 0;

        String[] splitInfo = (info.split("\n+"));
        final String PROC_REGEX = "^Process:\\s+(.*)";
        final String PKG_REGEX = "^Package:\\s+(.*)";
        Pattern procMatcher = Pattern.compile(PROC_REGEX);
        Pattern pkgMatcher = Pattern.compile(PKG_REGEX);
        Matcher m;

        for (String s : splitInfo) {
            m = procMatcher.matcher(s);
            if (m.matches()) proc = m.group(1);
            m = pkgMatcher.matcher(s);
            if (m.matches()) pkgs += m.group(1) + "\n";
        }

        //detail = "Backtrace of all threads:\n\n" + getAllThreadStackTraces();
        detail = "Backtrace of all threads:\n\n";
        if (!pid.equals("")) {
            lpid = Long.parseLong(pid);
        }

        /// M: Avoid WTF report by sending non-protected broadcast @{
        if (type.equals("system_server_wtf") && isSkipSystemWtfReport(info)) {
            return;
        }
        /// @}

        report(proc, pkgs, info, detail, type, lpid);
    }

    public void systemreport(byte Type, String Module, String Msg, String Path) {
        String Backtrace = getThreadStackTrace();
        systemreportImpl(Type, Module, Backtrace, Msg, Path);
        return;
    }

    public boolean getNativeExceptionPidList(int[] pidList) {
    return getNativeExceptionPidListImpl(pidList);
    }

    public boolean isNativeException(int pid) {
    return isNativeExceptionImpl(pid);
    }

    public boolean isException() {
    return isExceptionImpl();
    }

    /**
     * Get stack traces of current thread. Invoked at handling
     * application erorr.
     */
    private static String getThreadStackTrace() {
        Writer traces = new StringWriter();
        String ret_trace = "";

        try {
            Thread th = Thread.currentThread();
            StackTraceElement[] st = th.getStackTrace();

            traces.write("\"" + th.getName() + "\"" +
               " " + (th.isDaemon() ? "daemon" : "") +
               " prio=" + th.getPriority() +
               " Thread id=" + th.getId() + " " + th.getState() + "\n");
            for (StackTraceElement line: st) {
                traces.write("\t" + line + "\n");
            }
            traces.write("\n");
            if (traces != null) {
                ret_trace = traces.toString();
            }
        } catch (IOException e) {
            return "IOException";
        } catch (java.lang.OutOfMemoryError err) {
            return "java.lang.OutOfMemoryError";
        }

        return ret_trace;
    }

    /**
     * Get all stack traces of current process. Invoked at handling
     * application erorr.
     */
    private static String getAllThreadStackTraces() {
        Map<Thread, StackTraceElement[]> st = Thread.getAllStackTraces();
        Writer traces = new StringWriter();
        String ret_traces = "";

        try {
            for (Map.Entry<Thread, StackTraceElement[]> e: st.entrySet()) {
                StackTraceElement[] el = e.getValue();
                Thread th = e.getKey();

                traces.write("\"" + th.getName() + "\"" +
                      " " + (th.isDaemon() ? "daemon" : "") +
                      " prio=" + th.getPriority() +
                      " Thread id=" + th.getId() + " " + th.getState() + "\n");

                for (StackTraceElement line: el) {
                    traces.write("\t" + line + "\n");
                }
                traces.write("\n");
            }
            if (traces != null) {
                ret_traces = traces.toString();
            }
        } catch (IOException e) {
            return "IOException";
        } catch (java.lang.OutOfMemoryError err) {
            return "java.lang.OutOfMemoryError";
        }

        return ret_traces;
    }
    public void WDTMatterJava(long lParam) {
        WDTMatter(lParam) ;
        }
    public long SFMatterJava(long setorget, long lParam) {
        return SFMatter(setorget, lParam) ;
        }

    private static native void report(String process, String module, String traceback, String detail, String cause, long pid);
    private static native void systemreportImpl(byte Type, String Module, String Backtrace, String Msg, String Path);
    private static native boolean getNativeExceptionPidListImpl(int []pidList);
    private static native boolean isNativeExceptionImpl(int pid);
    private static native boolean isExceptionImpl();
// QHQ RT Monitor
    private static native void WDTMatter(long lParam) ;
    private static native long SFMatter(long setorget, long lParam) ;
// QHQ RT Monitor end

    /// M: Avoid WTF report by sending non-protected broadcast @{
    private final String SEND_NON_PROTECTED_BROADCAST = "Sending non-protected broadcast";

    private boolean isSkipSystemWtfReport(String info) {
        if (isSkipReportFromProtectedBroadcast(info)) {
            return true;
        }

        /// M: ALPS02395371 Failed to create file since the file path is null @{
        if (isSkipReportFromNullFilePath(info)) {
            return true;
        }
        /// @}

        return false;
    }

    private boolean isSkipReportFromProtectedBroadcast(String info) {
        if (info.contains(SEND_NON_PROTECTED_BROADCAST)) {
            return true;
        }
        return false;
    }
    /// @}

    /// M: ALPS02395371 Failed to create file since the file path is null @{
    private final String FILE_OBSERVER_NULL_PATH =
            "Unhandled exception in FileObserver com.android.server.BootReceiver";
    private boolean isSkipReportFromNullFilePath(String info) {
        if (info.contains(FILE_OBSERVER_NULL_PATH)) {
            return true;
        }
        return false;
    }
    /// @}
}
