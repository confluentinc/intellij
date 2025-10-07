package io.confluent.intellijplugin.core.rfs.driver.local.node

import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.tree.node.DriverFileRfsTreeNode
import io.confluent.intellijplugin.core.rfs.tree.node.RfsDriverTreeNodeBuilder

class LocalRfsDriverTreeNodeBuilder : RfsDriverTreeNodeBuilder() {
    override fun createNode(project: Project, path: RfsPath, driver: Driver): DriverFileRfsTreeNode =
        LocalDriverRfsNode(project, path, driver)
}