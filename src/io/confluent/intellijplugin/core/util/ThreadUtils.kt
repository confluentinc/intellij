package io.confluent.intellijplugin.core.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.Semaphore
import io.confluent.intellijplugin.core.rfs.driver.SafeExecutor
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.time.Duration

//Create Promise which not throw exceptions to the log
@Suppress("FunctionName")
fun <T> BdtAsyncPromise() = AsyncPromise<T>().asSilent()
fun <T> Promise<T>.asSilent(): Promise<T> = this.onError { }
fun <T> AsyncPromise<T>.asSilent(): AsyncPromise<T> = this.onError { }

fun <T> invokeLater(body: () -> T) =
  ApplicationManager.getApplication().invokeLater {
    body()
  }

fun <T> executeOnPooledThread(body: () -> T) {
  ApplicationManager.getApplication().executeOnPooledThread {
    body()
  }
}

fun <T> executeNotOnEdt(body: () -> T) {
  if (ApplicationManager.getApplication().isDispatchThread) {
    executeOnPooledThread(body)
  }
  else body()
}

inline fun <T> runAsyncSuspend(crossinline runnable: suspend () -> T): Promise<T> {
  return SafeExecutor.instance.asyncSuspend(taskName = null, timeout = Duration.INFINITE) {
    runnable()
  }.deferred.asCompletableFuture().asPromise().asSilent()
}


inline fun <T> runAsync(crossinline runnable: () -> T): Promise<T> {
  return SafeExecutor.instance.asyncInterruptible(taskName = null, timeout = Duration.INFINITE) {
    runnable()
  }.deferred.asCompletableFuture().asPromise().asSilent()
}

fun sleepWithCancellation(sleepAmount: java.time.Duration, indicator: ProgressIndicator?) {
  val semaphore = Semaphore(1)
  val future = AppExecutorUtil.getAppScheduledExecutorService().schedule(
    { semaphore.up() },
    sleepAmount.toMillis(),
    TimeUnit.MILLISECONDS
  )
  try {
    ProgressIndicatorUtils.awaitWithCheckCanceled(semaphore, indicator)
  }
  finally {
    future.cancel(true)
  }
}

/**
 *
 * Usefully if we need to run in settings dialog
 */
fun <T> invokeAndWaitSwing(runnable: () -> T): T? {
  var res: T? = null

  if (SwingUtilities.isEventDispatchThread()) {
    return runnable()
  }
  var error: Throwable? = null
  SwingUtilities.invokeAndWait {
    try {
      res = runnable()
    }
    catch (t: Throwable) {
      error = t
    }
  }

  error?.let { throw it }
  return res
}