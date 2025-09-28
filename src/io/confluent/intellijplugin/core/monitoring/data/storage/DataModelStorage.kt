package io.confluent.intellijplugin.core.monitoring.data.storage

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import io.confluent.intellijplugin.core.monitoring.data.model.DataModel
import io.confluent.intellijplugin.core.monitoring.data.updater.BdtMonitoringUpdater
import io.confluent.intellijplugin.core.util.executeOnPooledThread
import java.util.concurrent.atomic.AtomicBoolean

abstract class DataModelStorage(val updater: BdtMonitoringUpdater) : Disposable {
  private val isDisposed = AtomicBoolean(false)

  @Suppress("LeakingThis")
  private val alarm: Alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

  protected fun init() {
    updater.registryStorage(this)
    Disposer.register(this, Disposable {
      updater.removeStorage(this)
    })

    executeOnPooledThread {
      clearNotUsed()
    }
  }

  abstract fun getModelsForRefresh(): List<DataModel<*>>
  abstract fun getAsMap(): Map<*, DataModel<*>>
  abstract fun clearSelected(list: List<Any?>)

  override fun dispose() {
    isDisposed.set(true)
  }

  private fun clearNotUsed() {
    if (isDisposed.get())
      return
    doRemoveNotUsed()
    if (isDisposed.get())
      return
    if (alarm.isDisposed)
      return
    alarm.addRequest({ clearNotUsed() },
                     10 * 1000,
                     true)
  }

  private fun doRemoveNotUsed() {
    val readyForDispose = getAsMap().entries.filter { it.value.isReadyForDispose() }
    readyForDispose.forEach {
      Disposer.dispose(it.value)
    }
    clearSelected(readyForDispose.map { it.key })
  }
}