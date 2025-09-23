package io.confluent.kafka.core.util

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

/**
 * Each subscriber receives its own tick flow based on [sourceTicks].
 * All flows should be considered as cold flows, so that they are collected subsequently.
 * n-th tick to k-th collector comes only after all previous collectors consumed n-th tick.
 */
class RefreshTicker(coroutineScope: CoroutineScope, sourceTicks: Flow<Unit>) {
  private val myTicks = MutableSharedFlow<Pair<Int, Any?>>()
  private var mySubscriptions: AtomicReference<List<Any>> = AtomicReference(emptyList())
  init {
    coroutineScope.launch {
      var iteration = 0
      sourceTicks.collect {
        val stepSubscriptions: List<Any> = mySubscriptions.get()
        for (id in stepSubscriptions) {
          myTicks.emit(iteration to id)
          myTicks.emit(iteration to null)
        }
        iteration++
      }
    }
  }
  fun subscribe(): Flow<Int> {
    return flow {
      val myId = Object()
      mySubscriptions.getAndUpdate { it + myId }
      myTicks.filter { (iteration, index) -> index == myId }.map { (iteration, index) -> iteration }.onCompletion {
        mySubscriptions.getAndUpdate { it - myId }
      }.collect {
        emit(it)
      }
    }
  }
  /**
   * Replaces synchronous first tick to individual one.
   */
  fun subscribeImmediately(): Flow<Int> {
    return flowOf(-1).onCompletion { if (it == null) emitAll(subscribe().drop(1)) }
  }
}

/**
 * Turns off when any of [pauseToWakeUpDelay] if true,
 * turns on when all of [pauseToWakeUpDelay] become false (after delay corresponding to latest changed).
 * If [pauseToWakeUpDelay] is not [StateFlow], sending subsequent false value just activates the delay.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.pausedBy(vararg pauseToWakeUpDelay: Pair<Flow<Boolean>, Duration>): Flow<T> {
  val resultingWakeUps = flow {
    val mergedWakeUps = pauseToWakeUpDelay.mapIndexed { index, (pauseFlow, wakeUpDelay) ->
      pauseFlow.map { IndexedValue(index, it) }
    }.merge()
    val pausedState = BooleanArray(pauseToWakeUpDelay.size) { true }
    mergedWakeUps.collect { (index, paused) ->
      pausedState[index] = paused
      if (!pausedState.any { it }) {
        emit(index)
      }
      else {
        emit(null)
      }
    }
  }
  return flow {
    coroutineScope {
      val channel = Channel<T>()
      val backChannel = Channel<Unit>()
      launch {
        resultingWakeUps.collectLatest { index ->
          if (index != null) {
            delay(pauseToWakeUpDelay[index].second)
            this@pausedBy.collect { tick ->
              withContext(NonCancellable) {
                channel.send(tick)
                backChannel.receive()
              }
            }
          }
        }
      }
      channel.consumeAsFlow().collect {
        try {
          emit(it)
        }
        finally {
          backChannel.send(Unit)
        }
      }
    }
  }
}