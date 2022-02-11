package com.jetbrains.bigdatatools.kafka.common.editor

import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.MonospaceEditorCustomization
import com.intellij.util.ui.UIUtil
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.TopicInEditor
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.monitoring.data.listener.DataModelListener
import com.jetbrains.bigdatatools.ui.ComponentColoredBorder
import com.jetbrains.bigdatatools.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.ui.DarculaTextAreaBorder
import org.apache.kafka.common.ConsumerGroupState
import java.nio.charset.StandardCharsets
import java.util.*
import javax.swing.BorderFactory

object KafkaEditorUtils {

  fun createJsonTextArea(project: Project) = EditorTextFieldProvider.getInstance()
    .getEditorField(JsonLanguage.INSTANCE, project,
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
                    }, MonospaceEditorCustomization.getInstance())).apply {
      border = BorderFactory.createCompoundBorder(DarculaTextAreaBorder(), ComponentColoredBorder(3, 5, 3, 5))
      background = UIUtil.getTextFieldBackground() //DefaultLookup.getColor(this, null, "TextField.background", null)
      autoscrolls = false
      setCaretPosition(0)
    }

  fun getValueAsString(type: FieldType, value: Any?): String {
    return if (value == null) {
      ""
    }
    else if (type == FieldType.BASE64 && value is ByteArray) {
      try {
        String(Base64.getEncoder().encode(value), StandardCharsets.UTF_8)
      }
      catch (e: Exception) {
        value.toString()
      }
    }
    else {
      value.toString()
    }
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

    val listener = object : DataModelListener {
      override fun onChanged() = updateComboBox()
      override fun onError(msg: String, e: Throwable?) = updateComboBox()

      private fun updateComboBox() {
        val selectedItem = comboBox.item
        val oldTopics = (0 until comboBox.model.size).map {
          comboBox.model.getElementAt(it)
        }
        val newTopics = kafkaManager.consumerGroupsModel.data?.map { it }
        if (oldTopics == newTopics)
          return
        comboBox.removeAllItems()
        newTopics?.forEach {
          comboBox.addItem(it)
        }

        comboBox.item = if (newTopics?.contains(selectedItem) == true)
          selectedItem
        else
          null

        comboBox.invalidate()
        comboBox.repaint()
      }
    }
    kafkaManager.consumerGroupsModel.addListener(listener)
    Disposer.register(rootDisposable) {
      kafkaManager.consumerGroupsModel.removeListener(listener)
    }

    return comboBox
  }

  fun createTopicComboBox(rootDisposable: Disposable, kafkaManager: KafkaDataManager): ComboBox<TopicInEditor> {
    val topics = kafkaManager.getTopics()
    val topicComboBox = ComboBox(topics.map { it.toEditorTopic() }.toTypedArray())
    topicComboBox.prototypeDisplayValue = TopicInEditor("Topic sample name") // Field is set for limiting combobox width.
    topicComboBox.renderer = CustomListCellRenderer<TopicInEditor> { it.name }

    val listener = object : DataModelListener {
      override fun onChanged() = updateComboBox()
      override fun onError(msg: String, e: Throwable?) = updateComboBox()

      private fun updateComboBox() {
        val selectedItem = topicComboBox.item
        val oldTopics = (0 until topicComboBox.model.size).map {
          topicComboBox.model.getElementAt(it)
        }
        val newTopics = kafkaManager.getTopics().map { it.toEditorTopic() }
        if (oldTopics == newTopics)
          return
        topicComboBox.removeAllItems()
        newTopics.forEach {
          topicComboBox.addItem(it)
        }

        topicComboBox.item = if (selectedItem in newTopics)
          selectedItem
        else
          null

        topicComboBox.invalidate()
        topicComboBox.repaint()
      }
    }
    kafkaManager.topicModel.addListener(listener)
    Disposer.register(rootDisposable) {
      kafkaManager.topicModel.removeListener(listener)
    }

    return topicComboBox
  }
}