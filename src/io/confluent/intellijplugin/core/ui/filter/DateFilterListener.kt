package io.confluent.intellijplugin.core.ui.filter

import java.util.*

interface DateFilterListener {
    fun filterChanged(periodType: DatePeriodType, from: Date?, to: Date?)
}