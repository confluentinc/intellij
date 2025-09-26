package io.confluent.intellijplugin.core.rfs.tree.node

import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.rfs.driver.Driver

abstract class DriverRfsTreeNode(open val driver: Driver, project: Project) : RfsTreeNode(project) {
  abstract val isLoading: Boolean
  override val connId: String
    get() = driver.getExternalId()
}