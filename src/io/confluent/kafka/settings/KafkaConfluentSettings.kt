package io.confluent.kafka.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import io.confluent.kafka.core.settings.ModificationKey
import io.confluent.kafka.core.settings.connections.ConnectionData
import io.confluent.kafka.core.settings.fields.AbstractPropertiesFieldComponent
import io.confluent.kafka.core.settings.fields.StringNamedField
import io.confluent.kafka.core.settings.fields.WrappedComponent
import io.confluent.kafka.core.ui.components.ConnectionPropertiesEditor
import io.confluent.kafka.core.ui.doOnChange
import io.confluent.kafka.registry.KafkaRegistryType
import io.confluent.kafka.rfs.KafkaCloudType
import io.confluent.kafka.rfs.KafkaConfigurationSource
import io.confluent.kafka.rfs.KafkaConnectionData
import io.confluent.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.util.KafkaPropertiesUtils
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