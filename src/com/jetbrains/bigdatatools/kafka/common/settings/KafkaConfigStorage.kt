package com.jetbrains.bigdatatools.kafka.common.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.bigdatatools.kafka.consumer.models.RunConsumerConfig
import com.jetbrains.bigdatatools.kafka.producer.models.RunProducerConfig

@Suppress("MemberVisibilityCanBePrivate")
@State(name = "BdtKafkaConfigTemplate", storages = [Storage("BdtKafkaConfigTemplate.xml")])
class KafkaConfigStorage : PersistentStateComponent<KafkaConfigStorage> {
  var consumerRunConfigs: List<StorageConsumerConfig> = emptyList()
  var producerRunConfigs: List<StorageProducerConfig> = emptyList()

  override fun getState() = this

  override fun loadState(state: KafkaConfigStorage) = XmlSerializerUtil.copyBean(state, this)

  fun loadConsumerConfigs() = consumerRunConfigs.map { it.fromStorage() }
  fun saveConsumerConfigs(list: List<RunConsumerConfig>) {
    consumerRunConfigs = list.map { StorageConsumerConfig.toStorage(it) }
  }

  fun addConsumerConfig(config: RunConsumerConfig) = saveConsumerConfigs(loadConsumerConfigs() + config)
  fun removeConsumerConfig(config: RunConsumerConfig) = saveConsumerConfigs(loadConsumerConfigs().filter { it != config })

  fun loadProducerConfigs() = producerRunConfigs.map { it.fromStorage() }
  fun saveProducerConfigs(list: List<RunProducerConfig>) {
    producerRunConfigs = list.map { StorageProducerConfig.toStorage(it) }
  }

  fun addProducerConfig(config: RunProducerConfig) = saveProducerConfigs(loadProducerConfigs() + config)
  fun removeProducerConfig(config: RunProducerConfig) = saveProducerConfigs(loadProducerConfigs().filter { it != config })

  companion object {
    val instance: KafkaConfigStorage = ServiceManager.getService(KafkaConfigStorage::class.java)
  }
}

