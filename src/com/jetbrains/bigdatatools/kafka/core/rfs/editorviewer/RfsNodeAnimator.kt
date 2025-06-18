package com.jetbrains.bigdatatools.kafka.core.rfs.editorviewer

import com.intellij.openapi.Disposable
import com.intellij.ui.tree.BaseTreeModel
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.projectview.pane.NodesUpdateAnimator
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.RfsTreeNode

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