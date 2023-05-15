package com.jetbrains.bigdatatools.kafka.registry.schema

import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.treetable.ListTreeTableModel
import com.intellij.util.ui.ColumnInfo
import com.jetbrains.bigdatatools.common.rfs.editorviewer.RfsTreeTable
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import javax.swing.ScrollPaneConstants
import javax.swing.tree.DefaultMutableTreeNode

class SchemaTreePanel {
  // TODO: It's possible to show additional information
  // JsonSchema -- title, description
  // Avro -- doc
  // Protobuf -- label, documentation, options

  val treeTableModel = ListTreeTableModel(DefaultMutableTreeNode(), arrayOf(
    SchemaRegistryColumn("Type", 10) { it.type },
    SchemaRegistryColumn("Default", 10) { it.default },
  ))

  private var treeTable = RfsTreeTable(treeTableModel)
  private val scrollPanel = ScrollPaneFactory.createScrollPane(treeTable, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                               ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)

  init {
    val tree = treeTable.tree

    tree.isRootVisible = false
    tree.showsRootHandles = true
    tree.cellRenderer = NodeRenderer()
    //treeTable.setTableHeader(null);

    //treeTable.setTableHeader(null);
    //
    //TreeTableSpeedSearch.installOn(treeTable) { o: TreePath ->
    //  val userObject = (o.lastPathComponent as DefaultMutableTreeNode).userObject
    //  if (userObject is CompilerOptionInfo) (userObject as CompilerOptionInfo).DISPLAY_NAME else ""
    //}.comparator = SpeedSearchComparator(false)

  }

  fun update(schema: ParsedSchema) {
    val root = DefaultMutableTreeNode()
    when (schema) {
      is AvroSchema -> AvroSchemaTree(schema).buildTree(root)
      is JsonSchema -> JsonSchemaTree(schema).buildTree(root)
      is ProtobufSchema -> ProtobufSchemaTree(schema).buildTree(root)
      else -> {}
    }
    //RfsTableUtils.fitColumnsWidth(treeTable.table, treeTable.tree.model)
    treeTableModel.setRoot(root)
    treeTable.updateUI()
    treeTable.tree.updateUI()
  }

  fun getComponent() = scrollPanel

  companion object {
    class SchemaRegistryColumn<T : Comparable<T>>(@NlsContexts.ColumnName name: String,
                                                  val columns: Int,
                                                  val getValue: (SchemaRegistryFieldsInfo) -> T) : ColumnInfo<DefaultMutableTreeNode, T>(
      name) {

      override fun valueOf(item: DefaultMutableTreeNode): T = getValue((item.userObject as SchemaRegistryFieldsInfo))

    }
  }
}