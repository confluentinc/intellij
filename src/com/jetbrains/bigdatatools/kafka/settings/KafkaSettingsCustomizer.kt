package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.monitoring.TunnableSettingsCustomizer
import com.jetbrains.bigdatatools.settings.ModificationKey
import com.jetbrains.bigdatatools.settings.fields.PropertiesFieldComponent
import com.jetbrains.bigdatatools.settings.fields.WrappedComponent
import com.jetbrains.bigdatatools.ui.MigPanel
import javax.swing.JLabel

class KafkaSettingsCustomizer(project: Project,
                              connectionData: KafkaConnectionData,
                              uiDisposable: Disposable) : TunnableSettingsCustomizer<KafkaConnectionData>(connectionData,
                                                                                                          project,
                                                                                                          uiDisposable) {
  private val properties = PropertiesFieldComponent.create(KafkaConnectionData::properties,
                                                           KafkaSettingsKeys.PROPERTIES_KEY,
                                                           connectionData, uiDisposable)

  override fun getDefaultFields(conn: KafkaConnectionData): List<WrappedComponent<in KafkaConnectionData>> =
    listOf(nameField, url, properties, tunnelField)

  override fun getDefaultComponent(fields: List<WrappedComponent<in KafkaConnectionData>>, conn: KafkaConnectionData) = MigPanel().apply {
    row(nameField.labelComponent, nameField.getComponent())
    row(url.labelComponent, url.getComponent())

    val panel = MigPanel().apply {
      block(properties.getComponent())
      block(tunnelField.getComponent())
      val notice = JLabel(KafkaMessagesBundle.message("kafka.support.is.limited"))
      notice.icon = AllIcons.General.Information
      row(notice)
    }

    block(panel)
  }
}

object KafkaSettingsKeys {
  val PROPERTIES_KEY = ModificationKey("Properties:")
}

