/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "EglManager.h"

#include "Caches.h"
#include "DeviceInfo.h"
#include "Properties.h"
#include "RenderThread.h"
#include "renderstate/RenderState.h"
#include "utils/StringUtils.h"
#include <cutils/log.h>
#include <cutils/properties.h>
#include <EGL/eglext.h>
#include <string>

#define GLES_VERSION 2

// Android-specific addition that is used to show when frames began in systrace
EGLAPI void EGLAPIENTRY eglBeginFrame(EGLDisplay dpy, EGLSurface surface);

namespace android {
namespace uirenderer {
namespace renderthread {

#define ERROR_CASE(x) case x: return #x;
static const char* egl_error_str(EGLint error) {
    switch (error) {
        ERROR_CASE(EGL_SUCCESS)
        ERROR_CASE(EGL_NOT_INITIALIZED)
        ERROR_CASE(EGL_BAD_ACCESS)
        ERROR_CASE(EGL_BAD_ALLOC)
        ERROR_CASE(EGL_BAD_ATTRIBUTE)
        ERROR_CASE(EGL_BAD_CONFIG)
        ERROR_CASE(EGL_BAD_CONTEXT)
        ERROR_CASE(EGL_BAD_CURRENT_SURFACE)
        ERROR_CASE(EGL_BAD_DISPLAY)
        ERROR_CASE(EGL_BAD_MATCH)
        ERROR_CASE(EGL_BAD_NATIVE_PIXMAP)
        ERROR_CASE(EGL_BAD_NATIVE_WINDOW)
        ERROR_CASE(EGL_BAD_PARAMETER)
        ERROR_CASE(EGL_BAD_SURFACE)
        ERROR_CASE(EGL_CONTEXT_LOST)
    default:
        return "Unknown error";
    }
}
static const char* egl_error_str() {
    return egl_error_str(eglGetError());
}

static struct {
    bool bufferAge = false;
    bool setDamage = false;
} EglExtensions;

void Frame::map(const SkRect& in, EGLint* out) const {
    /* The rectangles are specified relative to the bottom-left of the surface
     * and the x and y components of each rectangle specify the bottom-left
     * position of that rectangle.
     *
     * HWUI does everything with 0,0 being top-left, so need to map
     * the rect
     */
    SkIRect idirty;
    in.roundOut(&idirty);
    EGLint y = mHeight - (idirty.y() + idirty.height());
    // layout: {x, y, width, height}
    out[0] = idirty.x();
    out[1] = y;
    out[2] = idirty.width();
    out[3] = idirty.height();
}

EglManager::EglManager(RenderThread& thread)
        : mRenderThread(thread)
        , mEglDisplay(EGL_NO_DISPLAY)
        , mEglConfig(nullptr)
        , mEglContext(EGL_NO_CONTEXT)
        , mPBufferSurface(EGL_NO_SURFACE)
        , mCurrentSurface(EGL_NO_SURFACE)
        , mAtlasMap(nullptr)
        , mAtlasMapSize(0) {
}

void EglManager::initialize() {
    if (hasEglContext()) return;

    ATRACE_NAME("Creating EGLContext");

    TIME_LOG("eglGetDisplay", mEglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY));
    LOG_ALWAYS_FATAL_IF(mEglDisplay == EGL_NO_DISPLAY,
            "Failed to get EGL_DEFAULT_DISPLAY! err=%s", egl_error_str());

    EGLint major, minor;
    EGLBoolean success;
    TIME_LOG("eglInitialize", success = eglInitialize(mEglDisplay, &major, &minor));
    LOG_ALWAYS_FATAL_IF(success == EGL_FALSE,
            "Failed to initialize display %p! err=%s", mEglDisplay, egl_error_str());

    ALOGI("Initialized EGL, version %d.%d", (int)major, (int)minor);

    initExtensions();

    // Now that extensions are loaded, pick a swap behavior
    if (Properties::enablePartialUpdates) {
        if (Properties::useBufferAge && EglExtensions.bufferAge) {
            mSwapBehavior = SwapBehavior::BufferAge;
        } else {
            mSwapBehavior = SwapBehavior::Preserved;
        }
    }

