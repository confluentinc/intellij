package io.confluent.kafka.core.table.renderers

class FixedProgressCellRenderer(private val limit: Int) : AbstractProgressCellRenderer() {
  override fun currentLimit(row: Int): Int = limit
}