package io.confluent.kafka.core.table.renderers

import java.awt.Component
import javax.swing.JTable
import javax.swing.table.TableCellRenderer
import kotlin.math.roundToInt

abstract class AbstractProgressCellRenderer : TableCellRenderer {

  private val component = CellProgressBar()

  abstract fun currentLimit(row: Int): Int

  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    component.maximumValue = currentLimit(table!!.convertRowIndexToModel(row))
    component.value = convertValue(value)
    return component
  }

  protected fun convertValue(value: Any?) = when (value) {
    is Int -> value
    is Double -> (value * 100).roundToInt()
    is Float -> value.toInt()
    else -> 1
  }
}