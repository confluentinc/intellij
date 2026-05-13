package io.confluent.intellijplugin.registry.schema

import io.confluent.intellijplugin.model.SchemaRegistryFieldsInfo
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class JsonSchemaTreeTest {

    private fun loadFixture(name: String): String =
        javaClass.getResourceAsStream("/fixtures/registry/schema/json/$name")!!.bufferedReader().use { it.readText() }

    private fun buildTreeFor(fixture: String): Triple<DefaultMutableTreeNode, JsonSchemaTree, JTree> {
        val schema = JsonSchema(loadFixture(fixture))
        val root = DefaultMutableTreeNode("root")
        val model = DefaultTreeModel(root)
        val tree = JsonSchemaTree(model, schema)
        tree.buildTree(root)
        return Triple(root, tree, JTree(model))
    }

    private fun DefaultMutableTreeNode.info(): SchemaRegistryFieldsInfo = userObject as SchemaRegistryFieldsInfo
    private fun DefaultMutableTreeNode.childNamed(name: String): DefaultMutableTreeNode =
        children().toList().map { it as DefaultMutableTreeNode }.first { it.info().name == name }

    @Nested
    inner class `when building tree` {

        @Test
        fun `primitive properties are typed and carry description and default`() {
            val (root, _, _) = buildTreeFor("complex.json")

            val id = root.childNamed("id")
            assertEquals("integer", id.info().type)
            assertEquals("id field", id.info().description)
            assertEquals("true", id.info().required)

            val active = root.childNamed("active")
            assertEquals("boolean", active.info().type)
            assertEquals("true", active.info().default)
            assertEquals("false", active.info().required)
        }

        @Test
        fun `array with single item schema exposes a value child of that type`() {
            val (root, _, _) = buildTreeFor("complex.json")

            val tags = root.childNamed("tags")
            assertEquals("array<string>", tags.info().type)
            assertEquals("string", tags.childNamed("value").info().type)
        }

        @Test
        fun `array with tuple item schemas exposes indexed children`() {
            val (root, _, _) = buildTreeFor("complex.json")

            val tuple = root.childNamed("tuple")
            assertEquals("array<>", tuple.info().type)
            val children = tuple.children().toList().map { (it as DefaultMutableTreeNode).info() }
            assertEquals(listOf("[0]", "[1]"), children.map { it.name })
            assertEquals(listOf("string", "integer"), children.map { it.type })
        }

        @Test
        fun `enum members appear as leaf children`() {
            val (root, _, _) = buildTreeFor("complex.json")

            val color = root.childNamed("color")
            val members = color.children().toList().map { (it as DefaultMutableTreeNode).info().name }
            assertEquals(listOf("red", "green"), members)
        }

        @Test
        fun `oneOf is rendered as combined type with indexed sub-schemas`() {
            val (root, _, _) = buildTreeFor("complex.json")

            val either = root.childNamed("either")
            assertEquals("string | integer", either.info().type)
            val subs = either.children().toList().map { (it as DefaultMutableTreeNode).info() }
            assertEquals(listOf("type 0", "type 1"), subs.map { it.name })
        }

        @Test
        fun `nested object is added as a placeholder leaf`() {
            val (root, _, _) = buildTreeFor("complex.json")

            val address = root.childNamed("address")
            assertEquals("object", address.info().type)
            assertEquals(1, address.childCount)
            assertEquals("", (address.firstChild as DefaultMutableTreeNode).info().name)
        }

        @Test
        fun `non-object root is rendered as a single field node`() {
            val (root, _, _) = buildTreeFor("non-object.json")

            assertEquals(1, root.childCount)
            val field = root.firstChild as DefaultMutableTreeNode
            assertEquals("field", field.info().name)
            assertEquals("string", field.info().type)
        }
    }

    @Nested
    inner class `when expanding nodes` {

        @Test
        fun `treeExpanded populates a placeholder with object properties`() {
            val (root, tree, jTree) = buildTreeFor("complex.json")

            val address = root.childNamed("address")
            tree.treeExpanded(TreeExpansionEvent(jTree, TreePath(arrayOf<Any>(root, address))))
            assertEquals("street", (address.firstChild as DefaultMutableTreeNode).info().name)
            assertEquals("string", (address.firstChild as DefaultMutableTreeNode).info().type)
        }

        @Test
        fun `treeExpanded short-circuits on null event and unknown nodes`() {
            val (root, tree, jTree) = buildTreeFor("complex.json")
            tree.treeExpanded(null)

            val unknown = DefaultMutableTreeNode("not-a-fields-info")
            tree.treeExpanded(TreeExpansionEvent(jTree, TreePath(arrayOf<Any>(root, unknown))))
            assertEquals(0, unknown.childCount)
        }
    }
}
