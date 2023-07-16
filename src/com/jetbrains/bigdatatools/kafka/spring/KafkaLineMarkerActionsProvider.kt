package com.jetbrains.bigdatatools.kafka.spring

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.microservices.mq.KAFKA_TOPIC_TYPE
import com.intellij.microservices.mq.MQTargetInfo
import com.intellij.microservices.mq.gutters.MQLineMarkerActionsProvider
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.psi.PsiElement
import com.intellij.spring.boot.model.SpringBootConfigValueSearcher
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import icons.MicroservicesIcons
import javax.swing.Icon

internal class KafkaLineMarkerActionsProvider : MQLineMarkerActionsProvider {
  override fun getActions(relatedItems: Collection<MQTargetInfo>, anchor: PsiElement?): Collection<MQLineMarkerActionsProvider.Action> {
    if (!relatedItems.any { it.destination.type.isCompatibleWith(KAFKA_TOPIC_TYPE) }) {
      return emptyList()
    }

    if (anchor == null) return emptyList()
    val module = ModuleUtilCore.findModuleForPsiElement(anchor) ?: return emptyList()

    val servers = findServers(module).firstOrNull()

    return listOf(
      OpenConsumerAction(servers, relatedItems.firstOrNull()?.destination?.name),
      OpenProducerAction(servers, relatedItems.firstOrNull()?.destination?.name),
    )
  }

  private fun findServers(module: Module): Sequence<String> {
    return sequence {
      SpringBootConfigValueSearcher.productionForAllProfiles(module, SPRING_KAFKA_BOOTSTRAP_SERVERS_KEY)
        .findValueText()
        ?.let { yield(it) }

      SpringBootConfigValueSearcher.productionForAllProfiles(module, SPRING_KAFKA_CONSUMER_BOOTSTRAP_SERVERS_KEY)
        .findValueText()
        ?.let { yield(it) }
    }
  }
}

internal class OpenConsumerAction(
  private val brokerServers: String? = null,
  private val topicName: String? = null
) :  DumbAwareAction(), MQLineMarkerActionsProvider.Action, HighPriorityAction {
  override fun actionPerformed(e: AnActionEvent) {
    KafkaBootstrapService.getInstance(e.project!!).showConsumerWithPopup(brokerServers, topicName, e.dataContext)
  }

  override fun getLineMarkerName(): String = KafkaMessagesBundle.message("gutter.action.observe.messages.in.topic")
  override fun getLineMarkerTooltipText(): String = KafkaMessagesBundle.message("gutter.action.opens.kafka.consumer.ui.description")
  override fun getLineMarkerIcon(): Icon = MicroservicesIcons.MQ.Kafka

  override val action: AnAction
    get() = this
}

internal class OpenProducerAction(
  private val brokerServers: String? = null,
  private val topicName: String? = null
) :  DumbAwareAction(), MQLineMarkerActionsProvider.Action, HighPriorityAction {
  override fun actionPerformed(e: AnActionEvent) {
    KafkaBootstrapService.getInstance(e.project!!).showProducerWithPopup(brokerServers, topicName, e.dataContext)
  }

  override fun getLineMarkerName(): String = KafkaMessagesBundle.message("gutter.action.send.messages.to.topic")
  override fun getLineMarkerTooltipText(): String = KafkaMessagesBundle.message("gutter.action.opens.kafka.producer.ui.description")
  override fun getLineMarkerIcon(): Icon = MicroservicesIcons.MQ.Kafka

  override val action: AnAction
    get() = this
}