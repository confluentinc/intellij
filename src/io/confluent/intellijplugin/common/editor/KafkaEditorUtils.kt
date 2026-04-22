package io.confluent.intellijplugin.common.editor

import com.amazonaws.services.schemaregistry.serializers.json.JsonDataWithSchema
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.protobuf.Message
import com.intellij.json.JsonLanguage
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.common.models.RegistrySchemaInEditor
import io.confluent.intellijplugin.common.models.TopicInEditor
import io.confluent.intellijplugin.core.monitoring.data.listener.DataModelListener
import io.confluent.intellijplugin.core.settings.getValidator
import io.confluent.intellijplugin.core.settings.withValidator
import io.confluent.intellijplugin.core.ui.ComponentColoredBorder
import io.confluent.intellijplugin.core.ui.CustomListCellRenderer
import io.confluent.intellijplugin.core.ui.DarculaTextAreaBorder
import io.confluent.intellijplugin.core.util.executeNotOnEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.data.BaseClusterDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaUtils
import org.apache.avro.generic.GenericData
import java.awt.event.ItemEvent.SELECTED
import java.nio.charset.Charset
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JList

object KafkaEditorUtils {
    fun createTextArea(
        project: Project,
        language: Language = JsonLanguage.INSTANCE,
        additionalCustomization: List<EditorCustomization> = emptyList()
    ) =
        EditorTextFieldProvider.getInstance()
            .getEditorField(
                language, project,
                listOf(EditorCustomization {
                    it.settings.apply {
                        isLineNumbersShown = false
                        isLineMarkerAreaShown = false
                        isFoldingOutlineShown = false
                        isRightMarginShown = false
                        additionalLinesCount = 0
                        additionalColumnsCount = 0
                        isAdditionalPageAtBottom = false
                        isShowIntentionBulb = false
                    }
                }) + additionalCustomization
            ).apply {
                border = BorderFactory.createCompoundBorder(DarculaTextAreaBorder(), ComponentColoredBorder(3, 5, 3, 5))
                background = UIUtil.getTextFieldBackground()
                autoscrolls = false
                setCaretPosition(0)
            }

    fun getValueAsString(type: KafkaFieldType, value: Any?, format: KafkaRegistryFormat): String = when {
        value == null -> ""
        type == KafkaFieldType.BASE64 && value is ByteArray -> try {
            Base64.getEncoder().withoutPadding().encodeToString(value)
        } catch (e: Exception) {
            value.toString()
        }

        type == KafkaFieldType.JSON -> tryFormatJson(value.toString())
        type == KafkaFieldType.PROTOBUF_CUSTOM -> try {
            val message = value as Message
            tryFormatJson(ProtobufSchemaUtils.toJson(message).toString(Charset.defaultCharset()))
        } catch (t: Throwable) {
            value.toString()
        }

        type == KafkaFieldType.SCHEMA_REGISTRY -> {
            when (format) {
                KafkaRegistryFormat.AVRO -> {
                    val avro = AvroSchemaUtils.toJson(value).toString(Charset.defaultCharset())
                    val name = (value as? GenericData.Record)?.schema?.fullName
                    tryFormatJson(avro, name)
                }

                KafkaRegistryFormat.PROTOBUF -> try {
                    val message = value as Message
                    tryFormatJson(ProtobufSchemaUtils.toJson(message).toString(Charset.defaultCharset()))
                } catch (t: Throwable) {
                    value.toString()
                }

                KafkaRegistryFormat.JSON -> {
                    val jsonString = when (value) {
                        is JsonDataWithSchema -> {
                            value.payload
                        }

                        is ObjectNode -> {
                            if (value.get("payload") != null)
                                JsonSchemaUtils.toJson(value.get("payload")).toString(Charset.defaultCharset())
                            else
                                JsonSchemaUtils.toJson(value).toString(Charset.defaultCharset())
                        }

                        else -> {
                            JsonSchemaUtils.toJson(value).toString(Charset.defaultCharset())
                        }
                    }
                    tryFormatJson(jsonString)
                }

                KafkaRegistryFormat.UNKNOWN -> value.toString()
            }
        }

        else -> value.toString()
    }

