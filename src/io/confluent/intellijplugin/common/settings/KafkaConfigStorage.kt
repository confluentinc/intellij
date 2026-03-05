package io.confluent.intellijplugin.common.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

interface ConfigChangeListener<T> {
    fun configAdded(config: T)
    fun configRemoved(config: T)
}

class KafkaRunConfig(
    val configsGetter: () -> List<StorageConfig>,
    val configsSetter: (List<StorageConfig>) -> Unit,
    private val changeListeners: MutableList<ConfigChangeListener<StorageConfig>>,
    private val filter: (StorageConfig) -> Boolean = { true }
) {

    fun hasConfig(config: StorageConfig) = loadConfigs().contains(config)

    fun loadConfigs() = configsGetter().filter(filter)

    fun shouldShow(config: StorageConfig) = filter(config)

    private fun saveConfigs(list: List<StorageConfig>) = configsSetter(list)

    fun addConfig(config: StorageConfig) {
        saveConfigs(configsGetter() + config)
        changeListeners.forEach { it.configAdded(config) }
    }

    fun removeConfig(config: StorageConfig) {
        saveConfigs(configsGetter().filter { it != config })
        changeListeners.forEach { it.configRemoved(config) }
    }

    fun addChangeListener(listener: ConfigChangeListener<StorageConfig>) = changeListeners.add(listener)

    fun removeChangeListener(listener: ConfigChangeListener<StorageConfig>) = changeListeners.remove(listener)
}

@Suppress("MemberVisibilityCanBePrivate")
@State(name = "ConfluentIntellijKafkaConfigTemplate", storages = [Storage("confluent-kafka-config-template.xml")])
class KafkaConfigStorage : PersistentStateComponent<KafkaConfigStorage> {

    // Never access this fields directly. Use consumerConfig and producerConfig
    var consumerRunConfigs: List<StorageConsumerConfig> = mutableListOf()
    var producerRunConfigs: List<StorageProducerConfig> = mutableListOf()

    private val consumerChangeListeners = mutableListOf<ConfigChangeListener<StorageConfig>>()
    private val producerChangeListeners = mutableListOf<ConfigChangeListener<StorageConfig>>()

    val consumerConfig = KafkaRunConfig(
        { consumerRunConfigs },
        { consumerRunConfigs = it as List<StorageConsumerConfig> }, consumerChangeListeners
    )

    fun consumerConfigFor(connectionType: String?) = KafkaRunConfig(
        { consumerRunConfigs },
        { consumerRunConfigs = it as List<StorageConsumerConfig> },
        consumerChangeListeners,
        filter = { (it as? StorageConsumerConfig)?.connectionType == connectionType }
    )

    val producerConfig = KafkaRunConfig(
        { producerRunConfigs },
        { producerRunConfigs = it as List<StorageProducerConfig> }, producerChangeListeners
    )

    override fun getState() = this

    override fun loadState(state: KafkaConfigStorage) = XmlSerializerUtil.copyBean(state, this)

    companion object {
        fun getInstance(): KafkaConfigStorage = service()
    }
}