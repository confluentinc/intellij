package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.DetailsTableMonitoringController
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MainTreeController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupOffsetInfo
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings

class ConsumerGroupOffsetsController(val dataManager: KafkaDataManager) : DetailsTableMonitoringController<ConsumerGroupOffsetInfo, String>() {
  init {
    init()

    dataTable.customDataProvider = DataProvider { dataId ->
      when {
        MainTreeController.DATA_MANAGER.`is`(dataId) -> dataManager
        MainTreeController.RFS_PATH.`is`(dataId) -> {
          val consumerGroup = selectedId ?: return@DataProvider null
          KafkaDriver.consumerPath.child(consumerGroup, isDirectory = true)
        }
        else -> null
      }
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