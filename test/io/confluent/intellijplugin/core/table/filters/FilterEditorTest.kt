package io.confluent.intellijplugin.core.table.filters

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

@TestApplication
class FilterEditorTest {

    private fun newEditor(): FilterEditor {
        var editor: FilterEditor? = null
        SwingUtilities.invokeAndWait { editor = FilterEditor(modelIndex = 0) }
        return editor!!
    }

    @Test
    fun `search icon is shown initially when field is blank and unfocused`() {
        val editor = newEditor()
        assertTrue(editor.isSearchIconVisibleInTest)
    }

    @Test
    fun `setting non-blank text programmatically hides the search icon`() {
        val editor = newEditor()
        SwingUtilities.invokeAndWait { editor.text = "foo" }
        assertFalse(editor.isSearchIconVisibleInTest)
    }

    @Test
    fun `clearing text programmatically while unfocused restores the search icon`() {
        val editor = newEditor()
        SwingUtilities.invokeAndWait {
            editor.text = "foo"
            editor.text = ""
        }
        assertTrue(editor.isSearchIconVisibleInTest)
    }

    @Test
    fun `setting same text is a no-op and does not fire listeners`() {
        val editor = newEditor()
        SwingUtilities.invokeAndWait { editor.text = "foo" }
        var fired = 0
        editor.addListener { fired++ }
        SwingUtilities.invokeAndWait { editor.text = "foo" }
        assertEquals(0, fired)
    }

    @Test
    fun `listener fires on text changes and can be removed`() {
        val editor = newEditor()
        var fired = 0
        val listener = FilerEditorChangeListener { fired++ }
        editor.addListener(listener)
        SwingUtilities.invokeAndWait { editor.text = "a" }
        assertEquals(1, fired)

        editor.removeListener(listener)
        SwingUtilities.invokeAndWait { editor.text = "b" }
        assertEquals(1, fired)
    }

    @Test
    fun `text getter reflects current editor content`() {
        val editor = newEditor()
        SwingUtilities.invokeAndWait { editor.text = "hello" }
        assertEquals("hello", editor.text)
    }

    @Test
    fun `setting text to null stores empty string`() {
        val editor = newEditor()
        SwingUtilities.invokeAndWait {
            editor.text = "x"
            editor.text = null
        }
        assertEquals("", editor.text)
        assertTrue(editor.isSearchIconVisibleInTest)
    }
}
