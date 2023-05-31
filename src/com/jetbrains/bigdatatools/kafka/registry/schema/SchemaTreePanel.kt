package com.jetbrains.bigdatatools.kafka.registry.schema

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.treetable.ListTreeTableModel
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.ui.ColumnInfo
import com.jetbrains.bigdatatools.common.rfs.editorviewer.RfsTreeTable
import com.jetbrains.bigdatatools.common.table.MaterialTableUtils
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.ScrollPaneConstants
import javax.swing.event.TreeExpansionListener
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
  private val scrollPanel = ScrollPaneFactory.createScrollPane(treeTable,
                                                               ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                               ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER).apply {
    border = BorderFactory.createEmptyBorder()
  }

  private var listener: TreeExpansionListener? = null

  init {
    val tree = treeTable.tree
    treeTable.table.emptyText.text = ""

    // We need to set max size explicitly because by default maxSize == preferredSize
    // and this leads to TreeTable first column resize problem.
    treeTable.tree.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

    tree.isRootVisible = false

    MaterialTableUtils.fitColumnsWidth(treeTable.table)
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
  }

  private fun updateListener(newListener: TreeExpansionListener) {
    if (listener != null) {
      treeTable.tree.removeTreeExpansionListener(listener)
    }

    listener = newListener
    treeTable.tree.addTreeExpansionListener(listener)
  }

  fun getComponent() = scrollPanel

  companion object {
    class SchemaRegistryNameColumn<T : Comparable<T>>(val getValue: (SchemaRegistryFieldsInfo) -> T)
      : ColumnInfo<DefaultMutableTreeNode, T>(KafkaMessagesBundle.message("column.name.name")) {
      override fun valueOf(item: DefaultMutableTreeNode): T = getValue((item.userObject as SchemaRegistryFieldsInfo))
      override fun getColumnClass(): Class<*> = TreeTableModel::class.java
    }

    class SchemaRegistryColumn<T : Comparable<T>>(@NlsContexts.ColumnName name: String, val getValue: (SchemaRegistryFieldsInfo) -> T)
      : ColumnInfo<DefaultMutableTreeNode, T>(name) {
      override fun valueOf(item: DefaultMutableTreeNode): T = getValue((item.userObject as SchemaRegistryFieldsInfo))
    }
  }
}