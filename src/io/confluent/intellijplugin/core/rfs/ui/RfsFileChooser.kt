package io.confluent.intellijplugin.core.rfs.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.projectview.RfsPanelDoubleClickListener
import io.confluent.intellijplugin.core.rfs.projectview.actions.RfsActionPlaces
import io.confluent.intellijplugin.core.rfs.projectview.pane.RfsPane
import io.confluent.intellijplugin.core.rfs.tree.node.DriverFileRfsTreeNode
import io.confluent.intellijplugin.core.rfs.tree.node.RfsTreeNode
import io.confluent.intellijplugin.core.rfs.util.RfsUtil
import io.confluent.intellijplugin.core.rfs.viewer.utils.DriverRfsTreeUtil.lastDriverNode
import io.confluent.intellijplugin.core.settings.connections.ConnectionGroup
import io.confluent.intellijplugin.core.settings.connections.ConnectionSettingProviderEP
import io.confluent.intellijplugin.core.util.ToolbarUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class RfsFileChooser(
    @Nls private val mainTitle: String,
    project: Project,
    private val preselectedDriver: Driver?,
    private val preselectedPath: String,
    private val descriptor: RfsChooserDescriptor,
    rootProvider: () -> List<Pair<String, RfsPath?>> = { emptyList() },
    drivers: List<Driver> = listOfNotNull(preselectedDriver),
    groups: List<ConnectionGroup> = ConnectionSettingProviderEP.getGroups()
) : DialogWrapper(project, false) {

    private val rfsPane = RfsPane(
        project,
        rootProvider,
        drivers,
        paneId = "RfsPaneFileChooser",
        preprocessChildren = ::preprocessChildren,
        groups = groups
    ).apply {
        init()
        RfsPanelDoubleClickListener.installOn(project, tree, ::onFileDoubleClicked)
        Disposer.register(myDisposable, this)
    }

    private val paneOwner = rfsPane.actionsOwner
    private val jTree = paneOwner.jTree
    private val filePathsTextField = JBTextField("")

    init {
        init()
    }

    private fun onFileDoubleClicked(tree: JTree, node: DriverFileRfsTreeNode) {
        val fileInfo = node.fileInfo ?: return
        filePathsTextField.text = fileInfo.externalPath
        close(OK_EXIT_CODE)
    }

    fun updateRoots() = rfsPane.updateRoots()

    override fun init() {
        super.init()
        title = mainTitle
        filePathsTextField.isEditable = false

        jTree.addTreeSelectionListener { _ ->
            val paths = jTree.selectionPaths
            filePathsTextField.text = if (paths == null) {
                isOKActionEnabled = false
                ""
            } else {
                val infos = mapToFileInfos(paths)
                isOKActionEnabled = isValidSelection(infos)
                filterSelectedPaths(infos).joinToString("; ") { it.externalPath }
            }
        }

        jTree.selectionModel?.selectionMode = if (descriptor.multipleSelection)
            TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
        else
            TreeSelectionModel.SINGLE_TREE_SELECTION

        jTree.scrollsOnExpand = true

        isOKActionEnabled = jTree.selectionPaths?.let { isValidSelection(mapToFileInfos(it)) } ?: false

        preselectedDriver?.let { RfsUtil.select(it.getExternalId(), it.createRfsPath(preselectedPath), paneOwner) }
    }

    override fun createCenterPanel(): JComponent {
        val toolBar = ToolbarUtils.createActionToolbar(
            rfsPane.tree,
            RfsActionPlaces.FILE_CHOOSER_TOOLBAR,
            descriptor.toolbarActions(),
            true
        )

        val treePanel = rfsPane.createComponent().apply {
            border = IdeBorderFactory.createBorder()
        }

        return JPanel(BorderLayout()).apply {
            preferredSize = JBUI.size(450)
            add(toolBar.component, BorderLayout.NORTH)
            add(treePanel, BorderLayout.CENTER)
            add(createTextFieldsPanel(filePathsTextField), BorderLayout.SOUTH)
        }
    }

    fun showAndGetResult() = if (showAndGet())
        filterSelectedPaths(mapToFileInfos(jTree.selectionPaths))
    else
        null

    private fun createTextFieldsPanel(filePathField: JTextField): JPanel {
        val panel = JPanel(BorderLayout())
        val message = KafkaMessagesBundle.message("rfs.file.chooser.path.label")
        panel.add(LabeledComponent.create(filePathField, message, BorderLayout.WEST), BorderLayout.CENTER)
        return panel
    }

    private fun mapToFileInfos(paths: Array<TreePath>?): List<FileInfo> = paths?.mapNotNull { path ->
        path.lastDriverNode?.fileInfo
    } ?: emptyList()

    private fun filterSelectedPaths(infos: List<FileInfo>): List<FileInfo> = infos.filter { descriptor.canChoose(it) }

    private fun isValidSelection(infos: List<FileInfo>): Boolean {
        val grouped = infos.groupBy { descriptor.canChoose(it) }
        return grouped.containsKey(true) && !grouped.containsKey(false)
    }

    private fun preprocessChildren(nodes: List<RfsTreeNode>) = nodes.filter { node ->
        node !is DriverFileRfsTreeNode || node.fileInfo?.let { descriptor.canChoose(it) } != false
    }
}

