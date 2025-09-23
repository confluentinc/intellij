package io.confluent.kafka.core.monitoring.rfs

import com.intellij.openapi.project.Project
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.rfs.tree.node.CustomDoubleClickable
import io.confluent.kafka.core.rfs.tree.node.DriverFileRfsTreeNode

open class MonitoringRfsTreeNode(project: Project, rfsPath: RfsPath,
                                 final override val driver: MonitoringDriver) : DriverFileRfsTreeNode(project, rfsPath, driver),
                                                                                CustomDoubleClickable {
  protected val focusId = driver.connectionData.innerId

  override fun isAlwaysLeaf() = true

  override fun onDoubleClick(): Boolean {
    val project = project ?: return true
    val controller = driver.getController(project)
    controller?.focusOn(focusId)
    return true
  }
}