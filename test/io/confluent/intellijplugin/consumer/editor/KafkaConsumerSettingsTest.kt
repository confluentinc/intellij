package io.confluent.intellijplugin.consumer.editor

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.common.settings.StorageConsumerConfig
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
        fun `should return all 5 properties when ALL_PROPERTIES is used and values are set`() {
            val settings = KafkaConsumerSettings(KafkaConsumerSettings.ALL_PROPERTIES)
            val config = StorageConsumerConfig(
                properties = KafkaConsumerSettings.ALL_PROPERTIES.associateWith { "1" }
            )

            settings.applyConfig(config)
            val properties = settings.getProperties()

            assertEquals(KafkaConsumerSettings.ALL_PROPERTIES, properties.keys)
        }

        @Test
        fun `should return only CCloud-supported properties when CCLOUD_PROPERTIES is used`() {
            val settings = KafkaConsumerSettings(KafkaConsumerSettings.CCLOUD_PROPERTIES)
            // Try to set all properties, but only the 2 CCloud ones should come back
            val config = StorageConsumerConfig(
                properties = KafkaConsumerSettings.ALL_PROPERTIES.associateWith { "1" }
            )

            settings.applyConfig(config)
            val properties = settings.getProperties()

            assertEquals(KafkaConsumerSettings.CCLOUD_PROPERTIES, properties.keys)
            assertFalse(properties.containsKey(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG))
            assertFalse(properties.containsKey(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG))
            assertFalse(properties.containsKey(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG))
        }

        @Test
        fun `should not return properties that are at default values`() {
            val settings = KafkaConsumerSettings(KafkaConsumerSettings.ALL_PROPERTIES)

            val properties = settings.getProperties()
            assertTrue(properties.isEmpty(), "Properties at default values should not be returned")
        }

        @Test
        fun `should always show plugin settings regardless of connection type`() {
            val ccloudSettings = KafkaConsumerSettings(KafkaConsumerSettings.CCLOUD_PROPERTIES)
            val nativeSettings = KafkaConsumerSettings(KafkaConsumerSettings.ALL_PROPERTIES)

            // MAX_CONSUMER_RECORDS has no default, so it won't appear until set
            assertFalse(ccloudSettings.getSettings().containsKey(KafkaConsumerSettings.MAX_CONSUMER_RECORDS))
            assertFalse(nativeSettings.getSettings().containsKey(KafkaConsumerSettings.MAX_CONSUMER_RECORDS))
        }

        @Test
        fun `should return no properties for empty supported set`() {
            val settings = KafkaConsumerSettings(emptySet())
            val config = StorageConsumerConfig(
                properties = KafkaConsumerSettings.ALL_PROPERTIES.associateWith { "1" }
            )

            settings.applyConfig(config)
            assertTrue(settings.getProperties().isEmpty())
        }
    }
}
