package io.confluent.intellijplugin.registry.schema

import io.confluent.intellijplugin.model.SchemaRegistryFieldsInfo
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class ProtobufSchemaTreeTest {

    private fun loadFixture(name: String): String =
        javaClass.getResourceAsStream("/fixtures/registry/schema/protobuf/$name")!!.bufferedReader().use { it.readText() }

    private fun buildTreeFor(fixture: String): Triple<DefaultMutableTreeNode, ProtobufSchemaTree, JTree> {
        val schema = ProtobufSchema(loadFixture(fixture))
        val root = DefaultMutableTreeNode("root")
        val model = DefaultTreeModel(root)
        val tree = ProtobufSchemaTree(model, schema)
        tree.buildTree(root)
        return Triple(root, tree, JTree(model))
    }

    private fun DefaultMutableTreeNode.info(): SchemaRegistryFieldsInfo = userObject as SchemaRegistryFieldsInfo
    private fun DefaultMutableTreeNode.childNamed(name: String): DefaultMutableTreeNode =
        children().toList().map { it as DefaultMutableTreeNode }.first { it.info().name == name }

    @Nested
    inner class `when building tree` {

        @Test
        fun `scalar fields are added with lowercased proto type names`() {
            val (root, _, _) = buildTreeFor("complex.proto")

            val id = root.childNamed("id")
            assertEquals("string", id.info().type)
        }

        @Test
        fun `enum field exposes all enum value names as children`() {
            val (root, _, _) = buildTreeFor("complex.proto")

            val status = root.childNamed("status")
            assertEquals("enum", status.info().type)
            val values = status.children().toList().map { (it as DefaultMutableTreeNode).info().name }
            assertEquals(listOf("NEW", "PAID"), values)
        }

        @Test
        fun `nested message field is added as a placeholder`() {
            val (root, _, _) = buildTreeFor("complex.proto")

            val customer = root.childNamed("customer")
            assertEquals("Customer", customer.info().type)
            assertEquals(1, customer.childCount)
            assertEquals("", (customer.firstChild as DefaultMutableTreeNode).info().name)
        }

        @Test
        fun `map field renders as map with string key and value type`() {
            val (root, _, _) = buildTreeFor("complex.proto")

            val metadata = root.childNamed("metadata")
            assertEquals("map<string, string>", metadata.info().type)
        }

        @Test
        fun `oneOf groups its fields under a single oneOf node`() {
            val (root, _, _) = buildTreeFor("complex.proto")

            val payment = root.childNamed("payment")
            assertEquals("oneOf", payment.info().type)
            val members = payment.children().toList().map { (it as DefaultMutableTreeNode).info().name }
            assertEquals(listOf("card", "voucher"), members)
        }

        @Test
        fun `oneOf is added only once even though it contains multiple fields`() {
            val (root, _, _) = buildTreeFor("complex.proto")

            val paymentNodes = root.children().toList()
                .map { (it as DefaultMutableTreeNode).info().name }
                .filter { it == "payment" }
            assertEquals(1, paymentNodes.size)
        }
    }

    @Nested
    inner class `when expanding nodes` {

        @Test
        fun `treeExpanded replaces placeholder with message fields`() {
            val (root, tree, jTree) = buildTreeFor("complex.proto")

            val customer = root.childNamed("customer")
            tree.treeExpanded(TreeExpansionEvent(jTree, TreePath(arrayOf<Any>(root, customer))))
            assertEquals("name", (customer.firstChild as DefaultMutableTreeNode).info().name)
            assertEquals("string", (customer.firstChild as DefaultMutableTreeNode).info().type)
        }

        @Test
        fun `treeExpanded short-circuits on null event and unknown nodes`() {
            val (root, tree, jTree) = buildTreeFor("complex.proto")
            tree.treeExpanded(null)

            val unknown = DefaultMutableTreeNode("not-a-fields-info")
            tree.treeExpanded(TreeExpansionEvent(jTree, TreePath(arrayOf<Any>(root, unknown))))
            assertEquals(0, unknown.childCount)
        }
    }
}
