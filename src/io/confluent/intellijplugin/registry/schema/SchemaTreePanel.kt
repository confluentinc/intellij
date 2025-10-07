package io.confluent.intellijplugin.registry.schema

import com.intellij.openapi.ui.Splitter.DividerPositionStrategy
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.treeStructure.treetable.ListTreeTableModel
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.ui.util.preferredWidth
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import io.confluent.intellijplugin.core.rfs.editorviewer.RfsTreeTable
import io.confluent.intellijplugin.core.table.MaterialTableUtils
import io.confluent.intellijplugin.core.ui.onFirstSizeChange
import io.confluent.intellijplugin.model.SchemaRegistryFieldsInfo
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import java.awt.Component
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.ScrollPaneConstants
import javax.swing.border.CompoundBorder
import javax.swing.event.TreeExpansionListener
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer
import javax.swing.tree.DefaultMutableTreeNode

class SchemaTreePanel {
    private val commonColumns = arrayOf(
        SchemaRegistryNameColumn { it.name },
        SchemaRegistryColumn(KafkaMessagesBundle.message("column.name.type")) { it.type },
        SchemaRegistryColumn(KafkaMessagesBundle.message("column.name.default")) { it.default },
        SchemaRegistryColumn(KafkaMessagesBundle.message("column.name.documentation")) { it.description },
        SchemaRegistryColumn(KafkaMessagesBundle.message("column.name.required")) { it.required },
    )

    private val treeTableModel = ListTreeTableModel(DefaultMutableTreeNode(), commonColumns)

    private val treeTable = RfsTreeTable(treeTableModel)

    private val scrollPanel = ScrollPaneFactory.createScrollPane(
        treeTable, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    ).apply {
        border = BorderFactory.createEmptyBorder()
        preferredWidth = JBUI.scale(400)
    }

    private var listener: TreeExpansionListener? = null

    class TreeHeaderRenderer : JLabel(), TableCellRenderer {

        init {
            border = CompoundBorder(JBUI.Borders.emptyRight(1), IdeBorderFactory.createBorder(SideBorder.RIGHT))
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            font = table.font
            text = " ${value.toString()} "
            return this
        }
    }

    init {
        treeTable.split.dividerPositionStrategy = DividerPositionStrategy.KEEP_FIRST_SIZE

        val tree = treeTable.tree
        treeTable.table.emptyText.text = ""

        // We need to set max size explicitly because by default maxSize == preferredSize
        // and this leads to TreeTable first column resize problem.
        treeTable.tree.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        val headerRenderer = TreeHeaderRenderer()
        treeTable.setupFirstColumnRenderer(headerRenderer)
        tree.isRootVisible = false

        treeTable.table.autoResizeMode = JTable.AUTO_RESIZE_OFF

        // Setting the width of first column (tree component). This is done to select approximately width, which is good to display
        // expandable tree with elements of unknown length.
        val comp = headerRenderer.getTableCellRendererComponent(treeTable.table, "A".repeat(24), false, false, 0, 0)
        treeTable.tree.preferredSize = Dimension(comp.preferredSize.width, treeTable.tree.preferredSize.height)

        treeTable.table.columnModel.columns.asIterator().forEach {
            it.cellRenderer = CellRendererWithBackground()
        }
    }

    private var dividerProportionUpdated = false

    private fun updateDividerProportion() {
        if (dividerProportionUpdated) return

        if (treeTable.size.width == 0) {
            treeTable.onFirstSizeChange {
                if (treeTable.size.width != 0) {
                    updateDividerProportion()
                } else {
                    treeTable.onFirstSizeChange {
                        updateDividerProportion()
                    }
                }
            }
            return
        }

        treeTable.split.proportion = treeTable.tree.preferredSize.width.toFloat() / treeTable.width

        dividerProportionUpdated = true
    }

    fun update(schema: ParsedSchema) {
        val root = DefaultMutableTreeNode()

        val schemaTree = when (schema) {
            is AvroSchema -> AvroSchemaTree(treeTableModel, schema)
            is JsonSchema -> JsonSchemaTree(treeTableModel, schema)
            is ProtobufSchema -> ProtobufSchemaTree(treeTableModel, schema)
            else -> null
        } ?: return
        updateListener(schemaTree)
        schemaTree.buildTree(root)
        treeTableModel.setRoot(root)

        updateDividerProportion()
        MaterialTableUtils.fitColumnsWidth(treeTable.table)
    }

    private fun updateListener(newListener: TreeExpansionListener) {
        if (listener != null) {
            treeTable.tree.removeTreeExpansionListener(listener)
        }

        listener = newListener
        treeTable.tree.addTreeExpansionListener(listener)
    }

    fun getComponent() = scrollPanel

    // We have a problems rendering selection in standard cell renderer from TreeTable table.
    class CellRendererWithBackground : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            component.background = if (isSelected) table.selectionBackground else null
            return component
        }
    }

    companion object {
        class SchemaRegistryNameColumn<T : Comparable<T>>(val getValue: (SchemaRegistryFieldsInfo) -> T) :
            ColumnInfo<DefaultMutableTreeNode, T>(
                KafkaMessagesBundle.message("column.name.name")
            ) {
            override fun valueOf(item: DefaultMutableTreeNode): T =
                getValue((item.userObject as SchemaRegistryFieldsInfo))

            override fun getColumnClass(): Class<*> = TreeTableModel::class.java
        }

        class SchemaRegistryColumn<T : Comparable<T>>(
            @NlsContexts.ColumnName name: String,
            val getValue: (SchemaRegistryFieldsInfo) -> T
        ) : ColumnInfo<DefaultMutableTreeNode, T>(
            name
        ) {
            override fun valueOf(item: DefaultMutableTreeNode): T =
                getValue((item.userObject as SchemaRegistryFieldsInfo))
        }
    }
}