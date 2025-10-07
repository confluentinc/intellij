package io.confluent.intellijplugin.core.table.filters

import io.confluent.intellijplugin.core.table.renderers.DateRenderer
import java.util.*
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.table.TableModel

class TableRowFilter(var table: JTable) : RowFilter<TableModel, Int>() {

    private val conditions = mutableListOf<Pair<Int, (Any?) -> Boolean>>()

    var compareCaseInsensitive = false

    private fun toOperatorAndValue(text: String): Pair<String, Double>? {
        if (text.isEmpty() || (text[0] != '=' && text[0] != '>' && text[0] != '<')) {
            return null
        }

        val value = text.substring(1).toDoubleOrNull() ?: return null
        return Pair(text.first().toString(), value)
    }

    private fun stringCondition(search: String) = if (compareCaseInsensitive) {
        { value: Any? -> value?.toString()?.matches(Regex("(?i).*$search.*")) == true }
    } else {
        { value: Any? -> value?.toString()?.contains(search) == true }
    }

    fun setConditions(conditions: List<Pair<Int, String>>) {
        this.conditions.clear()

        conditions.forEach {

            val cls = table.model.getColumnClass(it.first)

            val complex = toOperatorAndValue(it.second)
            val filter: ((Any?) -> Boolean) =
                if (complex == null || (cls != Int::class.java && cls != Long::class.java && cls != Double::class.java)) {
                    if (cls == Date::class.java) {
                        // ToDo Hack to make it possible to search by date. We should rework model.getValueAt to table.getRenderer.getPresentationValue
                        //  But this is not so easy.
                        { value: Any? ->
                            if (value == null) false else DateRenderer.df.format(value).contains(it.second)
                        }
                    } else {
                        stringCondition(it.second)
                    }
                } else {
                    val valueAsDouble = when (cls) {
                        Int::class.java -> { value: Any? -> (value as? Int)?.toDouble() }
                        Long::class.java -> { value: Any? -> (value as? Long)?.toDouble() }
                        Double::class.java -> { value: Any? -> (value as? Double)?.toDouble() }
                        else -> null
                    }
                    if (valueAsDouble == null) {
                        stringCondition(it.second)
                    } else {
                        when (complex.first) {
                            "=" -> { value: Any? ->
                                val doubleValue = valueAsDouble.invoke(value)
                                if (doubleValue == null) false else doubleValue == complex.second
                            }

                            ">" -> { value: Any? ->
                                val doubleValue = valueAsDouble.invoke(value)
                                if (doubleValue == null) false else doubleValue > complex.second
                            }

                            "<" -> { value: Any? ->
                                val doubleValue = valueAsDouble.invoke(value)
                                if (doubleValue == null) false else doubleValue < complex.second
                            }

                            else -> stringCondition(it.second)
                        }
                    }
                }

            this.conditions.add(Pair(it.first, filter))
        }
    }

    override fun include(entry: Entry<out TableModel, out Int>): Boolean {
        return conditions.firstOrNull {
            !it.second(entry.model.getValueAt(entry.identifier, it.first))
        } == null
    }
}