package io.confluent.kafka.core.rfs.driver

import com.intellij.openapi.util.NlsSafe

@JvmInline
value class FilePermission private constructor(val mode: Int) {
  constructor(readable: Boolean, writeable: Boolean, executable: Boolean) : this(
    listOf(if (readable) 4 else 0, if (writeable) 2 else 0, if (executable) 1 else 0).sum()
  )
  val readable: Boolean get() = mode.and(0b100) != 0
  val writeable: Boolean get() = mode.and(0b010) != 0
  private val executable: Boolean get() = mode.and(0b001) != 0
  @NlsSafe
  fun printString(): String {
    val readableCode = if (readable) "r" else "-"
    val writableCode = if (writeable) "w" else "-"
    val executableCode = if (executable) "x" else "-"
    return StringBuilder(3).append(readableCode).append(writableCode).append(executableCode).toString()
  }
  override fun toString() = printString()
}