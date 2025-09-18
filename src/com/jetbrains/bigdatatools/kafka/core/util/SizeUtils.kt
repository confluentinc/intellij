package com.jetbrains.bigdatatools.kafka.core.util

import org.jetbrains.annotations.Nls
import java.text.DecimalFormat

object SizeUtils {
  // DecimalFormat is not thread safe, and we need to use ThreadLocal.
  private val decimalFormat = ThreadLocal.withInitial { DecimalFormat("0.##") }
  private val units = arrayOf("B", "KB", "MB", "GB", "TB")
  private const val THRESHOLD = 1024

  fun toString(bytes: Int): String = toString(bytes.toLong())

  @Nls
  fun toString(bytes: Long): String {
    val suffix = units
    var i = 0
    var dblBytes = bytes.toDouble()
    while (dblBytes >= THRESHOLD && i < suffix.size - 1) {
      dblBytes /= THRESHOLD.toDouble()
      i++
    }
    @Suppress("HardCodedStringLiteral")
    return "${decimalFormat.get().format(dblBytes)} ${suffix[i]}"
  }
}