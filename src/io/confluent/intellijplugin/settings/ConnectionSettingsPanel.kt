package io.confluent.intellijplugin.core.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.*
import com.intellij.util.IconUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import io.confluent.intellijplugin.core.settings.actions.CreateConnectionPopup
import io.confluent.intellijplugin.core.settings.actions.showForToolbarOrInBestPositionFor
import io.confluent.intellijplugin.core.settings.connections.*
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import io.confluent.intellijplugin.core.settings.paneadd.StandaloneCreateConnectionUtil
import io.confluent.intellijplugin.core.util.BdIdeRegistryUtil
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Insets
import java.util.*
import java.util.function.Predicate
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.TitledBorder
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import kotlin.math.max
import kotlin.random.Random

class ConnectionSettingsPanel(val project: Project) : MasterDetailsComponent(),
    ConfigurableUi<RfsConnectionDataManager>, ConnectionSettingsListener {
    val addedConnections = mutableListOf<ConnectionData>()
    val removedConnections = mutableSetOf<ConnectionData>()

    private val topLevelGroups = mutableListOf<ActionNode>()
    private val groupToActionNode = mutableMapOf<String, ActionNode>() //ID -> Node
    private val idToGroup = mutableMapOf<String, ConnectionGroup>()

    private val extendedState: ExtendedState

    private var savedSelectedId: String? = null

    companion object {
        val CONNECTION_SETTINGS_PANEL_KEY = Key.create<ConnectionSettingsPanel>("ConnectionSettingsPanel")
        private val logger = Logger.getInstance(this::class.java)
        const val COMMON_HELP_ID = "big.data.tools.connections"
    }

    init {
        initTree()
        myRoot.userObject = null
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = BDTTreeCellRenderer()
        tree.emptyText.text = KafkaMessagesBundle.message("settings.empty.text")
        extendedState = ExtendedState(myRoot)

        TreeSpeedSearch.installOn(tree)

        tree.addTreeExpansionListener(object : TreeExpansionListener {
            override fun treeExpanded(event: TreeExpansionEvent?) {
                (event?.path?.lastPathComponent as? MyNode)?.let { extendedState.expand(it) }
            }

            override fun treeCollapsed(event: TreeExpansionEvent?) {
                (event?.path?.lastPathComponent as? MyNode)?.let { extendedState.collapse(it) }
            }
        })

        RfsConnectionDataManager.instance?.addListener(this)
    }

    // Overridden only to add null check in node.configurable?.disposeUIResources().
    override fun clearChildren() {
        for (node in TreeUtil.treeNodeTraverser(myRoot).filter(MyNode::class.java)) {
            node.configurable?.disposeUIResources()
            if (node !is MyRootNode) {
                node.userObject = null
            }
        }
        myRoot.removeAllChildren()
    }

    override fun disposeUIResources() {
        super.disposeUIResources()
        RfsConnectionDataManager.instance?.removeListener(this)
    }

    fun getTreeNode(innerId: String): MyNode? {
        return TreeUtil.treeNodeTraverser(tree.model.root as DefaultMutableTreeNode).filter(MyNode::class.java)
            .find { node ->
                getConnectionData(node)?.innerId == innerId
            }
    }

    // region ConnectionSettingsListener
    override fun onConnectionAdded(project: Project?, newConnectionData: ConnectionData) {
        if (addedConnections.contains(newConnectionData)) {
            return
        }

        val group = idToGroup[newConnectionData.groupId]
        if (group == null) {
            // Nearly impossible case of strange error.
            val dataManager = RfsConnectionDataManager.instance ?: return
            reset(dataManager)
        } else {
            createTreeNode(group, newConnectionData, selectAddedNode = false)
            expandAllNodes()
        }
    }

    override fun onConnectionRemoved(project: Project?, removedConnectionData: ConnectionData) {
        if (removedConnections.contains(removedConnectionData)) {
            return
        }

        val nodeToRemove = getTreeNode(removedConnectionData.innerId) ?: return
        treeWalkUpRemove(nodeToRemove)
    }
    // endregion ConnectionSettingsListener

    override fun getHelpTopic() = extendedState.selectedNode?.configurable?.helpTopic ?: COMMON_HELP_ID

    override fun getDisplayName() = KafkaMessagesBundle.message("connections.settings.display.name")

    override fun createActions(fromPopup: Boolean): List<AnAction> {
        val duplicateAction = DuplicateConnectionAction().apply {
            registerCustomShortcutSet(CommonShortcuts.getDuplicate(), tree)
        }

        val addAction = RootAddActionGroup(
            KafkaMessagesBundle.message("settings.addConnection.text"),
            IconUtil.addIcon,
            KafkaMessagesBundle.message("settings.addConnection.hint")
        ).apply {
            registerCustomShortcutSet(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD), tree)
        }

        return listOf(
            addAction,
            RemoveConnectionAction(),
            duplicateAction
        )
    }

    override fun reset(settings: RfsConnectionDataManager) {

        val selectedId = (extendedState.selectedNode?.configurable as? ConnectionConfigurable<*, *>)?.innerId
        selectedId?.let { setFirstSelectedNodeConnId(it) }

        fetchProviders()

        addedConnections.clear()
        myRoot.removeAllChildren()

        // Add all top-level groups to myRoot in priority order before populating connections,
        // so retrieveNode() calls below find the group already placed and don't append it out of order.
        topLevelGroups.forEach { it.retrieveNode() }

        for (connection in settings.getConnections(project)) {
            val groupNode = groupToActionNode[connection.groupId] ?: topLevelGroups.firstOrNull()

            if (groupNode != null) {
                val connectionGroup = idToGroup[connection.groupId] ?: continue
                val connectionConfigurable = connection.createConfigurable(project, connectionGroup)
                connectionConfigurable.putUserData(CONNECTION_SETTINGS_PANEL_KEY, this)
                groupNode.retrieveNode().add(MyNode(connectionConfigurable))
            } else {
                if (BdIdeRegistryUtil.isInternalFeaturesAvailable()) {
                    logger.warn("Missing group id:${connection.groupId}")
                }
            }
        }

        (myTree.model as DefaultTreeModel).reload(myRoot)

        expandAllNodes()
        selectFirstLeaf()
    }

    private fun expandAllNodes(startingIndex: Int = 0, rowCount: Int = tree.rowCount) {
        for (i in startingIndex until rowCount) {
            tree.expandRow(i)
        }

        if (tree.rowCount != rowCount) {
            expandAllNodes(rowCount, tree.rowCount)
        }
    }

    /** Used for informing the tree that connection settings node changed (for example: name changed). */
    fun nodeChanged(connId: String) {
        val nodeToSelect = getTreeNode(connId)
        (myTree.model as DefaultTreeModel).nodeChanged(nodeToSelect)
    }

    private fun selectFirstLeaf() {  // myRoot
        val savedSelectedId = savedSelectedId
        val nodeToSelect = if (savedSelectedId == null) myRoot.firstLeaf else getTreeNode(savedSelectedId)
        nodeToSelect?.let { selectNodeInTree(it) }
    }

    override fun isModified(settings: RfsConnectionDataManager): Boolean =
        addedConnections.isNotEmpty() || removedConnections.isNotEmpty() || isModified

    override fun apply(settings: RfsConnectionDataManager) {

        // Inside apply() of MasterDetailComponent there is validation for only changed connections, but it could be that
        // some unchanged are invalid (for example, newly created connections have no changes). Here we are running validation for all.
        for (node in TreeUtil.treeNodeTraverser(myRoot).filter(MyNode::class.java)) {
            val configurable = node.configurable as? ConnectionConfigurable<*, *>
            if (configurable != null && isInitialized(configurable)) {
                val connectionSettingsPanel = configurable.component
                connectionSettingsPanel?.validateConfig()
            }
        }

        apply()

        addedConnections.sortedBy { it.sourceConnection == null }.forEach {
            RfsConnectionDataManager.instance?.addConnection(project, it)
        }

        removedConnections.forEach {
            runWithModalProgressBlocking(project, KafkaMessagesBundle.message("progress.title.credentials.saving")) {
                it.clearCredentials()
            }
            RfsConnectionDataManager.instance?.removeConnectionKeepingCredentials(project, it)
        }

        addedConnections.clear()
        removedConnections.clear()
    }

    override fun getComponent(): JComponent {
        if (myWholePanel == null) {
            myToReInitWholePanel = true
            reInitWholePanelIfNeeded()

            master.border = IdeBorderFactory.createBorder(SideBorder.TOP or SideBorder.BOTTOM or SideBorder.LEFT)

            var originalSize = myWholePanel.minimumSize
            myWholePanel.minimumSize = JBUI.size(max(originalSize.width, 640), originalSize.height)

            originalSize = myWholePanel.preferredSize
            myWholePanel.preferredSize = JBUI.size(max(originalSize.width, 640), originalSize.height)
        }

        return myWholePanel
    }

    override fun getPreferredFocusedComponent(): JComponent = myTree

    override fun getEmptySelectionString(): String = KafkaMessagesBundle.message("settings.empty.selection")

    override fun reset() {
        val intNode = extendedState.selectedNode
        super.reset()

        for (node in extendedState.expandedNodes) tree.expandPath(TreePath(node.path))
        intNode?.let {
            tree.selectionPath = TreePath(it.path)
            extendedState.selectedNode = it
        }
    }

    fun createCurrentTestConnection(): ConnectionData? {
        val component = (selectedNode?.configurable as? ConnectionConfigurable<*, *>)?.component
        return component?.createTestConnection()
    }

    fun removeNode(myNode: MyNode) {
        (myNode.configurable as? ConnectionConfigurable<*, *>)?.let {
            val conn = it.editableObject

            if (!addedConnections.remove(conn)) {
                removedConnections.add(conn)
            } else {
                runWithModalProgressBlocking(
                    project,
                    KafkaMessagesBundle.message("progress.title.credentials.removing")
                ) {
                    conn.clearCredentials()
                }
            }
        }

        treeWalkUpRemove(myNode)
    }

    override fun setSelectedNode(node: MyNode?) {
        super.setSelectedNode(node)
        extendedState.selectedNode = node
    }

    fun setFirstSelectedNodeConnId(connId: String) {
        savedSelectedId = connId
    }

    private fun createTreeNode(
        group: ConnectionGroup,
        data: ConnectionData,
        selectAddedNode: Boolean = true
    ) {
        val groupNode = groupToActionNode[group.id]?.retrieveNode()
        if (groupNode == null) return

        val connectionConfigurable = data.createConfigurable(project, group)
        connectionConfigurable.putUserData(CONNECTION_SETTINGS_PANEL_KEY, this)

        val connNode = MyNode(connectionConfigurable)

        groupNode.add(connNode)
        (myTree.model as DefaultTreeModel).nodesWereInserted(groupNode, intArrayOf(groupNode.childCount - 1))

        if (selectAddedNode) {
            selectNodeInTree(connNode)
        }
    }

    fun createNewConnectionFor(
        group: ConnectionFactory<*>,
        data: ConnectionData? = null,
        selectAddedNode: Boolean = true
    ): ConnectionData {
        val newConnectionData = data ?: group.createBlankData()
        createTreeNode(group, newConnectionData, selectAddedNode)
        addedConnections.add(newConnectionData)
        return newConnectionData
    }

    private fun fetchProviders() {
        val keywords = mutableSetOf<String>()

        val groupToParent = mutableMapOf<ConnectionGroup, ConnectionGroup?>()
        val parentToGroups = MultiMap<String?, ConnectionGroup>()
        for (provider in ConnectionSettingProviderEP.getAll()) {

            provider.retrieveSearchKeywords().forEach { keywords.add(it.first) }

            for (group in provider.createConnectionGroups()) {
                parentToGroups.putValue(group.parentGroupId, group)
                idToGroup[group.id] = group
            }
        }

        parentToGroups.keySet().forEach { parentId ->
            parentToGroups[parentId].forEach {
                groupToParent[it] = idToGroup[parentId]
            }
        }

        topLevelGroups.clear()
        groupToActionNode.clear()

        parentToGroups.get(null).forEach { topLevelGroups.add(ActionNode(it)) }
        topLevelGroups.sortBy { StandaloneCreateConnectionUtil.groupsPriority.getOrDefault(it.group.id, 4) }

        fun getTopLevelGroupFor(group: ConnectionGroup): ConnectionGroup {
            val res = groupToParent[group]
            return if (res == null) group else getTopLevelGroupFor(res)
        }

        parentToGroups.keySet().forEach { parentId ->
            if (parentId != null) {
                parentToGroups.get(parentId).forEach {
                    val tlg = getTopLevelGroupFor(it)
                    val tlgActionNode =
                        topLevelGroups.find { topLevelActionNode -> topLevelActionNode.group.id == tlg.id }!!
                    groupToActionNode[it.id] = tlgActionNode
                    tlgActionNode.children.add(ActionNode(it))
                }
            }
        }

        // Wire up the "Create Connection" button on the Message Brokers group panel
        (idToGroup[BrokerConnectionGroup.GROUP_ID] as? BrokerConnectionGroup)?.onCreateConnection = {
            val kafkaGroup = idToGroup.values.filterIsInstance<ConnectionFactory<*>>()
                .find { it.parentGroupId == BrokerConnectionGroup.GROUP_ID }
            if (kafkaGroup != null) {
                createNewConnectionFor(kafkaGroup)
            }
        }

        installSearchIndex(keywords)
    }

    private fun treeWalkUpRemove(fromNode: MyNode) {
        var current: MyNode? = fromNode

        while (current != null && current.parent != myRoot && current.parent?.childCount == 1) {
            val parent = current.parent as? MyNode

            (current.configurable as? GroupEmptyConfigurable)?.group?.id?.let { groupToActionNode[it] }
                ?.removeFromTree()
            current.removeFromParent()

            current = parent
        }

        current?.let { removeNodes(listOf(it)) }
    }

    private fun installSearchIndex(keywords: Collection<String>) {
        (tree.parent?.parent as? JComponent)?.border = FakeTitledBorder(keywords)
    }

    private class GroupEmptyConfigurable(val group: ConnectionGroup) : NamedConfigurable<ConnectionGroup>(false, null) {
        override fun setDisplayName(name: String) {}
        override fun apply() {}

        override fun getEditableObject(): ConnectionGroup = group

        @Suppress("DialogTitleCapitalization")
        override fun getBannerSlogan(): String = group.name
        override fun getDisplayName(): String = group.name
        override fun createOptionsPanel(): JComponent = group.createOptionsPanel()
        override fun isModified(): Boolean = false
        override fun getIcon(expanded: Boolean): Icon? = group.icon

        override fun disposeUIResources() {
            super.disposeUIResources()
            group.disposeOptionsPanel()
        }
    }

    private abstract inner class AbstractAddActionGroup(
        @Nls(capitalization = Nls.Capitalization.Title) text: String?,
        icon: Icon?,
        @Nls(capitalization = Nls.Capitalization.Sentence) description: String?
    ) : ActionGroup(text, true) {

        init {
            templatePresentation.text = text
            templatePresentation.icon = icon
            templatePresentation.description = description
        }

        abstract fun getChildrenNodes(): Collection<ActionNode>

        override fun getChildren(e: AnActionEvent?): Array<AnAction> {
            val result = mutableListOf<AnAction>()

            getChildrenNodes().map { node ->
                node.group.let {
                    if (it is ConnectionFactory<*>) result.add(AddConnectionAction(it))
                    if (node.children.isNotEmpty()) result.add(
                        NodeAddActionGroup(node.children, text = it.name, icon = it.icon)
                    )
                }
            }

            return result.toTypedArray()
        }

        override fun actionPerformed(e: AnActionEvent) {
            CreateConnectionPopup.createPopup(RootAddActionGroup(), e).showForToolbarOrInBestPositionFor(e)
        }
    }

    private inner class RootAddActionGroup(
        @Nls(capitalization = Nls.Capitalization.Title) text: String? = null,
        icon: Icon? = null,
        @Nls(capitalization = Nls.Capitalization.Sentence) description: String? = null
    ) : AbstractAddActionGroup(text, icon, description) {
        override fun getChildrenNodes(): Collection<ActionNode> = topLevelGroups
    }

    private inner class NodeAddActionGroup(
        val children: Collection<ActionNode>,
        @Nls(capitalization = Nls.Capitalization.Title) text: String? = null,
        icon: Icon? = null,
        @Nls(capitalization = Nls.Capitalization.Sentence) description: String? = null
    ) : AbstractAddActionGroup(text, icon, description) {
        override fun getChildrenNodes(): Collection<ActionNode> = children
    }

    private inner class AddConnectionAction(private val group: ConnectionFactory<*>) :
        AnAction(group.name, null, group.icon), DumbAware {

        override fun actionPerformed(e: AnActionEvent) {
            createNewConnectionFor(group)
            addNotify()
        }
    }

    private inner class RemoveConnectionAction : MyDeleteAction(Predicate<Array<Any>> { nodes ->
        !nodes.any { (it as? MyNode)?.configurable is GroupEmptyConfigurable } && nodes.any { (it as? MyNode)?.userObject != null }
    }), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            val myNode = myTree.selectionPath?.lastPathComponent as? MyNode ?: return
            removeNode(myNode)
            addNotify()
        }
    }

    private inner class DuplicateConnectionAction : DumbAwareAction(
        KafkaMessagesBundle.message("settings.duplicateConnection"), null,
        AllIcons.Actions.Copy
    ) {
        override fun update(e: AnActionEvent) {
            val myNode = myTree.selectionPath?.lastPathComponent as? MyNode
            // disable for group-level ("Message Broker" groups) or empty tree nodes
            val isValidConnection = myNode?.configurable !is GroupEmptyConfigurable && myNode?.userObject != null
            e.presentation.isEnabled = isValidConnection
        }

        override fun actionPerformed(e: AnActionEvent) {
            val myNode = myTree.selectionPath?.lastPathComponent as? MyNode ?: return
            (myNode.configurable as? ConnectionConfigurable<*, *>)?.let {
                val connectionData = it.editableObject
                val connectionGroup = idToGroup[connectionData.groupId] ?: return@let
                if (connectionGroup !is ConnectionFactory<*>) return@let

                val packed = ConnectionSettingsBase.packData(connectionData)
                val newData = ConnectionSettingsBase.unpackData(packed)

                newData.innerId = "${newData.name}@${connectionGroup.id}@${Random.nextLong()}"

                runWithModalProgressBlocking(
                    project,
                    KafkaMessagesBundle.message("progress.title.credentials.saving")
                ) {
                    connectionData.getCredentials()?.let { credentials -> newData.setCredentials(credentials) }

                    connectionData.credentialIds().toHashSet().forEach { credentialsId ->
                        connectionData.getCredentials(credentialsId)
                            ?.let { credentials -> newData.setCredentials(credentials, credentialsId) }
                    }
                }

                createNewConnectionFor(connectionGroup, newData, true)
            }
        }
    }

    private fun getConnectionData(node: MyNode) = (node.configurable as? ConnectionConfigurable<*, *>)?.editableObject

    override fun getNodeComparator() = Comparator { o1: MyNode, o2: MyNode ->
        val conn1 = getConnectionData(o1)
        val conn2 = getConnectionData(o2)
        if (conn1 != null && conn2 != null) {
            if (conn1.isEnabled == conn2.isEnabled)
                StringUtil.naturalCompare(o1.displayName, o2.displayName)
            else if (conn1.isEnabled)
                -1
            else
                1
        } else StringUtil.naturalCompare(o1.displayName, o2.displayName)
    }

    private inner class BDTTreeCellRenderer : MyColoredTreeCellRenderer() {
        override fun getAdditionalAttributes(node: MyNode): SimpleTextAttributes {
            return if (getConnectionData(node)?.isEnabled == false)
                SimpleTextAttributes.GRAYED_ATTRIBUTES
            else super.getAdditionalAttributes(node)
        }
    }

    private inner class ActionNode(var group: ConnectionGroup) {
        var children = mutableListOf<ActionNode>()
        var parent: ActionNode? = null

        private var node: MyNode? = null
        private var retrieving = false

        fun removeFromTree() {
            node = null
        }

        fun retrieveNode(): MyNode {
            if (retrieving) throw IllegalStateException("Cyclic dependency")

            try {
                retrieving = true
                if (!group.visible) return parent!!.retrieveNode()

                if (node == null) node = MyNode(GroupEmptyConfigurable(group))
                if (node!!.parent == null) addToParent()

                return node!!
            } finally {
                retrieving = false
            }
        }

        fun addToParent() {
            if (node == null || node!!.parent != null) return

            val parentNode = parent?.retrieveNode() ?: myRoot
            parentNode.add(node)

            (myTree.model as DefaultTreeModel).nodesWereInserted(parentNode, intArrayOf(parentNode.childCount - 1))
        }
    }

    private class ExtendedState(private val root: MyNode) {
        val expandedNodes = mutableSetOf<MyNode>()
        var selectedNode: MyNode? = null

        fun expand(node: MyNode) {
            if (node == root) return
            expandedNodes.add(node)
        }

        fun collapse(node: MyNode) {
            if (node == root) return

            val queue = ArrayDeque<MyNode>()
            queue.add(node)

            while (queue.isNotEmpty()) {
                val el = queue.pop()

                if (expandedNodes.remove(el)) for (c in el.children()) (c as? MyNode)?.let {
                    if (it.configurable is GroupEmptyConfigurable) queue.push(it)
                }
            }
        }
    }

    private class FakeTitledBorder(keywords: Collection<String>) : TitledBorder(
        BorderFactory.createEmptyBorder(),
        keywords.joinToString(separator = " ") { it.lowercase() }) {
        override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {}

        override fun getMinimumSize(c: Component?): Dimension = c?.size ?: Dimension(0, 0)

        override fun getBorderInsets(c: Component?, insets: Insets?): Insets = JBUI.emptyInsets()
    }
}