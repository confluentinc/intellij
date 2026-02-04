package io.confluent.intellijplugin.performance

import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.consumer.editor.KafkaRecord
import io.confluent.intellijplugin.core.settings.connections.Property
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import java.util.concurrent.ThreadLocalRandom

/**
 * Generates synthetic KafkaRecord instances for load testing.
 *
 * Produces records with varied payload sizes to simulate realistic workloads:
 * - Small JSON: ~100 bytes
 * - Medium JSON: ~500 bytes
 * - Large JSON: ~2KB
 * - Binary: random byte payloads
 */
object SyntheticKafkaRecordGenerator {

    private val random get() = ThreadLocalRandom.current()

    /**
     * Payload size distribution for realistic testing.
     */
    enum class PayloadProfile(val minBytes: Int, val maxBytes: Int) {
        SMALL(50, 150),
        MEDIUM(400, 600),
        LARGE(1800, 2200),
        MIXED(50, 2200)
    }

    /**
     * Generates a sequence of KafkaRecord instances.
     *
     * @param count Number of records to generate
     * @param topic Topic name for all records
     * @param partitions Number of partitions to distribute across
     * @param profile Payload size profile
     * @param baseTimestamp Starting timestamp (records increment by 1ms each)
     */
    fun generate(
        count: Int,
        topic: String = "test-topic",
        partitions: Int = 10,
        profile: PayloadProfile = PayloadProfile.MIXED,
        baseTimestamp: Long = System.currentTimeMillis() - count
    ): Sequence<KafkaRecord> = sequence {
        val partitionOffsets = LongArray(partitions) { 0L }

        repeat(count) { index ->
            val partition = index % partitions
            val offset = partitionOffsets[partition]++
            val timestamp = baseTimestamp + index

            yield(
                createRecord(
                    topic = topic,
                    partition = partition,
                    offset = offset,
                    timestamp = timestamp,
                    profile = profile
                )
            )
        }
    }

    /**
     * Generates a list of KafkaRecord instances.
     */
    fun generateList(
        count: Int,
        topic: String = "test-topic",
        partitions: Int = 10,
        profile: PayloadProfile = PayloadProfile.MIXED,
        baseTimestamp: Long = System.currentTimeMillis() - count
    ): List<KafkaRecord> = generate(count, topic, partitions, profile, baseTimestamp).toList()

    /**
     * Creates a single synthetic KafkaRecord.
     */
    fun createRecord(
        topic: String = "test-topic",
        partition: Int = 0,
        offset: Long = 0,
        timestamp: Long = System.currentTimeMillis(),
        profile: PayloadProfile = PayloadProfile.MEDIUM,
        headerCount: Int = 3
    ): KafkaRecord {
        val payloadSize = random.nextInt(profile.minBytes, profile.maxBytes + 1)
        val keySize = random.nextInt(10, 50)

        val key = generateKey(keySize)
        val value = generateJsonPayload(payloadSize)
        val headers = generateHeaders(headerCount)

        return KafkaRecord(
            keyType = KafkaFieldType.STRING,
            valueType = KafkaFieldType.JSON,
            error = null,
            key = key,
            value = value,
            topic = topic,
            partition = partition,
            offset = offset,
            duration = random.nextLong(1, 100),
            timestamp = timestamp,
            keySize = keySize,
            valueSize = payloadSize,
            headers = headers,
            keyFormat = KafkaRegistryFormat.UNKNOWN,
            valueFormat = KafkaRegistryFormat.JSON,
            errror = null
        )
    }

    /**
     * Generates a random key string.
     */
    private fun generateKey(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        return buildString(length) {
            repeat(length) {
                append(chars[random.nextInt(chars.length)])
            }
        }
    }

    /**
     * Generates a JSON-like payload of approximately the specified size.
     */
    private fun generateJsonPayload(targetSize: Int): String = buildString(targetSize + 50) {
        append("{")
        append("\"id\":${random.nextInt(1_000_000)},")
        append("\"timestamp\":${System.currentTimeMillis()},")
        append("\"active\":${random.nextBoolean()},")
        append("\"score\":${random.nextDouble()},")

        // Fill remaining space with a data field
        val overhead = length + 20 // account for closing brace and quotes
        val dataSize = (targetSize - overhead).coerceAtLeast(10)
        append("\"data\":\"")
        repeat(dataSize) {
            append(('a'.code + random.nextInt(26)).toChar())
        }
        append("\"}")
    }

    /**
     * Generates random headers.
     */
    private fun generateHeaders(count: Int): List<Property> = List(count) { index ->
        Property(
            name = "header-$index",
            value = generateKey(random.nextInt(10, 30))
        )
    }

    /**
     * Estimates memory footprint of a KafkaRecord in bytes.
     * This is an approximation based on field sizes and object overhead.
     */
    fun estimateRecordBytes(record: KafkaRecord): Int {
        // Object header: ~16 bytes
        // References: ~8 bytes each (on 64-bit JVM with compressed oops)
        // Primitives: int=4, long=8
        var bytes = 16 // object header

        // Primitive fields
        bytes += 4 // partition (int)
        bytes += 8 // offset (long)
        bytes += 8 // duration (long)
        bytes += 8 // timestamp (long)
        bytes += 4 // keySize (int)
        bytes += 4 // valueSize (int)

        // Reference fields (8 bytes each for reference + string/object size)
        bytes += 8 + (record.topic.length * 2 + 40) // topic String
        bytes += 8 + ((record.key?.toString()?.length ?: 0) * 2 + 40) // key
        bytes += 8 + ((record.value?.toString()?.length ?: 0) * 2 + 40) // value
        bytes += 8 // keyType enum reference
        bytes += 8 // valueType enum reference
        bytes += 8 // keyFormat enum reference
        bytes += 8 // valueFormat enum reference
        bytes += 8 // error (nullable)
        bytes += 8 // errror (nullable, typo in original)

        // Headers list
        bytes += 8 + 24 // ArrayList overhead
        record.headers.forEach { header ->
            bytes += 16 // Property object header
            bytes += 8 + ((header.name?.length ?: 0) * 2 + 40)
            bytes += 8 + ((header.value?.length ?: 0) * 2 + 40)
        }

        // Computed properties (keyText, valueText, errorText)
        bytes += 8 + ((record.keyText?.length ?: 0) * 2 + 40)
        bytes += 8 + ((record.valueText?.length ?: 0) * 2 + 40)
        bytes += 8 + (record.errorText.length * 2 + 40)

        return bytes
    }
}
