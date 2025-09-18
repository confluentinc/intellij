package com.jetbrains.bigdatatools.kafka.core.util

import java.io.InputStream
import java.io.OutputStream

open class DelegatingOutputStream(private val innerStream: OutputStream) : OutputStream() {
  override fun write(b: Int) = innerStream.write(b)
  override fun write(b: ByteArray) = innerStream.write(b)
  override fun write(b: ByteArray, off: Int, len: Int) = innerStream.write(b, off, len)
  override fun flush() = innerStream.flush()
  override fun close() = innerStream.close()
}

open class DelegatingInputStream(private val innerStream: InputStream) : InputStream() {
  override fun read(): Int = innerStream.read()
  override fun read(b: ByteArray, off: Int, len: Int): Int = innerStream.read(b, off, len)
  override fun available(): Int = innerStream.available()
  override fun close() = innerStream.close()
}
