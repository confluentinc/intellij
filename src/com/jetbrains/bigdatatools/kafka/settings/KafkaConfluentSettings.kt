package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.bigdatatools.common.settings.ModificationKey
import com.jetbrains.bigdatatools.common.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.common.settings.fields.AbstractPropertiesFieldComponent
import com.jetbrains.bigdatatools.common.settings.fields.StringNamedField
import com.jetbrains.bigdatatools.common.settings.fields.WrappedComponent
import com.jetbrains.bigdatatools.common.ui.components.ConnectionPropertiesEditor
import com.jetbrains.bigdatatools.common.ui.doOnChange
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.rfs.KafkaCloudType
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConfigurationSource
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.kafka.util.KafkaPropertiesUtils
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import javax.swing.SwingUtilities

internal class KafkaConfluentSettings(val project: Project,
                             val connectionData: KafkaConnectionData,
                             uiDisposable: Disposable,
                             val url: StringNamedField<ConnectionData>,
                             propertiesEditor: AbstractPropertiesFieldComponent<KafkaConnectionData>,
                             brokerSettings: KafkaBrokerSettings) {
  var updateFromCloud = false

  internal val confluentConf = ConnectionPropertiesEditor(project, KafkaPropertiesUtils.getAdminPropertiesDescriptions()).apply {
    getComponent().setCaretPosition(0)
    val listener = object : DocumentListener {
      override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
        if (updateFromCloud)
          return
        val text: String = getComponent().text

        SwingUtilities.invokeLater {
          updateFromCloud = true
          try {
            propertiesEditor.getComponent().text = text
          }
          finally {
            updateFromCloud = false
          }
        }
        if (brokerSettings.cloudSource.getValue() != KafkaCloudType.CONFLUENT ||
            brokerSettings.confSource.getValue() != KafkaConfigurationSource.CLOUD) {
          return
        }
        if (text.contains(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG))
          brokerSettings.registryType.setValue(KafkaRegistryType.CONFLUENT)
        else
          brokerSettings.registryType.setValue(KafkaRegistryType.NONE)
      }
    }
    getComponent().addDocumentListener(listener)
    Disposer.register(uiDisposable, Disposable {
      getComponent().removeDocumentListener(listener)
    })
  }

  init {
    propertiesEditor.getComponent().doOnChange {
      if (updateFromCloud)
        return@doOnChange
      confluentConf.getComponent().text = propertiesEditor.getComponent().text
    }
  }

  fun setPanelComponent(panel: Panel) = panel.setComponent()

  private fun Panel.setComponent() = rowsRange {
    row(CONFLUENT_PROPERTY.label) {
      contextHelp(KafkaMessagesBundle.message("settings.confluent.setup.desc"),
                  KafkaMessagesBundle.message("settings.cloud.setup.title"))
    }
    row {
      cell(confluentConf.getComponent()).align(Align.FILL).resizableColumn()
        .comment(KafkaMessagesBundle.message("settings.confluent.conf.comment"))
    }
  }

  fun getDefaultFields(): List<WrappedComponent<in KafkaConnectionData>> = listOf()

  companion object {
    val CONFLUENT_PROPERTY = ModificationKey(KafkaMessagesBundle.message("settings.confluent.configuration"))
  }
}