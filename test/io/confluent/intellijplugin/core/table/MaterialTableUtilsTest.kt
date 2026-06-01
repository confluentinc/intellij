package io.confluent.intellijplugin.core.table

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date
import javax.swing.DefaultRowSorter
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel

@TestApplication
class MaterialTableUtilsTest {

    /** Mixed-class model mirroring the message viewer: Topic/Key/Value are String, the rest typed. */
    private class StubModel : AbstractTableModel() {
        private val classes = listOf(
            String::class.java, Date::class.java, String::class.java,
            String::class.java, Int::class.java, Long::class.java,
        )

        override fun getRowCount() = 0
        override fun getColumnCount() = classes.size
        override fun getColumnClass(columnIndex: Int): Class<*> = classes[columnIndex]
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = null
    }

    private fun sorterFor(sortStringColumns: Boolean): DefaultRowSorter<*, *> {
        lateinit var table: JTable
        SwingUtilities.invokeAndWait {
            table = JTable(StubModel())
            MaterialTableUtils.setupSorters(table, sortStringColumns = sortStringColumns)
        }
        return table.rowSorter as DefaultRowSorter<*, *>
    }

    @Test
    fun `setupSorters disables sorting on String columns when sortStringColumns is false`() {
        val sorter = sorterFor(sortStringColumns = false)

        assertFalse(sorter.isSortable(0), "Topic (String) should not be sortable")
        assertFalse(sorter.isSortable(2), "Key (String) should not be sortable")
        assertFalse(sorter.isSortable(3), "Value (String) should not be sortable")

        assertTrue(sorter.isSortable(1), "Timestamp (Date) should remain sortable")
        assertTrue(sorter.isSortable(4), "Partition (Int) should remain sortable")
        assertTrue(sorter.isSortable(5), "Offset (Long) should remain sortable")
    }

    @Test
    fun `setupSorters keeps all columns sortable by default`() {
        val sorter = sorterFor(sortStringColumns = true)

        (0..5).forEach { assertTrue(sorter.isSortable(it), "column $it should be sortable by default") }
    }
}
