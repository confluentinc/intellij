package io.confluent.intellijplugin.core.table.renderers

import io.confluent.intellijplugin.core.table.DecoratableDataTableModel


class ProgressCellRenderer(private val limitName: String, private val model: DecoratableDataTableModel) :
    AbstractProgressCellRenderer() {
    override fun currentLimit(row: Int): Int = convertValue(model.getValueByColumnName(limitName, row))
}

