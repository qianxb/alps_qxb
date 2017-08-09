# This file is included by the top level services directory to collect source
# files
LOCAL_REL_DIR := core/jni

LOCAL_CFLAGS += -Wall -Werror -Wno-unused-parameter

ifneq ($(ENABLE_CPUSETS),)
ifneq ($(ENABLE_SCHED_BOOST),)
LOCAL_CFLAGS += -DUSE_SCHED_BOOST
endif
endif

LOCAL_SRC_FILES += \
    $(LOCAL_REL_DIR)/com_android_server_AlarmManagerService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_am_BatteryStatsService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_am_ActivityManagerService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_AssetAtlasService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_connectivity_Vpn.cpp \
    $(LOCAL_REL_DIR)/com_android_server_ConsumerIrService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_HardwarePropertiesManagerService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_hdmi_HdmiCecController.cpp \
    $(LOCAL_REL_DIR)/com_android_server_input_InputApplicationHandle.cpp \
    $(LOCAL_REL_DIR)/com_android_server_input_InputManagerService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_input_InputWindowHandle.cpp \
    $(LOCAL_REL_DIR)/com_android_server_lights_LightsService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_location_GnssLocationProvider.cpp \
    $(LOCAL_REL_DIR)/com_android_server_location_FlpHardwareProvider.cpp \
    $(LOCAL_REL_DIR)/com_android_server_power_PowerManagerService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_SerialService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_SystemServer.cpp \
    $(LOCAL_REL_DIR)/com_android_server_tv_TvUinputBridge.cpp \
    $(LOCAL_REL_DIR)/com_android_server_tv_TvInputHal.cpp \
    $(LOCAL_REL_DIR)/com_android_server_vr_VrManagerService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_UsbDeviceManager.cpp \
    $(LOCAL_REL_DIR)/com_android_server_UsbMidiDevice.cpp \
    $(LOCAL_REL_DIR)/com_android_server_UsbHostManager.cpp \
    $(LOCAL_REL_DIR)/com_android_server_VibratorService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_PersistentDataBlockService.cpp \
    $(LOCAL_REL_DIR)/com_mediatek_perfservice_PerfServiceManager.cpp \
    $(LOCAL_REL_DIR)/com_mediatek_hdmi_MtkHdmiManagerService.cpp \
    $(LOCAL_REL_DIR)/com_android_server_display_DisplayPowerController.cpp \
    $(LOCAL_REL_DIR)/onload.cpp

ifneq (yes,$(MTK_BSP_PACKAGE))
LOCAL_SRC_FILES += \
	$(LOCAL_REL_DIR)/com_android_internal_app_ShutdownManager.cpp
endif
LOCAL_C_INCLUDES += \
    $(JNI_H_INCLUDE) \
    frameworks/base/services \
    frameworks/base/libs \
    frameworks/base/libs/hwui \
    frameworks/base/core/jni \
    frameworks/native/services \
    libcore/include \
    libcore/include/libsuspend \
    system/security/keystore/include \
    $(call include-path-for, libhardware)/hardware \
    $(call include-path-for, libhardware_legacy)/hardware_legacy \

LOCAL_SHARED_LIBRARIES += \
    libandroid_runtime \
    libandroidfw \
    libbinder \
    libcutils \
    liblog \
    libhardware \
    libhardware_legacy \
    libkeystore_binder \
    libnativehelper \
    libutils \
    libui \
    libinput \
    libinputflinger \
    libinputservice \
    libsensorservice \
    libskia \
    libgui \
    libusbhost \
    libsuspend \
    libdl \
    libEGL \
    libGLESv2 \
    libnetutils \
	libmedia

ifeq ($(strip $(BOARD_USES_MTK_AUDIO)),true)
  LOCAL_CFLAGS += -DMTK_VIBSPK_OPTION_SUPPORT
  
   LOCAL_SHARED_LIBRARIES += \
        libmtkplayer
   LOCAL_C_INCLUDES += $(TOP)/$(MTK_PATH_SOURCE)/frameworks/av/media/libs
endif

ifeq ($(MTK_VIBSPK_SUPPORT),yes)
  LOCAL_CFLAGS += -DMTK_VIBSPK_SUPPORT
endif

ifeq ($(MTK_PERFSERVICE_SUPPORT),yes)
  LOCAL_CFLAGS += -DMTK_PERFSERVICE_SUPPORT
endif

# Add for MTK HDMI
ifeq ($(strip $(MTK_HDMI_SUPPORT)), yes)
  LOCAL_CFLAGS += -DMTK_HDMI_SUPPORT
endif
ifeq ($(strip $(MTK_HDMI_HDCP_SUPPORT)), yes)
  LOCAL_CFLAGS += -DMTK_HDMI_HDCP_SUPPORT
endif

ifeq ($(MTK_DRM_KEY_MNG_SUPPORT), yes)
LOCAL_CFLAGS += -DMTK_DRM_KEY_MNG_SUPPORT
LOCAL_SHARED_LIBRARIES += libcutils libnetutils libc
ifeq ($(strip $(MTK_IN_HOUSE_TEE_SUPPORT)),yes)
LOCAL_SHARED_LIBRARIES += liburee_meta_drmkeyinstall_v2
else
LOCAL_SHARED_LIBRARIES += liburee_meta_drmkeyinstall
endif
endif

ifeq ($(strip $(MTK_IN_HOUSE_TEE_SUPPORT)),yes)
ifeq ($(MTK_DRM_KEY_MNG_SUPPORT), yes)
LOCAL_SHARED_LIBRARIES += libtz_uree
endif
endif

ifeq ($(MTK_DRM_KEY_MNG_SUPPORT), yes)
ifeq ($(strip $(MTK_IN_HOUSE_TEE_SUPPORT)),yes)
LOCAL_C_INCLUDES += $(TOPDIR)vendor/mediatek/proprietary/external/trustzone/mtee/include/tz_cross/
else
LOCAL_C_INCLUDES += $(TOPDIR)vendor/mediatek/proprietary/trustzone/trustonic/source/trustlets/keyinstall/common/TlcKeyInstall/public/
endif
endif
# Add for MTK HDMI end

ifeq ($(strip $(MTK_AAL_SUPPORT)),yes)
    LOCAL_C_INCLUDES += \
        $(MTK_PATH_SOURCE)/hardware/aal/include

    LOCAL_SHARED_LIBRARIES += \
        libaal

    LOCAL_CFLAGS += -DMTK_AAL_SUPPORT
endif

ifeq ($(strip $(MTK_SENSOR_HUB_SUPPORT)),yes)
    LOCAL_C_INCLUDES += \
        $(MTK_PATH_SOURCE)/frameworks/native/services \
        $(MTK_PATH_SOURCE)/frameworks/native/libs/sensorhub/include  \
        $(MTK_PATH_SOURCE)/hardware/sensorhub

    LOCAL_SHARED_LIBRARIES += \
        libsensorhubservice

    LOCAL_CFLAGS += -DMTK_SENSOR_HUB_SUPPORT
endif

LOCAL_C_INCLUDES += $(MTK_PATH_SOURCE)/hardware/connectivity/gps/gps_hal/inc


