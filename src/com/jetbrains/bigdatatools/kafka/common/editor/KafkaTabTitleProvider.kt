package com.jetbrains.bigdatatools.kafka.common.editor

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorProvider.Companion.KAFKA_EDITOR_TYPE
import com.jetbrains.bigdatatools.kafka.common.models.KafkaEditorType
import com.jetbrains.bigdatatools.kafka.consumer.editor.KafkaConsumerPanel
import com.jetbrains.bigdatatools.kafka.producer.editor.KafkaProducerEditor
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class KafkaTabTitleProvider : EditorTabTitleProvider, DumbAware {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    val type: KafkaEditorType = file.getUserData(KAFKA_EDITOR_TYPE) ?: return null
    return if (type == KafkaEditorType.CONSUMER) {
      val consumerState = file.getUserData(KafkaConsumerPanel.STATE_KEY) ?: return null
      val topicName = consumerState.config.topic
      if (!topicName.isNullOrEmpty())
        "$topicName ${KafkaMessagesBundle.message("kafka.consumer.title")}"
      else null
    }
    else {
      val producerState = file.getUserData(KafkaProducerEditor.STATE_KEY) ?: return null
      val topicName = producerState.config.topic
      if (topicName.isNotEmpty())
        "$topicName ${KafkaMessagesBundle.message("kafka.producer.title")}"
      else null
    }
  }
}