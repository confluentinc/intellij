package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.fileTypes.FileType
import icons.BigdatatoolsKafkaIcons

/** Fake file type to supply Consumer and Producer editor tabs with icon. */
class KafkaFileType : FileType {
  override fun getName() = ""
  override fun getDescription() = ""
  override fun getDefaultExtension() = ""
  override fun getIcon() = BigdatatoolsKafkaIcons.Kafka
  override fun isBinary() = true
}