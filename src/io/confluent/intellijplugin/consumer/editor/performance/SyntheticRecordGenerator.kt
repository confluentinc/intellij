package io.confluent.intellijplugin.consumer.editor.performance

import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.consumer.editor.KafkaRecord
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import kotlin.random.Random

object SyntheticRecordGenerator {
    private val random = Random(12345L) // Fixed seed for reproducibility

    fun generateRecords(
        count: Int,
        options: GeneratorOptions = GeneratorOptions()
    ): List<KafkaRecord> {
        return (0 until count).map { i ->
            KafkaRecord(
                keyType = KafkaFieldType.STRING,
                valueType = KafkaFieldType.STRING,
                error = if (options.includeErrors && random.nextDouble() < options.errorRate) {
                    RuntimeException("Synthetic error")
                } else null,
                key = "key-${i}",
                value = generateJsonValue(options.averageValueSize),
                topic = options.topicName,
                partition = i % 10,
                offset = i.toLong(),
                duration = random.nextLong(10, 500),
                timestamp = System.currentTimeMillis() - (count - i) * 1000L,
                keySize = options.averageKeySize,
                valueSize = options.averageValueSize,
                headers = emptyList(),
                keyFormat = KafkaRegistryFormat.UNKNOWN,
                valueFormat = KafkaRegistryFormat.UNKNOWN
            )
        }
    }

    private fun generateJsonValue(targetSize: Int): String {
        val fields = (targetSize / 50).coerceAtLeast(1) // ~50 chars per field
        val json = buildString {
            append("{")
            repeat(fields) { i ->
                if (i > 0) append(",")
                append("\"field$i\":\"${randomString(30)}\"")
            }
            append("}")
        }
        return json
    }

    private fun randomString(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    data class GeneratorOptions(
        val topicName: String = "perf-test-topic",
        val averageKeySize: Int = 50,
        val averageValueSize: Int = 500,
        val includeErrors: Boolean = false,
        val errorRate: Double = 0.05
    )
}
