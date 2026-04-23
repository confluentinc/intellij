package io.confluent.intellijplugin.core.table.filters

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter

@TestApplication
class TableFilterHeaderTest {

    private fun newTable(): JTable {
        val model = DefaultTableModel(
            arrayOf(
                arrayOf<Any?>("a", "x"),
                arrayOf<Any?>("b", "y"),
            ),
            arrayOf("Col1", "Col2"),
        )
        return JTable(model).apply { rowSorter = TableRowSorter(model) }
    }

    @Test
    fun `columnsController is created with one editor per column`() {
        lateinit var header: TableFilterHeader
        SwingUtilities.invokeAndWait {
            val table = newTable()
            header = TableFilterHeader(table)
        }
        assertNotNull(header.columnsController)
        assertEquals(2, header.columnsController!!.count())
    }

    @Test
    fun `editor listener applies row filter when externalFilterMode is false`() {
        lateinit var table: JTable
        lateinit var header: TableFilterHeader
        SwingUtilities.invokeAndWait {
            table = newTable()
            header = TableFilterHeader(table)
        }

        SwingUtilities.invokeAndWait {
            header.columnsController!!.first().text = "a"
        }

        val sorter = table.rowSorter as TableRowSorter<*>
        assertNotNull(sorter.rowFilter)
        assertEquals(1, sorter.viewRowCount)
    }

    @Test
    fun `externalFilterMode suppresses row filter updates from editors`() {
        lateinit var table: JTable
        lateinit var header: TableFilterHeader
        SwingUtilities.invokeAndWait {
            table = newTable()
            header = TableFilterHeader(table).apply { externalFilterMode = true }
        }

        SwingUtilities.invokeAndWait {
            header.columnsController!!.first().text = "a"
        }

        val sorter = table.rowSorter as TableRowSorter<*>
        assertNull(sorter.rowFilter)
        assertEquals(2, sorter.viewRowCount)
    }

    @Test
    fun `setting table to null removes the controller`() {
        lateinit var header: TableFilterHeader
        SwingUtilities.invokeAndWait {
            header = TableFilterHeader(newTable())
        }
        assertNotNull(header.columnsController)

        SwingUtilities.invokeAndWait { header.table = null }
        assertNull(header.columnsController)
    }

    @Test
    fun `changing table model recreates the controller with fresh editors`() {
        lateinit var table: JTable
        lateinit var header: TableFilterHeader
        SwingUtilities.invokeAndWait {
            table = newTable()
            header = TableFilterHeader(table)
        }
        val originalController = header.columnsController

        SwingUtilities.invokeAndWait {
            table.model = DefaultTableModel(
                arrayOf(arrayOf<Any?>("z", "w", "new")),
                arrayOf("Col1", "Col2", "Col3"),
            )
        }

        assertTrue(header.columnsController !== originalController)
        assertEquals(3, header.columnsController!!.count())
    }
}
