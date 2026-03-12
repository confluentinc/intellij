package io.confluent.intellijplugin.toolwindow.controllers

import io.confluent.intellijplugin.icons.BigdatatoolsKafkaIcons
import com.intellij.openapi.fileTypes.FileType

/** Fake file type to supply Consumer and Producer editor tabs with icon. */
class KafkaFileType : FileType {
    override fun getName() = ""
    override fun getDescription() = ""
    override fun getDefaultExtension() = ""
    override fun getIcon() = BigdatatoolsKafkaIcons.ConfluentTab
    override fun isBinary() = true
}