package io.confluent.intellijplugin.core.util

import java.lang.Thread.currentThread

inline fun <T : Any, R> T.withPluginClassLoader(block: () -> R): R {
  val oldClassLoader = currentThread().contextClassLoader
  try {
    currentThread().contextClassLoader = this::class.java.classLoader
    return block()
  }
  finally {
    currentThread().contextClassLoader = oldClassLoader
  }
}
