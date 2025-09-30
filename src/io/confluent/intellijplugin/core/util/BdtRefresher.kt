package io.confluent.intellijplugin.core.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.flow.mapStateIn
import io.confluent.intellijplugin.core.rfs.driver.Driver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.awt.Window
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class BdtRefresher(
  disposable: Disposable,
  driver: Driver,
  updateDelay: Int,
  runOnlyInActiveFrame: Boolean,
  startupDelay: Int = 0,
  private val isPaused: MutableStateFlow<Boolean> = MutableStateFlow(true),
  private val coroutineScope: CoroutineScope = driver.safeExecutor.coroutineScope.childScope(),
  delayTicks: Flow<Unit> = BdtRefresherService.delayTicks(updateDelay.milliseconds),
  requests: Flow<Unit> = if (runOnlyInActiveFrame) {
    delayTicks.pausedBy(
      isPaused to startupDelay.milliseconds,
      BdtRefresherService.getInstance().applicationIsActive.mapStateIn(coroutineScope) { !it } to Duration.ZERO
    )
  }
  else {
    delayTicks.pausedBy(
      isPaused to startupDelay.milliseconds
    )
  },
  body: suspend () -> Unit
) : Disposable {

  init {
    Disposer.register(disposable, this)
    coroutineScope.launch {
      delay(startupDelay.milliseconds)
      requests.collect {
        body()
      }
    }
  }

  override fun dispose() {
    coroutineScope.cancel()
  }

  fun startIfRequired() {
    isPaused.value = false
  }

  fun stopIfRequired() {
    isPaused.value = true
  }
}

@Service
class BdtRefresherService(val coroutineScope: CoroutineScope) {
  val driverRefreshIntervalSetting =
    BdIdeRegistryUtil.DRIVER_AUTO_REFRESH_PERIOD.seconds
  val driverFileRefreshIntervalSetting =
    BdIdeRegistryUtil.DRIVER_AUTO_FILES_RELOAD_PERIOD.seconds.takeIf { BdIdeRegistryUtil.DRIVER_AUTO_FILES_RELOAD_ENABLED }

  val applicationIsActive: StateFlow<Boolean> by lazy {
    val result = MutableStateFlow(true)
    val activationListener = object : ApplicationActivationListener {
      override fun applicationActivated(ideFrame: IdeFrame) {
        result.value = true
      }
      override fun delayedApplicationDeactivated(ideFrame: Window) {
        result.value = false
      }
    }
    val application = ApplicationManager.getApplication()
    application.messageBus.connect(coroutineScope).subscribe(ApplicationActivationListener.TOPIC, activationListener)
    result
  }

  private val commonSchedule: RefreshTicker = RefreshTicker(coroutineScope, delayTicks(driverRefreshIntervalSetting).onEach {
    applicationIsActive.first { it }
  })

  val driverRefreshSchedule: RefreshTicker =
    RefreshTicker(coroutineScope, commonSchedule.subscribe().map { Unit })

  val driverFileRefreshSchedule: RefreshTicker? =
    if (driverFileRefreshIntervalSetting != null) {
      val throttled = flow {
        var timestamp = TimeSource.Monotonic.markNow()
        commonSchedule.subscribe().collect {
          if (timestamp.elapsedNow() > driverFileRefreshIntervalSetting) {
            emit(Unit)
          }
        }
      }
      RefreshTicker(coroutineScope, throttled)
    }
    else null

  companion object {

    fun delayTicks(duration: Duration) = flow {
      while (true) {
        emit(Unit)
        delay(duration)
      }
    }

    fun getInstance(): BdtRefresherService = service()
  }
}
