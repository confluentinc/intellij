package io.confluent.intellijplugin.core.rfs.tree.node

import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.editorviewer.RfsViewerEditorProvider
import io.confluent.intellijplugin.core.rfs.fileInfo.RfsChildrenPartId
import io.confluent.intellijplugin.util.KafkaMessagesBundle

abstract class DriverRfsServiceNode(driver: Driver, project: Project) : DriverRfsTreeNode(driver, project) {
    override val isLoading: Boolean get() = true
    override fun getChildren(): List<RfsTreeNode> = emptyList()
    override fun isAlwaysLeaf(): Boolean = true
    override val isMount: Boolean get() = false
}

class DriverRfsLoadingNode(
    driver: Driver, project: Project
) : DriverRfsServiceNode(driver, project) {
    override fun update(presentation: PresentationData) {
        presentation.addText(
            ColoredFragment(
                KafkaMessagesBundle.message("tree.action.label.loading"),
                SimpleTextAttributes.GRAY_ATTRIBUTES
            )
        )
    }
}

class DriverRfsLoadMoreNode(
    driver: Driver, project: Project,
    private val rfsChildrenPartId: RfsChildrenPartId,
    val loadMoreAction: DriverRfsLoadMoreNode.() -> Unit
) : DriverRfsServiceNode(driver, project) {

    override fun update(presentation: PresentationData) {
        presentation.addText(
            ColoredFragment(
                KafkaMessagesBundle.message("tree.action.label.load.more"),
                SimpleTextAttributes.LINK_ATTRIBUTES
            )
        )
        presentation.addText(ColoredFragment(" ".repeat(4), SimpleTextAttributes.REGULAR_ATTRIBUTES))
        presentation.addText(
            ColoredFragment(
                KafkaMessagesBundle.message("tree.action.label.open.in.editor"),
                SimpleTextAttributes.LINK_ATTRIBUTES
            )
        )
    }

    fun onClick(simpleColoredComponent: SimpleColoredComponent?, fragment: Int?): Boolean {
        fragment ?: return false
        simpleColoredComponent ?: return false
        return when (simpleColoredComponent.iterator(fragment).nextOrNull()) {
            KafkaMessagesBundle.message("tree.action.label.load.more") -> {
                loadMoreAction()
                true
            }

            KafkaMessagesBundle.message("tree.action.label.open.in.editor") -> {
                RfsViewerEditorProvider.createFileViewerEditor(project, driver, rfsChildrenPartId.rfsPath)
                true
            }

            else -> false
        }
    }
}

private fun <T> MutableIterator<T>.nextOrNull() = if (hasNext()) next() else null