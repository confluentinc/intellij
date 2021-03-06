package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.TopicPartition
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.monitoring.table.DataTable
import com.jetbrains.bigdatatools.monitoring.table.DataTableCreator
import com.jetbrains.bigdatatools.monitoring.table.extension.TableExtensionType
import com.jetbrains.bigdatatools.monitoring.table.extension.TableHeightFitter
import com.jetbrains.bigdatatools.monitoring.table.extension.TableLoadingDecorator
import com.jetbrains.bigdatatools.monitoring.table.model.DataTableColumnModel
import com.jetbrains.bigdatatools.monitoring.table.model.DataTableModel
import java.awt.BorderLayout
import java.util.*
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

class TopicPartitionsController(private val dataManager: KafkaDataManager) : Disposable {
  private val scrollPane: JBScrollPane

  private var topicId: String? = null

  private lateinit var topicPartitionsTable: DataTable<TopicPartition>
  private lateinit var topicPartitionsTableScrollPane: JBScrollPane

  init {
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.add(createTopicPartitions())

    val parentPanel = JPanel(BorderLayout(0, 10))
    parentPanel.add(panel, BorderLayout.NORTH)
    scrollPane = JBScrollPane(parentPanel)
    scrollPane.border = BorderFactory.createEmptyBorder()
  }

  fun setTopicId(topicId: String) {
    this.topicId = topicId

    val topicPartitionModel = dataManager.getTopicPartitionsModel(topicId)
    topicPartitionsTable.tableModel.setDataModel(topicPartitionModel)
    if (topicPartitionModel.size > 0) {
      TableHeightFitter.fitSize(topicPartitionsTableScrollPane, topicPartitionsTable)
    }

    TableLoadingDecorator.installOn(topicPartitionsTable)
  }

  fun getComponent() = scrollPane

  private fun createTopicPartitions(): JComponent {
    val tasksColumnSettings = KafkaToolWindowSettings.getInstance().topicPartitionsColumnSettings

    val columnModel = DataTableColumnModel(TopicPartition.renderableColumns, tasksColumnSettings)
    val tableModel = DataTableModel(null, columnModel)

    topicPartitionsTable = DataTableCreator.create(tableModel, EnumSet.of(TableExtensionType.SPEED_SEARCH,
                                                                          TableExtensionType.RENDERERS_SETTER,
                                                                          TableExtensionType.ERROR_HANDLER,
                                                                          TableExtensionType.LOADING_INDICATOR,
                                                                          TableExtensionType.COLUMNS_FITTER))
    Disposer.register(this, topicPartitionsTable)

    topicPartitionsTableScrollPane = JBScrollPane(topicPartitionsTable)
    topicPartitionsTableScrollPane.border = BorderFactory.createEmptyBorder()

    TableHeightFitter.installOn(topicPartitionsTableScrollPane, topicPartitionsTable)


    return SimpleToolWindowPanel(false, true).apply {
      setContent(topicPartitionsTableScrollPane)
    }
  }

  override fun dispose() {}
}