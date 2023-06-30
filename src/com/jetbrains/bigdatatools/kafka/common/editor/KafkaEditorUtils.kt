package com.jetbrains.bigdatatools.kafka.common.editor

import com.amazonaws.services.schemaregistry.serializers.json.JsonDataWithSchema
import com.fasterxml.jackson.databind.node.ObjectNode
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
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import com.jetbrains.bigdatatools.common.monitoring.data.listener.DataModelListener
import com.jetbrains.bigdatatools.common.settings.getValidator
import com.jetbrains.bigdatatools.common.settings.withValidator
import com.jetbrains.bigdatatools.common.ui.ComponentColoredBorder
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.ui.DarculaTextAreaBorder
import com.jetbrains.bigdatatools.common.util.MessagesBundle
import com.jetbrains.bigdatatools.common.util.executeNotOnEdt
import com.jetbrains.bigdatatools.kafka.common.models.KafkaFieldType
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
import java.nio.charset.Charset
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JList

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
                      }) + additionalCustomization).apply {
        border = BorderFactory.createCompoundBorder(DarculaTextAreaBorder(), ComponentColoredBorder(3, 5, 3, 5))
        background = UIUtil.getTextFieldBackground()
        autoscrolls = false
        setCaretPosition(0)
      }

  fun getValueAsString(type: KafkaFieldType, value: Any?, format: KafkaRegistryFormat): String = when {
    value == null -> ""
    type == KafkaFieldType.BASE64 && value is ByteArray -> try {
      Base64.getEncoder().withoutPadding().encodeToString(value)
    }
    catch (e: Exception) {
      value.toString()
    }
    type == KafkaFieldType.JSON -> try {
      toPrettyJson(value.toString())
    }
    catch (e: Exception) {
      value.toString()
    }
    type == KafkaFieldType.SCHEMA_REGISTRY -> {
      when (format) {
        KafkaRegistryFormat.AVRO -> {
          val avro = AvroSchemaUtils.toJson(value).toString(Charset.defaultCharset())
          toPrettyJson(avro)
        }
        KafkaRegistryFormat.PROTOBUF -> try {
          val message = value as Message
          toPrettyJson(ProtobufSchemaUtils.toJson(message).toString(Charset.defaultCharset()))
        }
        catch (t: Throwable) {
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
          toPrettyJson(jsonString)
        }
        KafkaRegistryFormat.UNKNOWN -> value.toString()
      }
    }
    else -> value.toString()
  }

  fun toPrettyJson(jsonString: String): String {
    val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().serializeNulls().create()
    return gson.toJson(JsonParser.parseString(jsonString))
  }

  internal class KafkaDataModelListener<T>(private val comboBox: ComboBox<T>,
                                           private val onListUpdate: (List<T>) -> Unit = {},
                                           private val dataSupplier: () -> Pair<List<T>?, Int?>) : DataModelListener {
    override fun onChanged() = updateComboBox(comboBox, onListUpdate, dataSupplier)
    override fun onError(msg: String, e: Throwable?) = updateComboBox(comboBox, onListUpdate, dataSupplier)
  }


  fun createFieldTypeComboBox(topicCombobox: ComboBox<TopicInEditor>,
                              dataManager: KafkaDataManager,
                              isKey: Boolean,
                              onChange: (ComboBox<KafkaFieldType>) -> Unit): ComboBox<KafkaFieldType> {
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
                           isKey: Boolean): ComboBox<RegistrySchemaInEditor> {
    val (initSchemas, preferedIndex) = calculateSchemasForCombobox(kafkaManager, topicComboBox, isKey)
    val schemaCombobox = ComboBox(initSchemas.toTypedArray())

    topicComboBox.name
    schemaCombobox.isSwingPopup = false
    schemaCombobox.toolTipText = KafkaMessagesBundle.message("registry.subject.combobox.default.name")
    schemaCombobox.renderer = object : ColoredListCellRenderer<RegistrySchemaInEditor>() {
      override fun customizeCellRenderer(list: JList<out RegistrySchemaInEditor>,
                                         value: RegistrySchemaInEditor?,
                                         index: Int,
                                         selected: Boolean,
                                         hasFocus: Boolean) {
        val registrySchemaInEditor = value ?: return

        append(registrySchemaInEditor.schemaName + "  ")
        append(registrySchemaInEditor.schemaFormat?.presentable ?: "", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }

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




    topicComboBox.addItemListener {
      if (it.stateChange != SELECTED)
        return@addItemListener

      updateComboBox(schemaCombobox) { calculateSchemasForCombobox(kafkaManager, topicComboBox, isKey) }
    }


    kafkaManager.initRefreshSchemasIfRequired()
    schemaCombobox.withValidator(rootDisposable) {
      if (schemaCombobox.item == null)
        ValidationInfo(MessagesBundle.message("validator.notEmpty"))
      else
        null
    }
    return schemaCombobox
  }


  private fun calculateSchemaTypeForTopic(kafkaManager: KafkaDataManager,
                                          topicComboBox: ComboBox<TopicInEditor>,
                                          isKey: Boolean): KafkaFieldType? {
    val schema = calculateTopicSchemaName(kafkaManager, topicComboBox.item?.name ?: "", isKey) ?: return null
    val type = KafkaRegistryUtil.getSchemaType(schema, kafkaManager)
    return if (type != null)
      KafkaFieldType.SCHEMA_REGISTRY
    else
      null
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
    if (!isKey && topic in schemas)
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