package io.confluent.intellijplugin.core.rfs.projectview.pane

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tree.BaseTreeModel
import com.intellij.util.ui.Animator
import com.intellij.util.ui.tree.TreeModelAdapter
import io.confluent.intellijplugin.core.delegate.Delegate2
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.tree.node.DriverFileRfsTreeNode
import io.confluent.intellijplugin.core.rfs.tree.node.RfsTreeNode
import io.confluent.intellijplugin.core.util.invokeLater
import javax.swing.event.TreeModelEvent

class NodesUpdateAnimator(private val treeModel: BaseTreeModel<out RfsTreeNode>, disposable: Disposable) :
    Animator("Loading Icons", 2, 150, true, true, disposable) {
    private var updatingPaths = listOf<Pair<Driver, RfsPath>>()

    private val listener = object : TreeModelAdapter() {
        override fun process(event: TreeModelEvent, type: EventType) {
            val node = event.treePath.lastPathComponent
            val nodes = ((event.children ?: emptyArray()) + node).filterIsInstance<DriverFileRfsTreeNode>()

            if (type == EventType.NodesRemoved) {
                nodes.forEach {
                    removeAnimatedNode(it)
                }
            } else {
                nodes.forEach {
                    processNode(it)
                }
            }
        }

        private fun processNode(node: DriverFileRfsTreeNode) = if (node.isLoading)
            addAnimatedNode(node)
        else
            removeAnimatedNode(node)
    }

    val paintNotifier = Delegate2<Driver, RfsPath, Unit>()

    init {
        treeModel.addTreeModelListener(listener)

        Disposer.register(disposable) {
            paintNotifier.clear()
            treeModel.removeTreeModelListener(listener)
        }
    }

    override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
        updatingPaths.ifEmpty { return }
        updatingPaths.forEach { paintNotifier.notify(it.first, it.second) }
    }


    fun addAnimatedNode(node: DriverFileRfsTreeNode) {
        val prevNodes = updatingPaths
        if (updatingPaths.contains(node.driver to node.rfsPath))
            return

        updatingPaths = updatingPaths + (node.driver to node.rfsPath)
        if (prevNodes.isEmpty())
            resume()
    }

    fun removeAnimatedNode(node: DriverFileRfsTreeNode) {
        if (node.driver to node.rfsPath !in updatingPaths)
            return

        updatingPaths = updatingPaths - (node.driver to node.rfsPath)
        invokeLater {
            paintNotifier.notify(node.driver, node.rfsPath)
        }

        if (updatingPaths.isEmpty())
            try {
                suspend()
            } catch (t: Throwable) {
                logger.warn(t)
            }
    }

    companion object {
        private val logger = Logger.getInstance(this::class.java)
    }
}