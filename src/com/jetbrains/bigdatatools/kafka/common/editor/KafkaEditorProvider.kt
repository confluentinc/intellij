package com.jetbrains.bigdatatools.kafka.common.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.bigdatatools.core.data.StructuredFilesUtil
import com.jetbrains.bigdatatools.kafka.common.models.KafkaEditorType
import com.jetbrains.bigdatatools.kafka.consumer.editor.KafkaConsumerEditor
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.producer.editor.KafkaProducerEditor

class KafkaEditorProvider : WeighedFileEditorProvider(), DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file.getUserData(KAFKA_EDITOR_TYPE) != null

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val manager = file.getUserData(KAFKA_MANAGER_KEY) ?: error("Kafka manager is not found")
    val type = file.getUserData(KAFKA_EDITOR_TYPE) ?: error("Kafka editor type is not found")
    val topic = file.getUserData(KAFKA_DEFAULT_TOPIC)
    return when (type) {
      KafkaEditorType.CONSUMER -> KafkaConsumerEditor(project, manager, file, topic)
      KafkaEditorType.PRODUCER -> KafkaProducerEditor(project, manager, file, topic)
    }
  }

  override fun getEditorTypeId(): String = PROVIDER_ID
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
  override fun getWeight(): Double = StructuredFilesUtil.DEFAULT_EDITOR_WEIGHT - 1

  companion object {
    private const val PROVIDER_ID = "kafka-producer"

    val KAFKA_MANAGER_KEY = Key<KafkaDataManager>("KAFKA_MANAGER")
    val KAFKA_EDITOR_TYPE = Key<KafkaEditorType>("KAFKA_EDITOR_TYPE")
    val KAFKA_DEFAULT_TOPIC = Key<String>("KAFKA_DEFAULT_TOPIC")
  }
}

