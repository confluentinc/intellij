package io.confluent.intellijplugin.core.monitoring.list

import io.confluent.intellijplugin.core.monitoring.BrowseOnClick
import io.confluent.intellijplugin.core.table.MaterialTable
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaField

object ListClickHelper {

    fun installOn(table: MaterialTable, rowName: String, callback: (Any?) -> Unit) {
        installOn(table, listOf(rowName), callback)
    }

    fun installOn(table: MaterialTable, rowNames: List<String>, callback: (Any?) -> Unit) {

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {

                if (e.button != MouseEvent.BUTTON1) {
                    return
                }

                val col = table.columnAtPoint(e.point)
                if (col != 1) {
                    return
                }

                val row = table.rowAtPoint(e.point)
                if (row < 0 || !rowNames.contains(table.getValueAt(row, 0))) {
                    return
                }

                callback(table.getValueAt(row, 1))
            }
        })
    }

    /** List of column.name for fields with BrowseOnClick annotation. */
    fun getBrowsableColumns(renderableColumns: List<KProperty1<*, *>>): List<String> {
        val browsableColumns = mutableListOf<String>()

        renderableColumns.forEach { column ->
            column.javaField?.annotations?.let { annotations ->
                if (annotations.find { it is BrowseOnClick } != null) {
                    browsableColumns.add(column.name)
                }
            }
        }
        return browsableColumns
    }
}