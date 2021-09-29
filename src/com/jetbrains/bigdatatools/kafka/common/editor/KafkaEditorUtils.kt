package com.jetbrains.bigdatatools.kafka.common.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.kafka.common.editor.renders.TopicRenderer
import com.jetbrains.bigdatatools.kafka.common.models.TopicInEditor
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.producer.models.RecordCompression
import com.jetbrains.bigdatatools.monitoring.data.listener.DataModelListener
import com.jetbrains.bigdatatools.ui.CustomListCellRenderer

object KafkaEditorUtils {
  fun createTopicComboBox(rootDisposable: Disposable, kafkaManager: KafkaDataManager): ComboBox<TopicInEditor> {
    val topics = kafkaManager.getTopics()
    val topicComboBox = ComboBox(topics.map { it.toEditorTopic() }.toTypedArray())
    topicComboBox.renderer = CustomListCellRenderer<TopicInEditor> { value -> value.name }

    val listener = object : DataModelListener {
      override fun onChanged() {
        updateComboBox()
      }

      override fun onError(msg: String, e: Throwable?) {
        updateComboBox()
      }

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
    Disposer.register(rootDisposable, Disposable {
      kafkaManager.topicModel.removeListener(listener)
    })

    return topicComboBox
  }
}