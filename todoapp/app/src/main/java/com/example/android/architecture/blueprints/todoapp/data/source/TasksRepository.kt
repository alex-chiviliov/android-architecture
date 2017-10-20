/*
 * Copyright 2017, The Android Open Source Project
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
package com.example.android.architecture.blueprints.todoapp.data.source

import com.example.android.architecture.blueprints.todoapp.data.Task

/**
 * Concrete implementation to load tasks from the data sources into a cache.
 *
 *
 * For simplicity, this implements a dumb synchronisation between locally persisted data and data
 * obtained from the server, by using the remote data source only if the local database doesn't
 * exist or is empty.
 */
class TasksRepository(
        val tasksRemoteDataSource: TasksDataSource,
        val tasksLocalDataSource: TasksDataSource
) : TasksDataSource {

    /**
     * This variable has public visibility so it can be accessed from tests.
     */
    var cachedTasks = mutableMapOf<String, Task>()

    /**
     * Marks the cache as invalid, to force an update the next time data is requested. This variable
     * has package local visibility so it can be accessed from tests.
     */
    var cacheIsDirty = false

    /**
     * Gets tasks from cache, local data source (SQLite) or remote data source, whichever is
     * available first.
     *
     *
     * Note: a  null [List] is returned if all data sources fail to
     * get the data.
     */
    override suspend fun getTasks(): List<Task>? {
        // Respond immediately with cache if available and not dirty
        if (cachedTasks.isNotEmpty() && !cacheIsDirty) {
            return cachedTasks.values.toList()
        }

        if (cacheIsDirty) {
            // If the cache is dirty we need to fetch new data from the network.
            return getTasksFromRemoteDataSource()
        } else {
            // Query the local storage if available. If not, query the network.
            val tasks = tasksLocalDataSource.getTasks()
            if (tasks != null) {
                refreshCache(tasks)
                return cachedTasks.values.toList()
            } else {
                return getTasksFromRemoteDataSource()
            }
        }
    }

    override suspend fun saveTask(task: Task) {
        // Do in memory cache update to keep the app UI up to date
        cache(task).let {
            tasksRemoteDataSource.saveTask(it)
            tasksLocalDataSource.saveTask(it)
        }
    }

    override suspend fun completeTask(task: Task) {
        // Do in memory cache update to keep the app UI up to date
        cache(task).let {
            it.isCompleted = true
            tasksRemoteDataSource.completeTask(it)
            tasksLocalDataSource.completeTask(it)
        }
    }

    override suspend fun completeTask(taskId: String) {
        getTaskWithId(taskId)?.let {
            completeTask(it)
        }
    }

    override suspend fun activateTask(task: Task) {
        // Do in memory cache update to keep the app UI up to date
        cache(task).let {
            it.isCompleted = false
            tasksRemoteDataSource.activateTask(it)
            tasksLocalDataSource.activateTask(it)
        }
    }

    override suspend fun activateTask(taskId: String) {
        getTaskWithId(taskId)?.let {
            activateTask(it)
        }
    }

    override suspend fun clearCompletedTasks() {
        tasksRemoteDataSource.clearCompletedTasks()
        tasksLocalDataSource.clearCompletedTasks()

        cachedTasks = cachedTasks.filterValues {
            !it.isCompleted
        }.toMutableMap()
    }

    /**
     * Gets tasks from local data source (sqlite) unless the table is new or empty. In that case it
     * uses the network data source. This is done to simplify the sample.
     *
     *
     * Note: the null is returned if both data sources fail to
     * get the data.
     */
    override suspend fun getTask(taskId: String): Task? {
        val taskInCache = getTaskWithId(taskId)

        // Respond immediately with cache if available
        if (taskInCache != null) {
            return taskInCache
        }

        // Load from server/persisted if needed.

        // Is the task in the local data source? If not, query the network.
        val taskLocal = tasksLocalDataSource.getTask(taskId)
        if (taskLocal != null) {
            // Do in memory cache update to keep the app UI up to date
            return cache(taskLocal)
        }

        val taskRemote = tasksRemoteDataSource.getTask(taskId)
        return if (taskRemote != null) cache(taskRemote) else null
    }

    override fun refreshTasks() {
        cacheIsDirty = true
    }

    override suspend fun deleteAllTasks() {
        tasksRemoteDataSource.deleteAllTasks()
        tasksLocalDataSource.deleteAllTasks()
        cachedTasks.clear()
    }

    override fun deleteTask(taskId: String) {
        tasksRemoteDataSource.deleteTask(taskId)
        tasksLocalDataSource.deleteTask(taskId)
        cachedTasks.remove(taskId)
    }

    private suspend fun getTasksFromRemoteDataSource(): List<Task>? {
        val tasks = tasksRemoteDataSource.getTasks()
        if (tasks != null) {
            refreshCache(tasks)
            refreshLocalDataSource(tasks)
            return cachedTasks.values.toList()
        } else {
            return null
        }
    }

    private fun refreshCache(tasks: List<Task>) {
        cachedTasks.clear()
        tasks.forEach {
            cache(it)
        }
        cacheIsDirty = false
    }

    private suspend fun refreshLocalDataSource(tasks: List<Task>) {
        tasksLocalDataSource.deleteAllTasks()
        for (task in tasks) {
            tasksLocalDataSource.saveTask(task)
        }
    }

    private fun getTaskWithId(id: String) = cachedTasks[id]

    private fun cache(task: Task): Task {
        val cachedTask = Task(task.title, task.description, task.id).apply {
            isCompleted = task.isCompleted
        }
        cachedTasks.put(cachedTask.id, cachedTask)
        return cachedTask
    }

    companion object {

        private var INSTANCE: TasksRepository? = null

        /**
         * Returns the single instance of this class, creating it if necessary.

         * @param tasksRemoteDataSource the backend data source
         * *
         * @param tasksLocalDataSource  the device storage data source
         * *
         * @return the [TasksRepository] instance
         */
        @JvmStatic fun getInstance(tasksRemoteDataSource: TasksDataSource,
                tasksLocalDataSource: TasksDataSource) =
                INSTANCE ?: synchronized(TasksRepository::class.java) {
                    INSTANCE ?: TasksRepository(tasksRemoteDataSource, tasksLocalDataSource)
                            .also { INSTANCE = it }
                }


        /**
         * Used to force [getInstance] to create a new instance
         * next time it's called.
         */
        @JvmStatic fun destroyInstance() {
            INSTANCE = null
        }
    }
}
