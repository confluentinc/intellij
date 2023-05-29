package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.bigdatatools.common.settings.ModificationKey
import com.jetbrains.bigdatatools.common.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.common.settings.fields.PropertiesFieldComponent
import com.jetbrains.bigdatatools.common.settings.fields.RadioGroupField
import com.jetbrains.bigdatatools.common.settings.fields.StringNamedField
import com.jetbrains.bigdatatools.common.settings.fields.WrappedComponent
import com.jetbrains.bigdatatools.common.ui.components.ConnectionPropertiesEditor
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.kafka.util.KafkaPropertiesUtils
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig

class KafkaConfluentSettings(
  val project: Project,
  val connectionData: KafkaConnectionData,
  uiDisposable: Disposable,
  val url: StringNamedField<ConnectionData>,
  propertiesEditor: PropertiesFieldComponent<KafkaConnectionData>,
  registryType: RadioGroupField<KafkaConnectionData, KafkaRegistryType>,
) {

  private val confluentConf = ConnectionPropertiesEditor(project, KafkaPropertiesUtils.getAdminPropertiesDescriptions()).apply {
    this.getComponent().text = connectionData.properties
    val value = object : DocumentListener {
      override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
        val text: String = this@apply.getComponent().text
        propertiesEditor.getComponent().text = text
        if (text.contains(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG))
          registryType.setValue(KafkaRegistryType.CONFLUENT)
        else
          registryType.setValue(KafkaRegistryType.NONE)

      }
    }
    this.getComponent().addDocumentListener(value)
    Disposer.register(uiDisposable, Disposable {
      this.getComponent().removeDocumentListener(value)
    })
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