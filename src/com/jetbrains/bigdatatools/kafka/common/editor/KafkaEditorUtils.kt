package com.jetbrains.bigdatatools.kafka.common.editor

import com.amazonaws.services.schemaregistry.serializers.json.JsonDataWithSchema
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.protobuf.Message
import com.intellij.json.JsonLanguage
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.MonospaceEditorCustomization
import com.intellij.util.ui.UIUtil
import com.jetbrains.bigdatatools.common.monitoring.data.listener.DataModelListener
import com.jetbrains.bigdatatools.common.settings.getValidator
import com.jetbrains.bigdatatools.common.settings.withValidator
import com.jetbrains.bigdatatools.common.ui.ComponentColoredBorder
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.ui.DarculaTextAreaBorder
import com.jetbrains.bigdatatools.common.util.executeNotOnEdt
import com.jetbrains.bigdatatools.common.util.invokeLater
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.RegistrySchemaInEditor
import com.jetbrains.bigdatatools.kafka.common.models.TopicInEditor
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaUtils
import org.apache.kafka.common.ConsumerGroupState
import java.awt.event.ItemEvent.SELECTED
import java.awt.event.ItemListener
import java.nio.charset.Charset
import java.util.*
import javax.swing.BorderFactory

object KafkaEditorUtils {
  fun createTextArea(project: Project,
                     language: Language = JsonLanguage.INSTANCE,
                     additionalCustomization: List<EditorCustomization> = emptyList()) =
    EditorTextFieldProvider.getInstance()
      .getEditorField(language, project,
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
                      }, MonospaceEditorCustomization.getInstance()) + additionalCustomization).apply {
        border = BorderFactory.createCompoundBorder(DarculaTextAreaBorder(), ComponentColoredBorder(3, 5, 3, 5))
        background = UIUtil.getTextFieldBackground()
        autoscrolls = false
        setCaretPosition(0)
      }

  fun getValueAsString(type: FieldType, value: Any?): String = when {
    value == null -> ""
    type == FieldType.BASE64 && value is ByteArray -> try {
      Base64.getEncoder().withoutPadding().encodeToString(value)
    }
    catch (e: Exception) {
      value.toString()
    }
    type == FieldType.JSON -> try {
      toPrettyJson(value.toString())
    }
    catch (e: Exception) {
      value.toString()
    }
    type == FieldType.AVRO_REGISTRY -> {
      val avro = AvroSchemaUtils.toJson(value).toString(Charset.defaultCharset())
      toPrettyJson(avro)
    }
    type == FieldType.PROTOBUF_REGISTRY -> {
      try {
        val message = value as Message
        toPrettyJson(ProtobufSchemaUtils.toJson(message).toString(Charset.defaultCharset()))
      }
      catch (t: Throwable) {
        value.toString()
      }
    }
    type == FieldType.JSON_REGISTRY -> {
      val jsonString = if (value is JsonDataWithSchema) {
        value.payload
      }
      else {
        JsonSchemaUtils.toJson(value).toString(Charset.defaultCharset())
      }
      toPrettyJson(jsonString)
    }
    else -> value.toString()
  }

  fun toPrettyJson(jsonString: String): String {
    val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().serializeNulls().create()
    return gson.toJson(JsonParser.parseString(jsonString))
  }

  internal class KafkaDataModelListener<T>(private val comboBox: ComboBox<T>,
                                           val onListUpdate: (List<T>) -> Unit = {},
                                           private val dataSupplier: () -> Pair<List<T>?, Int?>) : DataModelListener {
    override fun onChanged() = updateComboBox(comboBox, onListUpdate, dataSupplier)
    override fun onError(msg: String, e: Throwable?) = updateComboBox(comboBox, onListUpdate, dataSupplier)

  }


  fun createFieldTypeComboBox(topicCombobox: ComboBox<TopicInEditor>,
                              dataManager: KafkaDataManager,
                              isKey: Boolean,
                              onChange: (ComboBox<FieldType>) -> Unit): ComboBox<FieldType> {
    val fieldTypes = if (dataManager.registryType != KafkaRegistryType.NONE)
      FieldType.allValues
    else
      FieldType.defaultValues


    val defaultFieldType = if (isKey) FieldType.STRING else FieldType.JSON
    val fieldsCombobox = ComboBox(fieldTypes.toTypedArray<FieldType>()).apply<ComboBox<FieldType>> {
      renderer = CustomListCellRenderer<FieldType> { it.title }
      selectedItem = defaultFieldType
      addActionListener {
        onChange(this)
      }
    }

    if (dataManager.registryType != KafkaRegistryType.NONE) {
      topicCombobox.addItemListener {
        if (it.stateChange != SELECTED)
          return@addItemListener

        fieldsCombobox.selectedItem = calculateSchemaTypeForTopic(dataManager, topicCombobox, isKey) ?: defaultFieldType
      }
    }

    executeNotOnEdt {
      fieldsCombobox.selectedItem = calculateSchemaTypeForTopic(dataManager, topicCombobox, isKey) ?: return@executeNotOnEdt
    }

    return fieldsCombobox
  }


  fun createConsumerGroups(rootDisposable: Disposable, kafkaManager: KafkaDataManager): ComboBox<ConsumerGroupPresentable> {
    val groups = kafkaManager.consumerGroupsModel
    val comboBox = ComboBox(groups.data?.map { it }?.toTypedArray() ?: emptyArray())
    comboBox.prototypeDisplayValue = ConsumerGroupPresentable(state = ConsumerGroupState.UNKNOWN,
                                                              consumerGroup = "Group sample name",
                                                              consumers = 0,
                                                              topics = 0,
                                                              partitions = 0) // Field is set for limiting combobox width.
    comboBox.renderer = CustomListCellRenderer<ConsumerGroupPresentable> { it.consumerGroup }
    val listener = KafkaDataModelListener(comboBox) { kafkaManager.consumerGroupsModel.data?.map { it } to null }
    kafkaManager.consumerGroupsModel.addListener(listener)
    Disposer.register(rootDisposable) {
      kafkaManager.consumerGroupsModel.removeListener(listener)
    }

    return comboBox
  }


  fun createTopicComboBox(rootDisposable: Disposable, kafkaManager: KafkaDataManager): ComboBox<TopicInEditor> {
    val topics = kafkaManager.getTopics()
    val topicComboBox = ComboBox(topics.map { it.toEditorTopic() }.sortedBy { it.name }.toTypedArray())
    topicComboBox.isSwingPopup = false
    topicComboBox.prototypeDisplayValue = TopicInEditor("Topic sample name") // Field is set for limiting combobox width.
    topicComboBox.renderer = CustomListCellRenderer<TopicInEditor> { it.name }

    val listener = KafkaDataModelListener(topicComboBox) {
      kafkaManager.getTopics().map { it.toEditorTopic() }.sortedBy { it.name } to null
    }
    kafkaManager.topicModel.addListener(listener)
    Disposer.register(rootDisposable) {
      kafkaManager.topicModel.removeListener(listener)
    }

    topicComboBox.withValidator(rootDisposable) {
      val selectedItem = topicComboBox.selectedItem as? TopicInEditor
      if (selectedItem == null || selectedItem.name.isBlank())
        ValidationInfo(KafkaMessagesBundle.message("producer.error.topic.empty"), topicComboBox)
      else
        null
    }

    topicComboBox.getValidator()?.enableValidation()
    return topicComboBox
  }


  fun createSchemaComboBox(rootDisposable: Disposable,
                           kafkaManager: KafkaDataManager,
                           topicComboBox: ComboBox<TopicInEditor>,
                           fieldTypeComboBox: ComboBox<FieldType>,
                           isKey: Boolean): ComboBox<RegistrySchemaInEditor> {
    val (initSchemas, preferedIndex) = calculateSchemasForCombobox(kafkaManager, topicComboBox, isKey)
    val schemaCombobox = ComboBox(initSchemas.toTypedArray())

    topicComboBox.name
    schemaCombobox.isSwingPopup = false
    schemaCombobox.toolTipText = KafkaMessagesBundle.message("registry.subject.combobox.default.name")
    schemaCombobox.renderer = CustomListCellRenderer<RegistrySchemaInEditor> { it.toString() }

    schemaCombobox.selectedIndex = when {
      preferedIndex != null -> preferedIndex
      initSchemas.isNotEmpty() -> 0
      else -> -1
    }

    val listener = KafkaDataModelListener(schemaCombobox) {
      calculateSchemasForCombobox(kafkaManager, topicComboBox, isKey)
    }

    kafkaManager.schemaRegistryModel?.addListener(listener)
    Disposer.register(rootDisposable) {
      kafkaManager.schemaRegistryModel?.removeListener(listener)
    }

    var validationInfo: ValidationInfo? = null


    val updateListener = ItemListener {
      if (it.stateChange != SELECTED)
        return@ItemListener

      executeNotOnEdt {
        val newValidation = validateSchemaType(kafkaManager, schemaCombobox, fieldTypeComboBox)
        if (newValidation != validationInfo) {
          validationInfo = newValidation
          invokeLater {
            schemaCombobox.getValidator()?.revalidate()
          }
        }
      }
    }

    topicComboBox.addItemListener {
      if (it.stateChange != SELECTED)
        return@addItemListener

      updateComboBox(schemaCombobox) { calculateSchemasForCombobox(kafkaManager, topicComboBox, isKey) }
    }

    schemaCombobox.addItemListener(updateListener)
    fieldTypeComboBox.addItemListener(updateListener)


    kafkaManager.initRefreshSchemasIfRequired()
    schemaCombobox.withValidator(rootDisposable) { validationInfo }
    schemaCombobox.getValidator()?.enableValidation()

    return schemaCombobox
  }


  private fun validateSchemaType(kafkaManager: KafkaDataManager,
                                 schemaCombobox: ComboBox<RegistrySchemaInEditor>,
                                 fieldTypeComboBox: ComboBox<FieldType>): ValidationInfo? {
    val registry = schemaCombobox.item ?: return null
    val registryType = KafkaRegistryUtil.getSchemaType(registry.schemaName,
                                                       kafkaManager) ?: return null

    val selectedFieldType = fieldTypeComboBox.item ?: return null

    val isCorrect = when (registryType) {
      KafkaRegistryFormat.AVRO -> selectedFieldType == FieldType.AVRO_REGISTRY
      KafkaRegistryFormat.PROTOBUF -> selectedFieldType == FieldType.PROTOBUF_REGISTRY
      KafkaRegistryFormat.JSON -> selectedFieldType == FieldType.JSON_REGISTRY
    }
    if (isCorrect)
      return null
    val message = KafkaMessagesBundle.message("producer.validation.incorrect.schema.format",
                                              schemaCombobox.item?.schemaName ?: "",
                                              registryType.presentable)
    return ValidationInfo(message, schemaCombobox)
  }


  private fun calculateSchemaTypeForTopic(kafkaManager: KafkaDataManager,
                                          topicComboBox: ComboBox<TopicInEditor>,
                                          isKey: Boolean): FieldType? {
    val schema = calculateTopicSchemaName(kafkaManager, topicComboBox.item?.name ?: "", isKey) ?: return null
    val type = KafkaRegistryUtil.getSchemaType(schema, kafkaManager)
    return when (type) {
      KafkaRegistryFormat.AVRO -> FieldType.AVRO_REGISTRY
      KafkaRegistryFormat.PROTOBUF -> FieldType.PROTOBUF_REGISTRY
      KafkaRegistryFormat.JSON -> FieldType.JSON_REGISTRY
      null -> null
    }
  }


  private fun calculateSchemasForCombobox(kafkaManager: KafkaDataManager,
                                          topicComboBox: ComboBox<TopicInEditor>,
                                          isKey: Boolean): Pair<List<RegistrySchemaInEditor>, Int?> {
    val schemas = kafkaManager.getSchemasForEditor()
    val preferSchemaName = calculateTopicSchemaName(kafkaManager, topicComboBox.item?.name ?: "", isKey)
    val index = schemas.indexOfFirst { it.schemaName == preferSchemaName }.takeIf { it >= 0 }
    return schemas to index
  }

  private fun calculateTopicSchemaName(kafkaManager: KafkaDataManager, topic: String, isKey: Boolean): String? {
    val schemas = kafkaManager.getSchemasForEditor().map { it.schemaName }
    val confluentSchemaName = if (isKey) "$topic-key" else "$topic-value"
    if (confluentSchemaName in schemas)
      return confluentSchemaName
    if (topic in schemas)
      return topic
    return null
  }

  fun <T> updateComboBox(comboBox: ComboBox<T>, onListUpdate: (List<T>) -> Unit = {}, dataSupplier: () -> Pair<List<T>?, Int?>) {
    val oldTopics = (0 until comboBox.model.size).map {
      comboBox.model.getElementAt(it)
    }
    val (newTopics, selectedItemIndex) = dataSupplier()
    if (oldTopics == newTopics) {
      updateSelectedIndex(comboBox, selectedItemIndex)
      return
    }
    comboBox.removeAllItems()
    newTopics?.forEach {
      comboBox.addItem(it)
    }

    updateSelectedIndex(comboBox, selectedItemIndex)

    comboBox.invalidate()
    comboBox.repaint()
    onListUpdate(newTopics ?: emptyList())
  }

  private fun <T> updateSelectedIndex(comboBox: ComboBox<T>,
                                      selectedItemIndex: Int?) {
    comboBox.selectedIndex = when {
      selectedItemIndex != null -> selectedItemIndex
      else -> comboBox.selectedIndex
    }
  }

  fun tryFormatJson(text: String): String {
    if (!isJsonString(text))
      return text
    return try {
      val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().serializeNulls().create()
      gson.toJson(JsonParser.parseString(text))
    }
    catch (e: Exception) {
      text
    }
  }

  fun isJsonString(text: String): Boolean {
    val first = text.firstOrNull { !Character.isWhitespace(it) } ?: return false
    val last = text.lastOrNull { !Character.isWhitespace(it) } ?: return false
    return first == '{' && last == '}' && text.contains(":") || first == '[' && last == ']'
  }
}