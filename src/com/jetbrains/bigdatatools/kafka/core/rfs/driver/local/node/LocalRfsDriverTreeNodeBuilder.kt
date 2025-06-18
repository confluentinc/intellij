package com.jetbrains.bigdatatools.kafka.core.rfs.driver.local.node

import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.DriverFileRfsTreeNode
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.RfsDriverTreeNodeBuilder

class LocalRfsDriverTreeNodeBuilder : RfsDriverTreeNodeBuilder() {
  override fun createNode(project: Project, path: RfsPath, driver: Driver): DriverFileRfsTreeNode = LocalDriverRfsNode(project, path, driver)
}