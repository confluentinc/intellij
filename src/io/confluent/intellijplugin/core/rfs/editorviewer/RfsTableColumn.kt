package io.confluent.intellijplugin.core.rfs.editorviewer

import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.ColumnInfo
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.tree.node.DriverFileRfsTreeNode

/**  columns - the total number of columns, used to calculate the preferred width. */
abstract class RfsTableColumn<T : Comparable<T>>(
    @NlsContexts.ColumnName name: String,
    val id: String,
    val columns: Int = 0
) : ColumnInfo<Any, T>(name) {
    abstract fun valueForFileInfo(fileInfo: FileInfo): T?
    protected abstract fun getColumnComparable(): (FileInfo) -> Comparable<*>?

    override fun valueOf(item: Any?): T? {
        val fileInfo = (item as? DriverFileRfsTreeNode)?.fileInfo ?: return null
        return valueForFileInfo(fileInfo)
    }

    fun getComparatorAsc(): Comparator<FileInfo> {
        val selectors: (FileInfo) -> Comparable<*>? = getColumnComparable()
        return compareBy({ !it.isDirectory }, selectors, { it.name })
    }

    fun getComparatorDesc(): Comparator<FileInfo> {
        val comparables = getColumnComparable()
        return compareBy<FileInfo> { !it.isDirectory }.thenByDescending { comparables(it) }.thenBy { it.name }
    }
}