package io.confluent.intellijplugin.core.rfs.editorviewer

import io.confluent.intellijplugin.core.rfs.search.impl.BackListElement
import io.confluent.intellijplugin.core.rfs.search.impl.ListElement
import io.confluent.intellijplugin.core.rfs.search.impl.MoreListElement
import java.text.Collator
import javax.swing.SortOrder
import javax.swing.table.TableRowSorter

class RfsTableComparator(
    private val model: RfsTableModel,
    private val rowSorter: TableRowSorter<RfsTableModel>,
    private val columnIndex: Int
) : Comparator<ListElement> {

    private fun ListElement.getColumnValue(column: Int): Any? {
        return if (column == 0) fileInfo.name
        else {
            val fileInfo = fileInfo
            model.columns[columnIndex - 1].valueForFileInfo(fileInfo)
        }
    }

    private fun compareInner(o1: ListElement, o2: ListElement): Int {
        val columnClass = model.getColumnClass(columnIndex)

        val v1 = o1.getColumnValue(columnIndex)
        val v2 = o2.getColumnValue(columnIndex)

        if (v1 == null && v2 == null) {
            return 0
        } else if (v1 == null) {
            return -1
        } else if (v2 == null) {
            return 1
        }

        // Special comparison for "Size" column.
        return if (Long::class.java == columnClass) {
            @Suppress("UNCHECKED_CAST")
            (v1 as Comparable<Any?>).compareTo(v2)
        } else {
            Collator.getInstance().compare(v1.toString(), v2.toString())
        }
    }

    override fun compare(o1: ListElement, o2: ListElement): Int {
        val sortOrder = rowSorter.sortKeys.find { it.column == columnIndex }?.sortOrder == SortOrder.DESCENDING

        return when {
            o1 is MoreListElement -> if (sortOrder) -1 else 1
            o2 is MoreListElement -> if (sortOrder) 1 else -1
            o1 is BackListElement -> if (sortOrder) 1 else -1
            o2 is BackListElement -> if (sortOrder) -1 else 1
            o1.fileInfo.isDirectory && o2.fileInfo.isDirectory -> compareInner(o1, o2)
            o1.fileInfo.isDirectory -> if (sortOrder) 1 else -1
            o2.fileInfo.isDirectory -> if (sortOrder) -1 else 1
            else -> compareInner(o1, o2)
        }
    }
}

class RfsTableRowSorter(model: RfsTableModel) : TableRowSorter<RfsTableModel>(model) {

    private val comparators = mutableMapOf<Int, RfsTableComparator>()

    override fun useToString(column: Int) = false

    override fun getComparator(column: Int): Comparator<*> {
        return comparators.getOrPut(column) { RfsTableComparator(model, this, column) }
    }
}