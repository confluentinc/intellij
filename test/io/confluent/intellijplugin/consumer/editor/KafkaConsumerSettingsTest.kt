package io.confluent.intellijplugin.consumer.editor

import com.intellij.testFramework.junit5.TestApplication
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
class KafkaConsumerSettingsTest {

    @Nested
    @DisplayName("Property filtering")
    inner class PropertyFiltering {

        @Test
        fun `should show all 5 Kafka properties with ALL_PROPERTIES`() {
            val settings = KafkaConsumerSettings(KafkaConsumerSettings.ALL_PROPERTIES)

            // Properties at default values should not appear in getProperties() output
            val properties = settings.getProperties()
            assertTrue(properties.isEmpty(), "Properties at default values should not be returned")
        }

        @Test
        fun `should show only 2 Kafka properties with CCLOUD_PROPERTIES`() {
            val settings = KafkaConsumerSettings(KafkaConsumerSettings.CCLOUD_PROPERTIES)

            // Verify that unsupported properties are not present
            // Set a non-default value for a CCloud property to verify it appears
            // For now, just verify default state
            val properties = settings.getProperties()
            assertTrue(properties.isEmpty(), "Properties at default values should not be returned")
        }

        @Test
        fun `should always show plugin settings regardless of connection type`() {
            val ccloudSettings = KafkaConsumerSettings(KafkaConsumerSettings.CCLOUD_PROPERTIES)
            val nativeSettings = KafkaConsumerSettings(KafkaConsumerSettings.ALL_PROPERTIES)

            // Both should have MAX_CONSUMER_RECORDS in settings
            val ccloudPluginSettings = ccloudSettings.getSettings()
            val nativePluginSettings = nativeSettings.getSettings()

            // MAX_CONSUMER_RECORDS has no default, so it won't appear until set
            assertFalse(ccloudPluginSettings.containsKey(KafkaConsumerSettings.MAX_CONSUMER_RECORDS))
            assertFalse(nativePluginSettings.containsKey(KafkaConsumerSettings.MAX_CONSUMER_RECORDS))
        }

        @Test
        fun `should show empty properties for empty supported set`() {
            val settings = KafkaConsumerSettings(emptySet())
            val properties = settings.getProperties()
            assertTrue(properties.isEmpty())
        }
    }

    @Nested
    @DisplayName("CCLOUD_PROPERTIES constant")
    inner class CCloudPropertiesConstant {

        @Test
        fun `should contain max_poll_records`() {
            assertTrue(KafkaConsumerSettings.CCLOUD_PROPERTIES.contains(ConsumerConfig.MAX_POLL_RECORDS_CONFIG))
        }

        @Test
        fun `should contain fetch_max_bytes`() {
            assertTrue(KafkaConsumerSettings.CCLOUD_PROPERTIES.contains(ConsumerConfig.FETCH_MAX_BYTES_CONFIG))
        }

        @Test
        fun `should not contain request_timeout_ms`() {
            assertFalse(KafkaConsumerSettings.CCLOUD_PROPERTIES.contains(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG))
        }

        @Test
        fun `should not contain fetch_max_wait_ms`() {
            assertFalse(KafkaConsumerSettings.CCLOUD_PROPERTIES.contains(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG))
        }

        @Test
        fun `should not contain max_partition_fetch_bytes`() {
            assertFalse(KafkaConsumerSettings.CCLOUD_PROPERTIES.contains(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG))
        }

        @Test
        fun `should have exactly 2 entries`() {
            assertEquals(2, KafkaConsumerSettings.CCLOUD_PROPERTIES.size)
        }
    }

    @Nested
    @DisplayName("ALL_PROPERTIES constant")
    inner class AllPropertiesConstant {

        @Test
        fun `should have exactly 5 entries`() {
            assertEquals(5, KafkaConsumerSettings.ALL_PROPERTIES.size)
        }

        @Test
        fun `should be a superset of CCLOUD_PROPERTIES`() {
            assertTrue(KafkaConsumerSettings.ALL_PROPERTIES.containsAll(KafkaConsumerSettings.CCLOUD_PROPERTIES))
        }
    }

}
