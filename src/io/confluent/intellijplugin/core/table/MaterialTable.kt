package io.confluent.intellijplugin.core.table

import com.intellij.icons.AllIcons
import com.intellij.ide.CopyProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.SideBorder
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.confluent.intellijplugin.core.table.renderers.*
import java.awt.Color
import java.awt.Component
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.event.MouseInputAdapter
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumnModel
import javax.swing.table.TableModel

/**
 *  Table by latest guidelines
 *  https://jetbrains.github.io/ui/controls/table/
 */
open class MaterialTable : JBTable, Disposable, UiDataProvider, CopyProvider {

    companion object {
        private val cellBorder = BorderFactory.createCompoundBorder(
            IdeBorderFactory.createBorder(JBColor.LIGHT_GRAY, SideBorder.LEFT),
            JBUI.Borders.empty(5)
        )

        private val firstCellBorder = JBUI.Borders.empty(5)
        val BDI_TABLE = DataKey.create<MaterialTable>("BDI_MATERIAL_TABLE_PARENT")
    }

    constructor(model: TableModel, columnModel: TableColumnModel) : super(model, columnModel)

    var customDataProvider: UiDataProvider? = null

    override fun uiDataSnapshot(sink: DataSink) {
        sink[PlatformDataKeys.COPY_PROVIDER] = this
        sink[BDI_TABLE] = this
        DataSink.uiDataSnapshot(sink, customDataProvider)
    }

    override fun performCopy(dataContext: DataContext) {
        ClipboardUtils.setStringContent(ClipboardUtils.getSelectedAsString(this))
    }

    override fun isCopyEnabled(dataContext: DataContext) = selectedRowCount > 0 && selectedColumnCount > 0
    override fun isCopyVisible(dataContext: DataContext) = true
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    // TODO need to discuss with UI/UX dep
    class SimpleHeaderRenderer : JLabel(), TableCellRenderer {
        companion object {
            private val emptyBorder = BorderFactory.createEmptyBorder()
        }

        init {
            horizontalTextPosition = LEADING
            border = emptyBorder
        }

        private fun getSortingIcon(column: Int, sortKeys: List<RowSorter.SortKey>): Icon? {
            val sortKey = sortKeys.firstOrNull { it.column == column } ?: return null
            return when (sortKey.sortOrder) {
                SortOrder.ASCENDING -> AllIcons.General.ArrowDown
                SortOrder.DESCENDING -> AllIcons.General.ArrowUp
                else -> null
            }
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            icon = if (table.columnCount <= column) null else getSortingIcon(
                table.convertColumnIndexToModel(column),
                table.rowSorter.sortKeys
            )
            text = " ${value.toString()} "
            return this
        }
    }

    var rollOverRowIndex = -1

    var oneAndHalfRowHeight = false
        set(value) {
            if (value) {
                rowHeight = (tableHeader.defaultRenderer.getTableCellRendererComponent(
                    this, "", false, false, 0,
                    0
                ).preferredSize.height * 1.5).toInt()
            }
            field = value
        }

    private var initialized = false

    init {
        autoResizeMode = AUTO_RESIZE_OFF
        setShowColumns(true)
        autoCreateRowSorter = true
        intercellSpacing = JBUI.emptySize()

        setShowGrid(false)

        tableHeader.apply {
            defaultRenderer = SimpleHeaderRenderer()
            resizingAllowed = true
            reorderingAllowed = true
            border = IdeBorderFactory.createBorder(SideBorder.BOTTOM or SideBorder.TOP)
        }

        val mouseListener = object : MouseInputAdapter() {
            override fun mouseExited(e: MouseEvent) {
                rollOverRowIndex = -1
                repaint()
            }

            override fun mouseMoved(e: MouseEvent) {
                val row = rowAtPoint(e.point)
                if (row != rollOverRowIndex) {
                    rollOverRowIndex = row
                    repaint()
                }
            }
        }

        addMouseMotionListener(mouseListener)
        addMouseListener(mouseListener)

        PopupHandler.installPopupMenu(this, "BdIde.TableEditor.PopupActionGroup", "MaterialTable")

        initialized = true
    }

    // We need to override all default renderers.
    override fun createDefaultRenderers() {
        defaultRenderersByColumnClass = UIDefaults(8, 0.75f)

        // Objects
        defaultRenderersByColumnClass[Any::class.java] = UIDefaults.LazyValue { MaterialTableCellRenderer() }

        // Numbers
        defaultRenderersByColumnClass[Number::class.java] = UIDefaults.LazyValue { NumberRenderer() }

        // Doubles and Floats
        defaultRenderersByColumnClass[Float::class.java] = UIDefaults.LazyValue { DoubleRenderer() }
        defaultRenderersByColumnClass[Double::class.java] = UIDefaults.LazyValue { DoubleRenderer() }

        // Dates
        defaultRenderersByColumnClass[Date::class.java] = UIDefaults.LazyValue { DateRenderer() }

        // Icons and all subclasses (like ImageIcons)
        defaultRenderersByColumnClass[Icon::class.java] = UIDefaults.LazyValue { IconRenderer() }

        // Booleans
        defaultRenderersByColumnClass[Boolean::class.java] = UIDefaults.LazyValue { BooleanRenderer() }
    }

    override fun updateUI() {
        if (font != null) {
            font = font.deriveFont(UIManager.getFont("Table.font").size.toFloat())
        }
        // To restore extended row height after enabling to presentation mode.
        if (oneAndHalfRowHeight) {
            oneAndHalfRowHeight = true
        }
        super.updateUI()
        if (initialized) {
            MaterialTableUtils.fitColumnsWidth(this)
        }
    }

    open fun getSelectionRowBackground(): Color = UIUtil.getTableSelectionBackground(false)

    open fun getSelectionRowForeground(): Color = foreground

    /** We are preparing renderer background for mouse hovered row. */
    override fun prepareRenderer(renderer: TableCellRenderer, row: Int, column: Int): Component {
        val tableIsFocusOwner = isFocusOwner
        val c = super.prepareRenderer(renderer, row, column) as JComponent
        c.border = if (column != 0) cellBorder else firstCellBorder
        val (foregroundColor, backgroundColor) = selectCellColors(
            isRowSelected(row),
            isColumnSelected(column),
            tableIsFocusOwner,
            row,
            column
        )
        c.font = font
        c.foreground = foregroundColor
        c.background = backgroundColor
        return c
    }

    override fun dispose() {}

    /**
     * @return (Foreground color, Background color)
     */
    protected open fun selectCellColors(
        isRowSelected: Boolean,
        isColumnSelected: Boolean,
        isFocusOwner: Boolean,
        row: Int,
        column: Int
    ): Pair<Color, Color> {
        return when {
            isRowSelected -> {
                if (isColumnSelected) {
                    if (isFocusOwner) Pair(getSelectionForeground(), getSelectionBackground())
                    else Pair(getSelectionRowForeground(), getSelectionRowBackground())
                } else Pair(
                    if (isFocusOwner) getSelectionRowForeground() else foreground,
                    if (isFocusOwner) getSelectionRowBackground() else UIUtil.getTableSelectionBackground(false)
                )
            }

            row == rollOverRowIndex -> Pair(foreground, JBUI.CurrentTheme.Table.Hover.background(true))
            else -> Pair(foreground, background)
        }
    }
}