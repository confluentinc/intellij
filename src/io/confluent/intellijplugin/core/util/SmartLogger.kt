package io.confluent.intellijplugin.core.util

import com.intellij.openapi.diagnostic.Logger

class SmartLogger<T>(clazz: Class<T>) {
  @Suppress("SSBasedInspection")
  private val logger = Logger.getInstance(clazz)

  private var prevWarnMessage: String? = null
  private var prevInfoMessage: String? = null

  fun warn(t: Throwable) = warn("", t)

  fun warn(message: String, throwable: Throwable) {
    val newMsg = message + "@" + throwable.toPresentableText()
    if (newMsg == prevWarnMessage) {
      logger.debug(message, throwable)
      return
    }
    prevWarnMessage = newMsg
    logger.warn(message, throwable)
  }

  fun info(message: String) {
    info(message, null)
  }

  fun info(throwable: Throwable?) {
    info("", throwable)
  }

  fun info(message: String, throwable: Throwable?) {
    val newMsg = message + "@" + (throwable?.toPresentableText() ?: "")
    if (newMsg == prevInfoMessage) {
      logger.debug(message, throwable)
      return
    }
    prevInfoMessage = newMsg
    logger.info(message, throwable)
  }

  fun debug(message: String, throwable: Throwable?) {
    logger.debug(message, throwable)
  }

  fun error(throwable: Throwable) {
    logger.error(throwable)
  }

  fun error(message: String, throwable: Throwable?) {
    logger.error(message, throwable)
  }
}