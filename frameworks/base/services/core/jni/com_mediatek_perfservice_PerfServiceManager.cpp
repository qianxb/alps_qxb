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


#define LOG_TAG "PerfService"
#include "utils/Log.h"

#include <stdio.h>
#include <dlfcn.h>

#include <unistd.h>

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"

namespace android
{
#if defined(MTK_PERFSERVICE_SUPPORT)
static int inited = false;

static int (*perfBoostEnable)(int) = NULL;
static int (*perfBoostDisable)(int) = NULL;
static int (*perfNotifyAppState)(const char*, const char*, int, int) = NULL;
static int (*perfUserScnReg)(int, int, int, int) = NULL;
static int (*perfUserScnRegBigLittle)(int, int, int, int, int, int) = NULL;
static int (*perfUserScnUnreg)(int) = NULL;
static int (*perfUserGetCapability)(int) = NULL;
static int (*perfUserRegScn)(int, int) = NULL;
static int (*perfUserRegScnConfig)(int, int, int, int, int, int) = NULL;
static int (*perfUserUnregScn)(int) = NULL;
static int (*perfUserScnEnable)(int) = NULL;
static int (*perfUserScnDisable)(int) = NULL;
static int (*perfUserScnResetAll)(void) = NULL;
static int (*perfUserScnDisableAll)(void) = NULL;
static int (*perfUserScnRestoreAll)(void) = NULL;
static int (*perfDumpAll)(void) = NULL;
static int (*perfSetFavorPid)(int) = NULL;
static int (*perfRestorePolicy)(int) = NULL;
static int (*perfNotifyDisplayType)(int) = NULL;
static int (*perfGetLastBoostPid)(void) = NULL;
static int (*perfNotifyUserStatus)(int, int) = NULL;
static int (*perfSetPackAttr)(int, int) = NULL;
static int (*perfSetUeventIndex)(int) = NULL;
static int (*perfLevelBoost)(int) = NULL;
static int (*perfGetClusterInfo)(int, int) = NULL;
static int (*perfGetPackAttr)(const char*, int) = NULL;
#endif

typedef int (*ena)(int);
typedef int (*disa)(int);
typedef int (*notify)(const char*, const char*, int, int);
typedef int (*user_reg)(int, int, int, int);
typedef int (*user_reg_big_little)(int, int, int, int, int, int);
typedef int (*user_unreg)(int);
typedef int (*user_get_capability)(int);
typedef int (*user_reg_scn)(int, int);
typedef int (*user_reg_scn_config)(int, int, int, int, int, int);
typedef int (*user_unreg_scn)(int);
typedef int (*user_enable)(int);
typedef int (*user_disable)(int);
typedef int (*user_reset_all)(void);
typedef int (*user_disable_all)(void);
typedef int (*user_restore_all)(void);
typedef int (*dump_all)(void);
typedef int (*set_favor_pid)(int);
typedef int (*restore_policy)(int);
typedef int (*notify_display_type)(int);
typedef char* (*get_pack_name)();
typedef int (*get_last_boost_pid)();
typedef int (*notify_user_status)(int, int);
typedef int (*set_pack_attr)(int, int);
typedef int (*set_uevent_index)(int);
typedef int (*level_boost)(int);
typedef int (*get_cluster_info)(int, int);
typedef int (*get_pack_attr)(const char*, int);

#define LIB_FULL_NAME "libperfservice.so"

#if defined(MTK_PERFSERVICE_SUPPORT)
static void init()
{
    void *handle, *func;

    // only enter once
    inited = true;

    handle = dlopen(LIB_FULL_NAME, RTLD_NOW);
    if (handle == NULL) {
        ALOGE("Can't load library: %s", dlerror());
        return;
    }

    func = dlsym(handle, "perfBoostEnable");
    perfBoostEnable = reinterpret_cast<ena>(func);

    if (perfBoostEnable == NULL) {
        ALOGE("perfBoostEnable error: %s", dlerror());
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfBoostDisable");
    perfBoostDisable = reinterpret_cast<disa>(func);

    if (perfBoostDisable == NULL) {
        ALOGE("perfBoostDisable error: %s", dlerror());
        perfBoostEnable = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfNotifyAppState");
    perfNotifyAppState = reinterpret_cast<notify>(func);

    if (perfNotifyAppState == NULL) {
        ALOGE("perfNotifyAppState error: %s", dlerror());
        perfNotifyAppState = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfUserScnReg");
    perfUserScnReg = reinterpret_cast<user_reg>(func);

    if (perfUserScnReg == NULL) {
        ALOGE("perfUserScnReg error: %s", dlerror());
        perfUserScnReg = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfUserScnRegBigLittle");
    perfUserScnRegBigLittle = reinterpret_cast<user_reg_big_little>(func);

    if (perfUserScnRegBigLittle == NULL) {
        ALOGE("perfUserScnRegBigLittle error: %s", dlerror());
        perfUserScnRegBigLittle = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfUserScnUnreg");
    perfUserScnUnreg = reinterpret_cast<user_unreg>(func);

    if (perfUserScnUnreg == NULL) {
        ALOGE("perfUserScnUnreg error: %s", dlerror());
        perfUserScnUnreg = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfUserGetCapability");
    perfUserGetCapability = reinterpret_cast<user_get_capability>(func);

    if (perfUserGetCapability == NULL) {
        ALOGE("perfUserGetCapability error: %s", dlerror());
        perfUserGetCapability = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfUserRegScn");
    perfUserRegScn = reinterpret_cast<user_reg_scn>(func);

    if (perfUserRegScn == NULL) {
        ALOGE("perfUserRegScn error: %s", dlerror());
        perfUserRegScn = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfUserRegScnConfig");
    perfUserRegScnConfig = reinterpret_cast<user_reg_scn_config>(func);

    if (perfUserRegScnConfig == NULL) {
        ALOGE("perfUserRegScnConfig error: %s", dlerror());
        perfUserRegScnConfig = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfUserUnregScn");
    perfUserUnregScn = reinterpret_cast<user_unreg_scn>(func);

    if (perfUserUnregScn == NULL) {
        ALOGE("perfUserUnregScn error: %s", dlerror());
        perfUserUnregScn = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfUserScnEnable");
    perfUserScnEnable = reinterpret_cast<user_enable>(func);

    if (perfUserScnEnable == NULL) {
        ALOGE("perfUserScnEnable error: %s", dlerror());
        perfUserScnEnable = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfUserScnDisable");
    perfUserScnDisable = reinterpret_cast<user_disable>(func);

    if (perfUserScnDisable == NULL) {
        ALOGE("perfUserScnDisable error: %s", dlerror());
        perfUserScnDisable = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfUserScnResetAll");
    perfUserScnResetAll = reinterpret_cast<user_reset_all>(func);

    if (perfUserScnResetAll == NULL) {
        ALOGE("perfUserScnResetAll error: %s", dlerror());
        perfUserScnResetAll = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfUserScnDisableAll");
    perfUserScnDisableAll = reinterpret_cast<user_disable_all>(func);

    if (perfUserScnDisableAll == NULL) {
        ALOGE("perfUserScnDisableAll error: %s", dlerror());
        perfUserScnDisableAll = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfUserScnRestoreAll");
    perfUserScnRestoreAll = reinterpret_cast<user_restore_all>(func);

    if (perfUserScnRestoreAll == NULL) {
        ALOGE("perfUserScnRestoreAll error: %s", dlerror());
        perfUserScnRestoreAll = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfDumpAll");
    perfDumpAll = reinterpret_cast<dump_all>(func);

    if (perfDumpAll == NULL) {
        ALOGE("perfDumpAll error: %s", dlerror());
        perfDumpAll = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfSetFavorPid");
    perfSetFavorPid = reinterpret_cast<set_favor_pid>(func);

    if (perfSetFavorPid == NULL) {
        ALOGE("perfSetFavorPid error: %s", dlerror());
        perfSetFavorPid = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfRestorePolicy");
    perfRestorePolicy = reinterpret_cast<restore_policy>(func);

    if (perfRestorePolicy == NULL) {
        ALOGE("perfRestorePolicy error: %s", dlerror());
        perfRestorePolicy = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfNotifyDisplayType");
    perfNotifyDisplayType = reinterpret_cast<notify_display_type>(func);

    if (perfNotifyDisplayType == NULL) {
        ALOGE("perfNotifyDisplayType error: %s", dlerror());
        perfNotifyDisplayType = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfGetLastBoostPid");
    perfGetLastBoostPid= reinterpret_cast<get_last_boost_pid>(func);

    if (perfGetLastBoostPid== NULL) {
        ALOGE("perfGetLastBoostPid error: %s", dlerror());
        perfGetLastBoostPid= NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfNotifyUserStatus");
    perfNotifyUserStatus = reinterpret_cast<notify_user_status>(func);

    if (perfNotifyUserStatus == NULL) {
        ALOGE("perfNotifyUserStatus error: %s", dlerror());
        perfNotifyUserStatus = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfSetPackAttr");
    perfSetPackAttr = reinterpret_cast<set_pack_attr>(func);

    if (perfSetPackAttr == NULL) {
        ALOGE("perfSetPackAttr error: %s", dlerror());
        perfSetPackAttr = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfSetUeventIndex");
    perfSetUeventIndex = reinterpret_cast<set_uevent_index>(func);

    if (perfSetUeventIndex == NULL) {
        ALOGE("perfSetUeventIndex error: %s", dlerror());
        perfSetUeventIndex = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfLevelBoost");
    perfLevelBoost = reinterpret_cast<level_boost>(func);

    if (perfLevelBoost == NULL) {
        ALOGE("perfLevelBoost error: %s", dlerror());
        perfLevelBoost = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfGetClusterInfo");
    perfGetClusterInfo = reinterpret_cast<get_cluster_info>(func);

    if (perfGetClusterInfo == NULL) {
        ALOGE("perfGetClusterInfo error: %s", dlerror());
        perfGetClusterInfo = NULL;
        dlclose(handle);
        return;
    }

    func = dlsym(handle, "perfGetPackAttr");
    perfGetPackAttr = reinterpret_cast<get_pack_attr>(func);

    if (perfGetPackAttr == NULL) {
        ALOGE("perfGetPackAttr error: %s", dlerror());
        perfGetPackAttr = NULL;
        dlclose(handle);
        return;
    }
}
#endif

static int
android_server_PerfBoostEnable(JNIEnv *env, jobject thiz,
                                        jint scenario)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfBoostEnable)
        return perfBoostEnable(scenario);

    ALOGE("perfBoostEnable bypassed!");
#endif
    return -1;
}

static int
android_server_PerfBoostDisable(JNIEnv *env, jobject thiz,
                                        jint scenario)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfBoostDisable)
        return perfBoostDisable(scenario);

    ALOGE("perfBoostDisable bypassed!");
#endif
    return -1;
}

static int
android_server_PerfNotifyAppState(JNIEnv *env, jobject thiz, jstring packName, jstring className,
                                          jint state, jint pid)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfNotifyAppState) {
        const char *nativeApp = (packName) ? env->GetStringUTFChars(packName, 0) : NULL;
        const char *nativeCom = (className) ? env->GetStringUTFChars(className, 0) : NULL;

        if(nativeApp != NULL) {
            //ALOGI("android_server_PerfNotifyAppState: %s %s %d", nativeApp, nativeCom, state);
            perfNotifyAppState(nativeApp, nativeCom, state, pid);
            if(nativeCom != NULL)
                env->ReleaseStringUTFChars(className, nativeCom);
            env->ReleaseStringUTFChars(packName, nativeApp);
            return 0;
        }
        else
            return -1;
    }

    ALOGE("perfNotifyAppState bypassed!");
#endif
    return -1;
}

static int
android_server_PerfUserScnReg(JNIEnv *env, jobject thiz,
                                        jint scn_core, jint scn_freq, jint pid, jint tid)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfUserScnReg) {
        return perfUserScnReg(scn_core, scn_freq, pid, tid);
    }

    ALOGE("perfUserScnReg bypassed!");
#endif
    return -1;
}

static int
android_server_PerfUserScnRegBigLittle(JNIEnv *env, jobject thiz,
                                        jint scn_core_little, jint scn_freq_little, jint scn_core_big, jint scn_freq_big, jint pid, jint tid)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfUserScnRegBigLittle)
        return perfUserScnRegBigLittle(scn_core_little, scn_freq_little, scn_core_big, scn_freq_big, pid, tid);

    ALOGE("perfUserScnRegBigLittle bypassed!");
#endif
    return -1;
}

static int
android_server_PerfUserScnUnreg(JNIEnv *env, jobject thiz,
                                        jint handle)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfUserScnUnreg)
        return perfUserScnUnreg(handle);

    ALOGE("perfUserScnUnreg bypassed!");
#endif
    return -1;
}

static int
android_server_PerfUserGetCapability(JNIEnv *env, jobject thiz, jint cmd)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfUserGetCapability)
        return perfUserGetCapability(cmd);

    ALOGE("perfUserGetCapability bypassed!");
#endif
    return -1;
}

static int
android_server_PerfUserRegScn(JNIEnv *env, jobject thiz, jint pid, jint tid)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfUserRegScn)
        return perfUserRegScn(pid, tid);

    ALOGE("perfUserRegScn bypassed!");
#endif
    return -1;
}

static int
android_server_PerfUserRegScnConfig(JNIEnv *env, jobject thiz,
                                     jint handle, jint cmd, jint param_1, jint param_2, jint param_3, jint param_4)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfUserRegScnConfig)
        return perfUserRegScnConfig(handle, cmd, param_1, param_2, param_3, param_4);

    ALOGE("perfUserRegScnConfig bypassed!");
#endif
    return -1;
}

static int
android_server_PerfUserUnregScn(JNIEnv *env, jobject thiz, jint handle)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfUserUnregScn)
        return perfUserUnregScn(handle);

    ALOGE("perfUserUnregScn bypassed!");
#endif
    return -1;
}

static int
android_server_PerfUserScnEnable(JNIEnv *env, jobject thiz,
                                        jint handle)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfUserScnEnable)
        return perfUserScnEnable(handle);

    ALOGE("perfBoostEnable bypassed!");
#endif
    return -1;
}

static int
android_server_PerfUserScnDisable(JNIEnv *env, jobject thiz,
                                        jint handle)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfUserScnDisable)
        return perfUserScnDisable(handle);

    ALOGE("perfBoostDisable bypassed!");
#endif
    return -1;
}

static int
android_server_PerfUserScnResetAll(JNIEnv *env, jobject thiz)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfUserScnResetAll)
        return perfUserScnResetAll();

    ALOGE("perfUserScnResetAll bypassed!");
