package com.jetbrains.bigdatatools.kafka.common.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.bigdatatools.kafka.common.models.RunConfig
import com.jetbrains.bigdatatools.kafka.consumer.models.RunConsumerConfig
import com.jetbrains.bigdatatools.kafka.producer.models.RunProducerConfig

interface ConfigChangeListener<T> {
  fun configAdded(config: T)
  fun configRemoved(config: T)
}

class KafkaRunConfig(val configsGetter: () -> List<StorageConfig>,
                     val configsSetter: (List<StorageConfig>) -> Unit,
                     private val changeListeners: MutableList<ConfigChangeListener<RunConfig>>) {

  fun loadConfigs() = configsGetter().map { it.fromStorage() }
  fun saveConfigs(list: List<RunConfig>) {
    configsSetter(list.map { it.toStorage() })
  }

  fun addConfig(config: RunConfig) {
    saveConfigs(loadConfigs() + config)
    changeListeners.forEach { it.configAdded(config) }
  }

  fun removeConfig(config: RunConfig) {
    saveConfigs(loadConfigs().filter { it != config })
    changeListeners.forEach { it.configRemoved(config) }
  }

  fun addChangeListener(listener: ConfigChangeListener<RunConfig>) = changeListeners.add(listener)

  fun removeChangeListener(listener: ConfigChangeListener<RunConfig>) = changeListeners.remove(listener)
}

@Suppress("MemberVisibilityCanBePrivate")
@State(name = "BdtKafkaConfigTemplate", storages = [Storage("BdtKafkaConfigTemplate.xml")])
class KafkaConfigStorage : PersistentStateComponent<KafkaConfigStorage> {

  // Never access this fields directly. Use consumerConfig and producerConfig
  var consumerRunConfigs: List<StorageConsumerConfig> = emptyList()
  var producerRunConfigs: List<StorageProducerConfig> = emptyList()

  private val consumerChangeListeners = mutableListOf<ConfigChangeListener<RunConsumerConfig>>()
  private val producerChangeListeners = mutableListOf<ConfigChangeListener<RunProducerConfig>>()

  val consumerConfig = KafkaRunConfig({ consumerRunConfigs }, { consumerRunConfigs = it as List<StorageConsumerConfig> },
    consumerChangeListeners as MutableList<ConfigChangeListener<RunConfig>>)

  val producerConfig = KafkaRunConfig({ producerRunConfigs }, { producerRunConfigs = it as List<StorageProducerConfig> },
    producerChangeListeners as MutableList<ConfigChangeListener<RunConfig>>)

  override fun getState() = this

  override fun loadState(state: KafkaConfigStorage) = XmlSerializerUtil.copyBean(state, this)

  companion object {
    val instance: KafkaConfigStorage = ApplicationManager.getApplication().getService(KafkaConfigStorage::class.java)
  }
}