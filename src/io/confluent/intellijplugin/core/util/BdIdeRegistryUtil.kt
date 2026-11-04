package io.confluent.intellijplugin.core.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object BdIdeRegistryUtil {

    val RFS_CACHE_SIZE: Int
        get() = getInt("bdide.rfs.cache.size", 1000)

    val RFS_LOAD_SHOW_PROGRESS: Boolean
        get() = getBoolean("bdide.rfs.load.show.progress", false)

    val RFS_DEFAULT_TIMEOUT: Int
        get() = getInt("bdide.rfs.operation.default.timeout", 15_000)

    /** Per-bootstrap-URL timeout of the Kafka TCP reachability probe. Declared in plugin.xml so it can be changed from the Registry. */
    val KAFKA_TCP_PROBE_CONNECT_TIMEOUT_MS: Int
        get() = getInt("bdide.kafka.tcp.probe.connect.timeout.ms", 20_000).coerceAtLeast(1_000)

    /** Total budget of the Kafka TCP reachability probe across all bootstrap URLs. */
    val KAFKA_TCP_PROBE_TOTAL_TIMEOUT_MS: Int
        get() = getInt("bdide.kafka.tcp.probe.total.timeout.ms", 30_000).coerceAtLeast(1_000)

    val DRIVER_AUTO_FILES_RELOAD_ENABLED: Boolean
        get() = if (!ApplicationManager.getApplication().isUnitTestMode) {
            val javaPropValue =
                System.getProperty("bdide.disable.hdfs.autorefresh")?.ifBlank { null }?.toBooleanStrictOrNull()?.not()
            javaPropValue ?: try {
                Registry.get("bdide.rfs.auto.files.list.reload.enabled").asBoolean()
            } catch (t: Throwable) {
                true
            }
        } else
            false

    val DRIVER_AUTO_FILES_RELOAD_PERIOD: Int
        get() = if (!ApplicationManager.getApplication().isUnitTestMode) {
            val javaPropValue = System.getProperty("bdt.rfs.refresh.driver")?.ifBlank { null }?.toIntOrNull()
            javaPropValue ?: try {
                Registry.get("bdide.rfs.auto.files.list.reload.period").asInteger()
            } catch (t: Throwable) {
                300
            }
        } else
            0

    val DRIVER_AUTO_REFRESH_PERIOD: Int
        get() = getInt("bdide.rfs.auto.refresh.period", 60)

    fun isInternalFeaturesAvailable(): Boolean = getBoolean("bdide.is.internal.features.available", false, true)

    fun minimalRefreshDriverTime(): Duration =
        getInt("bdide.driver.minimal.refresh.time.ms", default = 1000, testValue = 0).milliseconds

    /** Upper bound for a Schema Registry write awaited by the caller. Declared in plugin.xml so it can be changed from the Registry. */
    val SCHEMA_REGISTRY_CALL_TIMEOUT_MS: Int
        get() = getInt("bdide.kafka.schema.registry.call.timeout.ms", 180_000).coerceAtLeast(1_000)

    private fun getInt(key: String, default: Int, testValue: Int = default) =
        if (ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode)
            testValue
        else {
            try {
                Registry.get(key).asInteger()
            } catch (t: Throwable) {
                thisLogger().warn("Cannot load $key registry value. Use default")
                default
            }
        }

    private fun getBoolean(key: String, default: Boolean, testValue: Boolean = default) =
        if (ApplicationManager.getApplication().isUnitTestMode)
            testValue
        else {
            try {
                Registry.`is`(key, default)
            } catch (t: Throwable) {
                default
            }
        }
}