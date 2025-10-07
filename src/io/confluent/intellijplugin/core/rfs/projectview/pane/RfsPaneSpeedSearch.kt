package io.confluent.intellijplugin.core.rfs.projectview.pane

import com.intellij.ui.TreeSpeedSearch
import io.confluent.intellijplugin.core.rfs.viewer.utils.DriverRfsTreeUtil.lastDriverNode
import javax.swing.JTree
import javax.swing.tree.TreePath

internal class RfsPaneSpeedSearch private constructor(jTree: JTree) : TreeSpeedSearch(jTree, null as Void?) {
    override fun isMatchingElement(element: Any?, pattern: String): Boolean {
        val node = (element as? TreePath)?.lastDriverNode ?: return false
        val nodePath = if (pattern.contains("/"))
            node.fileInfo?.path?.stringRepresentation()
        else
            node.presentation.presentableText

        return nodePath != null && compare(nodePath, pattern)
    }

    companion object {
        fun installOn(jTree: JTree): RfsPaneSpeedSearch {
            val search = RfsPaneSpeedSearch(jTree)
            search.setupListeners()
            return search
        }
    }
}