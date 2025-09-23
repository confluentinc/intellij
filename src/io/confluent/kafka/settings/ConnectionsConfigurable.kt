package io.confluent.kafka.core.settings

import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import io.confluent.kafka.core.settings.manager.RfsConnectionDataManager
import io.confluent.kafka.util.KafkaMessagesBundle
import javax.swing.JComponent

class ConnectionsConfigurable(private val project: Project) : SearchableConfigurable, NoScroll {

  private val myUiDelegate = lazy { ConnectionSettingsPanel(project) }
  val myUi: ConnectionSettingsPanel by myUiDelegate

  private fun getSettings(): RfsConnectionDataManager = RfsConnectionDataManager.instance ?: error("RfsConnectionDataManager is not found")

  override fun getId(): String = "KafkaConnectionsSettings"

  override fun getDisplayName(): String = KafkaMessagesBundle.message("connections.settings.display.name")

  override fun getHelpTopic(): String {
    return if (myUiDelegate.isInitialized())
      myUi.helpTopic
    else
      ConnectionSettingsPanel.COMMON_HELP_ID
  }

  override fun reset() {
    if (myUiDelegate.isInitialized()) {
      myUi.reset(getSettings())
    }
  }

  override fun createComponent(): JComponent = myUi.component

  override fun isModified(): Boolean = myUiDelegate.isInitialized() && myUi.isModified(getSettings())

  override fun apply() {
    if (myUiDelegate.isInitialized()) {
      myUi.apply(getSettings())
    }
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    if (myUiDelegate.isInitialized()) {
      myUi.disposeUIResources()
    }
  }
}