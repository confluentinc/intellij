package io.confluent.intellijplugin.core.rfs.fileInfo.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.ProjectManager
import io.confluent.intellijplugin.core.rfs.fileInfo.RfsChildrenPartId
import io.confluent.intellijplugin.core.rfs.projectview.toolwindow.BigDataToolWindowController
import io.confluent.intellijplugin.core.rfs.tree.node.DriverFileRfsTreeNode
import io.confluent.intellijplugin.core.util.BdtRefresherService
import kotlinx.coroutines.launch
import javax.swing.tree.TreePath
import kotlin.time.TimeSource

internal class DriverFilesAutoRefresher(val fileInfoManager: DriverCacheFileInfoManager) : Disposable {
    private var lastUpdateTime = TimeSource.Monotonic.markNow()
    override fun dispose() {}

    fun startUpdate() {
        fileInfoManager.driver.safeExecutor.coroutineScope.launch {
            BdtRefresherService.getInstance()
                .driverFileRefreshSchedule
                ?.subscribeImmediately()
                ?.collect {
                    if (fileInfoManager.getDriverConnectionStatus().isConnected()) {
                        if (lastUpdateTime.elapsedNow() > BdtRefresherService.getInstance().driverFileRefreshIntervalSetting!!) {
                            val expandedDirectories =
                                ProjectManager.getInstance().getOpenProjects().mapNotNull { project ->
                                    project.serviceIfCreated<BigDataToolWindowController>()
                                }.flatMap { toolWindow ->
                                    val tree = toolWindow.getMainPane().tree
                                    val treeModel = toolWindow.getMainPane().treeModel

                                    tree.getExpandedDescendants(TreePath(treeModel.root)).asSequence()
                                        .map { treePath -> treePath.lastPathComponent }
                                        .filterIsInstance<DriverFileRfsTreeNode>()
                                        .filter { node -> node.driver == fileInfoManager.driver }
                                        .map { node -> node.rfsPath }

                                }.distinct().sortedBy { it.size }
                            fileInfoManager.invalidateAll()
                            expandedDirectories.forEach { rfsPath ->
                                fileInfoManager.getChildren(RfsChildrenPartId(rfsPath), force = true).collect {}
                            }
                        }
                    }
                }
        }
    }

    fun allFilesUpdated() {
        lastUpdateTime = TimeSource.Monotonic.markNow()
    }
}