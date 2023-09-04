package com.jetbrains.bigdatatools.kafka.producer.editor

import com.intellij.json.JsonLanguage
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.*
import com.jetbrains.bigdatatools.common.ui.revalidateOnLinesChanged
import com.jetbrains.bigdatatools.kafka.common.models.KafkaCustomSchemaSource
import com.jetbrains.bigdatatools.kafka.common.models.KafkaFieldType
import com.jetbrains.bigdatatools.kafka.common.settings.StorageConsumerConfig
import com.jetbrains.bigdatatools.kafka.common.settings.StorageProducerConfig
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaRegistrySchemaEditor
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema
import java.io.File
import javax.swing.JPanel

class CustomSchemaController(project: Project, private val isKey: Boolean, val kafkaManager: KafkaDataManager) : Disposable {
  private lateinit var customSchemaSource: SegmentedButton<KafkaCustomSchemaSource>
  private lateinit var customSchemaFile: Cell<TextFieldWithBrowseButton>
  private val customSchema = KafkaRegistrySchemaEditor(project, parentDisposable = this, lineBorder = true).apply {
    customSchemaEditor.revalidateOnLinesChanged()
  }
  private lateinit var customSchemaImplicit: Cell<JPanel>

  override fun dispose() {}

  fun initComponent(panel: Panel) = panel.rowsRange {
    row(KafkaMessagesBundle.message("settings.format.registry.schema")) {
      customSchemaSource = segmentedButton(
        KafkaCustomSchemaSource.values().toList()) { this.text = it.title }.whenItemSelected { source ->
        updateVisibility(source)
      }.resizableColumn()
    }.bottomGap(BottomGap.NONE)

    row {
      customSchemaFile = textFieldWithBrowseButton().align(AlignX.FILL).resizableColumn()
      customSchema.component.size.height = 100

      customSchemaImplicit = cell(customSchema.component).align(AlignX.FILL).resizableColumn()
    }
    customSchemaSource.selectedItem = KafkaCustomSchemaSource.FILE
  }

  private var innerType: KafkaFieldType = KafkaFieldType.PROTOBUF_CUSTOM

  init {
    setLanguage(KafkaFieldType.PROTOBUF_CUSTOM)
  }

  fun setLanguage(type: KafkaFieldType) {
    innerType = type
    customSchema.setLanguage(getInnerLang())
  }


  fun getSchema(): ParsedSchema {
    val schemaText = when (customSchemaSource.selectedItem) {
      KafkaCustomSchemaSource.FILE -> {
        File(customSchemaFile.component.text).readText()
      }
      KafkaCustomSchemaSource.IMPLICIT -> customSchema.text
      null -> ""
    }
    val format = when (innerType) {
      KafkaFieldType.PROTOBUF_CUSTOM -> KafkaRegistryFormat.PROTOBUF
      KafkaFieldType.AVRO_CUSTOM -> KafkaRegistryFormat.AVRO
      else -> error("Wrong type")
    }
    return KafkaRegistryUtil.parseSchema(format, schemaText, kafkaManager).getOrThrow()
  }

  fun setConfig(config: StorageProducerConfig) {
    customSchemaSource.selectedItem = (if (isKey) config.customKeySchemaSource else config.customValueSchemaSource)
                                      ?: KafkaCustomSchemaSource.FILE
    customSchemaFile.component.text = (if (isKey) config.customKeyFile else config.customValueFile) ?: ""
    customSchema.setText((if (isKey) config.customKeySchemaImplicit else config.customValueSchemaImplicit) ?: "", getInnerLang())

    val type = if (isKey) config.takeKeyType() else config.takeValueType()
    setLanguage(type)
  }

  fun setConfig(config: StorageConsumerConfig) {
    customSchemaSource.selectedItem = (if (isKey) config.customKeySchemaSource else config.customValueSchemaSource)
                                      ?: KafkaCustomSchemaSource.FILE
    customSchemaFile.component.text = (if (isKey) config.customKeyFile else config.customValueFile) ?: ""
    customSchema.setText((if (isKey) config.customKeySchemaImplicit else config.customValueSchemaImplicit) ?: "", getInnerLang())

    val type = if (isKey) config.getKeyType() else config.getValueType()
    when (type) {
      KafkaFieldType.PROTOBUF_CUSTOM -> KafkaRegistryUtil.protobufLanguage
      KafkaFieldType.AVRO_CUSTOM -> JsonLanguage.INSTANCE
      else -> PlainTextLanguage.INSTANCE
    }
  }

  private fun getInnerLang(): Language = when (innerType) {
    KafkaFieldType.PROTOBUF_CUSTOM -> KafkaRegistryUtil.protobufLanguage
    KafkaFieldType.AVRO_CUSTOM -> JsonLanguage.INSTANCE
    else -> PlainTextLanguage.INSTANCE
  }


  private fun updateVisibility(source: KafkaCustomSchemaSource) {
    customSchemaFile.visible(source == KafkaCustomSchemaSource.FILE)
    customSchemaImplicit.visible(source == KafkaCustomSchemaSource.IMPLICIT)
  }
}