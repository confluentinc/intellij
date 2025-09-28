package io.confluent.intellijplugin.core.util

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.*

object TimeUtils {
  private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  @Suppress("HardCodedStringLiteral") // Because we cannot localize "format(formatter)".
  @Nls
  fun unixTimeToString(unixtime: Long): String = Instant.ofEpochMilli(unixtime).atZone(TimeZone.getDefault().toZoneId()).format(formatter)

  @NlsSafe
  fun intervalAsString(milliseconds: Long, withMs: Boolean = true): String {
    if (milliseconds == 0L) {
      return "<1 ms"
    }

    val mss = milliseconds % 1000
    val seconds = milliseconds / 1000 % 60
    val minutes = milliseconds / (1000 * 60) % 60
    val hours = milliseconds / (1000 * 60 * 60) % 24
    val days = milliseconds / (1000 * 60 * 60 * 24)

    val sb = StringBuilder()

    var notFirst = false

    fun addHours() {
      if (hours != 0L) {
        if (notFirst) sb.append(" ") else notFirst = true
        sb.append(hours).append(" h")
      }
    }

    fun addMinutes() {
      if (minutes != 0L) {
        if (notFirst) sb.append(" ") else notFirst = true
        sb.append(minutes).append(" m")
      }
    }

    fun addSeconds() {
      if (seconds != 0L) {
        if (notFirst) sb.append(" ") else notFirst = true
        sb.append(seconds).append(" s")
      }
    }

    if (days != 0L) {
      sb.append(days).append(" d")
      notFirst = true
      addHours()
      addMinutes()
      return sb.toString()
    }

    if (hours != 0L) {
      if (notFirst) sb.append(" ") else notFirst = true
      sb.append(hours).append(" h")
      addMinutes()
      addSeconds()
      return sb.toString()
    }

    if (minutes != 0L) {
      if (notFirst) sb.append(" ") else notFirst = true
      sb.append(minutes).append(" m")
    }

    if (seconds != 0L) {
      if (notFirst) sb.append(" ") else notFirst = true
      sb.append(seconds).append(" s")
    }

    if (withMs && mss != 0L) {
      if (notFirst) sb.append(" ")
      sb.append(mss).append(" ms")
    }

    return sb.toString()
  }

  fun parseIsoTime(s: String?): Date? {
    s ?: return null
    return try {
      val ta: TemporalAccessor = DateTimeFormatter.ISO_INSTANT.parse(s)
      val i: Instant = Instant.from(ta)
      val d: Date = Date.from(i)
      return d
    }
    catch (t: Throwable) {
      null
    }
  }
}