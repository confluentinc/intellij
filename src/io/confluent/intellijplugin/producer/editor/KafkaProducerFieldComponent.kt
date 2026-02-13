package io.confluent.intellijplugin.producer.editor

import com.google.gson.JsonParser
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.readBytes
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.not
import com.intellij.ui.layout.selectedValueMatches
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import io.confluent.intellijplugin.common.editor.KafkaEditorUtils
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.common.models.RegistrySchemaInEditor
import io.confluent.intellijplugin.common.settings.StorageProducerConfig
import io.confluent.intellijplugin.completion.KafkaProducerGeneratorCompletionProvider
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.consumer.models.CustomSchemaData
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.settings.*
import io.confluent.intellijplugin.core.ui.chooser.FileChooserUtil
import io.confluent.intellijplugin.core.ui.revalidateOnLinesChanged
import io.confluent.intellijplugin.core.util.executeNotOnEdt
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.core.util.toPresentableText
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryUtil
import io.confluent.intellijplugin.registry.ui.KafkaSchemaInfoDialog
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import io.confluent.intellijplugin.util.generator.FieldTemplateGenerator
import io.confluent.intellijplugin.util.generator.GenerateRandomData
import java.util.*
import javax.swing.JComponent

class KafkaProducerFieldComponent(private val producedEditor: KafkaProducerEditor, val isKey: Boolean) : Disposable {
    private var isInitialized: Boolean = false
    val project = producedEditor.project
    private val kafkaManager = producedEditor.kafkaManager

    private var schemaValidationError: Throwable? = null
    private var curIsJsonView: Boolean = !isKey

    var randomGenerationEnabled = AtomicBooleanProperty(false).apply {
        afterChange {
            updateVisibility()
        }
    }

    private val customSchemaController =
        CustomSchemaController(project, isKey).also { Disposer.register(this, it) }

    val fieldTypeComboBox =
        KafkaEditorUtils.createFieldTypeComboBox(producedEditor.topicComboBox, kafkaManager, isKey) {
            if (!isInitialized)
                return@createFieldTypeComboBox

            jsonField.putUserData(SKIP_VALIDATION, true)
            textField.putUserData(SKIP_VALIDATION, true)

            updateVisibility()

            val newIsJsonView = it.item in jsonFieldTypes
            if (newIsJsonView != curIsJsonView) {
                if (curIsJsonView)
                    updateFieldsText(it.item, jsonField.text)
                else
                    updateFieldsText(it.item, textField.text)
            }
            curIsJsonView = newIsJsonView

            textField.getValidator()?.revalidate()
            jsonField.getValidator()?.revalidate()

            producedEditor.getComponent().revalidate()
            customSchemaController.setLanguage(it.item)
        }

    val schemaComboBox = KafkaEditorUtils.createSchemaComboBox(
        this, kafkaManager,
        producedEditor.topicComboBox, isKey
    )
    private val customSchemaPredicate = fieldTypeComboBox.selectedValueMatches {
        it in setOf(KafkaFieldType.AVRO_CUSTOM, KafkaFieldType.PROTOBUF_CUSTOM)
    }

