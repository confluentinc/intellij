package io.confluent.intellijplugin.core.table.renderers

class FixedProgressCellRenderer(private val limit: Int) : AbstractProgressCellRenderer() {
    override fun currentLimit(row: Int): Int = limit
}