    loadConfig();
    createContext();
    createPBufferSurface();
    makeCurrent(mPBufferSurface);
    DeviceInfo::initialize();
    mRenderThread.renderState().onGLContextCreated();
    initAtlas();
}

void EglManager::initExtensions() {
    auto extensions = StringUtils::split(
            eglQueryString(mEglDisplay, EGL_EXTENSIONS));
    EglExtensions.bufferAge = extensions.has("EGL_EXT_buffer_age");
    EglExtensions.setDamage = extensions.has("EGL_KHR_partial_update");
    LOG_ALWAYS_FATAL_IF(!extensions.has("EGL_KHR_swap_buffers_with_damage"),
            "Missing required extension EGL_KHR_swap_buffers_with_damage");
}

bool EglManager::hasEglContext() {
    return mEglDisplay != EGL_NO_DISPLAY;
}

void EglManager::loadConfig() {
    ALOGD("Swap behavior %d", static_cast<int>(mSwapBehavior));
    EGLint swapBehavior = (mSwapBehavior == SwapBehavior::Preserved)
            ? EGL_SWAP_BEHAVIOR_PRESERVED_BIT : 0;
    EGLint attribs[] = {
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 0,
            EGL_CONFIG_CAVEAT, EGL_NONE,
            EGL_STENCIL_SIZE, Stencil::getStencilSize(),
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT | swapBehavior,
            EGL_NONE
    };

    EGLint num_configs = 1;
    EGLBoolean success;
    TIME_LOG("eglChooseConfig", success =
        eglChooseConfig(mEglDisplay, attribs, &mEglConfig, num_configs, &num_configs));
    if (!success || num_configs != 1) {
        if (mSwapBehavior == SwapBehavior::Preserved) {
            // Try again without dirty regions enabled
            ALOGW("Failed to choose config with EGL_SWAP_BEHAVIOR_PRESERVED, retrying without...");
            mSwapBehavior = SwapBehavior::Discard;
            loadConfig();
        } else {
            // Failed to get a valid config
            LOG_ALWAYS_FATAL("Failed to choose config, error = %s", egl_error_str());
        }
    }
}

void EglManager::createContext() {
    EGLint attribs[] = {
            EGL_CONTEXT_CLIENT_VERSION, GLES_VERSION,
            EGL_NONE
    };
    TIME_LOG("eglCreateContext", mEglContext =
        eglCreateContext(mEglDisplay, mEglConfig, EGL_NO_CONTEXT, attribs));
    LOG_ALWAYS_FATAL_IF(mEglContext == EGL_NO_CONTEXT,
        "Failed to create context, error = %s", egl_error_str());
    ALOGD("Created EGL context (%p)", mEglContext);
}

void EglManager::setTextureAtlas(const sp<GraphicBuffer>& buffer,
        int64_t* map, size_t mapSize) {

    // Already initialized
    if (mAtlasBuffer.get()) {
        ALOGW("Multiple calls to setTextureAtlas!");
        delete map;
        return;
    }

    mAtlasBuffer = buffer;
    mAtlasMap = map;
    mAtlasMapSize = mapSize;

    if (hasEglContext()) {
        initAtlas();
    }
}

void EglManager::initAtlas() {
    if (mAtlasBuffer.get()) {
        mRenderThread.renderState().assetAtlas().init(mAtlasBuffer,
                mAtlasMap, mAtlasMapSize);
    }
}

void EglManager::createPBufferSurface() {
    LOG_ALWAYS_FATAL_IF(mEglDisplay == EGL_NO_DISPLAY,
            "usePBufferSurface() called on uninitialized GlobalContext!");

    if (mPBufferSurface == EGL_NO_SURFACE) {
        EGLint attribs[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };

        TIME_LOG("eglCreatePbufferSurface", mPBufferSurface =
            eglCreatePbufferSurface(mEglDisplay, mEglConfig, attribs));
    }
}

EGLSurface EglManager::createSurface(EGLNativeWindowType window) {
    initialize();
    EGLSurface surface;
    TIME_LOG("eglCreateWindowSurface",
        surface = eglCreateWindowSurface(mEglDisplay, mEglConfig, window, nullptr));
    LOG_ALWAYS_FATAL_IF(surface == EGL_NO_SURFACE,
            "Failed to create EGLSurface for window %p, eglErr = %s",
            (void*) window, egl_error_str());

    if (mSwapBehavior != SwapBehavior::Preserved) {
        EGLBoolean success;
        TIME_LOG("eglSurfaceAttrib", success =
            eglSurfaceAttrib(mEglDisplay, surface, EGL_SWAP_BEHAVIOR, EGL_BUFFER_DESTROYED));
        LOG_ALWAYS_FATAL_IF(success == EGL_FALSE,
                            "Failed to set swap behavior to destroyed for window %p, eglErr = %s",
                            (void*) window, egl_error_str());
    }

    return surface;
}

void EglManager::destroySurface(EGLSurface surface) {
    if (isCurrent(surface)) {
        makeCurrent(EGL_NO_SURFACE);
    }
    EGLBoolean success;
    TIME_LOG("eglDestroySurface", success = eglDestroySurface(mEglDisplay, surface));
    if (!success) {
        ALOGW("Failed to destroy surface %p, error=%s", (void*)surface, egl_error_str());
    }
}

void EglManager::destroy() {
    if (mEglDisplay == EGL_NO_DISPLAY) return;

    mRenderThread.renderState().onGLContextDestroyed();
    TIME_LOG("eglDestroyContext", eglDestroyContext(mEglDisplay, mEglContext));
    ALOGD("Destroyed EGL context (%p)", mEglContext);
    TIME_LOG("eglDestroySurface", eglDestroySurface(mEglDisplay, mPBufferSurface));
    TIME_LOG("eglMakeCurrent", eglMakeCurrent(mEglDisplay,
        EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT));
    TIME_LOG("eglTerminate", eglTerminate(mEglDisplay));
    ALOGD("Terminated EGL display (%p)", mEglDisplay);
    TIME_LOG("eglReleaseThread", eglReleaseThread());

    mEglDisplay = EGL_NO_DISPLAY;
    mEglContext = EGL_NO_CONTEXT;
    mPBufferSurface = EGL_NO_SURFACE;
    mCurrentSurface = EGL_NO_SURFACE;
}

bool EglManager::makeCurrent(EGLSurface surface, EGLint* errOut) {
    if (isCurrent(surface)) return false;

    if (surface == EGL_NO_SURFACE) {
        // Ensure we always have a valid surface & context
        surface = mPBufferSurface;
    }

    EGLBoolean success;
    TIME_LOG("eglMakeCurrent", success =
        eglMakeCurrent(mEglDisplay, surface, surface, mEglContext));
    if (!success) {
        if (errOut) {
            *errOut = eglGetError();
            ALOGW("Failed to make current on surface %p, error=%s",
                    (void*)surface, egl_error_str(*errOut));
        } else {
            LOG_ALWAYS_FATAL("Failed to make current on surface %p, error=%s",
                    (void*)surface, egl_error_str());
        }
    }
    mCurrentSurface = surface;
    return true;
}

EGLint EglManager::queryBufferAge(EGLSurface surface) {
    switch (mSwapBehavior) {
    case SwapBehavior::Discard:
        return 0;
    case SwapBehavior::Preserved:
        return 1;
    case SwapBehavior::BufferAge:
        EGLint bufferAge;
        TIME_LOG("eglQuerySurface",
            eglQuerySurface(mEglDisplay, surface, EGL_BUFFER_AGE_EXT, &bufferAge));
        return bufferAge;
    }
    return 0;
}

Frame EglManager::beginFrame(EGLSurface surface) {
    LOG_ALWAYS_FATAL_IF(surface == EGL_NO_SURFACE,
            "Tried to beginFrame on EGL_NO_SURFACE!");
    makeCurrent(surface);
    Frame frame;
    frame.mSurface = surface;
    TIME_LOG("eglQuerySurface", eglQuerySurface(mEglDisplay, surface, EGL_WIDTH, &frame.mWidth));
    TIME_LOG("eglQuerySurface", eglQuerySurface(mEglDisplay, surface, EGL_HEIGHT, &frame.mHeight));
    frame.mBufferAge = queryBufferAge(surface);
    TIME_LOG_BASIC("eglBeginFrame", eglBeginFrame(mEglDisplay, surface));
    return frame;
}

void EglManager::damageFrame(const Frame& frame, const SkRect& dirty) {
#ifdef EGL_KHR_partial_update
    if (EglExtensions.setDamage && mSwapBehavior == SwapBehavior::BufferAge) {
        EGLint rects[4];
        frame.map(dirty, rects);
        EGLBoolean success;
        TIME_LOG("eglSetDamageRegionKHR", success =
            eglSetDamageRegionKHR(mEglDisplay, frame.mSurface, rects, 1));
        if (!success) {
            LOG_ALWAYS_FATAL("Failed to set damage region on surface %p, error=%s",
                    (void*)frame.mSurface, egl_error_str());
        }
    }
#endif
}

bool EglManager::damageRequiresSwap() {
    return EglExtensions.setDamage && mSwapBehavior == SwapBehavior::BufferAge;
}

bool EglManager::swapBuffers(const Frame& frame, const SkRect& screenDirty) {

    if (CC_UNLIKELY(Properties::waitForGpuCompletion)) {
        ATRACE_NAME("Finishing GPU work");
        fence();
    }

    EGLint rects[4];
    frame.map(screenDirty, rects);
    TIME_LOG_BASIC("eglSwapBuffersWithDamageKHR", eglSwapBuffersWithDamageKHR(mEglDisplay,
                frame.mSurface, rects, screenDirty.isEmpty() ? 0 : 1));

    EGLint err = eglGetError();
    if (CC_LIKELY(err == EGL_SUCCESS)) {
        return true;
    }
    if (err == EGL_BAD_SURFACE || err == EGL_BAD_NATIVE_WINDOW) {
        // For some reason our surface was destroyed out from under us
        // This really shouldn't happen, but if it does we can recover easily
        // by just not trying to use the surface anymore
        ALOGW("swapBuffers encountered EGL error %d on %p, halting rendering...",
                err, frame.mSurface);
        return false;
    }
    if (err == EGL_BAD_ALLOC) {
        /// M: 3d driver reaches an unstable state, do not render anymore if it happened.
        ALOGE("swapBuffers encountered EGL_BAD_ALLOC on %p, halting rendering...", frame.mSurface);
        return false;
    }
    LOG_ALWAYS_FATAL("Encountered EGL error %d %s during rendering",
            err, egl_error_str(err));
    // Impossible to hit this, but the compiler doesn't know that
    return false;
}

void EglManager::fence() {
    EGLSyncKHR fence;
    TIME_LOG("eglCreateSyncKHR", fence = eglCreateSyncKHR(mEglDisplay, EGL_SYNC_FENCE_KHR, NULL));
    TIME_LOG("eglClientWaitSyncKHR", eglClientWaitSyncKHR(mEglDisplay, fence,
            EGL_SYNC_FLUSH_COMMANDS_BIT_KHR, EGL_FOREVER_KHR));
    TIME_LOG("eglDestroySyncKHR", eglDestroySyncKHR(mEglDisplay, fence));
}

bool EglManager::setPreserveBuffer(EGLSurface surface, bool preserve) {
    if (mSwapBehavior != SwapBehavior::Preserved) return false;

    bool preserved;
    TIME_LOG("eglSurfaceAttrib", preserved =
        eglSurfaceAttrib(mEglDisplay, surface, EGL_SWAP_BEHAVIOR,
            preserve ? EGL_BUFFER_PRESERVED : EGL_BUFFER_DESTROYED));
    if (!preserved) {
        ALOGW("Failed to set EGL_SWAP_BEHAVIOR on surface %p, error=%s",
                (void*) surface, egl_error_str());
        // Maybe it's already set?
        EGLint swapBehavior;
        EGLBoolean success;
        TIME_LOG("eglQuerySurface", success =
            eglQuerySurface(mEglDisplay, surface, EGL_SWAP_BEHAVIOR, &swapBehavior));
        if (success) {
            preserved = (swapBehavior == EGL_BUFFER_PRESERVED);
        } else {
            ALOGW("Failed to query EGL_SWAP_BEHAVIOR on surface %p, error=%p",
                                (void*) surface, egl_error_str());
        }
    }

    return preserved;
}

} /* namespace renderthread */
} /* namespace uirenderer */
} /* namespace android */
