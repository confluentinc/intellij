package io.confluent.intellijplugin.core.monitoring.data.updater

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import io.confluent.intellijplugin.core.monitoring.connection.MonitoringClient
import io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager
import io.confluent.intellijplugin.core.monitoring.data.model.DataModel
import io.confluent.intellijplugin.core.monitoring.data.model.ObjectDataModel
import io.confluent.intellijplugin.core.monitoring.data.storage.DataModelStorage
import io.confluent.intellijplugin.core.monitoring.data.storage.RootDataModelStorage
import io.confluent.intellijplugin.core.util.executeOnPooledThread
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

class BdtMonitoringUpdater(val dataManager: MonitoringDataManager) : Disposable {
    private var listeners = listOf<MonitoringUpdateListener>()

    val isDisposed = AtomicBoolean(false)

    val client: MonitoringClient
        get() = dataManager.client

    private val alarm: Alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    private val registeredStorages = mutableListOf<DataModelStorage>()

    private val executingTask = ConcurrentSkipListSet<UpdateTask>()

    val models
        get() = registeredStorages.sortedBy { it !is RootDataModelStorage }.flatMap { it.getModelsForRefresh() }

    override fun dispose() {
        isDisposed.set(true)
    }

    fun cancelCurrent() {
        executingTask.forEach {
            it.isCanceled.set(true)
        }
    }

    fun registryStorage(storage: DataModelStorage) {
        registeredStorages.add(storage)
        invokeRefreshModels(storage.getModelsForRefresh())
    }

    fun removeStorage(storage: DataModelStorage) {
        registeredStorages.remove(storage)
    }


    fun invokeRefreshModel(dataModel: DataModel<*>) = invokeRefreshModels(listOf(dataModel))

    fun invokeRefreshModels(dataModels: List<DataModel<*>>) = executeOnPooledThread {
        executeTask(UpdateTask { innerRefresh(dataModels, it, withConnectionCheck = false) })
    }


    fun syncRefreshModels(dataModels: List<DataModel<*>>) {
        executeTask(UpdateTask { innerRefresh(dataModels, it, withConnectionCheck = false) })
    }


    internal fun reloadAll(checkConnection: Boolean = true) {
        if (isDisposed.get())
            return

        if (client.isConnecting())
            return

        stopAll()

        executeTask(UpdateTask { innerRefresh(models, it, withConnectionCheck = checkConnection) })
        rescheduleAll()
    }

    fun stopAll() {
        alarm.cancelAllRequests()
        executingTask.forEach { it.isCanceled.set(true) }
        notify {
            it.onEnd(null)
        }
    }

    fun rescheduleAll() = invokeLater {
        alarm.cancelAllRequests()

        if (isDisposed.get())
            return@invokeLater

        if (dataManager.settings.dataUpdateIntervalMillis > 0)
            alarm.addRequest(
                { runBlockingMaybeCancellable { reloadAll() } },
                dataManager.settings.dataUpdateIntervalMillis,
                true
            )
    }


    private fun innerRefresh(
        models: List<DataModel<*>>,
        isCurrentCanceled: AtomicBooleanProperty,
        withConnectionCheck: Boolean = false
    ) {
        val toUpdate = models.filter { it.isRequireToUpdate() }

        if (toUpdate.isEmpty() && !withConnectionCheck)
            return

        if (!client.isInited())
            return

        if (client.isConnecting())
            return

        if (client.connectionError != null) {
            models.forEach { it.setError(client.connectionError) }
            return
        }

        val id = System.identityHashCode(toUpdate)
        isCurrentCanceled.set(false)

        try {
            val connectionError = client.connectionError
            if (connectionError != null) {
                models.forEach { it.setError(connectionError) }
            }

            notify {
                it.onStartRefreshModels(id, toUpdate)
            }

            if (withConnectionCheck) {
                notify {
                    it.setIntermediate(id, true)
                }
                notify {
                    it.setText(id, KafkaMessagesBundle.message("monitoring.progress.check.connection"))
                }

                client.checkConnection()

                if (!client.isConnected()) {
                    models.forEach { it.setError(connectionError) }
                    return
                }
            }

            if (isCurrentCanceled.get()) {
                return
            }
            notify {
                it.setIntermediate(id, false)
            }


            toUpdate.withIndex().forEach { indexedValue ->
                notify {
                    it.setProgress(id, (indexedValue.index).toDouble() / (toUpdate.size))
                }
                notify {
                    it.setText(
                        id,
                        KafkaMessagesBundle.message("monitoring.progress.update.data", indexedValue.index + 1)
                    )
                }
                indexedValue.value.update()
                if (isCurrentCanceled.get()) {
                    return
                }
            }

            val modelsForAdditionalUpdate = toUpdate
                .filterIsInstance<ObjectDataModel<*>>()
                .filter { it.additionalInfoLoading != null }

            val disposable = Disposer.newDisposable(this)
            isCurrentCanceled.afterChange {
                Disposer.dispose(disposable)
                notify {
                    it.onEnd(id)
                }
            }

            runBlockingMaybeCancellable {
                val jobs = modelsForAdditionalUpdate.mapNotNull {
                    val job = it.launchAdditionalUpdate(this)
                    job?.cancelOnDispose(disposable)
                    job
                }
                jobs.forEach { it.join() }
            }
        } catch (t: Throwable) {
            models.forEach {
                it.setError(t, msg = KafkaMessagesBundle.message("monitoring.updater.driver.error"))
            }
        } finally {
            notify { it.onEnd(id) }
        }
    }

    fun addListener(listener: MonitoringUpdateListener) {
        listeners += listener
    }

    fun removeListener(listener: MonitoringUpdateListener) {
        listeners -= listener
    }

    internal fun notify(listenerFun: (MonitoringUpdateListener) -> Unit) = invokeLater {
        listeners.forEach {
            listenerFun(it)
        }
    }

    private fun executeTask(task: UpdateTask) {
        executingTask.add(task)
        try {
            task.perform()
        } catch (t: Throwable) {
            thisLogger().warn(t)
        } finally {
            executingTask.remove(task)
        }
    }

    @RequiresBackgroundThread
    fun checkConnectionOrRefresh(
        calledByUser: Boolean,
        prepareConnection: () -> Unit
    ) {
        if (client.isConnecting())
            return
        stopAll()

        notify {
            it.onStartRefreshConnection()
        }

        try {
            if (client.isConnected()) {
                client.checkConnection()
            } else {
                client.connect(calledByUser, prepareConnection)
            }
        } finally {
            notify {
                it.onEnd(null)
            }
        }
    }

    class UpdateTask(val id: Int = Random.nextInt(), val update: (AtomicBooleanProperty) -> Unit) :
        Comparable<UpdateTask> {
        val isCanceled: AtomicBooleanProperty = AtomicBooleanProperty(false)

        fun perform() {
            update(isCanceled)
        }

        override fun compareTo(other: UpdateTask) = id.compareTo(other.id)
    }
}