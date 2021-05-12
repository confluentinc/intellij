package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.ContextHelpLabel
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.monitoring.TunnableSettingsCustomizer
import com.jetbrains.bigdatatools.settings.ModificationKey
import com.jetbrains.bigdatatools.settings.defaultui.UiUtil
import com.jetbrains.bigdatatools.settings.fields.PropertiesFieldComponent
import com.jetbrains.bigdatatools.settings.fields.WrappedComponent
import com.jetbrains.bigdatatools.ui.MigPanel
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class KafkaSettingsCustomizer(project: Project, connectionData: KafkaConnectionData, uiDisposable: Disposable) :
  TunnableSettingsCustomizer<KafkaConnectionData>(connectionData, project, uiDisposable) {

  private val properties = PropertiesFieldComponent.create(KafkaConnectionData::properties,
                                                           KafkaSettingsKeys.PROPERTIES_KEY,
                                                           connectionData, uiDisposable)

  override fun getDefaultFields(conn: KafkaConnectionData): List<WrappedComponent<in KafkaConnectionData>> =
    listOf(nameField, url, properties, tunnelField)

  override fun getDefaultComponent(fields: List<WrappedComponent<in KafkaConnectionData>>, conn: KafkaConnectionData) = MigPanel().apply {
    row(nameField.labelComponent, nameField.getComponent())
    row(url.labelComponent, url.getComponent())
    row(properties.labelComponent, properties.getComponent())
    add(tunnelField.getComponent(), UiUtil.spanXWrap)

    val warningPanel = JPanel().apply {
      add(JLabel(KafkaMessagesBundle.message("kafka.support.is.limited"), AllIcons.General.Warning, SwingConstants.LEADING))
      add(ContextHelpLabel.create(KafkaMessagesBundle.message("kafka.support.is.limited.helper")))
    }
    add(warningPanel, UiUtil.spanXWrap)
  }

  object KafkaSettingsKeys {
    val PROPERTIES_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties"))
  }
}