/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2009 The Android Open Source Project
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

#define LOG_TAG "VibratorService"

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"
#if defined(MTK_VIBSPK_OPTION_SUPPORT)
#include "VibSpkAudioPlayer.h"
#include <cutils/properties.h>
#endif

#include <utils/misc.h>
#include <utils/Log.h>
#include <hardware/vibrator.h>

#include <stdio.h>

namespace android
{

static hw_module_t *gVibraModule = NULL;
static vibrator_device_t *gVibraDevice = NULL;

#if defined(MTK_VIBSPK_OPTION_SUPPORT)
const char PROPERTY_KEY_VIBSPK_ON_JNI[PROPERTY_KEY_MAX] = "persist.af.feature.vibspk";
static bool IsJNISupportVibSpk(void)
{
    bool bSupportFlg = false;
    char stForFeatureUsage[PROPERTY_VALUE_MAX];

#if defined(MTK_VIBSPK_SUPPORT)
    property_get(PROPERTY_KEY_VIBSPK_ON_JNI, stForFeatureUsage, "1"); //"1": default on
#else
    property_get(PROPERTY_KEY_VIBSPK_ON_JNI, stForFeatureUsage, "0"); //"0": default off
#endif
    bSupportFlg = (stForFeatureUsage[0] == '0') ? false : true;

    return bSupportFlg;
}
#endif

static void vibratorInit(JNIEnv /* env */, jobject /* clazz */)
{
    if (gVibraModule != NULL) {
        return;
    }

    int err = hw_get_module(VIBRATOR_HARDWARE_MODULE_ID, (hw_module_t const**)&gVibraModule);

    if (err) {
        ALOGE("Couldn't load %s module (%s)", VIBRATOR_HARDWARE_MODULE_ID, strerror(-err));
    } else {
        if (gVibraModule) {
            vibrator_open(gVibraModule, &gVibraDevice);
        }
    }
}

static jboolean vibratorExists(JNIEnv* /* env */, jobject /* clazz */)
{
#if defined(MTK_VIBSPK_OPTION_SUPPORT)
    if(IsJNISupportVibSpk())
        return JNI_TRUE;
    else if (gVibraModule && gVibraDevice) {
        return JNI_TRUE;
    } else {
        return JNI_FALSE;
    }
#else
    if (gVibraModule && gVibraDevice) {
        return JNI_TRUE;
    } else {
        return JNI_FALSE;
    }
#endif
}

static void vibratorOn(JNIEnv* /* env */, jobject /* clazz */, jlong timeout_ms)
{
#if defined(MTK_VIBSPK_OPTION_SUPPORT)
    if(IsJNISupportVibSpk()) {
#if defined(MTK_AOSP_ENHANCEMENT)
        if(timeout_ms == 0)
            VIBRATOR_SPKOFF();
        else
            VIBRATOR_SPKON((unsigned int)timeout_ms);
#endif
    } else {
        if (gVibraDevice) {
            int err = gVibraDevice->vibrator_on(gVibraDevice, timeout_ms);
            if (err != 0) {
                ALOGE("The hw module failed in vibrator_on: %s", strerror(-err));
            }
        } else {
            ALOGW("Tried to vibrate but there is no vibrator device.");
        }
    }
#else
    if (gVibraDevice) {
        int err = gVibraDevice->vibrator_on(gVibraDevice, timeout_ms);
        if (err != 0) {
            ALOGE("The hw module failed in vibrator_on: %s", strerror(-err));
        }
    } else {
        ALOGW("Tried to vibrate but there is no vibrator device.");
    }
#endif
}

static void vibratorOff(JNIEnv* /* env */, jobject /* clazz */)
{
#if defined(MTK_VIBSPK_OPTION_SUPPORT)
    if(IsJNISupportVibSpk()){
#if defined(MTK_AOSP_ENHANCEMENT)
        VIBRATOR_SPKOFF();
#endif
    } else {
        if (gVibraDevice) {
            int err = gVibraDevice->vibrator_off(gVibraDevice);
            if (err != 0) {
                ALOGE("The hw module failed in vibrator_off(): %s", strerror(-err));
            }
        } else {
            ALOGW("Tried to stop vibrating but there is no vibrator device.");
        }
    }
#else
    if (gVibraDevice) {
        int err = gVibraDevice->vibrator_off(gVibraDevice);
        if (err != 0) {
            ALOGE("The hw module failed in vibrator_off(): %s", strerror(-err));
        }
    } else {
        ALOGW("Tried to stop vibrating but there is no vibrator device.");
    }
#endif
}

static const JNINativeMethod method_table[] = {
    { "vibratorExists", "()Z", (void*)vibratorExists },
    { "vibratorInit", "()V", (void*)vibratorInit },
    { "vibratorOn", "(J)V", (void*)vibratorOn },
    { "vibratorOff", "()V", (void*)vibratorOff }
};

int register_android_server_VibratorService(JNIEnv *env)
{
    return jniRegisterNativeMethods(env, "com/android/server/VibratorService",
            method_table, NELEM(method_table));
}

};
