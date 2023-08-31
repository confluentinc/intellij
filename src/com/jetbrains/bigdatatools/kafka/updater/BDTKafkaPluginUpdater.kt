package com.jetbrains.bigdatatools.kafka.updater

import com.intellij.ide.plugins.StandalonePluginUpdateChecker
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.jetbrains.bigdatatools.common.BigdatatoolsCoreIcons
import com.jetbrains.bigdatatools.common.constants.BdtPluginType
import com.jetbrains.bigdatatools.common.constants.BdtPlugins
import com.jetbrains.bigdatatools.common.settings.connections.connType
import com.jetbrains.bigdatatools.common.settings.manager.RfsConnectionDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BDTKafkaPostStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    // https://youtrack.jetbrains.com/issue/BDIDE-4925
    // Here, on post startup, we only check that kafka connection exists.
    val connections = RfsConnectionDataManager.instance?.getConnections(project) ?: return
    if (connections.any { it.connType.pluginType == BdtPluginType.KAFKA }) {
      withContext(Dispatchers.EDT) {
        BDTKafkaPluginUpdater.instance.pluginUsed()
      }
    }
  }
}

@Service
class BDTKafkaPluginUpdater : StandalonePluginUpdateChecker(
  PluginId.getId(BdtPlugins.KAFKA_ID),
  "bdt.kafka.lastUpdateCheck",
  NotificationGroupManager.getInstance().getNotificationGroup("BDT Updates"),
  BigdatatoolsCoreIcons.BigData) {

  companion object {
    val instance: BDTKafkaPluginUpdater
      get() = service()
  }
}