package io.confluent.intellijplugin.core.rfs.editorviewer

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.table.JBTable
import com.intellij.ui.tree.TreePathBackgroundSupplier
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.ui.treeStructure.treetable.TreeTableModelAdapter
import com.intellij.util.ui.accessibility.AccessibleContextDelegate
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.JTableHeader
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

// ToDo copy of [com.intellij.ui.components.JBTreeTable] because of private/internal and some bugs.
open class RfsTreeTable(model: TreeTableModel) : JComponent(), TreePathBackgroundSupplier {

    val tree: Tree

    val table: JBTable

    val split: OnePixelSplitter

    var model: TreeTableModel? = null
        private set
    private var myTreeTableHeader: JTableHeader? = null
    private var myColumnProportion = 0.1f

    private var columnProportion: Float
        get() = myColumnProportion
        set(columnProportion) {
            myColumnProportion = columnProportion
            split.proportion = 1f - (model!!.columnCount - 1) * columnProportion
        }

    init {
        layout = BorderLayout()

        tree = RTree()

        table = Table()

        split = createSplitter()

        add(split)
        val treePane = ScrollPaneFactory.createScrollPane(tree, SideBorder.NONE)
        split.firstComponent = treePane

        val tablePane = ScrollPaneFactory.createScrollPane(table, SideBorder.NONE)
        split.secondComponent = tablePane
        treePane.setColumnHeaderView(myTreeTableHeader)

        //treePane.horizontalScrollBar.addComponentListener(object : ComponentAdapter() {
        //  init {
        //    if (tablePane.isVisible) {
        //      tablePane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
        //    }
        //  }
        //
        //  override fun componentShown(e: ComponentEvent) {
        //    tablePane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
        //  }
        //
        //  override fun componentHidden(e: ComponentEvent) {
        //    tablePane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        //  }
        //})
        val scrollMode = if (!SystemInfo.isMac) JViewport.SIMPLE_SCROLL_MODE else JViewport.BLIT_SCROLL_MODE
        treePane.viewport.scrollMode = scrollMode
        tablePane.verticalScrollBar = treePane.verticalScrollBar
        tablePane.viewport.scrollMode = scrollMode

        val selection = SelectionSupport()
        tree.getSelectionModel().addTreeSelectionListener(selection)
        table.selectionModel.addListSelectionListener(selection)
        table.rowMargin = 0
        table.addMouseListener(selection)
        table.putClientProperty(RenderingUtil.PAINT_HOVERED_BACKGROUND, java.lang.Boolean.FALSE)
        val tableRenderer = getDefaultRenderer(TreeTableModel::class.java)
        val tableRendererComponent = tableRenderer.getTableCellRendererComponent(table, "jJ", false, false, -1, -1)

        tree.addPropertyChangeListener(JTree.ROW_HEIGHT_PROPERTY) {
            val treeRowHeight = tree.getRowHeight()
            if (treeRowHeight == table.rowHeight)
                return@addPropertyChangeListener
            table.rowHeight = treeRowHeight
        }
        tree.rowHeight = tableRendererComponent.preferredSize.height

        tree.setCellRenderer { tree, value, selected, expanded, leaf, row, hasFocus ->
            val cm = myTreeTableHeader!!.columnModel as TreeColumnModel
            val renderer = getDefaultRenderer(TreeTableModel::class.java)
            renderer.getTableCellRendererComponent(table, value, selected, hasFocus, row, cm.treeColumnIndex)
        }
        tree.putClientProperty(RenderingUtil.FOCUSABLE_SIBLING, table)
        table.putClientProperty(RenderingUtil.FOCUSABLE_SIBLING, tree)
        setModel(model)
    }

    private fun createSplitter(): OnePixelSplitter = RfsSplitter(table, myTreeTableHeader!!)

    fun setupFirstColumnRenderer(renderer: TableCellRenderer) {
        myTreeTableHeader?.defaultRenderer = renderer
    }

    private fun getDefaultRenderer(columnClass: Class<*>): TableCellRenderer {
        return table.getDefaultRenderer(columnClass)
    }

    private fun setModel(model: TreeTableModel) {
        this.model = model
        tree.model = model
        table.model = TreeTableModelAdapter(model, tree, table)
        val tcm = myTreeTableHeader!!.columnModel as TreeColumnModel
        if (tcm.treeColumnIndex >= 0) {
            table.removeColumn(table.columnModel.getColumn(tcm.treeColumnIndex))
        }
        columnProportion = myColumnProportion
    }

    override fun getPathBackground(path: TreePath, row: Int): Color? {
        return null
    }

    override fun hasFocus(): Boolean {
        return tree.hasFocus() || table.hasFocus()
    }

