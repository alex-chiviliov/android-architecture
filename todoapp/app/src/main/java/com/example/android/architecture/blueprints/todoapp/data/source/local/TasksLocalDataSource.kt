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
package com.example.android.architecture.blueprints.todoapp.data.source.local

import android.content.ContentValues
import android.content.Context
import android.support.annotation.VisibleForTesting
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.TasksDataSource
import com.example.android.architecture.blueprints.todoapp.data.source.local.TasksPersistenceContract.TaskEntry.COLUMN_NAME_COMPLETED
import com.example.android.architecture.blueprints.todoapp.data.source.local.TasksPersistenceContract.TaskEntry.COLUMN_NAME_DESCRIPTION
import com.example.android.architecture.blueprints.todoapp.data.source.local.TasksPersistenceContract.TaskEntry.COLUMN_NAME_ENTRY_ID
import com.example.android.architecture.blueprints.todoapp.data.source.local.TasksPersistenceContract.TaskEntry.COLUMN_NAME_TITLE
import com.example.android.architecture.blueprints.todoapp.data.source.local.TasksPersistenceContract.TaskEntry.TABLE_NAME
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.withContext

/**
 * Concrete implementation of a data source as a db.
 */
class TasksLocalDataSource private constructor(context: Context) : TasksDataSource {

    private val dbHelper: TasksDbHelper = TasksDbHelper(context)

    /**
     * Note: the null is returned if the database doesn't exist
     * or the table is empty.
     */
    override suspend fun getTasks(): List<Task>? = withContext(DefaultDispatcher) {
        dbHelper.readableDatabase.use { db ->

            val projection = arrayOf(COLUMN_NAME_ENTRY_ID, COLUMN_NAME_TITLE,
                    COLUMN_NAME_DESCRIPTION, COLUMN_NAME_COMPLETED)

            db.query(TABLE_NAME, projection, null, null, null, null, null).use { cursor ->

                with(cursor) {
                    val tasks = mutableListOf<Task>()
                    while (moveToNext()) {
                        val itemId = getString(getColumnIndexOrThrow(COLUMN_NAME_ENTRY_ID))
                        val title = getString(getColumnIndexOrThrow(COLUMN_NAME_TITLE))
                        val description = getString(getColumnIndexOrThrow(COLUMN_NAME_DESCRIPTION))
                        val task = Task(title, description, itemId).apply {
                            isCompleted = getInt(getColumnIndexOrThrow(COLUMN_NAME_COMPLETED)) == 1
                        }
                        tasks.add(task)
                    }
                    if (tasks.isNotEmpty()) {
                        tasks
                    } else {
                        // This will be returned if the table is new or just empty.
                        null
                    }
                }
            }
        }
    }

    /**
     * Note: the null is returned if the [Task] isn't found.
     */
     override suspend fun getTask(taskId: String): Task? = withContext(DefaultDispatcher) {
        dbHelper.readableDatabase.use { db ->

            val projection = arrayOf(COLUMN_NAME_ENTRY_ID, COLUMN_NAME_TITLE,
                    COLUMN_NAME_DESCRIPTION, COLUMN_NAME_COMPLETED)

            db.query(TABLE_NAME, projection, "$COLUMN_NAME_ENTRY_ID LIKE ?", arrayOf(taskId), null,
                    null, null).use { cursor ->

                with(cursor) {
                    if (moveToFirst()) {
                        val itemId = getString(getColumnIndexOrThrow(COLUMN_NAME_ENTRY_ID))
                        val title = getString(getColumnIndexOrThrow(COLUMN_NAME_TITLE))
                        val description = getString(getColumnIndexOrThrow(COLUMN_NAME_DESCRIPTION))
                        val task = Task(title, description, itemId).apply {
                            isCompleted = getInt(getColumnIndexOrThrow(COLUMN_NAME_COMPLETED)) == 1
                        }
                        task
                    } else {
                        null
                    }
                }
            }
        }
    }

    override suspend fun saveTask(task: Task) {
        withContext(DefaultDispatcher) {
            val values = ContentValues().apply {
                put(COLUMN_NAME_ENTRY_ID, task.id)
                put(COLUMN_NAME_TITLE, task.title)
                put(COLUMN_NAME_DESCRIPTION, task.description)
                put(COLUMN_NAME_COMPLETED, task.isCompleted)
            }
            dbHelper.writableDatabase.use { db ->
                db.insert(TABLE_NAME, null, values)
            }
        }
    }

    override suspend fun completeTask(task: Task) {
        withContext(DefaultDispatcher) {
            val values = ContentValues().apply {
                put(COLUMN_NAME_COMPLETED, true)
            }
            dbHelper.writableDatabase.use { db ->
                db.update(TABLE_NAME, values, "$COLUMN_NAME_ENTRY_ID LIKE ?", arrayOf(task.id))
            }
        }
    }

    override suspend fun completeTask(taskId: String) {
        // Not required for the local data source because the {@link TasksRepository} handles
        // converting from a {@code taskId} to a {@link task} using its cached data.
    }

    override suspend fun activateTask(task: Task) {
        withContext(DefaultDispatcher) {
            val values = ContentValues().apply {
                put(COLUMN_NAME_COMPLETED, false)
            }

            dbHelper.writableDatabase.use { db ->
                db.update(TABLE_NAME, values, "$COLUMN_NAME_ENTRY_ID LIKE ?", arrayOf(task.id))
            }
        }
    }

    override suspend fun activateTask(taskId: String) {
        // Not required for the local data source because the {@link TasksRepository} handles
        // converting from a {@code taskId} to a {@link task} using its cached data.
    }

    override suspend fun clearCompletedTasks() {
        withContext(DefaultDispatcher) {
            val selection = "$COLUMN_NAME_COMPLETED LIKE ?"
            val selectionArgs = arrayOf("1")
            dbHelper.writableDatabase.use { db ->
                db.delete(TABLE_NAME, selection, selectionArgs)
            }
        }
    }

    override fun refreshTasks() {
        // Not required because the {@link TasksRepository} handles the logic of refreshing the
        // tasks from all the available data sources.
    }

    override suspend fun deleteAllTasks() {
        withContext(DefaultDispatcher) {
            dbHelper.writableDatabase.use { db ->
                db.delete(TABLE_NAME, null, null)
            }
        }
    }

    override suspend fun deleteTask(taskId: String) {
        withContext(DefaultDispatcher) {
            val selection = "$COLUMN_NAME_ENTRY_ID LIKE ?"
            val selectionArgs = arrayOf(taskId)
            dbHelper.writableDatabase.use { db ->
                db.delete(TABLE_NAME, selection, selectionArgs)
            }
        }
    }

    companion object {
        private var INSTANCE: TasksLocalDataSource? = null

        @JvmStatic fun getInstance(context: Context): TasksLocalDataSource {
            return INSTANCE ?: TasksLocalDataSource(context).apply { INSTANCE = this }
        }
    }

    /**
     * A dummy method to avoid occasional "method runBlocking" not found errors
     * when running Android Instrumentation tests.
     */
    @VisibleForTesting
    fun dummy() = runBlocking {  }
}