    internal class KafkaDataModelListener<T>(
        private val comboBox: ComboBox<T>,
        private val onListUpdate: (List<T>) -> Unit = {},
        private val dataSupplier: () -> Pair<List<T>?, Int?>
    ) : DataModelListener {
        override fun onChangedNonEdt() {
            updateComboBox(comboBox, onListUpdate, dataSupplier)
        }

        override fun onError(msg: String, e: Throwable?) = executeNotOnEdt {
            updateComboBox(comboBox, onListUpdate, dataSupplier)
        }
    }

    fun createFieldTypeComboBox(
        topicCombobox: ComboBox<TopicInEditor>,
        dataManager: BaseClusterDataManager,
        isKey: Boolean,
        rootDisposable: Disposable,
        onChange: (ComboBox<KafkaFieldType>) -> Unit
    ): ComboBox<KafkaFieldType> {
        val fieldTypes = if (dataManager.registryType != KafkaRegistryType.NONE)
            KafkaFieldType.allValues
        else
            KafkaFieldType.defaultValues

        val defaultFieldType = if (isKey) KafkaFieldType.STRING else KafkaFieldType.JSON
        val fieldsCombobox = ComboBox(fieldTypes.toTypedArray<KafkaFieldType>()).apply<ComboBox<KafkaFieldType>> {
            renderer = CustomListCellRenderer<KafkaFieldType> { it.title }
            selectedItem = defaultFieldType
            addActionListener {
                onChange(this)
            }
        }

        if (dataManager.registryType != KafkaRegistryType.NONE) {
            val job = SupervisorJob()
            val scope = CoroutineScope(Dispatchers.Default + job)
            Disposer.register(rootDisposable) { job.cancel() }

            topicCombobox.addItemListener {
                if (it.stateChange != SELECTED)
                    return@addItemListener

                scope.launch {
                    val schemaType = calculateSchemaTypeForTopic(dataManager, topicCombobox, isKey)
                        ?: KafkaFieldType.STRING
                    runInEdt {
                        fieldsCombobox.selectedItem = schemaType
                    }
                }
            }

            scope.launch {
                val schemaType = calculateSchemaTypeForTopic(dataManager, topicCombobox, isKey) ?: return@launch
                runInEdt {
                    fieldsCombobox.selectedItem = schemaType
                }
            }
        }

        return fieldsCombobox
    }

    fun createConsumerGroups(
        rootDisposable: Disposable,
        dataManager: BaseClusterDataManager,
        withEmpty: Boolean
    ): ComboBox<String> {
        val calcData = {
            val prefix = if (withEmpty) listOf("") else listOf()
            val cachedData = dataManager.consumerGroupsModel.data?.map { it.consumerGroup } ?: emptyList()
            prefix + cachedData
        }

        val comboBox = ComboBox((calcData()).toTypedArray())
        comboBox.isSwingPopup = false
        comboBox.prototypeDisplayValue = "       " // Field is set for limiting combobox width.
        comboBox.renderer = CustomListCellRenderer<String> { it }

        val listener = object : DataModelListener {
            override fun onChanged() = updateEditableComboBox(comboBox, calcData())
        }

        dataManager.consumerGroupsModel.addListener(listener)
        Disposer.register(rootDisposable) {
            dataManager.consumerGroupsModel.removeListener(listener)
        }
        comboBox.isEditable = true
        return comboBox
    }

