package io.confluent.intellijplugin.util

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.consumer.models.ConsumerStartType
import io.confluent.intellijplugin.consumer.models.ConsumerStartWith
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.model.BdtTopicPartition
import io.confluent.intellijplugin.model.InternalReplica
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

@TestApplication
@DisplayName("KafkaOffsetUtils.calculateOffsets")
class KafkaOffsetUtilsCalculateOffsetsTest {

    private lateinit var dataManager: KafkaDataManager

    @BeforeEach
    fun setUp() {
        dataManager = mock()
    }

    private fun createPartition(
        topic: String = "test-topic",
        partitionId: Int = 0,
        startOffset: Long? = 0L,
        endOffset: Long? = 100L,
        leader: Int? = 1,
        internalReplicas: List<InternalReplica> = emptyList(),
        inSyncReplicasCount: Int = 1,
        replicas: String = "1"
    ): BdtTopicPartition {
        return BdtTopicPartition(
            topic = topic,
            partitionId = partitionId,
            leader = leader,
            internalReplicas = internalReplicas,
            inSyncReplicasCount = inSyncReplicasCount,
            replicas = replicas,
            endOffset = endOffset,
            startOffset = startOffset
        )
    }

    @Nested
    @DisplayName("Offset-based start types")
    inner class OffsetBasedStartTypes {

        @Test
        fun `should return startOffset for THE_BEGINNING`() = runBlocking {
            val partition = createPartition(startOffset = 0L, endOffset = 100L)
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.THE_BEGINNING, time = null, offset = null, consumerGroup = null
            )
            val result = KafkaOffsetUtils.calculateOffsets(listOf(partition), startWith, dataManager)
            val expectedKey = TopicPartition("test-topic", 0)
            assertEquals(1, result.size)
            assertEquals(0L, result[expectedKey]?.offset())
        }

