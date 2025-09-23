package io.confluent.kafka.spring

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.lang.properties.psi.Property
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl
import com.intellij.microservices.jvm.config.MetaConfigKeyReference
import com.intellij.microservices.jvm.mq.gutters.MQLineMarkerActionsProvider
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.spring.SpringLibraryUtil
import io.confluent.kafka.util.KafkaMessagesBundle
import javax.swing.Icon

internal class KafkaSpringBootConfigLineMarkers : LineMarkerProviderDescriptor() {
  override fun getName(): String = KafkaMessagesBundle.message("gutter.name.kafka.configuration.in.spring.boot")

  override fun getIcon(): Icon = AllIcons.Providers.Kafka

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

  override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
    val module = elements.firstOrNull()?.let { ModuleUtilCore.findModuleForPsiElement(it) }
    if (!SpringLibraryUtil.hasSpringLibrary(module)) return

    for (element in elements) {
      addLineMarker(element, result)
    }
  }

  private fun addLineMarker(element: PsiElement, result: MutableCollection<in LineMarkerInfo<*>>) {
    if (element is PropertyKeyImpl) {
      // Properties
      for (reference in element.references) {
        if (reference is MetaConfigKeyReference<*>) {
          val name = reference.resolvedKey?.name
          if (isServersKey(name)) {
            val servers = (element.parent as? Property)?.unescapedValue

            result.add(createContributedActionMarker(element, OpenSettingsAction(servers)))
          }

          break
        }
      }
    }
    else if (element.language.id == "yaml") {
      // YAML
      for (reference in element.references) {
        if (reference is MetaConfigKeyReference<*>) {
          val name = reference.resolvedKey?.name
          if (isServersKey(name)) {
            val valueElement = element.getLastChild() as? PsiLanguageInjectionHost
            val servers = valueElement?.let { ElementManipulators.getValueText(it) }

            result.add(createContributedActionMarker(element, OpenSettingsAction(servers)))
          }

          break
        }
      }
    }
  }

  private fun isServersKey(name: String?): Boolean {
    return name == SPRING_KAFKA_BOOTSTRAP_SERVERS_KEY || name == SPRING_KAFKA_CONSUMER_BOOTSTRAP_SERVERS_KEY
  }

  @NlsSafe
  private val KAFKA_NAVIGATION_GROUP: String = "Kafka"

  private fun createContributedActionMarker(
    anchor: PsiElement,
    lineMarkerAction: MQLineMarkerActionsProvider.Action
  ): RelatedItemLineMarkerInfo<PsiElement> {
    return NavigationGutterIconBuilder.create(lineMarkerAction.getLineMarkerIcon(), KAFKA_NAVIGATION_GROUP)
      .setAlignment(GutterIconRenderer.Alignment.LEFT)
      .setTooltipText(lineMarkerAction.getLineMarkerTooltipText())
      .setTargets(emptyList())
      .createLineMarkerInfo(anchor) { e, _ ->
        lineMarkerAction.action.actionPerformed(
          AnActionEvent.createFromInputEvent(e, "", null, DataManager.getInstance().getDataContext(e.component))
        )
      }
  }

  private class OpenSettingsAction(
    private val brokerServers: String? = null
  ) : DumbAwareAction(), MQLineMarkerActionsProvider.Action, HighPriorityAction {
    override fun actionPerformed(e: AnActionEvent) {
      KafkaBootstrapService.getInstance(e.project!!).showKafkaSettingsPopup(brokerServers, null, e)
    }

    override fun getLineMarkerName(): String = KafkaMessagesBundle.message("gutter.action.setup.connection")
    override fun getLineMarkerTooltipText(): String = KafkaMessagesBundle.message("gutter.action.setup.connection.description")
    override fun getLineMarkerIcon(): Icon = AllIcons.Providers.Kafka

    override val action: AnAction
      get() = this
  }
}