    fun createTopicComboBox(rootDisposable: Disposable, dataManager: BaseClusterDataManager): ComboBox<TopicInEditor> {
        val topics = dataManager.getTopics()
        val topicComboBox = ComboBox(topics.map { it.toEditorTopic() }.sortedBy { it.name }.toTypedArray())
        topicComboBox.isSwingPopup = false
        topicComboBox.prototypeDisplayValue =
            TopicInEditor("Topic sample name") // Field is set for limiting combobox width.
        topicComboBox.renderer = CustomListCellRenderer<TopicInEditor> { it.name }

        val listener = KafkaDataModelListener(topicComboBox) {
            dataManager.loadTopicNames().map { it.toEditorTopic() }.sortedBy { it.name } to null
        }
        dataManager.topicModel.addListener(listener)
        Disposer.register(rootDisposable) {
            dataManager.topicModel.removeListener(listener)
        }

        topicComboBox.withValidator(rootDisposable) {
            val selectedItem = topicComboBox.selectedItem as? TopicInEditor
            if (selectedItem == null || selectedItem.name.isBlank())
                ValidationInfo(KafkaMessagesBundle.message("producer.error.topic.empty"), topicComboBox)
            else
                null
        }

        topicComboBox.getValidator()?.enableValidation()

        listener.onChangedNonEdt()

        return topicComboBox
    }

