package io.confluent.intellijplugin.core.util

import org.jetbrains.annotations.Nls

fun Throwable.realSource(): Throwable {
  val skippedCauses = mutableListOf<Throwable>()
  var source: Throwable = this
  while (source.message == null) {
    val cause = source.cause ?: break
    if (skippedCauses.contains(cause)) {
      break
    }
    skippedCauses.add(source)
    source = cause
  }
  return source
}

fun Throwable.messageOrDefault(@Nls default: String? = null): @Nls String {
  val source = realSource()
  return source.localizedMessage ?: source.message ?: default ?: ""
}