package com.jetbrains.bigdatatools.kafka.common.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.bigdatatools.kafka.consumer.models.RunConsumerConfig
import com.jetbrains.bigdatatools.kafka.producer.models.RunProducerConfig

interface ConfigChangeListener<T> {
  fun configAdded(config: T)
  fun configRemoved(config: T)
}

@Suppress("MemberVisibilityCanBePrivate")
@State(name = "BdtKafkaConfigTemplate", storages = [Storage("BdtKafkaConfigTemplate.xml")])
class KafkaConfigStorage : PersistentStateComponent<KafkaConfigStorage> {
  var consumerRunConfigs: List<StorageConsumerConfig> = emptyList()
  var producerRunConfigs: List<StorageProducerConfig> = emptyList()

  private val consumerChangeListeners = mutableListOf<ConfigChangeListener<RunConsumerConfig>>()
  private val producerChangeListeners = mutableListOf<ConfigChangeListener<RunProducerConfig>>()

  override fun getState() = this

  override fun loadState(state: KafkaConfigStorage) = XmlSerializerUtil.copyBean(state, this)

  fun loadConsumerConfigs() = consumerRunConfigs.map { it.fromStorage() }
  fun saveConsumerConfigs(list: List<RunConsumerConfig>) {
    consumerRunConfigs = list.map { StorageConsumerConfig.toStorage(it) }
  }

  fun addConsumerConfig(config: RunConsumerConfig) {
    saveConsumerConfigs(loadConsumerConfigs() + config)
    consumerChangeListeners.forEach { it.configAdded(config) }
  }

  fun removeConsumerConfig(config: RunConsumerConfig) {
    saveConsumerConfigs(loadConsumerConfigs().filter { it != config })
    consumerChangeListeners.forEach { it.configRemoved(config) }
  }

  fun loadProducerConfigs() = producerRunConfigs.map { it.fromStorage() }
  fun saveProducerConfigs(list: List<RunProducerConfig>) {
    producerRunConfigs = list.map { StorageProducerConfig.toStorage(it) }
  }

  fun addProducerConfig(config: RunProducerConfig) {
    saveProducerConfigs(loadProducerConfigs() + config)
    producerChangeListeners.forEach { it.configAdded(config) }
  }

  fun removeProducerConfig(config: RunProducerConfig) {
    saveProducerConfigs(loadProducerConfigs().filter { it != config })
    producerChangeListeners.forEach { it.configRemoved(config) }
  }

  fun addConsumerChangeListener(listener: ConfigChangeListener<RunConsumerConfig>) = consumerChangeListeners.add(listener)
  fun addProducerChangeListener(listener: ConfigChangeListener<RunProducerConfig>) = producerChangeListeners.add(listener)

  fun removeConsumerChangeListener(listener: ConfigChangeListener<RunConsumerConfig>) = consumerChangeListeners.remove(listener)
  fun removeProducerChangeListener(listener: ConfigChangeListener<RunProducerConfig>) = producerChangeListeners.remove(listener)

  companion object {
    val instance: KafkaConfigStorage = ApplicationManager.getApplication().getService(KafkaConfigStorage::class.java)
  }
}