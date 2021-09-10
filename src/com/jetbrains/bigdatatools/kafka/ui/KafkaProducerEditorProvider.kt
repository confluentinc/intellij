package com.jetbrains.bigdatatools.kafka.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.bigdatatools.data.StructuredFilesUtil
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager

class KafkaProducerEditorProvider : WeighedFileEditorProvider(), DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file.extension == "kafkaProducer"
  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val manager = file.getUserData(KAFKA_MANAGER_KEY) ?: error("Kafka manager is not found")
    return KafkaProducerEditor(project, manager)
  }

  override fun getEditorTypeId(): String = PROVIDER_ID
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
  override fun getWeight(): Double = StructuredFilesUtil.DEFAULT_EDITOR_WEIGHT - 1

  companion object {
    private const val PROVIDER_ID = "kafka-producer"

    val KAFKA_MANAGER_KEY = Key<KafkaDataManager>("KAFKA_MANAGER_KEY")
  }
}