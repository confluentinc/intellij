package io.confluent.intellijplugin.core.settings

import com.intellij.icons.AllIcons
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
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import io.confluent.intellijplugin.core.constants.BdtConnectionType
import io.confluent.intellijplugin.core.settings.connections.*
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import io.confluent.intellijplugin.core.settings.paneadd.StandaloneCreateConnectionUtil
import io.confluent.intellijplugin.core.util.BdIdeRegistryUtil
import io.confluent.intellijplugin.settings.KafkaConnectionGroup
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Insets
import java.util.*
import java.util.function.Predicate
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.border.TitledBorder
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import kotlin.math.max
import kotlin.random.Random

/**
 * Settings panel for managing Kafka and Confluent Cloud connections in the IDE settings dialog.
 * Extends [MasterDetailsComponent] for the master-detail tree layout and implements
 * [ConfigurableUi] to integrate with the IDE settings framework.
 *
 * @see com.intellij.openapi.ui.MasterDetailsComponent
 * @see com.intellij.openapi.options.ConfigurableUi
 */
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

    // === MasterDetailsComponent overrides ===

    /** Disposes UI resources for all nodes and clears the tree, preparing it for a rebuild. */
    override fun clearChildren() {
        // myRoot.userObject is null in init, so we null-check the configurable to prevent a null
        // pointer exception
        for (node in TreeUtil.treeNodeTraverser(myRoot).filter(MyNode::class.java)) {
            node.configurable?.disposeUIResources()
            if (node !is MyRootNode) {
                node.userObject = null
            }
        }
        myRoot.removeAllChildren()
    }

    /** Removes the [ConnectionSettingsListener] registration when the settings panel is closed. */
    override fun disposeUIResources() {
        super.disposeUIResources()
        RfsConnectionDataManager.instance?.removeListener(this)
    }

    /** Returns the selected node's help topic, or [COMMON_HELP_ID] when the root or a group node is selected. */
    override fun getHelpTopic() = extendedState.selectedNode?.configurable?.helpTopic ?: COMMON_HELP_ID

    /** Returns the top-level display name shown in the IDE settings tree. */
    override fun getDisplayName() = KafkaMessagesBundle.message("connections.settings.display.name")

    /** Lazily initializes the settings panel on first access, enforcing a minimum width of 640px. */
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

    /** Returns the tree as the initial focus target when the settings panel is opened. */
    override fun getPreferredFocusedComponent(): JComponent = myTree

    /** Text shown in the detail panel when no connection is selected. */
    override fun getEmptySelectionString(): String = KafkaMessagesBundle.message("settings.empty.selection")

    /**
     * Sorts connections so enabled ones appear before disabled ones; within each group, sorts
     * alphabetically by display name. Group header nodes (which have null [ConnectionData]) sort
     * alphabetically against each other.
     */
    override fun getNodeComparator() = Comparator { o1: MyNode, o2: MyNode ->
        // FUTURE: if user-controlled ordering (drag-to-reorder) is added, this will need to be
        // replaced with an index-based comparator
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

    /**
     * Builds the action list for the toolbar ([fromPopup] = false) and right-click context menu
     * ([fromPopup] = true). Called twice by [MasterDetailsComponent] on initialization.
     *
     * @see com.intellij.openapi.ui.MasterDetailsComponent.createActions
     */
    override fun createActions(fromPopup: Boolean): List<AnAction> {
        val addAction = AddConnectionAction(fromPopup).apply {
            if (!fromPopup) registerCustomShortcutSet(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD), tree)
        }

        val duplicateAction = DuplicateConnectionAction(fromPopup).apply {
            if (!fromPopup) registerCustomShortcutSet(CommonShortcuts.getDuplicate(), tree)
        }

        val deleteAction = RemoveConnectionAction(fromPopup)

        return buildList {
            add(addAction)
            add(duplicateAction)
            add(deleteAction)
            // sign-in/out action for Confluent Cloud is only shown via context menu, not the toolbar
            if (fromPopup) add(CCloudSignInOutAction(::isCCloudGroupSelected))
        }
    }

    /** Restores expanded and selected tree state after [MasterDetailsComponent.reset] rebuilds the nodes. */
    override fun reset() {
        val intNode = extendedState.selectedNode
        super.reset()

        for (node in extendedState.expandedNodes) tree.expandPath(TreePath(node.path))
        intNode?.let {
            tree.selectionPath = TreePath(it.path)
            extendedState.selectedNode = it
        }
    }

    /** Selects [node] in the tree and records it in [extendedState] so the selection survives the next [reset] call. */
    override fun setSelectedNode(node: MyNode?) {
        // extended to keep extendedState in sync
        super.setSelectedNode(node)
        extendedState.selectedNode = node
    }

    // === ConfigurableUi<RfsConnectionDataManager> overrides ===

    /** Rebuilds the entire tree from [settings], restoring the previously selected connection if possible. */
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

    /** True if connections are pending addition or removal, or if any field in the connection config is dirty. */
    override fun isModified(settings: RfsConnectionDataManager): Boolean =
        addedConnections.isNotEmpty() || removedConnections.isNotEmpty() || isModified

    /**
     * Validates all connection nodes, then commits pending additions and removals to
     * [RfsConnectionDataManager]. Credentials for removed connections are cleared before removal.
     */
    override fun apply(settings: RfsConnectionDataManager) {
        // MasterDetailsComponent only validates changed nodes; we validate all because newly added connections have no "changes" yet.
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

    // === ConnectionSettingsListener overrides ===

    /** Handles a connection added outside this panel (e.g., via the CCloud sign-in flow); skips entries already tracked locally. */
    override fun onConnectionAdded(project: Project?, newConnectionData: ConnectionData) {
        if (addedConnections.contains(newConnectionData)) {
            return
        }

        val actionNode = groupToActionNode[newConnectionData.groupId]
        if (actionNode == null) {
            // groupId doesn't match any registered provider; fall back to a full reset
            val dataManager = RfsConnectionDataManager.instance ?: return
            reset(dataManager)
        } else {
            createTreeNode(actionNode, newConnectionData, selectAddedNode = false)
            expandAllNodes()
        }
    }

    /** Handles a connection removed outside this panel; skips entries already tracked in [removedConnections]. */
    override fun onConnectionRemoved(project: Project?, removedConnectionData: ConnectionData) {
        if (removedConnections.contains(removedConnectionData)) {
            return
        }

        val nodeToRemove = getTreeNode(removedConnectionData.innerId) ?: return
        treeWalkUpRemove(nodeToRemove)
    }

    // === Tree state: expanded rows and selection, preserved across tree rebuilds ===

    /** Recursively expands all tree rows; re-runs if the row count grows mid-loop (lazy-loaded children). */
    private fun expandAllNodes(startingIndex: Int = 0, rowCount: Int = tree.rowCount) {
        for (i in startingIndex until rowCount) {
            tree.expandRow(i)
        }

        if (tree.rowCount != rowCount) {
            expandAllNodes(rowCount, tree.rowCount)
        }
    }

    /** Selects the node matching [savedSelectedId], or the first leaf if no ID is saved. */
    private fun selectFirstLeaf() {
        val savedSelectedId = savedSelectedId
        val nodeToSelect = if (savedSelectedId == null) myRoot.firstLeaf else getTreeNode(savedSelectedId)
        nodeToSelect?.let { selectNodeInTree(it) }
    }

    /** Notifies the tree that the display name or state of [connId]'s node has changed. */
    fun nodeChanged(connId: String) {
        val nodeToSelect = getTreeNode(connId)
        (myTree.model as DefaultTreeModel).nodeChanged(nodeToSelect)
    }

    /** Stores [connId] so [selectFirstLeaf] can restore the selection after the next tree rebuild. */
    fun setFirstSelectedNodeConnId(connId: String) {
        savedSelectedId = connId
    }

    // === Connection management methods ===

    /** Finds the tree node whose [ConnectionData.innerId] matches [innerId], or null if not found. */
    fun getTreeNode(innerId: String): MyNode? {
        return TreeUtil.treeNodeTraverser(tree.model.root as DefaultMutableTreeNode).filter(MyNode::class.java)
            .find { node ->
                getConnectionData(node)?.innerId == innerId
            }
    }

    /** Extracts the [ConnectionData] from a node's configurable, or null for group header nodes. */
    private fun getConnectionData(node: MyNode) = (node.configurable as? ConnectionConfigurable<*, *>)?.editableObject

    /** Creates a test-only [ConnectionData] snapshot from the currently selected connection's form state. */
    fun createCurrentTestConnection(): ConnectionData? {
        val component = (selectedNode?.configurable as? ConnectionConfigurable<*, *>)?.component
        return component?.createTestConnection()
    }

    /** Creates a [MyNode] for [data] under [actionNode]'s group node, optionally selecting the new node. */
    private fun createTreeNode(
        actionNode: ActionNode,
        data: ConnectionData,
        selectAddedNode: Boolean = true
    ) {
        val groupNode = actionNode.retrieveNode()

        // wrap the connection data in a configurable and tag it with this panel so the
        // configurable can call back into us (e.g., to notify on name change)
        val connectionConfigurable = data.createConfigurable(project, actionNode.group)
        connectionConfigurable.putUserData(CONNECTION_SETTINGS_PANEL_KEY, this)

        // add to the tree and notify the model so the new row renders immediately
        val connNode = MyNode(connectionConfigurable)
        groupNode.add(connNode)
        (myTree.model as DefaultTreeModel).nodesWereInserted(groupNode, intArrayOf(groupNode.childCount - 1))

        if (selectAddedNode) {
            selectNodeInTree(connNode)
        }
    }

    /** Creates a new [ConnectionData] in [group] (using [data] if provided), inserts a tree node, and tracks it in [addedConnections]. */
    fun createNewConnectionFor(
        group: ConnectionFactory<*>,
        data: ConnectionData? = null,
        selectAddedNode: Boolean = true
    ): ConnectionData {
        val newConnectionData = data ?: group.createBlankData()
        val actionNode = groupToActionNode[group.id] ?: return newConnectionData
        createTreeNode(actionNode, newConnectionData, selectAddedNode)
        addedConnections.add(newConnectionData)
        return newConnectionData
    }

    /**
     * Stages [myNode]'s connection for removal (or clears credentials immediately if it was added
     * in the same session and never persisted), then removes it from the tree.
     */
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

    // === Action helper methods ===

    /** Returns the ConnectionGroup for the selected node, or null if a connection (leaf) is selected. */
    private fun selectedConnectionGroup(): ConnectionGroup? {
        val selectedNode = myTree.selectionPath?.lastPathComponent as? MyNode
        return (selectedNode?.configurable as? GroupEmptyConfigurable)?.group
    }

    /** True when any group node is selected (Confluent Cloud or Connections). */
    private fun isConnectionGroupSelected(): Boolean = selectedConnectionGroup() != null

    /** True when the Confluent Cloud group node is selected. */
    private fun isCCloudGroupSelected(): Boolean = selectedConnectionGroup() is CCloudDisplayGroup

    /** Looks up the Kafka connection factory and delegates to [createNewConnectionFor]. */
    private fun performAddConnectionAction(e: AnActionEvent) {
        val factory = idToGroup[BdtConnectionType.KAFKA.id] as? ConnectionFactory<*> ?: return
        createNewConnectionFor(factory)
        addNotify()
    }

    // === provider loading and node removal ===

    /**
     * Rebuilds group and search-index state from all registered [ConnectionSettingProviderEP] extensions.
     * Called at the start of each [reset] to pick up any registered providers. Also wires up
     * [KafkaConnectionGroup.onCreateConnection] for the "Add Connection" button in the options panel.
     */
    private fun fetchProviders() {
        val keywords = mutableSetOf<String>()

        topLevelGroups.clear()
        groupToActionNode.clear()

        for (provider in ConnectionSettingProviderEP.getAll()) {
            provider.retrieveSearchKeywords().forEach { keywords.add(it.first) }

            for (group in provider.createConnectionGroups()) {
                idToGroup[group.id] = group
                // all current groups are top-level (parentGroupId == null)
                topLevelGroups.add(ActionNode(group))
            }
        }

        topLevelGroups.sortBy { StandaloneCreateConnectionUtil.groupsPriority.getOrDefault(it.group.id, 4) }
        topLevelGroups.forEach { groupToActionNode[it.group.id] = it }

        // wire up the "Add Connection" button on the Connections group node
        (idToGroup[BdtConnectionType.KAFKA.id] as? KafkaConnectionGroup)?.let { group ->
            group.onCreateConnection = { createNewConnectionFor(group) }
        }

        installSearchIndex(keywords)
    }

    /**
     * Removes [fromNode] from the settings tree.
     *
     * For non-last connections: delegates to [removeNodes].
     * For the last connection in a group: manually removes it and fires [DefaultTreeModel.nodeStructureChanged]
     * on the now-empty group, keeping the group header node visible.
     *
     * The while loop was designed for nested group hierarchies; in the current flat structure (all
     * groups are direct children of myRoot) it executes at most once and only on connection leaf nodes.
     *
     * @see com.intellij.openapi.ui.MasterDetailsComponent.removeNodes
     */
    private fun treeWalkUpRemove(fromNode: MyNode) {
        var current: MyNode? = fromNode

        while (current != null && current.parent != myRoot && current.parent?.childCount == 1) {
            val parent = current.parent as? MyNode

            current.removeFromParent()

            current = parent
        }

        current?.let {
            if (it.configurable is GroupEmptyConfigurable && it.parent == myRoot) {
                (myTree.model as DefaultTreeModel).nodeStructureChanged(it)
            } else {
                removeNodes(listOf(it))
            }
        }
    }

    /** Wraps the tree's scroll pane in a [FakeTitledBorder] carrying search keywords for IntelliJ's settings search bar. */
    private fun installSearchIndex(keywords: Collection<String>) {
        (tree.parent?.parent as? JComponent)?.border = FakeTitledBorder(keywords)
    }

    // === group header node classes ===

    /**
     * Configurable for a group header node in the settings tree. Delegates display name, icon, and
     * options panel to the underlying [ConnectionGroup].
     *
     * Not all group nodes have child connections: [CCloudDisplayGroup] uses this to show a sign-in
     * panel when selected, while [KafkaConnectionGroup] (a [ConnectionFactory]) uses it as the header
     * above individual connection nodes. This is a plugin design choice, not a platform requirement.
     *
     * @see com.intellij.openapi.ui.NamedConfigurable
     */
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

    /**
     * Wraps a [ConnectionGroup] with lazy [com.intellij.openapi.ui.MasterDetailsComponent.MyNode] creation.
     * [retrieveNode] creates the node on first access and re-attaches it to myRoot if orphaned,
     * allowing the tree to be rebuilt in [reset] without re-creating nodes unnecessarily.
     */
    private inner class ActionNode(val group: ConnectionGroup) {
        private var node: MyNode? = null

        fun retrieveNode(): MyNode {
            if (node == null) node = MyNode(GroupEmptyConfigurable(group))
            if (node!!.parent == null) addToParent()
            return node!!
        }

        private fun addToParent() {
            if (node == null || node!!.parent != null) return

            myRoot.add(node)
            (myTree.model as DefaultTreeModel).nodesWereInserted(myRoot, intArrayOf(myRoot.childCount - 1))
        }
    }

    // === toolbar and context menu actions, ordered by visual hierarchy: sign in/out, add, duplicate/delete ===

    /** Creates a Kafka connection. In the context menu, only visible on the Kafka connections group node. */
    private inner class AddConnectionAction(
        private val fromPopup: Boolean = false
    ) : DumbAwareAction(
        KafkaMessagesBundle.message("settings.addConnection.text"),
        KafkaMessagesBundle.message("settings.addConnection.hint"),
        IconUtil.addIcon
    ) {
        override fun actionPerformed(e: AnActionEvent) = performAddConnectionAction(e)

        override fun update(e: AnActionEvent) {
            // only visible on the Connections group node context menu
            if (fromPopup) {
                e.presentation.isVisible = isConnectionGroupSelected() && !isCCloudGroupSelected()
            }
        }
    }

    private inner class DuplicateConnectionAction(
        private val fromPopup: Boolean = false
    ) : DumbAwareAction(
        KafkaMessagesBundle.message("settings.duplicateConnection"), null,
        AllIcons.Actions.Copy
    ) {
        override fun update(e: AnActionEvent) {
            // hide on group nodes in the context menu (only applies to individual connections)
            if (fromPopup && isConnectionGroupSelected()) {
                e.presentation.isVisible = false
                return
            }
            val myNode = myTree.selectionPath?.lastPathComponent as? MyNode
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

    private inner class RemoveConnectionAction(
        private val fromPopup: Boolean = false
    ) : MyDeleteAction(Predicate<Array<Any>> { nodes ->
        !nodes.any { (it as? MyNode)?.configurable is GroupEmptyConfigurable } && nodes.any { (it as? MyNode)?.userObject != null }
    }), DumbAware {
        override fun update(e: AnActionEvent) {
            super.update(e)
            // hide on group nodes in the context menu (only applies to individual connections)
            if (fromPopup && isConnectionGroupSelected()) {
                e.presentation.isVisible = false
            }
        }

        override fun actionPerformed(e: AnActionEvent) {
            val myNode = myTree.selectionPath?.lastPathComponent as? MyNode ?: return
            removeNode(myNode)
            addNotify()
        }
    }

    // === Tree rendering and UI state ===

    /**
     * Colors disabled connections with [com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES]
     * in the settings tree.
     *
     * @see com.intellij.openapi.ui.MasterDetailsComponent.MyColoredTreeCellRenderer
     */
    private inner class BDTTreeCellRenderer : MyColoredTreeCellRenderer() {
        override fun getAdditionalAttributes(node: MyNode): SimpleTextAttributes {
            return if (getConnectionData(node)?.isEnabled == false)
                SimpleTextAttributes.GRAYED_ATTRIBUTES
            else super.getAdditionalAttributes(node)
        }
    }

    /**
     * Preserves tree UI state (expanded nodes and selected node) across [reset] calls so the user's
     * view is restored after the tree is rebuilt.
     *
     * @see com.intellij.openapi.ui.MasterDetailsComponent
     */
    private class ExtendedState(private val root: MyNode) {
        val expandedNodes = mutableSetOf<MyNode>()
        var selectedNode: MyNode? = null

        fun expand(node: MyNode) {
            if (node == root) return
            expandedNodes.add(node)
        }

        fun collapse(node: MyNode) {
            if (node == root) return
            // no nested groups, so only the collapsed node needs removal
            expandedNodes.remove(node)
        }
    }

    /**
     * Invisible border whose title text embeds search keywords for IntelliJ's settings search index,
     * making connection types discoverable via the settings search bar without rendering a visible border.
     *
     * @see javax.swing.border.TitledBorder
     */
    private class FakeTitledBorder(keywords: Collection<String>) : TitledBorder(
        BorderFactory.createEmptyBorder(),
        keywords.joinToString(separator = " ") { it.lowercase() }) {
        override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {}

        override fun getMinimumSize(c: Component?): Dimension = c?.size ?: Dimension(0, 0)

        override fun getBorderInsets(c: Component?, insets: Insets?): Insets = JBUI.emptyInsets()
    }
}
