package io.confluent.intellijplugin.core.monitoring.data.model

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.core.monitoring.data.listener.DataModelListener
import io.confluent.intellijplugin.util.KafkaMessagesBundle

abstract class DataModel<T>(val allowAutoUpdate: Boolean = true) : Disposable {
  abstract val updater: (DataModel<*>) -> Pair<T, Boolean>


  protected var listeners = emptyList<DataModelListener>()
  private var lastListenerRemovedTimestamp: Long = System.currentTimeMillis()

  /** Flag is set on first attempt to initialize DataModel both via setError() or setData(). */
  var isInitedByFirstTime = false
    private set

  var data: T? = null
    private set

  var loadMore: Boolean = false

  var additionalLoadError: Throwable? = null
    set(value) {
      val oldValue = field
      field = value
      if (oldValue != value)
        notifyErrorLoadAdditionalInfo(value)
    }

  var filters: FilterModel = FilterModel()

  /** Error is set by setError() and removed on setData() call. */
  var error: DataModelError? = null
    private set

  fun subscribeOn(dataModel: DataModel<*>) {
    val listener = object : DataModelListener {
      override fun onChanged() {
        update()
      }

      override fun onError(msg: String, e: Throwable?) {
        setError(e, msg)
      }
    }

    Disposer.register(this, Disposable {
      dataModel.removeListener(listener)
    })
    dataModel.addListener(listener)
  }

  override fun dispose() {}

  fun setData(newValue: T) {
    error = null

    if (data == newValue) {
      return
    }

    notifyBeforeChanged()
    isInitedByFirstTime = true
    data = newValue
    notifyOnChanged()
  }

  fun setError(e: Throwable? = null, msg: String = KafkaMessagesBundle.message("monitoring.updater.error")) {
    thisLogger().debug(msg, e)

    val newError = DataModelError(msg, e)
    if (newError == error)
      return

    error = newError

    isInitedByFirstTime = true
    notifyBeforeChanged()
    data = null
    notifyOnError(msg, e)
  }

  open fun addListener(listener: DataModelListener) = synchronized(this) {
    listeners = listeners + listener
    lastListenerRemovedTimestamp = -1
  }

  open fun removeListener(listener: DataModelListener) = synchronized(this) {
    listeners = listeners - listener
    if (listeners.isEmpty()) {
      lastListenerRemovedTimestamp = System.currentTimeMillis()
    }
  }

  private fun timeWithoutListeners(): Long = synchronized(this) {
    if (lastListenerRemovedTimestamp == -1L || listeners.isNotEmpty()) return@synchronized -1

    System.currentTimeMillis() - lastListenerRemovedTimestamp
  }

  fun isReadyForDispose() = timeWithoutListeners() > INVALIDATION_TIME
  fun isRequireToUpdate() = !isInitedByFirstTime || listeners.isNotEmpty()

  fun update() {
    try {
      val res = updater(this)
      setData(res.first)
      loadMore = res.second
      notifyLoadMore()
    }
    catch (t: Throwable) {
      setError(t)
      loadMore = false
    }
    finally {
      notifyLoadMore()
    }
  }

  private fun notifyErrorLoadAdditionalInfo(value: Throwable?) = invokeLater {
    listeners.forEach {
      it.onErrorAdditionalLoad(value)
    }
  }

  private fun notifyLoadMore() {
    listeners.forEach {
      it.onLoadMoreNonEdt()
    }
    invokeLater {
      listeners.forEach {
        it.onLoadMore()
      }
    }
  }


  private fun notifyBeforeChanged() {
    listeners.forEach {
      it.beforeChanged()
    }
  }

  protected fun notifyOnChanged() {
    listeners.forEach {
      it.onChangedNonEdt()
    }
    invokeLater {
      listeners.forEach {
        it.onChanged()
      }
    }
  }

  private fun notifyOnError(msg: String, e: Throwable?) {
    listeners.forEach {
      it.onError(msg, e)
    }
  }

  companion object {
    private const val INVALIDATION_TIME = 60 * 1000
  }
}