package io.confluent.kafka.core.util.async

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class JobHandle(val coroutineScope: CoroutineScope) {
  private val ref = AtomicReference<Job>(null)
  val job: Job? get() = ref.get()
  fun cancelAndLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> Unit
  ) {
    ref.getAndSet(coroutineScope.launch(context, CoroutineStart.DEFAULT, block))?.cancel("restarted")
  }
}

class DeferredJobHandle<T>(val coroutineScope: CoroutineScope) {
  private val ref = AtomicReference<Deferred<T>?>(null)
  suspend fun cancelAndRun(block: suspend CoroutineScope.() -> T): T {
    val deferred = coroutineScope.async(start = CoroutineStart.LAZY, block = block)
    val previous = ref.getAndSet(deferred)
    previous?.cancel("Cancel and again")
    previous?.join()
    return deferred.await()
  }
  suspend fun runOrAwait(block: suspend CoroutineScope.() -> T): T {
    val deferred = ref.updateAndGet { existing ->
      if (existing != null && !existing.isCompleted) {
        existing
      }
      else {
        coroutineScope.async(start = CoroutineStart.LAZY, block = block)
      }
    }
    return requireNotNull(deferred).await()
  }
}

suspend fun <T> DeferredJobHandle<T>.runOrAwait(cancelPrevious: Boolean, block: suspend CoroutineScope.() -> T): T {
  return if (cancelPrevious) {
    cancelAndRun(block)
  }
  else {
    runOrAwait(block)
  }
}