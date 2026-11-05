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
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import org.jetbrains.annotations.VisibleForTesting

class BdtMonitoringUpdater(val dataManager: MonitoringDataManager) : Disposable {
    private var listeners = listOf<MonitoringUpdateListener>()

    val isDisposed = AtomicBoolean(false)

    val client: MonitoringClient
        get() = dataManager.client

    private val alarm: Alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    private val registeredStorages = mutableListOf<DataModelStorage>()

    private val executingTask = ConcurrentSkipListSet<UpdateTask>()

    /**
     * Consecutive refresh cycles whose connection was unreachable; stretches the next delay via [backoffInterval].
     * Cancelled cycles leave it unchanged.
     */
    private val consecutiveFailures = AtomicInteger(0)

    internal val consecutiveFailureCount: Int
        @VisibleForTesting get() = consecutiveFailures.get()

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


    fun invokeRefreshModel(dataModel: DataModel<*>?) {
        dataModel?.let { invokeRefreshModels(listOf(it)) }
    }

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

        var outcome = RefreshOutcome.SKIPPED
        executeTask(UpdateTask {
            outcome = innerRefresh(models, it, withConnectionCheck = checkConnection)
        })
        when (outcome) {
            RefreshOutcome.SUCCEEDED -> consecutiveFailures.set(0)
            RefreshOutcome.FAILED -> consecutiveFailures.incrementAndGet()
            RefreshOutcome.SKIPPED -> Unit // cancelled or not run: say nothing about the connection's health
        }
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

        val interval = dataManager.settings.dataUpdateIntervalMillis
        if (interval > 0)
            alarm.addRequest(
                { runBlockingMaybeCancellable { reloadAll() } },
                jitteredInterval(backoffInterval(interval, consecutiveFailures.get())),
                true
            )
    }


    private fun innerRefresh(
        models: List<DataModel<*>>,
        isCurrentCanceled: AtomicBooleanProperty,
        withConnectionCheck: Boolean = false
    ): RefreshOutcome {
        val toUpdate = models.filter { it.isRequireToUpdate() }

        if (toUpdate.isEmpty() && !withConnectionCheck)
            return RefreshOutcome.SUCCEEDED

        if (!client.isInited())
            return RefreshOutcome.SKIPPED

        if (client.isConnecting())
            return RefreshOutcome.SKIPPED

        // With a connection check, fall through and re-probe a failed connection so auto-refresh can recover it.
        val connectionError = client.connectionError
        if (connectionError != null && !withConnectionCheck) {
            models.forEach { it.setError(connectionError) }
            return RefreshOutcome.FAILED
        }

        val id = System.identityHashCode(toUpdate)
        isCurrentCanceled.set(false)

        try {
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

                // Throws (caught below) unless the connection is healthy. A failed client may have torn down state
                // that only connect() rebuilds (e.g. KafkaClient's admin client), so reconnect instead of re-checking,
                // as checkConnectionOrRefresh does.
                if (client.isConnected()) client.checkConnection() else client.connect(calledByUser = false)
            }

            if (isCurrentCanceled.get()) {
                return RefreshOutcome.SKIPPED
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
                    return RefreshOutcome.SKIPPED
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

            // Per-model errors (ACLs, an unreachable Schema Registry, ...) are deliberately not counted: backoff is
            // per connection, so one permanently failing model would otherwise slow every healthy view with it.
            return if (isCurrentCanceled.get()) RefreshOutcome.SKIPPED else RefreshOutcome.SUCCEEDED
        } catch (t: Throwable) {
            models.forEach {
                it.setError(t, msg = KafkaMessagesBundle.message("monitoring.updater.driver.error"))
            }
            return if (t is CancellationException || isCurrentCanceled.get()) RefreshOutcome.SKIPPED
            else RefreshOutcome.FAILED
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

    companion object {
        /** Max fraction by which each auto-refresh delay is randomly stretched or shrunk. */
        private const val JITTER_FRACTION = 0.2

        /**
         * Returns [intervalMillis] scaled by a random factor in `[1 - JITTER_FRACTION, 1 + JITTER_FRACTION)`, so
         * many open connections sharing the same refresh interval don't all hit their clusters in lockstep.
         */
        internal fun jitteredInterval(intervalMillis: Int, random: Random = Random.Default): Int {
            val factor = 1 + random.nextDouble(-JITTER_FRACTION, JITTER_FRACTION)
            return (intervalMillis * factor).toInt().coerceAtLeast(1)
        }

        /** Max number of doublings applied by [backoffInterval], i.e. failed refreshes wait at most 8x the interval. */
        private const val MAX_BACKOFF_DOUBLINGS = 3

        /**
         * Ceiling on a backed-off delay, applied before jitter so failing connections don't all re-sync on the cap
         * (the scheduled delay can land up to [JITTER_FRACTION] above it). Never shortens a configured interval
         * that is already longer.
         */
        internal const val MAX_BACKOFF_MILLIS = 5 * 60_000

        /**
         * Returns [intervalMillis] doubled once per consecutive failure, capped at [MAX_BACKOFF_DOUBLINGS] doublings
         * and at [MAX_BACKOFF_MILLIS] (or the interval itself, if longer), so a broken connection isn't hammered at
         * the normal refresh cadence.
         */
        internal fun backoffInterval(intervalMillis: Int, consecutiveFailures: Int): Int {
            val doublings = consecutiveFailures.coerceIn(0, MAX_BACKOFF_DOUBLINGS)
            val ceiling = maxOf(intervalMillis, MAX_BACKOFF_MILLIS).toLong()
            return (intervalMillis.toLong() shl doublings).coerceAtMost(ceiling).toInt()
        }
    }

    /** How a refresh cycle ended, for [consecutiveFailures]: only [FAILED] and [SUCCEEDED] change the counter. */
    private enum class RefreshOutcome { SUCCEEDED, FAILED, SKIPPED }

    class UpdateTask(val id: Int = Random.nextInt(), val update: (AtomicBooleanProperty) -> Unit) :
        Comparable<UpdateTask> {
        val isCanceled: AtomicBooleanProperty = AtomicBooleanProperty(false)

        fun perform() {
            update(isCanceled)
        }

        override fun compareTo(other: UpdateTask) = id.compareTo(other.id)
    }
}