    private val textField: EditorTextField by lazy {
        KafkaEditorUtils.createTextArea(project, language = PlainTextLanguage.INSTANCE)
            .withValidator(this, ::validateValue).apply {
            setDisposedWith(this@KafkaProducerFieldComponent)
            document.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    schemaValidationError = null
                }
            }, this@KafkaProducerFieldComponent)
            revalidateOnLinesChanged()
        }
    }

    private val jsonField: EditorTextField by lazy {
        KafkaEditorUtils.createTextArea(project).withValidator(this, ::validateValue).apply {
            setDisposedWith(this@KafkaProducerFieldComponent)
            document.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    schemaValidationError = null
                }
            }, this@KafkaProducerFieldComponent)
            revalidateOnLinesChanged()

            val virtualFile = FileDocumentManager.getInstance().getFile(document)
            virtualFile?.putUserData(KafkaProducerGeneratorCompletionProvider.KAFKA_JSON_WITH_GENERATOR, true)
        }
    }

    init {
        customSchemaController.setLanguage(fieldTypeComboBox.item)
    }

    override fun dispose() = Unit

    private fun revalidateFields() {
        textField.revalidateComponent()
        jsonField.revalidateComponent()
    }

    fun validateSchema(): Boolean {
        val oldValidationError = schemaValidationError?.toPresentableText()

        return try {
            val producerField = getProducerField()
            if (producerField.type == KafkaFieldType.SCHEMA_REGISTRY) {
                producerField.parsedSchema?.validate()
                //Random int in template is presented as text so we disable check object
                if (FieldTemplateGenerator.hasTemplatesWithRemoveQuotas(producerField.valueText))
                    return true
                producerField.getValueObj()
            }
            schemaValidationError = null
            true
        } catch (_: StackOverflowError) {
            schemaValidationError = StackOverflowError(KafkaMessagesBundle.message("error.schema.infinite.recursion"))
            false
        } catch (t: Throwable) {
            schemaValidationError = t
            false
        } finally {
            if (schemaValidationError?.toPresentableText() != oldValidationError)
                revalidateFields()
        }
    }

    @RequiresBackgroundThread
    fun getProducerField(): ConsumerProducerFieldConfig {
        val fieldType = fieldTypeComboBox.item
        val registryType = kafkaManager.registryType
        val schemaName = schemaComboBox.item?.schemaName ?: ""
        val schemaFormat = schemaComboBox.item?.schemaFormat ?: KafkaRegistryFormat.UNKNOWN

        val schema = when (fieldType) {
            null, KafkaFieldType.STRING, KafkaFieldType.JSON, KafkaFieldType.LONG, KafkaFieldType.INTEGER,
            KafkaFieldType.DOUBLE, KafkaFieldType.FLOAT, KafkaFieldType.BASE64, KafkaFieldType.NULL -> null

            KafkaFieldType.SCHEMA_REGISTRY -> KafkaRegistryUtil.loadSchema(schemaName, fieldType, kafkaManager)
            KafkaFieldType.AVRO_CUSTOM, KafkaFieldType.PROTOBUF_CUSTOM -> customSchemaController.getSchema()
        }

        val topic = producedEditor.topicComboBox.item ?: error(KafkaMessagesBundle.message("error.topic.is.not.chosen"))
        return ConsumerProducerFieldConfig(
            type = fieldType,
            valueText = getValueText(),
            isKey = isKey,
            topic = topic.name,
            registryType = registryType,
            schemaName = schemaName,
            schemaFormat = schemaFormat,
            parsedSchema = schema
        )
    }

    private lateinit var textRow: Row
    private lateinit var jsonRow: Row
    private lateinit var loadFileLinkRow: Row
    private lateinit var jsonCell: Cell<EditorTextField>

    private lateinit var generateDataAction: Cell<ActionButton>

    fun getValidationInfo() =
        textField.getValidationInfo() ?: jsonField.getValidationInfo() ?: schemaComboBox.getValidationInfo()

    fun getSchemaValidationInfo() = customSchemaController.getValidationInfo()

    fun createComponent(panel: Panel) {
        panel.apply {
            val title = if (isKey)
                KafkaMessagesBundle.message("consumer.producer.key.group")
            else
                KafkaMessagesBundle.message("consumer.producer.value.group")

            group(title, indent = false) {
                row(KafkaMessagesBundle.message("consumer.producer.format.type")) {
                    cell(fieldTypeComboBox).resizableColumn().gap(RightGap.SMALL)
                    val predicate = ComponentPredicate.fromObservableProperty(randomGenerationEnabled)
                    generateDataAction = actionButton(createGenerateAction()).visibleIf(!predicate)
                }
                rowsRange {
                    row(KafkaMessagesBundle.message("settings.format.registry.schema")) {
                        cell(schemaComboBox).onChanged { schemaValidationError = null }.resizableColumn()
                            .gap(RightGap.SMALL)
                        actionButton(
                            DumbAwareAction.create(
                                KafkaMessagesBundle.message("show.schema.info"),
                                AllIcons.Actions.ToggleVisibility
                            ) {
                                executeNotOnEdt {
                                    try {
                                        val config = getProducerField()
                                        val schema = config.parsedSchema ?: return@executeNotOnEdt
                                        invokeLater {
                                            KafkaSchemaInfoDialog.show(
                                                project = project, schemaType = schema.schemaType(),
                                                schemaDefinition = schema.canonicalString(),
                                                schemaName = config.schemaName
                                            )
                                        }
                                    } catch (t: Throwable) {
                                        RfsNotificationUtils.showExceptionMessage(project, t)
                                    }
                                }
                            })
                    }
                }.visibleIf(fieldTypeComboBox.selectedValueMatches { it == KafkaFieldType.SCHEMA_REGISTRY })
                customSchemaController.initComponent(this).visibleIf(customSchemaPredicate)
                row(KafkaMessagesBundle.message("settings.payload.row.label")) {}.visibleIf(customSchemaPredicate)
                jsonRow = row {
                    jsonCell = cell(jsonField).align(AlignX.FILL).resizableColumn().comment("")
                }
                textRow = row {
                    cell(textField).align(AlignX.FILL).resizableColumn()
                }
                row {
                    comment(KafkaMessagesBundle.message("producer.config.random.generation.enabled"))
                }.visibleIf(randomGenerationEnabled)
                loadFileLinkRow = row {
                    link(KafkaMessagesBundle.message("producer.config.link.upload.file")) {
                        val vf = FileChooserUtil.selectSingleFile(project) ?: return@link
                        executeNotOnEdt {
                            val readBytes = vf.readBytes()
                            invokeLater {
                                textField.text = Base64.getEncoder().encodeToString(readBytes)
                            }
                        }
                    }
                }.topGap(TopGap.NONE)
            }
        }
        isInitialized = true
        updateVisibility()
    }

    private fun updateFieldsText(type: KafkaFieldType, newText: String) = invokeAndWaitIfNeeded {
        if (type in jsonFieldTypes)
            jsonField.text = newText
        else
            textField.text = newText
    }

    @Suppress("UNUSED_PARAMETER")
    private fun validateValue(component: JComponent, text: String): String? {
        if (component.getUserData(SKIP_VALIDATION) == true) {
            component.putUserData(SKIP_VALIDATION, false)
            return null
        }
        return if (schemaValidationError != null)
            schemaValidationError?.message ?: schemaValidationError?.toPresentableText()
        else
            validate(fieldTypeComboBox.item ?: KafkaFieldType.STRING, getValueText())
    }

    fun getValueText(): String = when (fieldTypeComboBox.item!!) {
        KafkaFieldType.JSON -> jsonField.text
        KafkaFieldType.STRING, KafkaFieldType.INTEGER, KafkaFieldType.LONG, KafkaFieldType.DOUBLE, KafkaFieldType.FLOAT, KafkaFieldType.BASE64 -> textField.text
        KafkaFieldType.NULL -> ""
        KafkaFieldType.AVRO_CUSTOM, KafkaFieldType.PROTOBUF_CUSTOM, KafkaFieldType.SCHEMA_REGISTRY -> jsonField.text
    }

    private fun updateVisibility(): Unit = invokeLater {
        val fieldType = fieldTypeComboBox.item

        jsonRow.visible(!randomGenerationEnabled.get() && fieldType in jsonFieldTypes)
        textRow.visible(!randomGenerationEnabled.get() && fieldType in textFieldTypes)
        loadFileLinkRow.visible(!randomGenerationEnabled.get() && fieldType == KafkaFieldType.BASE64)

        invokeLater {
            generateDataAction.component.update()
        }

        updateJsonComment()
    }

    private fun validate(type: KafkaFieldType, value: String) = when (type) {
        KafkaFieldType.JSON -> try {
            JsonParser.parseString(value)
            null
        } catch (iae: Exception) {
            iae.cause?.message ?: iae.message
        }

        KafkaFieldType.STRING -> null
        KafkaFieldType.INTEGER -> if (value.toIntOrNull() != null) null else KafkaMessagesBundle.message(
            "producer.field.int.invalid",
            value
        )

        KafkaFieldType.LONG -> if (value.toLongOrNull() != null) null else KafkaMessagesBundle.message(
            "producer.field.long.invalid",
            value
        )

        KafkaFieldType.DOUBLE -> if (value.toDoubleOrNull() != null) null
        else KafkaMessagesBundle.message("producer.field.double.invalid", value)

        KafkaFieldType.FLOAT -> if (value.toFloatOrNull() != null) null else KafkaMessagesBundle.message(
            "producer.field.float.invalid",
            value
        )

        KafkaFieldType.BASE64 -> {
            val decoder = Base64.getDecoder()
            try {
                decoder.decode(value)
                null
            } catch (iae: IllegalArgumentException) {
                iae.message
            }
        }

        KafkaFieldType.NULL -> null // Any value match null type
        KafkaFieldType.AVRO_CUSTOM, KafkaFieldType.PROTOBUF_CUSTOM, KafkaFieldType.SCHEMA_REGISTRY -> try {
            JsonParser.parseString(value)
            executeNotOnEdt {
                validateSchema()
            }
            null
        } catch (iae: Exception) {
            iae.cause?.message ?: iae.message
        }
    }

    internal fun getCustomSchemaConfig(): CustomSchemaData? = customSchemaController.getSchemaConfig()

    fun applyConfig(config: StorageProducerConfig) {
        val fieldType = if (isKey) config.takeKeyType() else config.takeValueType()
        val text = if (isKey) config.key else config.value

        fieldTypeComboBox.item = fieldType
        when (fieldType) {
            KafkaFieldType.JSON -> jsonField.text = text
            KafkaFieldType.STRING, KafkaFieldType.INTEGER, KafkaFieldType.LONG, KafkaFieldType.DOUBLE, KafkaFieldType.FLOAT, KafkaFieldType.BASE64 -> textField.text =
                text

            KafkaFieldType.NULL -> Unit
            KafkaFieldType.SCHEMA_REGISTRY -> {
                jsonField.text = text
                schemaComboBox.item = if (isKey)
                    RegistrySchemaInEditor(config.keySubject, config.takeKeyFormat())
                else
                    RegistrySchemaInEditor(config.valueSubject, config.takeValueFormat())
            }

            KafkaFieldType.AVRO_CUSTOM, KafkaFieldType.PROTOBUF_CUSTOM -> {
                jsonField.text = text
                customSchemaController.setProducerConfig(config)
            }
        }
    }

    private fun updateJsonComment() {
        jsonCell.comment?.text = when (fieldTypeComboBox.item) {
            null, KafkaFieldType.STRING, KafkaFieldType.JSON, KafkaFieldType.INTEGER, KafkaFieldType.LONG, KafkaFieldType.DOUBLE, KafkaFieldType.FLOAT, KafkaFieldType.BASE64, KafkaFieldType.NULL -> ""
            KafkaFieldType.AVRO_CUSTOM, KafkaFieldType.PROTOBUF_CUSTOM, KafkaFieldType.SCHEMA_REGISTRY -> KafkaMessagesBundle.message(
                "producer.json.value.comment"
            )
        }
    }

    private fun createGenerateAction() = object : DumbAwareAction(
        KafkaMessagesBundle.message("generate.random.data"), null,
        AllIcons.Diff.MagicResolveToolbar
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            if (producedEditor.topicComboBox.item == null) {
                RfsNotificationUtils.showErrorMessage(
                    project,
                    KafkaMessagesBundle.message("error.topic.is.not.chosen"),
                    KafkaMessagesBundle.message("message.title")
                )
                return
            }

            if (schemaComboBox.isVisible && schemaComboBox.item == null) {
                RfsNotificationUtils.showErrorMessage(
                    project,
                    KafkaMessagesBundle.message("error.schema.is.not.chosen"),
                    KafkaMessagesBundle.message("message.title")
                )
                return
            }

            executeNotOnEdt {
                val config = try {
                    getProducerField()
                } catch (t: Throwable) {
                    invokeLater {
                        RfsNotificationUtils.showErrorMessage(
                            project,
                            t.message ?: t.toPresentableText(),
                            KafkaMessagesBundle.message("message.title")
                        )
                    }
                    null
                }
                config ?: return@executeNotOnEdt
                invokeLater {
                    updateFieldsText(config.type, GenerateRandomData.generate(project, config))
                    revalidateFields()
                }
            }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            when (fieldTypeComboBox.item) {
                KafkaFieldType.STRING,
                KafkaFieldType.LONG,
                KafkaFieldType.INTEGER,
                KafkaFieldType.DOUBLE,
                KafkaFieldType.FLOAT,
                KafkaFieldType.BASE64,
                KafkaFieldType.JSON,
                KafkaFieldType.SCHEMA_REGISTRY,
                KafkaFieldType.AVRO_CUSTOM,
                KafkaFieldType.PROTOBUF_CUSTOM
                    -> {
                    e.presentation.isEnabledAndVisible = !randomGenerationEnabled.get()
                    e.presentation.text = KafkaMessagesBundle.message("generate.random.data")
                }

                null, KafkaFieldType.NULL -> e.presentation.isEnabledAndVisible = false
            }
        }
    }

    companion object {
        private val jsonFieldTypes = setOf(
            KafkaFieldType.JSON, KafkaFieldType.AVRO_CUSTOM,
            KafkaFieldType.PROTOBUF_CUSTOM
        ) + KafkaFieldType.registryValues
        private val textFieldTypes = setOf(
            KafkaFieldType.STRING, KafkaFieldType.LONG, KafkaFieldType.INTEGER, KafkaFieldType.DOUBLE,
            KafkaFieldType.FLOAT,
            KafkaFieldType.BASE64
        )
    }
}