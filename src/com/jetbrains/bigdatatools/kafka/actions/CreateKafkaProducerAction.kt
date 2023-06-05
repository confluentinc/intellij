package com.jetbrains.bigdatatools.kafka.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.bigdatatools.common.rfs.driver.manager.DriverManager
import com.jetbrains.bigdatatools.common.settings.actions.CreateConnectionPopup
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.toolwindow.actions.KafkaCreateProducerAction

class CreateKafkaProducerAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val actions = DriverManager.getDrivers(project).filterIsInstance<KafkaDriver>().map { driver ->
      create(driver.connectionData.name) {
        KafkaCreateProducerAction.openProducer(driver.dataManager, project, null)
      }
    }

    val additional = listOf(Separator(), ActionManager.getInstance().getAction("Kafka.GlobalCreateKafkaConnection"))
    CreateConnectionPopup.createPopup(DefaultActionGroup(actions + additional), e).showCenteredInCurrentWindow(project)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}