        @Test
        fun `should return endOffset for NOW`() = runBlocking {
            val partition = createPartition(startOffset = 0L, endOffset = 100L)
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.NOW, time = null, offset = null, consumerGroup = null
            )
            val result = KafkaOffsetUtils.calculateOffsets(listOf(partition), startWith, dataManager)
            val expectedKey = TopicPartition("test-topic", 0)
            assertEquals(1, result.size)
            assertEquals(100L, result[expectedKey]?.offset())
        }

        @Test
        fun `should calculate startOffset plus user offset for OFFSET`() = runBlocking {
            val partition = createPartition(startOffset = 10L, endOffset = 100L)
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.OFFSET, time = null, offset = 5L, consumerGroup = null
            )
            val result = KafkaOffsetUtils.calculateOffsets(listOf(partition), startWith, dataManager)
            val expectedKey = TopicPartition("test-topic", 0)
            assertEquals(1, result.size)
            assertEquals(15L, result[expectedKey]?.offset())
        }

        @Test
        fun `should throw KafkaOffsetException when OFFSET exceeds endOffset`() {
            val partition = createPartition(startOffset = 0L, endOffset = 100L)
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.OFFSET, time = null, offset = 150L, consumerGroup = null
            )
            assertThrows(KafkaOffsetException::class.java) {
                runBlocking {
                    KafkaOffsetUtils.calculateOffsets(listOf(partition), startWith, dataManager)
                }
            }
        }

        @Test
        fun `should calculate endOffset minus X for LATEST_OFFSET_MINUS_X`() = runBlocking {
            val partition = createPartition(startOffset = 0L, endOffset = 100L)
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.LATEST_OFFSET_MINUS_X, time = null, offset = 20L, consumerGroup = null
            )
            val result = KafkaOffsetUtils.calculateOffsets(listOf(partition), startWith, dataManager)
            val expectedKey = TopicPartition("test-topic", 0)
            assertEquals(1, result.size)
            assertEquals(80L, result[expectedKey]?.offset())
        }

        @Test
        fun `should throw KafkaOffsetException when LATEST_OFFSET_MINUS_X goes below startOffset`() {
            val partition = createPartition(startOffset = 50L, endOffset = 100L)
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.LATEST_OFFSET_MINUS_X, time = null, offset = 60L, consumerGroup = null
            )
            assertThrows(KafkaOffsetException::class.java) {
                runBlocking {
                    KafkaOffsetUtils.calculateOffsets(listOf(partition), startWith, dataManager)
                }
            }
        }

        @Test
        fun `should handle multiple partitions with independent bounds`() = runBlocking {
            val partition1 = createPartition(topic = "topic1", partitionId = 0, startOffset = 0L, endOffset = 50L)
            val partition2 = createPartition(topic = "topic2", partitionId = 1, startOffset = 10L, endOffset = 100L)
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.OFFSET, time = null, offset = 5L, consumerGroup = null
            )
            val result = KafkaOffsetUtils.calculateOffsets(listOf(partition1, partition2), startWith, dataManager)
            assertEquals(2, result.size)
            assertEquals(5L, result[TopicPartition("topic1", 0)]?.offset())
            assertEquals(15L, result[TopicPartition("topic2", 1)]?.offset())
        }
    }

    @Nested
    @DisplayName("Timestamp-based start types")
    inner class TimestampBasedStartTypes {

        @Test
        fun `should use dataManager getOffsetsForData for timestamp-based types`() = runBlocking {
            val partition = createPartition(startOffset = 0L, endOffset = 100L)
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.LAST_HOUR, time = null, offset = null, consumerGroup = null
            )
            val topicPartition = TopicPartition("test-topic", 0)
            whenever(dataManager.getOffsetsForData(any(), any()))
                .thenReturn(mapOf(topicPartition to 42L))
            val result = KafkaOffsetUtils.calculateOffsets(listOf(partition), startWith, dataManager)
            assertEquals(1, result.size)
            assertEquals(42L, result[topicPartition]?.offset())
            verify(dataManager, times(1)).getOffsetsForData(any(), any())
        }

        @Test
        fun `should fall back to startOffset when getOffsetsForData returns -1`() = runBlocking {
            val partition = createPartition(startOffset = 25L, endOffset = 100L)
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.TODAY, time = null, offset = null, consumerGroup = null
            )
            val topicPartition = TopicPartition("test-topic", 0)
            whenever(dataManager.getOffsetsForData(any(), any()))
                .thenReturn(mapOf(topicPartition to -1L))
            val result = KafkaOffsetUtils.calculateOffsets(listOf(partition), startWith, dataManager)
            assertEquals(1, result.size)
            assertEquals(25L, result[topicPartition]?.offset())
        }
    }

    @Nested
    @DisplayName("Error cases")
    inner class ErrorCases {

        @Test
        fun `should throw error when endOffset is null`() {
            val partition = createPartition(startOffset = 0L, endOffset = null)
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.NOW, time = null, offset = null, consumerGroup = null
            )
            val exception = assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    KafkaOffsetUtils.calculateOffsets(listOf(partition), startWith, dataManager)
                }
            }
            assertEquals("Cannot detect latest offset", exception.message)
        }

        @Test
        fun `should throw error when startOffset is null`() {
            val partition = createPartition(startOffset = null, endOffset = 100L)
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.THE_BEGINNING, time = null, offset = null, consumerGroup = null
            )
            val exception = assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    KafkaOffsetUtils.calculateOffsets(listOf(partition), startWith, dataManager)
                }
            }
            assertEquals("Cannot detect beginning offset", exception.message)
        }

        @Test
        fun `should throw error for CONSUMER_GROUP type`() {
            val partition = createPartition(startOffset = 0L, endOffset = 100L)
            val startWith = ConsumerStartWith(
                type = ConsumerStartType.CONSUMER_GROUP, time = null, offset = null, consumerGroup = "test-group"
            )
            val exception = assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    KafkaOffsetUtils.calculateOffsets(listOf(partition), startWith, dataManager)
                }
            }
            assertEquals("Internal error. Must be not invoked", exception.message)
        }
    }
}
