package com.jetbrains.bigdatatools.kafka.core.table.renderers

import com.jetbrains.bigdatatools.kafka.core.table.DecoratableDataTableModel


class ProgressCellRenderer(private val limitName: String, private val model: DecoratableDataTableModel) : AbstractProgressCellRenderer() {
  override fun currentLimit(row: Int): Int = convertValue(model.getValueByColumnName(limitName, row))
}