#endif
    return -1;
}

static int
android_server_PerfUserScnDisableAll(JNIEnv *env, jobject thiz)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfUserScnDisableAll)
        return perfUserScnDisableAll();

    ALOGE("perfUserScnDisableAll bypassed!");
#endif
    return -1;
}

static int
android_server_PerfUserScnRestoreAll(JNIEnv *env, jobject thiz)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfUserScnRestoreAll)
        return perfUserScnRestoreAll();

    ALOGE("perfUserScnRestoreAll bypassed!");
#endif
    return -1;
}

static int
android_server_PerfDumpAll(JNIEnv *env, jobject thiz)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfDumpAll)
        return perfDumpAll();

    ALOGE("perfDumpAll bypassed!");
#endif
    return -1;
}

static int
android_server_PerfSetFavorPid(JNIEnv *env, jobject thiz, jint pid)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfSetFavorPid)
        return perfSetFavorPid(pid);

    ALOGE("perfSetFavorPid bypassed!");
#endif
    return -1;
}

static int
android_server_PerfRestorePolicy(JNIEnv *env, jobject thiz, jint pid)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfRestorePolicy)
        return perfRestorePolicy(pid);

    ALOGE("perfRestorePolicy bypassed!");
#endif
    return -1;
}

static int
android_server_PerfNotifyDisplayType(JNIEnv *env, jobject thiz, jint type)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfNotifyDisplayType)
        return perfNotifyDisplayType(type);

    ALOGE("perfNotifyDisplayType bypassed!");
