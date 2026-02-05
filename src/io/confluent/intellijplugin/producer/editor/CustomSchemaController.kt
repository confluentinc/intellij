package io.confluent.intellijplugin.producer.editor

import com.intellij.icons.AllIcons
import com.intellij.json.JsonLanguage
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.jbTextField
import com.intellij.ui.dsl.builder.*
import io.confluent.intellijplugin.common.models.KafkaCustomSchemaSource
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.common.settings.StorageConsumerConfig
import io.confluent.intellijplugin.common.settings.StorageProducerConfig
import io.confluent.intellijplugin.consumer.models.CustomSchemaData
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.settings.getValidationInfo
import io.confluent.intellijplugin.core.settings.withNonEmptyValidator
import io.confluent.intellijplugin.core.ui.revalidateOnLinesChanged
import io.confluent.intellijplugin.core.util.executeNotOnEdt
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.core.util.toPresentableText
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryUtil
import io.confluent.intellijplugin.registry.ui.KafkaRegistrySchemaEditor
import io.confluent.intellijplugin.registry.ui.KafkaSchemaInfoDialog
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema
import java.io.File
import java.io.FileNotFoundException
import javax.swing.JPanel

class CustomSchemaController(
    private val project: Project,
    private val isKey: Boolean,
) : Disposable {
    private lateinit var customSchemaSource: SegmentedButton<KafkaCustomSchemaSource>
    private lateinit var customSchemaFile: Cell<TextFieldWithBrowseButton>
    private val customSchema = KafkaRegistrySchemaEditor(project, parentDisposable = this, lineBorder = true).apply {
        customSchemaEditor.revalidateOnLinesChanged()
    }
    private lateinit var customSchemaImplicit: Cell<JPanel>
    private lateinit var showSchema: Cell<ActionButton>

    override fun dispose() {}

    fun initComponent(panel: Panel): RowsRange = panel.rowsRange {
        row(KafkaMessagesBundle.message("settings.format.registry.schema")) {
            customSchemaSource = segmentedButton(
                KafkaCustomSchemaSource.entries
            ) { this.text = it.title }.whenItemSelected { source ->
                updateVisibility(source)
            }.resizableColumn()
            showSchema = actionButton(
                DumbAwareAction.create(
                    KafkaMessagesBundle.message("show.schema.info"),
                    AllIcons.Actions.ToggleVisibility
                ) {
                    executeNotOnEdt {
                        try {
                            val schema = getSchema()
                            invokeLater {
                                KafkaSchemaInfoDialog.show(
                                    project = project, schemaType = schema.schemaType(),
                                    schemaDefinition = schema.canonicalString(),
                                    schemaName = schema.name() ?: "Custom"
                                )
                            }
                        } catch (t: Throwable) {
                            RfsNotificationUtils.showErrorMessage(
                                project,
                                t.message ?: t.toPresentableText(),
                                KafkaMessagesBundle.message("message.title")
                            )
                        }
                    }
                })
        }.bottomGap(BottomGap.NONE)

        row {
            customSchemaFile =
                textFieldWithBrowseButton(KafkaMessagesBundle.message("choose.schema.file")).align(AlignX.FILL)
                    .resizableColumn()
            customSchemaFile.component.jbTextField.withNonEmptyValidator(this@CustomSchemaController)
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
                val file = File(customSchemaFile.component.text)
                if (file.exists()) {
                    file.readText()
                } else throw FileNotFoundException("File not found")
            }

            KafkaCustomSchemaSource.IMPLICIT -> customSchema.text
            null -> ""
        }
        val format = when (innerType) {
            KafkaFieldType.PROTOBUF_CUSTOM -> KafkaRegistryFormat.PROTOBUF
            KafkaFieldType.AVRO_CUSTOM -> KafkaRegistryFormat.AVRO
            else -> error("Wrong type")
        }
        return KafkaRegistryUtil.parseSchema(format, schemaText).getOrThrow()
    }

    fun setProducerConfig(config: StorageProducerConfig) {
        setConfig(if (isKey) config.customKeySchema else config.customValueSchema)

        val type = if (isKey) config.takeKeyType() else config.takeValueType()
        setLanguage(type)
    }

    fun setConsumerConfig(config: StorageConsumerConfig) {
        setConfig(if (isKey) config.customKeySchema else config.customValueSchema)

        val type = if (isKey) config.getKeyType() else config.getValueType()
        when (type) {
            KafkaFieldType.PROTOBUF_CUSTOM -> KafkaRegistryUtil.protobufLanguage
            KafkaFieldType.AVRO_CUSTOM -> JsonLanguage.INSTANCE
            else -> PlainTextLanguage.INSTANCE
        }
    }

    internal fun getSchemaConfig(): CustomSchemaData = CustomSchemaData(
        customFile = customSchemaFile.component.text,
        customSchemaSource = customSchemaSource.selectedItem,
        customSchemaImplicit = customSchema.text
    )

    private fun setConfig(schemaData: CustomSchemaData?) {
        customSchemaSource.selectedItem = schemaData?.customSchemaSource ?: KafkaCustomSchemaSource.FILE
        if (customSchemaSource.selectedItem == KafkaCustomSchemaSource.FILE) {
            customSchemaFile.component.text = schemaData?.customFile ?: ""
        } else customSchema.setText(schemaData?.customSchemaImplicit ?: "", getInnerLang())
    }

    private fun getInnerLang(): Language = when (innerType) {
        KafkaFieldType.PROTOBUF_CUSTOM -> KafkaRegistryUtil.protobufLanguage
        KafkaFieldType.AVRO_CUSTOM -> JsonLanguage.INSTANCE
        else -> PlainTextLanguage.INSTANCE
    }


    private fun updateVisibility(source: KafkaCustomSchemaSource) {
        customSchemaFile.visible(source == KafkaCustomSchemaSource.FILE)
        customSchemaImplicit.visible(source == KafkaCustomSchemaSource.IMPLICIT)
        showSchema.visible(source == KafkaCustomSchemaSource.FILE)
    }

    fun getValidationInfo(): ValidationInfo? {
        return customSchema.customSchemaEditor.getValidationInfo()
            ?: customSchemaFile.component.jbTextField.getValidationInfo()
    }
}
