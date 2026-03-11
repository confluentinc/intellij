package io.confluent.intellijplugin.consumer.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.layout.selectedValueMatches
import io.confluent.intellijplugin.core.rfs.driver.SafeExecutor
import kotlinx.coroutines.launch
import io.confluent.intellijplugin.common.editor.KafkaEditorUtils
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.common.models.RegistrySchemaInEditor
import io.confluent.intellijplugin.common.settings.StorageConsumerConfig
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.consumer.models.CustomSchemaData
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.settings.getValidationInfo
import io.confluent.intellijplugin.core.util.executeNotOnEdtSuspend
import io.confluent.intellijplugin.producer.editor.CustomSchemaController
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryUtil
import io.confluent.intellijplugin.registry.ui.KafkaSchemaInfoDialog
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class KafkaConsumerFieldComponent(
    private val project: Project,
    private val consumerPanel: KafkaConsumerPanel, val isKey: Boolean
) : Disposable {
    private val kafkaManager = consumerPanel.kafkaManager

    private val customSchemaController =
        CustomSchemaController(project, isKey).also { Disposer.register(this, it) }


    val fieldTypeComboBox =
        KafkaEditorUtils.createFieldTypeComboBox(consumerPanel.topicComboBox, consumerPanel.kafkaManager, isKey, this) {
            consumerPanel.updateVisibility()
            consumerPanel.storeToUserData()
            customSchemaController.setLanguage(it.item)
        }
    private val customSchemaPredicate = fieldTypeComboBox.selectedValueMatches {
        it in setOf(KafkaFieldType.AVRO_CUSTOM, KafkaFieldType.PROTOBUF_CUSTOM)
    }

    val schemaComboBox = KafkaEditorUtils.createSchemaComboBox(
        consumerPanel,
        consumerPanel.kafkaManager,
        consumerPanel.topicComboBox,
        isKey
    )


    init {
        customSchemaController.setLanguage(fieldTypeComboBox.item)
    }

    internal fun getCustomSchemaConfig(): CustomSchemaData? = customSchemaController.getSchemaConfig()

    override fun dispose() {}

    fun createComponent(panel: Panel) {
        val title = if (isKey)
            KafkaMessagesBundle.message("consumer.producer.key.group")
        else
            KafkaMessagesBundle.message("consumer.producer.value.group")

        panel.apply {
            group(title) {
                row(KafkaMessagesBundle.message("consumer.producer.format.type")) {
                    cell(fieldTypeComboBox).align(Align.FILL).resizableColumn()
                }
                rowsRange {
                    row(KafkaMessagesBundle.message("settings.format.registry.schema")) {
                        cell(schemaComboBox).align(Align.FILL).resizableColumn().gap(RightGap.SMALL)
                        actionButton(
                            DumbAwareAction.create(
                                KafkaMessagesBundle.message("show.schema.info"),
                                AllIcons.Actions.ToggleVisibility
                            ) {
                                SafeExecutor.instance.coroutineScope.launch {
                                    try {
                                        executeNotOnEdtSuspend {
                                            val config = loadFieldConfig()
                                            val schema = config.parsedSchema ?: return@executeNotOnEdtSuspend
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
                    }.visibleIf(fieldTypeComboBox.selectedValueMatches { it == KafkaFieldType.SCHEMA_REGISTRY })
                }
                customSchemaController.initComponent(this).visibleIf(customSchemaPredicate)
            }
        }
    }

    fun load(config: StorageConsumerConfig) {
        fieldTypeComboBox.item = if (isKey) config.getKeyType() else config.getValueType()
        customSchemaController.setConsumerConfig(config)
        schemaComboBox.item = if (isKey)
            RegistrySchemaInEditor(schemaName = config.keySubject, schemaFormat = config.getKeyFormat())
        else
            RegistrySchemaInEditor(schemaName = config.valueSubject, schemaFormat = config.getValueFormat())
    }

    fun updateIsEnabled(isEnabled: Boolean) {
        fieldTypeComboBox.isEnabled = isEnabled
        schemaComboBox.isEnabled = isEnabled
    }

    suspend fun loadFieldConfig(): ConsumerProducerFieldConfig {
        val fieldType = fieldTypeComboBox.item
        val registryType = kafkaManager.registryType
        val schemaName = schemaComboBox.item?.schemaName ?: ""
        val schema = when (fieldType) {
            null, KafkaFieldType.STRING, KafkaFieldType.JSON, KafkaFieldType.LONG, KafkaFieldType.INTEGER,
            KafkaFieldType.DOUBLE, KafkaFieldType.FLOAT, KafkaFieldType.BASE64, KafkaFieldType.NULL -> null

            KafkaFieldType.SCHEMA_REGISTRY -> KafkaRegistryUtil.loadSchema(schemaName, fieldType, kafkaManager)
            KafkaFieldType.AVRO_CUSTOM, KafkaFieldType.PROTOBUF_CUSTOM -> customSchemaController.getSchema()
        }

        return ConsumerProducerFieldConfig(
            type = fieldType,
            valueText = "",
            isKey = isKey,
            topic = consumerPanel.topicComboBox.item?.name ?: "",
            registryType = registryType,
            schemaName = schemaName,
            schemaFormat = KafkaRegistryFormat.parse(schema?.schemaType()),
            parsedSchema = schema
        )
    }

    fun getValidationInfo() = schemaComboBox.getValidationInfo()
}