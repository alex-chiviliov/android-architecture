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
package com.example.android.architecture.blueprints.todoapp.addedittask


import android.app.Application
import android.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository
import com.example.android.architecture.blueprints.todoapp.util.any
import com.example.android.architecture.blueprints.todoapp.util.eq
import com.example.android.architecture.blueprints.todoapp.util.mock
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.runBlocking
import org.hamcrest.Matchers.`is`
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

/**
 * Unit tests for the implementation of [AddEditTaskViewModel].
 */
class AddEditTaskViewModelTest {

    // Executes each task synchronously using Architecture Components.
    @get:Rule var instantExecutorRule = InstantTaskExecutorRule()
    @Mock private lateinit var tasksRepository: TasksRepository
    private lateinit var addEditTaskViewModel: AddEditTaskViewModel

    @Before fun setupAddEditTaskViewModel() {
        // Mockito has a very convenient way to inject mocks by using the @Mock annotation. To
        // inject the mocks in the test the initMocks method needs to be called.
        MockitoAnnotations.initMocks(this)

        // Get a reference to the class under test
        addEditTaskViewModel = AddEditTaskViewModel(mock<Application>(), tasksRepository, Unconfined)
    }

    @Test fun saveNewTaskToRepository_showsSuccessMessageUi() = runBlocking<Unit> {
        // When the ViewModel is asked to save a task
        with(addEditTaskViewModel) {
            description.set("Some Task Description")
            title.set("New Task Title")
            saveTask()
        }

        // Then a task is saved in the repository and the view updated
        verify<TasksRepository>(tasksRepository).saveTask(any<Task>())
    }

    @Test fun populateTask_callsRepoAndUpdatesView() = runBlocking<Unit> {
        val testTask = Task("TITLE", "DESCRIPTION", "1")
        `when`(tasksRepository.getTask(eq(testTask.id))).thenReturn(testTask)

        // Get a reference to the class under test
        addEditTaskViewModel = AddEditTaskViewModel(mock<Application>(), tasksRepository, Unconfined).apply {
            // When the ViewModel is asked to populate an existing task
            start(testTask.id)
        }

        // Then the task repository is queried and the view updated
        verify(tasksRepository).getTask(eq(testTask.id))

        // Verify the fields were updated
        assertThat(addEditTaskViewModel.title.get(), `is`(testTask.title))
        assertThat(addEditTaskViewModel.description.get(), `is`(testTask.description))
    }
}
