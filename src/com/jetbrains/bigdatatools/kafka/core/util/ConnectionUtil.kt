package com.jetbrains.bigdatatools.kafka.core.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.jetbrains.bigdatatools.kafka.core.monitoring.rfs.MonitoringDriver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.manager.DriverManager
import com.jetbrains.bigdatatools.kafka.core.rfs.projectview.toolwindow.BigDataToolWindowController
import com.jetbrains.bigdatatools.kafka.core.rfs.projectview.toolwindow.BigDataToolWindowFactory
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.DisabledRfsTreeNode
import com.jetbrains.bigdatatools.kafka.core.rfs.util.RfsUtil
import com.jetbrains.bigdatatools.kafka.core.settings.CommonSettingsKeys
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.kafka.core.settings.manager.RfsConnectionDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls
import javax.swing.tree.TreePath

object ConnectionUtil {

  @Suppress("unused")
  fun createBearerAuth(token: String): Map<String, String> = mapOf("Authorization" to "Bearer $token")

  fun enableConnection(project: Project, treePath: TreePath): Boolean {
    val disabledNode = treePath.lastPathComponent as? DisabledRfsTreeNode ?: return false
    invokeLater {
      enableConnectionByData(project, disabledNode.connectionData)
    }
    return true
  }

  fun enableConnectionByData(project: Project, connectionData: ConnectionData) {
    enableConnectionsByData(project, listOf(connectionData))
  }

  fun enableConnectionsByData(project: Project, connectionDataList: List<ConnectionData>) {
    val connectionNames = connectionDataList.joinToString("\n") { it.name }
    val res = Messages.showYesNoDialog(project,
                                       KafkaMessagesBundle.message("enable.connection.confirmation.message",
                                                              if (connectionDataList.size == 1) 0 else 1, connectionNames),
                                       "",
                                       Messages.getQuestionIcon())
    if (res == Messages.OK) {
      connectionDataList.forEach { connectionData ->
        modifyConnection(project, connectionData, true)

        // And if this is possible, showing and focusing toolwindow.
        val driver = DriverManager.getDriverById(project, connectionData.innerId) as? MonitoringDriver
        val controller = driver?.getController(project)
        controller?.focusOn(connectionData.innerId)
      }
    }
  }

  fun modifyConnection(project: Project?, connectionData: ConnectionData, isEnable: Boolean) {
    connectionData.isEnabled = isEnable
    RfsConnectionDataManager.instance?.modifyConnection(project, connectionData, listOf(CommonSettingsKeys.ENABLED_KEY))
  }

  fun goToConnection(project: Project, connId: String, path: RfsPath? = null) {
    invokeLater {
      ToolWindowManager.getInstance(project).getToolWindow(BigDataToolWindowFactory.TOOL_WINDOW_ID)?.show()
      val paneOwner = BigDataToolWindowController.getInstance(project)?.getMainPane()?.actionsOwner ?: return@invokeLater
      val windowManager = ToolWindowManager.getInstance(project)
      (windowManager as ToolWindowManagerImpl).activateToolWindow("BigDataToolWindow", {
        RfsUtil.select(connId, path ?: RfsPath(listOf(), true), paneOwner)
      }, true, null)
    }
  }
}

fun Throwable.toPresentableText(): @Nls String {
  val source = realSource()
  return source.javaClass.simpleName.toString() +
         if (source.message != null) ": " + source.message.toString() else ""
}
