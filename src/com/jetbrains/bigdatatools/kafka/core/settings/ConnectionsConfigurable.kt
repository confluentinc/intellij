package com.jetbrains.bigdatatools.kafka.core.settings

import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.core.settings.manager.RfsConnectionDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class ConnectionsConfigurable(private val project: Project) : SearchableConfigurable, NoScroll {

  private val myUiDelegate = lazy { ConnectionSettingsPanel(project) }
  val myUi: ConnectionSettingsPanel by myUiDelegate

  private fun getSettings(): RfsConnectionDataManager = RfsConnectionDataManager.instance ?: error("RfsConnectionDataManager is not found")

  override fun getId() = "BdtConnectionsSettings"

  override fun getDisplayName() = KafkaMessagesBundle.message("connections.settings.display.name")

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

  override fun createComponent() = myUi.component

  override fun isModified() = myUiDelegate.isInitialized() && myUi.isModified(getSettings())

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