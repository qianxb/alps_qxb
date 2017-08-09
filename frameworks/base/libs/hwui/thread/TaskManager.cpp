/*
 * Copyright (C) 2013 The Android Open Source Project
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

#include <sys/resource.h>
#include <sys/sysinfo.h>

#include "../Debug.h"
#include "TaskManager.h"
#include "Task.h"
#include "TaskProcessor.h"
#include "utils/MathUtils.h"

namespace android {
namespace uirenderer {

#define TASK_LOGD(...) if (CC_UNLIKELY(g_HWUI_debug_hwuitask)) ALOGD(__VA_ARGS__);

///////////////////////////////////////////////////////////////////////////////
// Manager
///////////////////////////////////////////////////////////////////////////////

TaskManager::TaskManager() {
    // Get the number of available CPUs. This value does not change over time.
    int cpuCount = sysconf(_SC_NPROCESSORS_CONF);

    // Really no point in making more than 2 of these worker threads, but
    // we do want to limit ourselves to 1 worker thread on dual-core devices.
    int workerCount = cpuCount > 2 ? 2 : 1;
    TASK_LOGD("TaskManager() %p, cpu = %d, thread = %d", this, cpuCount, workerCount);
    for (int i = 0; i < workerCount; i++) {
        String8 name;
        name.appendFormat("hwuiTask%d", i + 1);
        mThreads.push_back(new WorkerThread(name));
    }
}

TaskManager::~TaskManager() {
    TASK_LOGD("~TaskManager() %p", this);
    for (size_t i = 0; i < mThreads.size(); i++) {
        mThreads[i]->exit();
    }
}

bool TaskManager::canRunTasks() const {
    return mThreads.size() > 0;
}

void TaskManager::stop() {
    TASK_LOGD("[TaskMgr] %p stop", this);
    for (size_t i = 0; i < mThreads.size(); i++) {
        mThreads[i]->exit();
    }
}

bool TaskManager::addTaskBase(const sp<TaskBase>& task, const sp<TaskProcessorBase>& processor) {
    if (mThreads.size() > 0) {
        TaskWrapper wrapper(task, processor);

        size_t minQueueSize = INT_MAX;
        sp<WorkerThread> thread;

        for (size_t i = 0; i < mThreads.size(); i++) {
            if (mThreads[i]->getTaskCount() < minQueueSize) {
                thread = mThreads[i];
                minQueueSize = mThreads[i]->getTaskCount();
            }
        }

        return thread->addTask(wrapper);
    }
    return false;
}

///////////////////////////////////////////////////////////////////////////////
// Thread
///////////////////////////////////////////////////////////////////////////////

status_t TaskManager::WorkerThread::readyToRun() {
    setpriority(PRIO_PROCESS, 0, PRIORITY_FOREGROUND);
    return NO_ERROR;
}

bool TaskManager::WorkerThread::threadLoop() {
    mSignal.wait();
    std::vector<TaskWrapper> tasks;
    {
        Mutex::Autolock l(mLock);
        tasks.swap(mTasks);
    }

    for (size_t i = 0; i < tasks.size(); i++) {
        const TaskWrapper& task = tasks[i];
        task.mProcessor->process(task.mTask);
        TASK_LOGD("[TaskMgr] %s finish task", mName.string());
    }

    return true;
}

bool TaskManager::WorkerThread::addTask(const TaskWrapper& task) {
    if (!isRunning()) {
        run(mName.string(), PRIORITY_DEFAULT);
        TASK_LOGD("[TaskMgr] Running thread %s (%d)", mName.string(), getTid());
    } else if (exitPending()) {
        return false;
    }
    TASK_LOGD("[TaskMgr] Add task to %s", mName.string());

    {
        Mutex::Autolock l(mLock);
        mTasks.push_back(task);
    }
    mSignal.signal();

    return true;
}

size_t TaskManager::WorkerThread::getTaskCount() const {
    Mutex::Autolock l(mLock);
    return mTasks.size();
}

void TaskManager::WorkerThread::exit() {
    TASK_LOGD("[TaskMgr] Exit thread %s (%d)", mName.string(), getTid());
    requestExit();
    mSignal.signal();
}

}; // namespace uirenderer
}; // namespace android
