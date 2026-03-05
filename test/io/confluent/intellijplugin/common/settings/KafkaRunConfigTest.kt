package io.confluent.intellijplugin.common.settings

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class KafkaRunConfigTest {

    private val storage = mutableListOf<StorageConfig>()
    private val addedConfigs = mutableListOf<StorageConfig>()
    private val removedConfigs = mutableListOf<StorageConfig>()
    private val changeListeners = mutableListOf<ConfigChangeListener<StorageConfig>>()

    @BeforeEach
    fun setUp() {
        storage.clear()
        addedConfigs.clear()
        removedConfigs.clear()
        changeListeners.clear()
        changeListeners.add(object : ConfigChangeListener<StorageConfig> {
            override fun configAdded(config: StorageConfig) { addedConfigs.add(config) }
            override fun configRemoved(config: StorageConfig) { removedConfigs.add(config) }
        })
    }

    private fun nativeConfig() = StorageConsumerConfig(connectionType = null)
    private fun ccloudConfig() = StorageConsumerConfig(connectionType = "ccloud")

    private fun makeRunConfig(filter: (StorageConfig) -> Boolean = { true }) = KafkaRunConfig(
        configsGetter = { storage.toList() },
        configsSetter = { storage.clear(); storage.addAll(it) },
        changeListeners = changeListeners,
        filter = filter
    )

    @Nested
    @DisplayName("loadConfigs")
    inner class LoadConfigs {

        @Test
        fun `should return all configs when no filter is applied`() {
            storage.addAll(listOf(nativeConfig(), ccloudConfig()))
            val runConfig = makeRunConfig()

            assertEquals(2, runConfig.loadConfigs().size)
        }

        @Test
        fun `should return only configs matching the filter`() {
            val native = nativeConfig()
            storage.addAll(listOf(native, ccloudConfig()))

            val runConfig = makeRunConfig { (it as? StorageConsumerConfig)?.connectionType == null }

            assertEquals(listOf(native), runConfig.loadConfigs())
        }

        @Test
        fun `should return empty list when no configs match the filter`() {
            storage.add(nativeConfig())
            val runConfig = makeRunConfig { (it as? StorageConsumerConfig)?.connectionType == "ccloud" }

            assertTrue(runConfig.loadConfigs().isEmpty())
        }
    }

    @Nested
    @DisplayName("shouldShow")
    inner class ShouldShow {

        @Test
        fun `should return true for configs matching the filter`() {
            val runConfig = makeRunConfig { (it as? StorageConsumerConfig)?.connectionType == "ccloud" }

            assertTrue(runConfig.shouldShow(ccloudConfig()))
        }

        @Test
        fun `should return false for configs not matching the filter`() {
            val runConfig = makeRunConfig { (it as? StorageConsumerConfig)?.connectionType == "ccloud" }

            assertFalse(runConfig.shouldShow(nativeConfig()))
        }
    }

    @Nested
    @DisplayName("addConfig")
    inner class AddConfig {

        @Test
        fun `should persist config to full storage even when excluded by the filter`() {
            val runConfig = makeRunConfig { (it as? StorageConsumerConfig)?.connectionType == null }

            runConfig.addConfig(ccloudConfig())

            assertEquals(1, storage.size)
            assertTrue(runConfig.loadConfigs().isEmpty())
        }

        @Test
        fun `should not lose configs outside the filter when adding a new config`() {
            val native = nativeConfig()
            storage.add(native)
            val runConfig = makeRunConfig { (it as? StorageConsumerConfig)?.connectionType == null }

            runConfig.addConfig(ccloudConfig())

            assertEquals(2, storage.size)
            assertEquals(listOf(native), runConfig.loadConfigs())
        }

        @Test
        fun `should notify change listeners for all added configs regardless of filter`() {
            val ccloud = ccloudConfig()
            val runConfig = makeRunConfig { (it as? StorageConsumerConfig)?.connectionType == null }

            runConfig.addConfig(ccloud)

            assertEquals(listOf(ccloud), addedConfigs)
        }
    }

    @Nested
    @DisplayName("removeConfig")
    inner class RemoveConfig {

        @Test
        fun `should remove config from full storage even when it is outside the filter`() {
            val native = nativeConfig()
            val ccloud = ccloudConfig()
            storage.addAll(listOf(native, ccloud))
            val runConfig = makeRunConfig { (it as? StorageConsumerConfig)?.connectionType == null }

            runConfig.removeConfig(ccloud)

            assertEquals(listOf(native), storage)
        }

        @Test
        fun `should notify change listeners on removal`() {
            val config = nativeConfig()
            storage.add(config)
            val runConfig = makeRunConfig()

            runConfig.removeConfig(config)

            assertEquals(listOf(config), removedConfigs)
        }
    }

    @Nested
    @DisplayName("hasConfig")
    inner class HasConfig {

        @Test
        fun `should return true only for configs visible in the filtered view`() {
            val native = nativeConfig()
            val ccloud = ccloudConfig()
            storage.addAll(listOf(native, ccloud))
            val runConfig = makeRunConfig { (it as? StorageConsumerConfig)?.connectionType == null }

            assertTrue(runConfig.hasConfig(native))
            assertFalse(runConfig.hasConfig(ccloud))
        }
    }
}