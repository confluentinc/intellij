package io.confluent.kafka.core.table

import javax.swing.table.TableModel

interface DecoratableDataTableModel : TableModel {
  fun getValueByColumnName(name: String, row: Int): Any?
}