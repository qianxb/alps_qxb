LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    android_media_AmrInputStream.cpp \
    android_media_ExifInterface.cpp \
    android_media_ImageWriter.cpp \
    android_media_ImageReader.cpp \
    android_media_MediaCrypto.cpp \
    android_media_MediaCodec.cpp \
    android_media_MediaCodecList.cpp \
    android_media_MediaDataSource.cpp \
    android_media_MediaDrm.cpp \
    android_media_MediaExtractor.cpp \
    android_media_MediaHTTPConnection.cpp \
    android_media_MediaMetadataRetriever.cpp \
    android_media_MediaMuxer.cpp \
    android_media_MediaPlayer.cpp \
    android_media_MediaProfiles.cpp \
    android_media_MediaRecorder.cpp \
    android_media_MediaScanner.cpp \
    android_media_MediaSync.cpp \
    android_media_ResampleInputStream.cpp \
    android_media_SyncParams.cpp \
    android_media_Utils.cpp \
    android_mtp_MtpDatabase.cpp \
    android_mtp_MtpDevice.cpp \
    android_mtp_MtpServer.cpp \

LOCAL_SHARED_LIBRARIES := \
    libandroid_runtime \
    libnativehelper \
    libutils \
    libbinder \
    libmedia \
    libmediadrm \
    libskia \
    libui \
    liblog \
    libcutils \
    libgui \
    libstagefright \
    libstagefright_foundation \
    libcamera_client \
    libmtp \
    libusbhost \
    libexif \
    libpiex \
    libstagefright_amrnb_common

LOCAL_STATIC_LIBRARIES := \
    libstagefright_amrnbenc

LOCAL_C_INCLUDES += \
    external/libexif/ \
    external/piex/ \
    external/tremor/Tremor \
    frameworks/base/core/jni \
    frameworks/base/libs/hwui \
    frameworks/av/media/libmedia \
    frameworks/av/media/libstagefright \
    frameworks/av/media/libstagefright/codecs/amrnb/enc/src \
    frameworks/av/media/libstagefright/codecs/amrnb/common \
    frameworks/av/media/libstagefright/codecs/amrnb/common/include \
    $(TOP)/mediatek/external/amr \
    frameworks/av/media/mtp \
    frameworks/native/include/media/openmax \
    $(call include-path-for, libhardware)/hardware \
    $(PV_INCLUDES) \
    $(JNI_H_INCLUDE)

LOCAL_CFLAGS += -Wall -Werror -Wno-error=deprecated-declarations -Wunused -Wunreachable-code
LOCAL_C_INCLUDES += $(TOP)/$(MTK_ROOT)/frameworks-ext/native/include

ifeq ($(strip $(MTK_TB_DEBUG_SUPPORT)),yes)
LOCAL_C_INCLUDES += \
    $(MTK_PATH_SOURCE)/frameworks/base/include
endif

ifeq ($(strip $(MTK_HIGH_QUALITY_THUMBNAIL)),yes)
LOCAL_CFLAGS += -DMTK_HIGH_QUALITY_THUMBNAIL
endif

ifeq ($(strip $(MTK_USE_ANDROID_MM_DEFAULT_CODE)),yes)
LOCAL_CFLAGS += -DANDROID_DEFAULT_CODE
endif

ifeq ($(HAVE_CMMB_FEATURE),yes)
LOCAL_CFLAGS += -DMTK_CMMB_ENABLE
endif

ifneq ($(MTK_BSP_PACKAGE), yes)
ifneq ($(MTK_BASIC_PACKAGE), yes)
    LOCAL_CFLAGS += -DMTK_AOSP_ENHANCEMENT
    LOCAL_CPPFLAGS += -DMTK_AOSP_ENHANCEMENT
endif
endif

ifeq ($(MTK_AUDIO),yes)
LOCAL_CFLAGS += -DMTK_AUDIO
LOCAL_C_INCLUDES+= \
   $(TOP)/$(MTK_PATH_SOURCE)/hardware/audio/common/include
endif

LOCAL_CFLAGS +=

LOCAL_MODULE:= libmedia_jni
#LOCAL_MULTILIB := 32

include $(BUILD_SHARED_LIBRARY)

# build libsoundpool.so
# build libaudioeffect_jni.so
include $(call all-makefiles-under,$(LOCAL_PATH))
