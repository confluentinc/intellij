package io.confluent.intellijplugin.core.rfs.projectview.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import io.confluent.intellijplugin.core.rfs.tree.node.RfsTreeNode
import javax.swing.Icon

abstract class RfsProjectPaneActionBase : DumbAwareAction {
    constructor() : super()
    constructor(
        @NlsActions.ActionText text: String,
        @NlsActions.ActionDescription description: String? = null,
        icon: Icon? = null
    ) :
            super(text, description, icon)

    companion object {
        inline fun <T> withRfsPane(dataContext: DataContext, body: RfsProjectPaneActionContext.() -> T): T? {
            val pane = RfsPaneOwner.DATA_KEY.getData(dataContext)
            val project = CommonDataKeys.PROJECT.getData(dataContext)
            if (pane == null || project == null) {
                return null
            }
            return body(object : RfsProjectPaneActionContext {
                override val pane: RfsPaneOwner get() = pane
                override val project: Project get() = project
            })
        }

        inline fun withRfsPane(e: AnActionEvent, body: RfsProjectPaneActionContext.() -> Unit = {}) {
            withRfsPane(e.dataContext) {
                body()
            } ?: run {
                e.presentation.isEnabledAndVisible = false
            }
        }

        fun updateBaseActionEnabledAndVisible(e: AnActionEvent) = withRfsPane(e) {
            val selectedDriverNode = getSelectedDriverNode()
            e.presentation.isEnabledAndVisible = !(selectedDriverNode == null ||
                    selectedDriverNode.rfsPath.isRoot ||
                    (selectedDriverNode.fileInfo?.isCopySupport == false && selectedDriverNode.fileInfo?.isMoveSupport == false))

        }

        fun getMountNodes(pane: RfsPaneOwner) =
            pane.getSelectionPaths().map { it.lastPathComponent as? RfsTreeNode }.filter { it?.isMount == true }
    }
}