package io.confluent.kafka.core.rfs.tree

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.rfs.driver.depend.MasterDriver
import io.confluent.kafka.core.settings.manager.RfsConnectionDataManager

class CompoundTreeSource(val project: Project, val driver: MasterDriver, val rfsPath: RfsPath) : Disposable {
  private var rootSources: List<Pair<String, Boolean>> = emptyList()
  val treeModel = CompoundRfsTreeModel(project, { rootSources.map { it.first to null } }, emptyList()).also {
    Disposer.register(this, it)
  }

  override fun dispose() {}

  @RequiresEdt
  fun update(force: Boolean = false) {
    val connIds = driver.listDependConnections(rfsPath).toSet()
    val connections = if (!connIds.isEmpty())
      RfsConnectionDataManager.instance?.getConnections(project)?.filter { it.innerId in connIds } ?: emptyList()
    else
      emptyList()

    val newSources = connections.map { it.innerId to it.isEnabled }
    if (!force && newSources == rootSources)
      return

    rootSources = newSources
    treeModel.updateRoots()
  }
}