package io.confluent.kafka.core.rfs.editorviewer

import com.intellij.openapi.Disposable
import com.intellij.ui.tree.BaseTreeModel
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.rfs.projectview.pane.NodesUpdateAnimator
import io.confluent.kafka.core.rfs.tree.node.RfsTreeNode

internal class RfsNodeAnimator(rfsTreeModel: BaseTreeModel<out RfsTreeNode>) : Disposable {
  private val animator = NodesUpdateAnimator(rfsTreeModel, this).also {
    it.resume()
  }

  fun setRepainter(body: (driver: Driver, rfsPath: RfsPath) -> Unit) {
    animator.paintNotifier.plusAssign { driver, rfsPath ->
      body(driver, rfsPath)
    }
  }

  override fun dispose() {
  }
}