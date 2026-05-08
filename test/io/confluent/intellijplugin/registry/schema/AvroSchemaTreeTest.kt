package io.confluent.intellijplugin.registry.schema

import io.confluent.intellijplugin.model.SchemaRegistryFieldsInfo
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.event.TreeExpansionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class AvroSchemaTreeTest {

    private fun loadFixture(name: String): String =
        javaClass.getResourceAsStream("/fixtures/registry/schema/avro/$name")!!.bufferedReader().use { it.readText() }

    private fun buildTreeFor(fixture: String): Pair<DefaultMutableTreeNode, AvroSchemaTree> {
        val schema = AvroSchema(loadFixture(fixture))
        val root = DefaultMutableTreeNode("root")
        val model = DefaultTreeModel(root)
        val tree = AvroSchemaTree(model, schema)
        tree.buildTree(root)
        return root to tree
    }

    private fun DefaultMutableTreeNode.info(): SchemaRegistryFieldsInfo = userObject as SchemaRegistryFieldsInfo
    private fun DefaultMutableTreeNode.childNamed(name: String): DefaultMutableTreeNode =
        children().toList().map { it as DefaultMutableTreeNode }.first { it.info().name == name }

    @Test
    fun `record fields are added with their schema type names`() {
        val (root, _) = buildTreeFor("nested.avsc")

        assertEquals("long", root.childNamed("id").info().type)
        assertEquals("Order id", root.childNamed("id").info().description)
        assertEquals("array<string>", root.childNamed("tags").info().type)
        assertEquals("map<string, string>", root.childNamed("metadata").info().type)
        assertEquals("enum", root.childNamed("status").info().type)
        assertEquals("fixed[16]", root.childNamed("checksum").info().type)
        assertEquals("null | double", root.childNamed("discount").info().type)
    }

    @Test
    fun `enum members are listed as leaf children`() {
        val (root, _) = buildTreeFor("nested.avsc")

        val symbols = root.childNamed("status").children().toList().map { (it as DefaultMutableTreeNode).info().name }
        assertEquals(listOf("NEW", "PAID"), symbols)
    }

    @Test
    fun `array element schema is exposed as a value child`() {
        val (root, _) = buildTreeFor("nested.avsc")

        val tags = root.childNamed("tags")
        val value = tags.childNamed("value")
        assertEquals("string", value.info().type)
    }

    @Test
    fun `map exposes key string and value child`() {
        val (root, _) = buildTreeFor("nested.avsc")

        val metadata = root.childNamed("metadata")
        assertEquals("string", metadata.childNamed("key").info().type)
        assertEquals("string", metadata.childNamed("value").info().type)
    }

    @Test
    fun `nested record is added as a placeholder leaf until expanded`() {
        val (root, _) = buildTreeFor("nested.avsc")

        val customer = root.childNamed("customer")
        assertEquals(1, customer.childCount)
        val placeholder = customer.firstChild as DefaultMutableTreeNode
        assertEquals("", placeholder.info().name)
    }

    @Test
    fun `treeExpanded replaces placeholder with record fields`() {
        val (root, tree) = buildTreeFor("nested.avsc")

        val customer = root.childNamed("customer")
        tree.treeExpanded(TreeExpansionEvent(this, TreePath(arrayOf<Any>(root, customer))))

        assertEquals(1, customer.childCount)
        assertEquals("name", (customer.firstChild as DefaultMutableTreeNode).info().name)
        assertEquals("string", (customer.firstChild as DefaultMutableTreeNode).info().type)
    }

    @Test
    fun `treeExpanded ignores null event and unknown nodes`() {
        val (root, tree) = buildTreeFor("nested.avsc")
        tree.treeExpanded(null)

        val unknown = DefaultMutableTreeNode("not-a-fields-info")
        tree.treeExpanded(TreeExpansionEvent(this, TreePath(arrayOf<Any>(root, unknown))))
        assertTrue(unknown.childCount == 0)
    }

    @Test
    fun `non-record root is added as a single named node`() {
        val (root, _) = buildTreeFor("non-record.avsc")

        assertEquals(1, root.childCount)
        val color = root.firstChild as DefaultMutableTreeNode
        assertEquals("Color", color.info().name)
        assertEquals("enum", color.info().type)
    }
}
