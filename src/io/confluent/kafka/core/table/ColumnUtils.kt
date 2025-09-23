package io.confluent.kafka.core.table

import javax.swing.JTable
import javax.swing.table.TableColumn
import kotlin.collections.iterator

fun JTable.removeColumn(headerValue: String) {
  columnModel.columns.asIterator().forEach {
    if (it.headerValue == headerValue) {
      removeColumn(it)
      return
    }
  }
}

fun JTable.getColumnByName(name: String): TableColumn? {
  for (column in columnModel.columns) {
    if (column.headerValue == name) {
      return column
    }
  }
  return null
}