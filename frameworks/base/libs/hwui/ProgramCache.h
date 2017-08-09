/*
 * Copyright (C) 2010 The Android Open Source Project
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

#ifndef ANDROID_HWUI_PROGRAM_CACHE_H
#define ANDROID_HWUI_PROGRAM_CACHE_H

#include <utils/KeyedVector.h>
#include <utils/Log.h>
#include <utils/String8.h>
#include <map>
/// M: [ProgramBinaryAtlas] For using program atlas.
#include "mediatek/MTKProgramAtlas.h"

#include <GLES2/gl2.h>

#include "Debug.h"
#include "Program.h"

namespace android {
namespace uirenderer {

///////////////////////////////////////////////////////////////////////////////
// Cache
///////////////////////////////////////////////////////////////////////////////

/**
 * Generates and caches program. Programs are generated based on
 * ProgramDescriptions.
 */
class ProgramCache {
public:
    ANDROID_API ProgramCache(Extensions& extensions);
    ANDROID_API ~ProgramCache();

    Program* get(const ProgramDescription& description);

    void clear();

    /**
     * M: [ProgramBinaryAtlas] Create program and its mapping table, return the total memory size
     * for caching programs binaries, and update the correct mapLength.
     *
     * The mapping will be ProgramKey, Offset, Length, "ProgramId", ProgramKey, Offset...
     */
    ANDROID_API int createPrograms(int64_t* map, int* mapLength);

    /**
     * M: [ProgramBinaryAtlas] Load program binaries to the buffer, delete programs, and update the map
     *
     * The mapping will be ProgramKey, Offset, Length, "Format", ProgramKey, Offset...
     */
    static ANDROID_API void loadProgramBinariesAndDelete(int64_t* map, int mapLength, void* buffer, int length);

    /**
     * M: Initialize program cache.
     */
    void init();

    /**
     * M: save program keys to disk, keys are used to preload programs when initializing
     */
    void saveToDisk();

private:
    Program* generateProgram(const ProgramDescription& description, programid key);
    String8 generateVertexShader(const ProgramDescription& description);
    String8 generateFragmentShader(const ProgramDescription& description);
    void generateBlend(String8& shader, const char* name, SkXfermode::Mode mode);
    void generateTextureWrap(String8& shader, GLenum wrapS, GLenum wrapT);

    void printLongString(const String8& shader) const;

    std::map<programid, std::unique_ptr<Program>> mCache;

    const bool mHasES3;
    /// M: [ProgramBinaryAtlas] Program atlas for caching program binaries.
    ProgramAtlas programAtlas;
}; // class ProgramCache

}; // namespace uirenderer
}; // namespace android

#endif // ANDROID_HWUI_PROGRAM_CACHE_H
