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
package com.example.android.architecture.blueprints.todoapp.statistics


import android.app.Application
import android.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.Is.`is`
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations

/**
 * Unit tests for the implementation of [StatisticsViewModel]
 */
class StatisticsViewModelTest {

    // Executes each task synchronously using Architecture Components.
    @get:Rule var instantExecutorRule = InstantTaskExecutorRule()
    @Mock private lateinit var tasksRepository: TasksRepository
    private lateinit var statisticsViewModel: StatisticsViewModel
    private lateinit var tasks: MutableList<Task>

    @Before fun setupStatisticsViewModel() {
        // Mockito has a very convenient way to inject mocks by using the @Mock annotation. To
        // inject the mocks in the test the initMocks method needs to be called.
        MockitoAnnotations.initMocks(this)

        // Get a reference to the class under test
        statisticsViewModel = StatisticsViewModel(mock(Application::class.java), tasksRepository, Unconfined)

        // We initialise the tasks to 3, with one active and two completed
        val task1 = Task("Title1", "Description1")
        val task2 = Task("Title2", "Description2").apply {
            isCompleted = true
        }
        val task3 = Task("Title3", "Description3").apply {
            isCompleted = true
        }
        tasks = mutableListOf(task1, task2, task3)
    }

    @Test fun loadEmptyTasksFromRepository_EmptyResults() = runBlocking<Unit> {
        // Given an initialized StatisticsViewModel with no tasks
        `when`(tasksRepository.getTasks()).thenReturn(emptyList())

        // When loading of Tasks is requested
        statisticsViewModel.loadStatistics()

        // Then the results are empty
        assertThat(statisticsViewModel.empty.get(), `is`(true))
    }

    @Test fun loadNonEmptyTasksFromRepository_NonEmptyResults() = runBlocking<Unit> {
        // When loading of Tasks is requested
        `when`(tasksRepository.getTasks()).thenReturn(tasks)
        statisticsViewModel.loadStatistics()

        // Then the results are not empty
        assertThat(statisticsViewModel.empty.get(), `is`(false))
    }


    @Test fun loadStatisticsWhenTasksAreUnavailable_CallErrorToDisplay() = runBlocking<Unit> {
        // When statistics are loaded
        // And tasks data isn't available
        `when`(tasksRepository.getTasks()).thenReturn(null)
        statisticsViewModel.loadStatistics()

        // Then an error message is shown
        assertEquals(statisticsViewModel.empty.get(), true)
        assertEquals(statisticsViewModel.error.get(), true)
    }
}
