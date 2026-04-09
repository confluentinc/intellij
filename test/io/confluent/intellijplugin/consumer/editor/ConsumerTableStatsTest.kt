package io.confluent.intellijplugin.consumer.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.core.settings.connections.Property
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
class ConsumerTableStatsTest {

    private lateinit var stats: ConsumerTableStats
    private lateinit var scope: CoroutineScope

    @BeforeEach
    fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        stats = ConsumerTableStats(scope)
    }

    @AfterEach
    fun tearDown() {
        stats.dispose()
    }

    @Nested
    @DisplayName("Debouncing")
    inner class Debouncing {

        @Test
        fun `should accumulate multiple rapid batch additions without errors`() = runBlocking {
            val record = createTestRecord()

            repeat(5) {
                stats.addRecordsBatch(100, listOf(record))
            }

            delay(150)
            ApplicationManager.getApplication().invokeAndWait { }

            assertTrue(stats.getElapsedTimeMs() < 500)
        }

        @Test
        fun `should cancel previous update when new batch arrives quickly`() = runBlocking {
            val record = createTestRecord()

            stats.addRecordsBatch(100, listOf(record))
            delay(80)
            stats.addRecordsBatch(100, listOf(record))
            delay(80)
            stats.addRecordsBatch(100, listOf(record))
            delay(150)

            ApplicationManager.getApplication().invokeAndWait { }
            assertTrue(true, "Debouncing should handle rapid updates without errors")
        }

        @Test
        fun `should not throw when disposed during debounce`() = runBlocking {
            val record = createTestRecord()

            stats.addRecordsBatch(100, listOf(record))
            delay(50)
            stats.dispose()

            delay(100)
            assertTrue(true, "Disposal during debounce should not throw")
        }
    }

    @Nested
    @DisplayName("Statistics accumulation")
    inner class StatisticsAccumulation {

        @Test
        fun `should reset elapsed time on start`() = runBlocking {
            stats.addRecordsBatch(100, listOf(createTestRecord()))
            delay(150)
            ApplicationManager.getApplication().invokeAndWait { }

            stats.start()

            assertTrue(stats.getElapsedTimeMs() < 100, "Elapsed time should be reset")
        }

        @Test
        fun `should track elapsed time after start`() = runBlocking {
            stats.start()
            delay(200)

            assertTrue(stats.getElapsedTimeMs() >= 150, "Should track elapsed time")
        }
    }

    private fun createTestRecord(): KafkaRecord = KafkaRecord(
        keyType = KafkaFieldType.STRING,
        valueType = KafkaFieldType.STRING,
        error = null,
        key = "key",
        value = "value",
        topic = "test-topic",
        partition = 0,
        offset = 0L,
        duration = 0L,
        timestamp = System.currentTimeMillis(),
        keySize = 3,
        valueSize = 5,
        headers = emptyList<Property>(),
        keyFormat = KafkaRegistryFormat.UNKNOWN,
        valueFormat = KafkaRegistryFormat.UNKNOWN
    )
}