    fun createSchemaComboBox(
        rootDisposable: Disposable,
        kafkaManager: BaseClusterDataManager,
        topicComboBox: ComboBox<TopicInEditor>,
        isKey: Boolean
    ): ComboBox<RegistrySchemaInEditor> {
        var prevSchemaName: String? = null
        val schemaCombobox = ComboBox(arrayOf<RegistrySchemaInEditor>())

        topicComboBox.name
        schemaCombobox.isSwingPopup = false
        schemaCombobox.toolTipText = KafkaMessagesBundle.message("registry.subject.combobox.default.name")
        schemaCombobox.renderer = object : ColoredListCellRenderer<RegistrySchemaInEditor>() {
            override fun customizeCellRenderer(
                list: JList<out RegistrySchemaInEditor>,
                value: RegistrySchemaInEditor?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                val registrySchemaInEditor = value ?: return

                append(registrySchemaInEditor.schemaName + "  ")
                append(registrySchemaInEditor.schemaFormat?.presentable ?: "", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }

        val listener = KafkaDataModelListener(schemaCombobox) {
            val prevSchema = schemaCombobox.item?.schemaName?.takeIf { it.isNotEmpty() }
            prevSchema?.let { prevSchemaName = it }
            calculateSchemasForCombobox(kafkaManager, topicComboBox, isKey, prevSchemaName)
        }

        executeNotOnEdt {
            listener.onChangedNonEdt()
        }

        kafkaManager.schemaRegistryModel?.addListener(listener)
        Disposer.register(rootDisposable) {
            kafkaManager.schemaRegistryModel?.removeListener(listener)
        }

        topicComboBox.addItemListener {
            if (it.stateChange != SELECTED)
                return@addItemListener
            prevSchemaName = null
            updateComboBox(schemaCombobox) {
                calculateSchemasForCombobox(
                    kafkaManager,
                    topicComboBox,
                    isKey,
                    prevSchemaName
                )
            }
        }

        kafkaManager.initRefreshSchemasIfRequired()
        schemaCombobox.withValidator(rootDisposable) {
            if (schemaCombobox.item == null)
                ValidationInfo(KafkaMessagesBundle.message("validator.notEmpty"))
            else
                null
        }
        return schemaCombobox
    }

    private suspend fun calculateSchemaTypeForTopic(
        kafkaManager: BaseClusterDataManager,
        topicComboBox: ComboBox<TopicInEditor>,
        isKey: Boolean
    ): KafkaFieldType? {
        val schema =
            calculateTopicSchemaName(kafkaManager, topicComboBox.item?.name ?: "", isKey, schemas = null) ?: return null
        val type = kafkaManager.getCachedOrLoadSchema(schema).type
        return if (type != null)
            KafkaFieldType.SCHEMA_REGISTRY
        else
            null
    }

    private fun calculateSchemasForCombobox(
        kafkaManager: BaseClusterDataManager,
        topicComboBox: ComboBox<TopicInEditor>,
        isKey: Boolean,
        prevSchema: String?
    ): Pair<List<RegistrySchemaInEditor>, Int?> {
        val schemas = kafkaManager.getSchemasForEditor()

        val preferSchemaName =
            prevSchema ?: calculateTopicSchemaName(kafkaManager, topicComboBox.item?.name ?: "", isKey, schemas)
        val index = schemas.indexOfFirst { it.schemaName == preferSchemaName }.takeIf { it >= 0 }
        return schemas to index
    }

    private fun calculateTopicSchemaName(
        kafkaManager: BaseClusterDataManager,
        topic: String,
        isKey: Boolean,
        schemas: List<RegistrySchemaInEditor>?
    ): String? {
        val schemasNames = (schemas ?: kafkaManager.getSchemasForEditor()).map { it.schemaName }
        val confluentSchemaName = if (isKey) "$topic-key" else "$topic-value"
        if (confluentSchemaName in schemasNames)
            return confluentSchemaName
        if (!isKey && topic in schemasNames)
            return topic
        return null
    }

    fun <T> updateEditableComboBox(comboBox: ComboBox<T>, newData: List<T>) {
        val oldValues = (0 until comboBox.model.size).map {
            comboBox.model.getElementAt(it)
        }
        if (oldValues == newData) {
            return
        }
        val oldValue = comboBox.item
        comboBox.removeAllItems()
        newData.forEach {
            comboBox.addItem(it)
        }
        comboBox.item = oldValue

        comboBox.invalidate()
        comboBox.repaint()
    }

    fun <T> updateComboBox(
        comboBox: ComboBox<T>,
        onListUpdate: (List<T>) -> Unit = {},
        dataSupplier: () -> Pair<List<T>?, Int?>
    ) = executeNotOnEdt {
        val oldElements = (0 until comboBox.model.size).map {
            comboBox.model.getElementAt(it)
        }
        val (newElements, selectedItemIndex) = dataSupplier()
        invokeLater {
            if (oldElements == newElements) {
                updateSelectedIndex(comboBox, selectedItemIndex)
                return@invokeLater
            }


            val previouslySelectedIndex = comboBox.selectedIndex
            val previouslySelected = if (previouslySelectedIndex >= 0 && previouslySelectedIndex < comboBox.model.size) {
                comboBox.model.getElementAt(previouslySelectedIndex)
            } else null

            comboBox.removeAllItems()
            newElements?.forEach {
                comboBox.addItem(it)
            }

            if (selectedItemIndex != null) {
                updateSelectedIndex(comboBox, selectedItemIndex)
            } else if (previouslySelected != null && newElements != null) {
                val restoredIndex = newElements.indexOf(previouslySelected)
                if (restoredIndex >= 0) {
                    comboBox.selectedIndex = restoredIndex
                }
            }

            comboBox.invalidate()
            comboBox.repaint()
            onListUpdate(newElements ?: emptyList())
        }
    }

    private fun <T> updateSelectedIndex(
        comboBox: ComboBox<T>,
        selectedItemIndex: Int?
    ) {
        comboBox.selectedIndex = when {
            selectedItemIndex != null -> selectedItemIndex
            else -> comboBox.selectedIndex
        }
    }

    fun tryFormatJson(text: String, shemaName: String? = null): String {
        if (!isJsonString(text))
            return text
        return try {
            val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().serializeNulls().create()
            val parseString = JsonParser.parseString(text)
            try {
                shemaName?.let {
                    parseString.asJsonObject.addProperty("\$\$\$SchemaName\$\$\$", shemaName)
                }
            } catch (t: Throwable) {
                //Ignore
            }
            gson.toJson(parseString)
        } catch (e: Exception) {
            text
        }
    }

    fun isJsonString(text: String): Boolean {
        val first = text.firstOrNull { !Character.isWhitespace(it) } ?: return false
        val last = text.lastOrNull { !Character.isWhitespace(it) } ?: return false
        return first == '{' && last == '}' && text.contains(":") || first == '[' && last == ']'
    }
}