package io.confluent.intellijplugin.core.monitoring.data.model

import io.confluent.intellijplugin.core.monitoring.data.listener.DataModelListener
import io.confluent.intellijplugin.core.monitoring.data.listener.ListenersChangeListener
import kotlin.reflect.KProperty1

abstract class ObjectDataModelBase<T : RemoteInfo>(
    private val idFieldName: KProperty1<T, Any?>,
    allowAutoRefresh: Boolean = true
) : DataModel<List<T>>(allowAutoRefresh) {
    private var listenerChangeListeners = listOf<ListenersChangeListener>()

    override fun dispose() {}

    fun getId(info: T): String = idFieldName.get(info).toString()

    val size: Int
        get() = data?.size ?: 0

    val entries: List<T>
        get() = data ?: emptyList()

    operator fun get(i: Int): T? = if (i in 0 until size) data?.get(i) else null

    fun addListenersChangeListener(listener: ListenersChangeListener) {
        listenerChangeListeners = listenerChangeListeners + listener
    }

    fun removeListenersChangeListener(listener: ListenersChangeListener) {
        listenerChangeListeners = listenerChangeListeners - listener
    }

    override fun addListener(listener: DataModelListener) {
        val wasEmpty = listeners.isEmpty()
        super.addListener(listener)
        if (wasEmpty)
            listenerChangeListeners.forEach { it.firstListenerSubscribed() }
    }

    override fun removeListener(listener: DataModelListener) {
        val wasNotEmpty = listeners.isNotEmpty()
        super.removeListener(listener)
        if (wasNotEmpty && listeners.isEmpty()) {
            listenerChangeListeners.forEach { it.lastListenerUnsubscribed() }
        }
    }
}