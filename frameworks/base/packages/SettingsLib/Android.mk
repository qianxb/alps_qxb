LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_USE_AAPT2 := true

LOCAL_MODULE := SettingsLib

LOCAL_SHARED_ANDROID_LIBRARIES := \
    android-support-v4 \
    android-support-v7-recyclerview \
    android-support-v7-preference \
    android-support-v7-appcompat \
    android-support-v14-preference

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

# M: Add for MTK resource
LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res_ext

LOCAL_JAVA_LIBRARIES := ims-common \
                        mediatek-framework \

LOCAL_STATIC_JAVA_LIBRARIES += com.mediatek.settingslib.ext

LOCAL_JAR_EXCLUDE_FILES := none

LOCAL_SRC_FILES := $(call all-java-files-under, src)

include $(BUILD_STATIC_JAVA_LIBRARY)

# This finds and builds ext as well.
include $(call all-makefiles-under,$(LOCAL_PATH))
