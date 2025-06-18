package com.jetbrains.bigdatatools.kafka.core.rfs.driver

import com.intellij.diagnostic.dumpCoroutines
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class ProgressOptions(
  @NlsContexts.ProgressTitle val taskName: String,
  val project: Project?,
  val cancellable: Boolean,
  val showProgress: Boolean = true,
) {
  val cancellation: TaskCancellation
    get() = if (cancellable) TaskCancellation.cancellable() else TaskCancellation.nonCancellable()
}

@Service(Service.Level.APP)
class SafeExecutorService(val coroutineScope: CoroutineScope)

@Service(Service.Level.APP)
class SafeExecutor(val coroutineScope: CoroutineScope, private val defaultTimeout: Duration) : Disposable {
  constructor(coroutineScope: CoroutineScope) : this(coroutineScope, Duration.INFINITE)

  override fun dispose() {
    coroutineScope.cancel("SafeExecutor dispose")
  }

  fun <T> asyncInterruptible(@NlsSafe taskName: String?, timeout: Duration = defaultTimeout, body: () -> T): SafeTask<T> {
    return createSafeTask(taskName, timeout) {
      runInterruptibleMC {
        body()
      }
    }
  }

  fun <T> asyncInterruptibleProgress(showProgress: ProgressOptions, body: () -> T): SafeTask<T> {
    return createSafeTask(showProgress, defaultTimeout) {
      runInterruptibleMC {
        body()
      }
    }
  }

  fun <T> asyncSuspend(@NlsSafe taskName: String?, timeout: Duration = defaultTimeout, body: suspend CoroutineScope.() -> T): SafeTask<T> {
    return createSafeTask(taskName, timeout, body)
  }

  fun <T> asyncSuspendProgress(showProgress: ProgressOptions, timeout: Duration = defaultTimeout, body: suspend CoroutineScope.() -> T): SafeTask<T> {
    return createSafeTask(showProgress, timeout, body)
  }

  private fun <T> createSafeTask(showProgress: ProgressOptions, timeout: Duration, body: suspend CoroutineScope.() -> T): SafeTask<T> {
    val deferred = coroutineScope.async(CoroutineName(showProgress.taskName)) {
      withTimeout(timeout) {
        computeDetachedWithBackgroundProgress(showProgress, body)
      }
    }
    return SafeTask(showProgress.project, showProgress.taskName, true, timeout, deferred, TimeSource.Monotonic.markNow(), this)
  }

  private fun <T> createSafeTask(@NlsSafe taskName: String?, timeout: Duration, body: suspend CoroutineScope.() -> T): SafeTask<T> {
    val name = taskName ?: KafkaMessagesBundle.message("driver.task.title.default")
    val deferred = coroutineScope.async {
      withTimeout(timeout) {
        computeDetached(name, null, body)
      }
    }
    return SafeTask(null, name, false, timeout, deferred, TimeSource.Monotonic.markNow(), this)
  }

  private suspend fun <T> computeDetachedWithBackgroundProgress(showProgress: ProgressOptions, body: suspend CoroutineScope.() -> T): T {
    if (!showProgress.showProgress) {
      return computeDetached(showProgress.taskName, showProgress.project, body)
    }
    return withBackgroundProgress(showProgress.project, showProgress.taskName, showProgress.cancellation) {
      computeDetached(showProgress.taskName, showProgress.project) {
        body()
      }
    }
  }

  suspend fun <T> computeDetached(title: String, project: Project? = null, body: suspend CoroutineScope.() -> T): T {
    val originalCoroutineContext = currentCoroutineContext()
    val scope = CompletableDeferred<CoroutineScope>()
    val originalTask = coroutineScope.async(originalCoroutineContext.minusKey(Job) + CoroutineName(title)) {
      scope.complete(this)
      body()
    }
    try {
      return originalTask.await()
    }
    catch (e: CancellationException) {
      originalTask.cancel(e)
      coroutineScope.launch(Dispatchers.IO) {
        withBackgroundProgress(project, KafkaMessagesBundle.message("driver.task.canceling.progress.title", title), TaskCancellation.nonCancellable()) {
          val logDetachedJob = launch {
            logDetached(scope.await(), title, e, originalTask)
          }
          originalTask.join()
          logDetachedJob.cancel()
        }
      }
      throw e
    }
  }