    private fun addTreeTableRowDirtyRegion(
        component: JComponent,
        tm: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Boolean {
        // checks if repaint manager should mark row of tree or table
        // and mark we need to repaint both components together,
        // otherwise super repaint
        val isNeedToRepaintRow = component.width == width
        if (isNeedToRepaintRow) {
            repaint(tm, 0, y, this.width, height)
        }
        return isNeedToRepaintRow
    }

    private inner class SelectionSupport : MouseAdapter(), TreeSelectionListener, ListSelectionListener {
        override fun mouseClicked(e: MouseEvent) {
            if (e.clickCount == 2 && e.source === table) {
                val row = table.selectedRow
                if (tree.isCollapsed(row)) {
                    tree.expandRow(row)
                } else {
                    tree.collapseRow(row)
                }
            }
        }

        private var skipTableProcessing = false
        private var skipTreeProcessing = false

        override fun valueChanged(e: ListSelectionEvent) {
            if (e.source === table.selectionModel && !skipTableProcessing) {
                skipTreeProcessing = true
                tree.selectionRows = table.selectionModel.selectedIndices
                tree.repaint()
                skipTreeProcessing = false
            }
        }

        override fun valueChanged(e: TreeSelectionEvent) {
            if (e.source === tree.selectionModel && !skipTreeProcessing) {
                skipTableProcessing = true
                table.clearSelection()
                tree.selectionModel.selectionPaths.forEach {
                    val row = tree.getRowForPath(it)
                    if (row >= 0) {
                        table.addRowSelectionInterval(row, row)
                    }
                }

                tree.repaint()

                if (table.width != 0) {
                    table.repaint()
                }
                skipTableProcessing = false
            }
        }
    }

    private inner class RTree : Tree() {
        init {
            getSelectionModel().selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
            isRootVisible = false
            border = BorderFactory.createEmptyBorder()
            putClientProperty(AUTO_SELECT_ON_MOUSE_PRESSED, false)
            dragEnabled = true
        }

        override fun repaint(tm: Long, x: Int, y: Int, width: Int, height: Int) {
            if (!addTreeTableRowDirtyRegion(this, tm, x, y, width, height)) {
                super.repaint(tm, x, y, width, height)
            }
        }

        override fun getPathBackground(path: TreePath, row: Int): Color? {
            return this@RfsTreeTable.getPathBackground(path, row)
        }
    }

    private inner class Table : JBTable() {
        val ref = JBTable()

        init {
            setShowGrid(false)
            setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
            columnSelectionAllowed = false
            tableHeader.reorderingAllowed = false

            myTreeTableHeader = object : JBTableHeader() {

                init {
                    setTable(ref) // <- We steal table header and need to provide any JTable to handle right ui painting.
                    setColumnModel(TreeColumnModel())
                    setReorderingAllowed(false)
                    setResizingAllowed(false)
                }

                override fun getAccessibleContext(): AccessibleContext {
                    return MyAccessibleContext()
                }

                override fun getWidth(): Int {
                    return super.getWidth() + 1
                }
            }

            // Do not paint hover for table row separately from tree.
            TableHoverListener.DEFAULT.removeFrom(this)
        }

        override fun setRowHeight(rowHeight: Int) {
            super.setRowHeight(rowHeight)
            tree.rowHeight = getRowHeight()
        }

        override fun updateUI() {
            super.updateUI()
            // dynamically update ui for stolen header
            @Suppress("UNNECESSARY_SAFE_CALL") // updateUI called from JBTable constructor, before Table class init.
            ref?.updateUI()
        }

        private inner class MyAccessibleContext : AccessibleContextDelegate(table.accessibleContext) {
            override fun getDelegateParent(): Container {
                return this@RfsTreeTable
            }
        }
    }

    private inner class TreeColumnModel : DefaultTableColumnModel() {
        var treeColumnIndex = -1
            get() {
                // This could be because of several listeners of JTree.TREE_MODEL_PROPERTY.
                // We are getting the request for treeColumnIndex from inside setModel().
                if (field == -1) {
                    field = findTreeColumnIndex()
                }
                return field
            }

        init {
            addColumn(object : TableColumn(0) {
                override fun getWidth(): Int {
                    return getTotalColumnWidth()
                }

                override fun getHeaderValue(): Any {
                    return if (treeColumnIndex < 0) " " else (tree.model as TreeTableModel).getColumnName(
                        treeColumnIndex
                    )
                }
            })
            tree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY) {
                treeColumnIndex = findTreeColumnIndex()
            }
        }

        private fun findTreeColumnIndex(): Int {
            val model = tree.model as TreeTableModel
            for (i in 0 until model.columnCount) {
                if (TreeTableModel::class.java.isAssignableFrom(model.getColumnClass(i))) {
                    require(i == 0) { "Tree column must be first" }
                    return i
                }
            }
            return -1
        }

        override fun getTotalColumnWidth(): Int {
            return tree.visibleRect.width + 1
        }
    }
}