#endif
    return -1;
}

static int
android_server_PerfGetLastBoostPid(JNIEnv *env, jobject thiz)
{

#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfGetLastBoostPid){
            return perfGetLastBoostPid();
       }
    ALOGE("perfGetLastBoostPid bypassed!");
#endif
    //return "perf error";

    return -1;
}

static int
android_server_PerfNotifyUserStatus(JNIEnv *env, jobject thiz, jint type, jint status)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfNotifyUserStatus)
        return perfNotifyUserStatus(type, status);

    ALOGE("perfNotifyUserStatus bypassed!");
#endif
    return -1;
}

static int
android_server_PerfSetPackAttr(JNIEnv *env, jobject thiz, jint isSystem, jint eabiNum)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfSetPackAttr)
        return perfSetPackAttr(isSystem, eabiNum);

    ALOGE("perfSetPackAttr bypassed!");
#endif
    return -1;
}

static int
android_server_PerfSetUeventIndex(JNIEnv *env, jobject thiz, jint index)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfSetUeventIndex)
        return perfSetUeventIndex(index);

    ALOGE("perfSetPackAttr bypassed!");
#endif
    return -1;
}

static int
android_server_PerfLevelBoost(JNIEnv *env, jobject thiz, jint level)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfLevelBoost)
        return perfLevelBoost(level);

    ALOGE("perfLevelBoost bypassed!");
