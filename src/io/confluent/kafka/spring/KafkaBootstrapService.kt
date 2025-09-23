package io.confluent.kafka.spring

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.awt.RelativePoint
import io.confluent.kafka.core.rfs.driver.manager.DriverManager
import io.confluent.kafka.core.settings.ConnectionSettings
import io.confluent.kafka.core.settings.actions.CreateConnectionPopup
import io.confluent.kafka.rfs.KafkaConfigurationSource
import io.confluent.kafka.rfs.KafkaDriver
import io.confluent.kafka.settings.KafkaConnectionGroup
import io.confluent.kafka.toolwindow.actions.KafkaCreateConsumerAction
import io.confluent.kafka.toolwindow.actions.KafkaCreateProducerAction
import io.confluent.kafka.util.KafkaMessagesBundle
import java.awt.event.MouseEvent

@Service(Service.Level.PROJECT)
internal class KafkaBootstrapService(val project: Project) {
  fun showConsumerWithPopup(brokerServers: String?, defaultTopic: String?, e: AnActionEvent) {
    val actions = DriverManager.getDrivers(project).filterIsInstance<KafkaDriver>().map { driver ->
      DumbAwareAction.create(driver.connectionData.name) {
        KafkaCreateConsumerAction.createConsumer(project, driver.dataManager, defaultTopic)
      }
    }
    val additional = listOf(Separator(), createKafkaSettingsAction(brokerServers))

    CreateConnectionPopup.createPopup(DefaultActionGroup(actions + additional), e.dataContext)
      .show(RelativePoint(e.inputEvent as MouseEvent))
  }

  fun showProducerWithPopup(brokerServers: String?, defaultTopic: String?, e: AnActionEvent) {
    val actions = DriverManager.getDrivers(project).filterIsInstance<KafkaDriver>().map { driver ->
      DumbAwareAction.create(driver.connectionData.name) {
        KafkaCreateProducerAction.openProducer(driver.dataManager, project, defaultTopic)
      }
    }
    val additional = listOf(Separator(), createKafkaSettingsAction(brokerServers))

    CreateConnectionPopup.createPopup(DefaultActionGroup(actions + additional), e.dataContext)
      .show(RelativePoint(e.inputEvent as MouseEvent))
  }

  fun showKafkaSettingsPopup(brokerServers: String?, defaultTopic: String?, e: AnActionEvent) {
    val actions = DriverManager.getDrivers(project).filterIsInstance<KafkaDriver>().map { driver ->
      DumbAwareAction.create(driver.connectionData.name) {
        ConnectionSettings.open(project, connectionId = driver.connectionData.innerId)
        KafkaCreateProducerAction.openProducer(driver.dataManager, project, defaultTopic)
      }
    }
    val additional = listOf(Separator(), createKafkaSettingsAction(brokerServers))

    CreateConnectionPopup.createPopup(DefaultActionGroup(actions + additional), e.dataContext)
      .show(RelativePoint(e.inputEvent as MouseEvent))
  }

  private fun createKafkaSettingsAction(brokerServers: String?) = DumbAwareAction.create(
    KafkaMessagesBundle.message("action.Kafka.GlobalCreateKafkaConnection.text")) {
    val group = KafkaConnectionGroup()
    val connectionData = group.createBlankData()
    if (brokerServers != null) {
      connectionData.brokerConfigurationSource = KafkaConfigurationSource.FROM_UI
      connectionData.uri = brokerServers
    }
    ConnectionSettings.create(project, group, connectionData, applyIfOk = true)
  }

  companion object {
    fun getInstance(project: Project) = project.service<KafkaBootstrapService>()
  }
}