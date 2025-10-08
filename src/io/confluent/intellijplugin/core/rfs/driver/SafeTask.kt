package io.confluent.intellijplugin.core.rfs.driver

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.rfs.driver.fileinfo.ErrorResult
import io.confluent.intellijplugin.core.rfs.driver.fileinfo.OkResult
import io.confluent.intellijplugin.core.rfs.driver.fileinfo.SafeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark

sealed class RunStrategy {
    /**
     * Executes provided task at most once, joins existing task if it is active
     */
    object Join : RunStrategy()

    /**
     * Executes provided task exactly once, cancels existing task if it is active
     */
    object Cancel : RunStrategy()
}

class SafeTaskHandle<T> {
    private val ref = AtomicReference<CompletableFuture<SafeTask<T>>?>(null)
    val currentTask: SafeTask<T>?
        get() = ref.get()?.getNow(null)

    fun runIfNotRunning(strategy: RunStrategy, taskFactory: () -> SafeTask<T>): SafeTask<T> {
        return when (strategy) {
            RunStrategy.Join -> {
                val newTaskHolder = CompletableFuture<SafeTask<T>>()
                val existingTaskOrHolder =
                    ref.updateAndGet { if (it != null && it.getNow(null)?.isActive != false) it else newTaskHolder }
                if (existingTaskOrHolder == newTaskHolder) {
                    taskFactory().also {
                        newTaskHolder.complete(it)
                    }
                } else {
                    checkNotNull(existingTaskOrHolder?.get())
                }
            }

            RunStrategy.Cancel -> {
                val newTaskHolder = CompletableFuture<SafeTask<T>>()
                val existingTaskOrHolder = ref.getAndSet(newTaskHolder)
                existingTaskOrHolder?.get()?.cancel("Task restarted by $strategy")
                taskFactory().also {
                    newTaskHolder.complete(it)
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
data class SafeTask<T>(
    val project: Project?,
    val name: String,
    val showProgress: Boolean,
    val timeout: Duration,
    val deferred: Deferred<T>,
    val startTime: TimeMark,
    val safeExecutor: SafeExecutor,
) {
    val isActive get() = deferred.isActive
    fun cancel(message: String = "Cancelled explicitly") = deferred.cancel(message)
    suspend fun awaitOwn(): T = try {
        deferred.await()
    } catch (e: CancellationException) {
        deferred.cancel(e)
        throw e
    }
}

fun <T, R> SafeTask<T>.map(f: (T) -> R): SafeTask<R> {
    return safeExecutor.asyncSuspend(this.name, this.timeout) {
        f(awaitOwn())
    }
}

fun <T> SafeTask<T>.blockingGet(): SafeResult<T> {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    //ApplicationManager.getApplication().assertReadAccessNotAllowed()
    return try {
        OkResult(this.deferred.asCompletableFuture().get())
    } catch (e: ExecutionException) {
        ErrorResult(e.cause ?: e)
    } catch (e: TimeoutCancellationException) {
        ErrorResult(TimeoutException("Timeout $timeout exceeded: $name").initCause(e))
    } catch (e: Throwable) {
        ErrorResult(e)
    }
}