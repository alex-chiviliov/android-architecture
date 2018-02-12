# todo-mvvm-live-kotlin-coroutines

This version of the app is called `todo-mvvm-live-kotlin-coroutines`. It is derived from [todo-mvvm-live-kotlin](https://github.com/googlesamples/android-architecture/tree/dev-todo-mvvm-live-kotlin/) and demonstrates the usage of Kotlin coroutines for asynchronous processing.

Kotlin coroutines bring in numerous benefits:

* The asynchronous code becomes much more intuitive and looks like "normal" sequential code. There is no noise created by callbacks, custom reactive operators, etc.

* Kotlin coroutines are lightweight, native to Kotlin and included into Kotlin standard libraries.

* All asynchronous programming tasks can be achieved naturally using the normal language constructs (conditional statements, loops, exception handling, etc.) without the need to learn numerous new "operators" (flatMap, zip, onErrorResumeNext, etc.) by 3rd party asynchronous libraries.


## What you need to know

Before exploring this sample, you should familiarize yourself with the following topics:

* The [project README](https://github.com/googlesamples/android-architecture/tree/master)
* The [todo-mvp](https://github.com/googlesamples/android-architecture/tree/todo-mvp) sample
* The [todo-mvvm-databinding](https://github.com/googlesamples/android-architecture/tree/todo-mvvm-databinding) sample
* The [MVVM](https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93viewmodel) architecture
* The [Kotlin programming language](https://kotlinlang.org)
* [Guide to kotlinx.coroutines by example](https://github.com/Kotlin/kotlinx.coroutines/blob/develop/coroutines-guide.md)
* [Guide to UI programming with coroutines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/ui/coroutines-guide-ui.md)


## Implementing the app

This section provides an overview of changes to the parent project [todo-mvvm-live-kotlin](https://github.com/googlesamples/android-architecture/tree/dev-todo-mvvm-live-kotlin/).

### Build files

To enable coroutine features in Kotlin compiler, the following directive should be added to `gradle.properties` file:
```
kotlin.coroutines=enable
```

The majority of application level coroutine primitives are contained in Kotlin coroutine extension library, so they should be added to `app/build.gradle`:
```gradle
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinx_version"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:$kotlinx_version"
``` 

### Data Source interface

The repository interface in the parent app is callback based. For example, the `getTask()` method in [TasksDataSource](https://github.com/googlesamples/android-architecture/tree/dev-todo-mvvm-live-kotlin/todoapp) is defined like this:
```kotlin
interface GetTaskCallback {
    fun onTaskLoaded(task: Task)
    fun onDataNotAvailable()
}

fun getTask(taskId: String, callback: GetTaskCallback)
```

It is screaming to be "reverse engineered" and converted to a native Kotlin asynchronous method. There are two styles of asynchronous programming in Kotlin:

* _suspending_ (or sequential) style;
* _async/await_ style

See [Asynchronous programming styles](https://github.com/Kotlin/kotlin-coroutines/blob/master/kotlin-coroutines-informal.md#asynchronous-programming-styles) for the enlightening discussion.

We have chosen to adopt the suspending style as it is more natural and easier to use. The above method was converted to:
```kotlin
suspend fun getTask(taskId: String): Task?
``` 

Similar transformations have been applied to all other long-running interface methods resulting in:
```kotlin
interface TasksDataSource {
    suspend fun getTasks(): List<Task>?
    suspend fun getTask(taskId: String): Task?
    suspend fun saveTask(task: Task)
    suspend fun completeTask(task: Task)
    suspend fun completeTask(taskId: String)
    suspend fun activateTask(task: Task)
    suspend fun activateTask(taskId: String)
    suspend fun clearCompletedTasks()
    fun refreshTasks()
    suspend fun deleteAllTasks()
    suspend fun deleteTask(taskId: String)
}
``` 

This interface change caused flow-on effects on classes implementing this interface, consuming it and used for testing it.

### Data Source implementation

`TasksLocalDataSource` is a good example. Two simple changes have been applied to the `getTask()` method:

1. Wrapping the method body into the `withContext` _coroutine builder_, which makes the method suspendable and ensures that its body is executed on the designated thread pool.

2. Returning the result by simply using the `return` statement instead of making a callback call.

### Consuming Data Source in the UI

The primary consumer of the `getTask()` method is `TaskDetailViewModel`. Instead of implementing the `GetTaskCallback` interface and updating the UI from there, `TaskDetailViewModel` now does it sequentially:
```kotlin
fun start(taskId: String?) = launch(UI, CoroutineStart.UNDISPATCHED) {
    if (taskId != null) {
        isDataLoading = true
        val task = tasksRepository.getTask(taskId)
        isDataLoading = false
        setTask(task)
    }
}
```

The `launch` coroutine builder creates a _coroutine context_ bound to the main UI thread, so that when the asynchronous call to the `getTask()` method is complete the execution resumes on the UI thread.

### Data Source unit testing

Fixing the unit tests was almost trivial (for example, check `TasksLocalDataSourceTest`):

1. Wrap the body of the test case into the `runBlocking` coroutine builder to make sure that the unit test code blocks on every call to a suspending function.

2. Consume outputs of the method being tested sequentially rather then via callbacks.

The result was much simpler and easier to read unit test code.
