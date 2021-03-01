package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.monitoring.table.DataTableCreator
import com.jetbrains.bigdatatools.monitoring.table.extension.TableExtensionType
import com.jetbrains.bigdatatools.monitoring.table.model.DataTableColumnModel
import com.jetbrains.bigdatatools.monitoring.table.model.DataTableModel
import com.jetbrains.bigdatatools.table.MaterialJBScrollPane
import java.util.*
import javax.swing.JComponent

class TopicsController(project: Project, connectionData: KafkaConnectionData) : Disposable {
  private val component: JComponent
  private val dataManager = KafkaDataManager.getInstance(connectionData.innerId, project) ?: error("Data Manager is not inited")

  init {
    val dataModel = dataManager.getTopicModel()

    val columnSettings = KafkaToolWindowSettings.getInstance().topicColumnSettings

    val columnModel = DataTableColumnModel(TopicPresentable.renderableColumns, columnSettings)
    val tableModel = DataTableModel(dataModel, columnModel)

    val table = DataTableCreator.create(tableModel, EnumSet.of(TableExtensionType.SPEED_SEARCH,
                                                               TableExtensionType.RENDERERS_SETTER,
                                                               TableExtensionType.COLUMNS_FITTER,
                                                               TableExtensionType.ERROR_HANDLER,
                                                               TableExtensionType.SELECTION_PRESERVER,
                                                               TableExtensionType.LOADING_INDICATOR))
    Disposer.register(this, table)


    component = MaterialJBScrollPane(table)
  }

  override fun dispose() {}

  fun getComponent() = component
}