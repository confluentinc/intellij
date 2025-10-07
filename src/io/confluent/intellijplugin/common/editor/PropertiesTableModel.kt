package io.confluent.intellijplugin.common.editor

import io.confluent.intellijplugin.core.settings.connections.Property
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.TableColumn

class PropertiesTableModel(properties: MutableList<Property>, private val isEditable: Boolean) : AbstractTableModel() {

    var properties: MutableList<Property> = properties
        set(value) {
            field = value
            fireTableDataChanged()
        }

    val columnModel = DefaultTableColumnModel().apply {
        addColumn(TableColumn(0).apply { headerValue = "Key" })
        addColumn(TableColumn(1).apply { headerValue = "Value" })
    }

    //region AbstractTableModel
    override fun getRowCount() = properties.size
    override fun getColumnCount() = 2
    override fun getValueAt(rowIndex: Int, columnIndex: Int) = if (columnIndex == 0)
        properties[rowIndex].name ?: ""
    else
        properties[rowIndex].value ?: ""
    //endregion AbstractTableModel

    @Suppress("HardCodedStringLiteral")
    override fun getColumnName(column: Int): String = columnModel.getColumn(column).headerValue.toString()

    override fun isCellEditable(rowIndex: Int, columnIndex: Int) = isEditable

    fun clear() {
        properties.clear()
        fireTableDataChanged()
    }

    fun addRow(property: Property) {
        properties.add(property)
        fireTableRowsInserted(properties.size - 1, properties.size - 1)
    }

    fun removeRow(rowIndex: Int) {
        properties.removeAt(rowIndex)
        fireTableRowsDeleted(rowIndex, rowIndex)
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (columnIndex == 0)
            properties[rowIndex].name = aValue?.toString() ?: ""
        else
            properties[rowIndex].value = aValue?.toString() ?: ""

        fireTableDataChanged()
    }
}