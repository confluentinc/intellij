package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.UiDataProvider
import com.jetbrains.bigdatatools.kafka.core.monitoring.toolwindow.DetailsTableMonitoringController
import com.jetbrains.bigdatatools.kafka.core.monitoring.toolwindow.MainTreeController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupOffsetInfo
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings

class ConsumerGroupOffsetsController(val dataManager: KafkaDataManager) : DetailsTableMonitoringController<ConsumerGroupOffsetInfo, String>() {
  init {
    init()

    dataTable.customDataProvider = UiDataProvider { sink ->
      sink[MainTreeController.DATA_MANAGER] = dataManager
      sink[MainTreeController.RFS_PATH] = selectedId
        ?.let { KafkaDriver.consumerPath.child(it, isDirectory = true) }
    }
  }

  override fun getAdditionalContextActions(): List<AnAction> {
    val actionManager = ActionManager.getInstance()
    val group = actionManager.getAction("Kafka.Consumer.Group.Actions") as DefaultActionGroup
    return group.getChildren(actionManager).toList()
  }


  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().consumerGroupOffsetColumnSettings

  override fun getRenderableColumns() = ConsumerGroupOffsetInfo.renderableColumns

  override fun showColumnFilter(): Boolean = false

  override fun getDataModel() = selectedId?.let { dataManager.consumerGroupsOffsets[it] }
}