  private suspend fun <T> withBackgroundProgress(
    projectToShowProgress: Project?,
    title: @NlsContexts.ProgressTitle String,
    cancellation: TaskCancellation,
    action: suspend CoroutineScope.() -> T,
  ): T {
    val projects = projectToShowProgress?.let { listOf(it) }
                   ?: ProjectManagerEx.getOpenProjects().takeIf { it.isNotEmpty() }
                   ?: listOf(ProjectManagerEx.getInstanceEx().defaultProject)
    return withBackgroundProgress(projects, title, cancellation, action)
  }

  private suspend fun <T> withBackgroundProgress(
    projects: List<Project>,
    title: @NlsContexts.ProgressTitle String,
    cancellation: TaskCancellation,
    action: suspend CoroutineScope.() -> T,
  ): T {
    when {
      projects.size == 1 -> return com.intellij.platform.ide.progress.withBackgroundProgress(projects.first(), title, cancellation, action)
      projects.size > 1 -> return com.intellij.platform.ide.progress.withBackgroundProgress(projects.first(), title, cancellation) {
        withBackgroundProgress(projects.drop(1), title, cancellation, action)
      }
      else -> throw IllegalArgumentException()
    }
  }

  companion object {

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun logDetached(hangingScope: CoroutineScope, title: String, e: CancellationException, originalTask: Job) {
      delay(10.milliseconds)
      if (originalTask.isCompleted) return

      val dump = dumpCoroutines(hangingScope)
      val giveUpTimeout = 3.minutes
      val currentTime = TimeSource.Monotonic.markNow()
      select {
        originalTask.onJoin {
          val waitTime = currentTime.elapsedNow()
          val application = ApplicationManager.getApplication()
          if (application.isInternal() || application.isUnitTestMode()) {
            thisLogger().error("SafeExecutor had to detach cancelling job $title. Finished after $waitTime. Coroutine dump:\n$dump", e)
          }
          else if (application.isEAP()) {
            if (waitTime > 30.seconds) {
              thisLogger().warn("SafeExecutor had to detach cancelling job $title. Finished after $waitTime. Coroutine dump:\n$dump", e)
            }
          }
        }
        onTimeout(giveUpTimeout) {
          val newDump = dumpCoroutines(hangingScope)
          thisLogger().error("SafeExecutor had to detach cancelling job $title. Not finished after $giveUpTimeout. Coroutine dump:\n$newDump", e)
        }
      }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createInstance(
      name: String = "SafeExecutor",
      defaultTimeout: Duration = Duration.INFINITE,
      parallelism: Int = 10,
    ): SafeExecutor {
      val childScope = service<SafeExecutorService>().coroutineScope.childScope(name, Dispatchers.IO.limitedParallelism(parallelism))
      return SafeExecutor(childScope, defaultTimeout)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun createInstance(
      parentDisposable: Disposable,
      name: String = "SafeExecutor",
      defaultTimeout: Duration = Duration.INFINITE,
      parallelism: Int = 10,
    ): SafeExecutor {
      val childScope = service<SafeExecutorService>().coroutineScope.childScope(name, Dispatchers.IO.limitedParallelism(parallelism))
      return SafeExecutor(childScope, defaultTimeout).also {
        Disposer.register(parentDisposable, it)
      }
    }

    val instance: SafeExecutor by lazy { createInstance() }
  }
}

fun <T> flowOfSingleInterruptible(body: () -> T): Flow<T> {
  return flow { emit(runInterruptibleMC { body() }) }
}

suspend fun <T> runInterruptibleMC(body: () -> T): T {
  return runInterruptible(Dispatchers.IO) {
    runBlockingMaybeCancellable {
      body()
    }
  }
}

fun <T> runBlockingInterruptible(body: () -> T): T {
  return runBlockingMaybeCancellable {
    runInterruptible {
      body()
    }
  }
}