#endif
    return -1;
}

static int
android_server_PerfGetClusterInfo(JNIEnv *env, jobject thiz, jint cmd, jint id)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    if (!inited)
        init();

    if (perfGetClusterInfo)
        return perfGetClusterInfo(cmd, id);

    ALOGE("perfGetClusterInfo bypassed!");
#endif
    return -1;
}

static int
android_server_PerfGetPackAttr(JNIEnv *env, jobject thiz, jstring packName, jint cmd)
{
#if defined(MTK_PERFSERVICE_SUPPORT)
    int result = -1;

    if (!inited)
        init();

    if (perfGetPackAttr) {
        const char *nativeApp = (packName) ? env->GetStringUTFChars(packName, 0) : NULL;

        if(nativeApp != NULL) {
            //ALOGI("perfGetPackAttr: %s %d", nativeApp, cmd);
            result = perfGetPackAttr(nativeApp, cmd);
            env->ReleaseStringUTFChars(packName, nativeApp);
            return result;
        }
        else
            return -1;
    }

    ALOGE("perfGetPackTimeout bypassed!");
#endif
    return -1;
}

static JNINativeMethod sMethods[] = {
    {"nativePerfBoostEnable",    "(I)I",   (int *)android_server_PerfBoostEnable},
    {"nativePerfBoostDisable",   "(I)I",   (int *)android_server_PerfBoostDisable},
    {"nativePerfNotifyAppState", "(Ljava/lang/String;Ljava/lang/String;II)I", (int *)android_server_PerfNotifyAppState},
    {"nativePerfUserScnReg",          "(IIII)I",   (int *)android_server_PerfUserScnReg},
    {"nativePerfUserScnRegBigLittle", "(IIIIII)I", (int *)android_server_PerfUserScnRegBigLittle},
    {"nativePerfUserScnUnreg",        "(I)I",      (int *)android_server_PerfUserScnUnreg},
    {"nativePerfUserGetCapability", "(I)I",        (int *)android_server_PerfUserGetCapability},
    {"nativePerfUserRegScn", "(II)I",              (int *)android_server_PerfUserRegScn},
    {"nativePerfUserRegScnConfig", "(IIIIII)I",    (int *)android_server_PerfUserRegScnConfig},
    {"nativePerfUserUnregScn", "(I)I",             (int *)android_server_PerfUserUnregScn},
    {"nativePerfUserScnEnable",    "(I)I", (int *)android_server_PerfUserScnEnable},
    {"nativePerfUserScnDisable",   "(I)I", (int *)android_server_PerfUserScnDisable},
    {"nativePerfUserScnResetAll",  "()I",  (int *)android_server_PerfUserScnResetAll},
    {"nativePerfUserScnDisableAll","()I",  (int *)android_server_PerfUserScnDisableAll},
    {"nativePerfUserScnRestoreAll","()I",  (int *)android_server_PerfUserScnRestoreAll},
    {"nativePerfDumpAll","()I",       (int *)android_server_PerfDumpAll},
    {"nativePerfSetFavorPid","(I)I",  (int *)android_server_PerfSetFavorPid},
    {"nativePerfRestorePolicy","(I)I",(int *)android_server_PerfRestorePolicy},
    {"nativePerfNotifyDisplayType","(I)I",  (int *)android_server_PerfNotifyDisplayType},
    {"nativePerfGetLastBoostPid","()I",  (int * )android_server_PerfGetLastBoostPid},
    {"nativePerfNotifyUserStatus","(II)I",  (int *)android_server_PerfNotifyUserStatus},
    {"nativePerfSetPackAttr","(II)I",  (int *)android_server_PerfSetPackAttr},
    {"nativePerfSetUeventIndex","(I)I",  (int *)android_server_PerfSetUeventIndex},
    {"nativePerfLevelBoost", "(I)I",  (int *)android_server_PerfLevelBoost},
    {"nativePerfGetClusterInfo", "(II)I",   (int *)android_server_PerfGetClusterInfo},
    {"nativePerfGetPackAttr", "(Ljava/lang/String;I)I", (int *)android_server_PerfGetPackAttr},
};

int register_com_mediatek_perfservice_PerfServiceManager(JNIEnv* env)
{
    jclass clazz = env->FindClass("com/mediatek/perfservice/PerfServiceManager");

    if (clazz == NULL) {
        ALOGE("Can't find com/mediatek/perfservice/PerfServiceManager");
        return -1;
    }

    return android::AndroidRuntime::registerNativeMethods(env, "com/mediatek/perfservice/PerfServiceManager", sMethods, NELEM(sMethods));
}

}
