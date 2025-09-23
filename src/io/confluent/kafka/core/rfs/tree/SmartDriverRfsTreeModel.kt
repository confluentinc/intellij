package io.confluent.kafka.core.rfs.tree

import com.intellij.openapi.project.Project
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.FileInfo
import io.confluent.kafka.core.rfs.driver.RfsPath
import javax.swing.tree.TreePath

open class SmartDriverRfsTreeModel(
  project: Project,
  rootPath: RfsPath,
  driver: Driver,
  enableLoadMore: Boolean = true
) : DriverRfsTreeModel(project, rootPath, driver, enableLoadMore) {
  var comparator: Comparator<FileInfo> = driver.getMetaInfoProvider().getDefaultComparator()

  override fun preprocessLoadedChildren(calculatedChildren: List<FileInfo>): List<FileInfo> {
    return calculatedChildren.sortedWith(comparator)
  }

  fun setSorting(comparator: Comparator<FileInfo>) {
    this.comparator = comparator
    invokeUpdateChildren(TreePath(root), propagate = true)
  }
}