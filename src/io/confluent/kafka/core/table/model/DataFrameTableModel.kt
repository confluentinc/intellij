package io.confluent.kafka.core.table.model

import com.intellij.charts.dataframe.DataFrame
import com.intellij.charts.dataframe.columns.IntegerType
import com.intellij.charts.dataframe.columns.LongType
import com.intellij.charts.dataframe.columns.RealType
import com.intellij.charts.dataframe.columns.StringType
import javax.swing.table.AbstractTableModel

class DataFrameTableModel(val dataFrame: DataFrame) : AbstractTableModel() {

  override fun getRowCount() = dataFrame.rowsCount

  override fun getColumnCount() = dataFrame.columnsCount

  override fun getColumnName(columnIndex: Int): String {
    @Suppress("HardCodedStringLiteral")
    return dataFrame[columnIndex].name
  }

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
    return dataFrame[columnIndex][rowIndex]
  }

  override fun isCellEditable(rowIndex: Int, columnIndex: Int) = false

  override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
    throw NotImplementedError()
  }

  override fun getColumnClass(columnIndex: Int): Class<*> {
    val columnType = dataFrame[columnIndex].type
    return when {
      columnType.isArray() -> super.getColumnClass(columnIndex)
      columnType == StringType -> String::class.java
      columnType == IntegerType -> Int::class.java
      columnType == LongType -> Long::class.java
      columnType == RealType -> Double::class.java
      else -> super.getColumnClass(columnIndex)
    }
  }
}