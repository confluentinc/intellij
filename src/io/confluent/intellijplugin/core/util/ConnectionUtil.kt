package io.confluent.intellijplugin.core.util

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresEdt
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver
import io.confluent.intellijplugin.core.rfs.driver.ActivitySource
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.manager.DriverManager
import io.confluent.intellijplugin.core.rfs.projectview.toolwindow.BigDataToolWindowController
import io.confluent.intellijplugin.core.rfs.projectview.toolwindow.BigDataToolWindowFactory
import io.confluent.intellijplugin.core.rfs.tree.node.DisabledRfsTreeNode
import io.confluent.intellijplugin.core.rfs.ui.BdtMessages
import io.confluent.intellijplugin.core.rfs.util.RfsUtil
import io.confluent.intellijplugin.core.settings.CommonSettingsKeys
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls
import javax.swing.tree.TreePath

object ConnectionUtil {
  internal val CONNECTION_ID: DataKey<String> = DataKey.create("ConnectionId")


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

  fun enableConnectionsByIds(project: Project, connectionIds: List<String>) {
    enableConnectionsByData(project, connectionIds.mapNotNull {
      RfsConnectionDataManager.instance?.getConnectionById(project, it)
    })
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

  private fun showRenameDialog(connData: ConnectionData, project: Project): String? {
    return BdtMessages.showInputDialogWithDescription(project,
                                                      KafkaMessagesBundle.message("action.rename.dialog.title", "connection"),
                                                      KafkaMessagesBundle.message("action.rename.dialog.label", "connection"),
                                                      null,
                                                      connData.name) { newName ->
      if (newName.isEmpty())
        KafkaMessagesBundle.message("validation.connection.should.not.empty")
      else
        null
    }
  }

  private fun getConnectionData(project: Project, connectionId: String): ConnectionData? {
    val dataManager = RfsConnectionDataManager.instance ?: return null
    return dataManager.getConnectionById(project, connectionId)
  }

  fun renameConnection(project: Project, connectionId: String) {
    val connectionData = getConnectionData(project, connectionId) ?: return
    val dataManager = RfsConnectionDataManager.instance ?: return
    val newName = showRenameDialog(connectionData, project) ?: return
    connectionData.name = newName
    dataManager.modifyConnection(project, connectionData, listOf(CommonSettingsKeys.NAME_KEY))
  }

  @RequiresEdt
  fun removeConnectionsWithConfirmation(project: Project, selectedConnectionsIds: List<String>) {
    val selectedConnections = selectedConnectionsIds.mapNotNull {
      RfsConnectionDataManager.instance?.getConnectionById(project, it)
    }

    val connectionNames = selectedConnections.joinToString { it.name }
    val messageText = KafkaMessagesBundle.message(if (selectedConnectionsIds.size < 2)
                                                    "deleteConnection.single.text"
                                                  else
                                                    "deleteConnection.multiple.text", connectionNames)

    val askUser = Messages.showOkCancelDialog(project, messageText, "",
                                              KafkaMessagesBundle.message("deleteConnection.okText"),
                                              CommonBundle.getCancelButtonText(),
                                              Messages.getQuestionIcon())
    if (askUser != Messages.OK)
      return

    runWithModalProgressBlocking(project, KafkaMessagesBundle.message("progress.title.credentials.removing")) {
      selectedConnections.forEach {
        it.clearCredentials()
      }
    }
    selectedConnections.forEach {
      RfsConnectionDataManager.instance?.removeConnectionKeepingCredentials(project, it)
    }
  }

  fun disableConnectionsByIds(project: Project, connectionIds: List<String>) {
    val resultDataList = connectionIds.mapNotNull { RfsConnectionDataManager.instance?.getConnectionById(project, it) }
    disableConnectionsByData(project, resultDataList)
  }

  private fun disableConnectionsByData(project: Project, connectionDataList: List<ConnectionData>) {
    if (connectionDataList.isEmpty()) {
      return
    }
    val connectionNames = connectionDataList.joinToString("\n") { it.name }

    val res = Messages.showYesNoDialog(project,
                                       KafkaMessagesBundle.message("disable.connection.text", if (connectionDataList.size == 1) 0 else 1,
                                                                   connectionNames), "", Messages.getQuestionIcon())
    if (res == Messages.OK) {
      connectionDataList.forEach {
        modifyConnection(project, it, false)
      }
    }
  }

  fun refreshConnectionsByIds(project: Project, connectionIds: List<String>) {
    val toRefresh = connectionIds.mapNotNull {
      DriverManager.getDriverById(project, it) as? MonitoringDriver
    }.map { it to null as RfsPath? }
    refreshConnections(toRefresh)
  }

  private fun refreshConnections(toRefresh: List<Pair<Driver, RfsPath?>>) {
    executeNotOnEdt {
      for ((driver, path) in toRefresh) {
        if (path == null)
          runBlockingMaybeCancellable {
            driver.fileInfoManager.refreshDriver(ActivitySource.ACTION)
          }
        else
          driver.fileInfoManager.refreshFiles(path)
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
