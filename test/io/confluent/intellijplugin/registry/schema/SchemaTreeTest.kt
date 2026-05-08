package io.confluent.intellijplugin.registry.schema

import io.confluent.intellijplugin.model.SchemaRegistryFieldsInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class SchemaTreeTest {

    private class FakeSchemaTree(model: DefaultTreeModel) : SchemaTree(model) {
        override fun buildTree(root: DefaultMutableTreeNode) = Unit
        override fun treeExpanded(event: javax.swing.event.TreeExpansionEvent?) = Unit

        fun newNode(name: String, type: String, default: Any? = null, description: String? = null, required: Boolean? = null) =
            createMutableNode(name, type, default, description, required)

        fun newEmpty() = createEmptyChild()
        fun readable(value: Any?) = getReadableVal(value)
        fun nodeId(node: DefaultMutableTreeNode) = node.getID()
    }

    private val tree = FakeSchemaTree(DefaultTreeModel(DefaultMutableTreeNode("root")))

    @Test
    fun `createMutableNode populates SchemaRegistryFieldsInfo with provided values`() {
        val node = tree.newNode("age", "int", default = 42, description = "user age", required = true)

        val info = node.userObject as SchemaRegistryFieldsInfo
        assertEquals("age", info.name)
        assertEquals("int", info.type)
        assertEquals("42", info.default)
        assertEquals("user age", info.description)
        assertEquals("true", info.required)
    }

    @Test
    fun `createMutableNode coerces null default and description to empty strings`() {
        val node = tree.newNode("name", "string")

        val info = node.userObject as SchemaRegistryFieldsInfo
        assertEquals("", info.default)
        assertEquals("", info.description)
        assertEquals("", info.required)
    }

    @Test
    fun `createEmptyChild returns a node with empty name and type`() {
        val empty = tree.newEmpty()

        val info = empty.userObject as SchemaRegistryFieldsInfo
        assertEquals("", info.name)
        assertEquals("", info.type)
    }

    @Test
    fun `getReadableVal returns empty string for null`() {
        assertEquals("", tree.readable(null))
    }

    @Test
    fun `getReadableVal renames Byte buffers and Null sentinels`() {
        assertEquals("bytes[]", tree.readable("java.nio.HeapByteBuffer[pos=0]"))
        assertEquals("null", tree.readable("org.apache.avro.JsonProperties\$Null@1"))
    }

    @Test
    fun `getReadableVal returns the toString value for unmatched inputs`() {
        assertEquals("hello", tree.readable("hello"))
        assertEquals("7", tree.readable(7))
    }

    @Test
    fun `getID returns a unique non-empty id per node`() {
        val a = tree.newNode("x", "int")
        val b = tree.newNode("x", "int")

        assertNotNull(tree.nodeId(a))
        assertNotEquals(tree.nodeId(a), tree.nodeId(b))
    }
}
