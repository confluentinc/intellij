package io.confluent.intellijplugin.core.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import java.net.URLStreamHandlerFactory

interface BdtUrlStreamHandlerFactoryProvider {
  fun getProtocol(): String
  fun getStreamHandler(): URLStreamHandlerFactory

  companion object {
    private val logger = Logger.getInstance(this::class.java)
    private val EP_NAME = ExtensionPointName.create<BdtUrlStreamHandlerFactoryProvider>(
      "com.intellij.bigdatatools.rfs.bdtUrlStreamHandlerFactoryProvider")

    fun getForProtocol(protocol: String): BdtUrlStreamHandlerFactoryProvider? = try {
      EP_NAME.extensionList.find {
        it.getProtocol().equals(protocol, ignoreCase = true)
      }

    }
    catch (t: Throwable) {
      logger.info(t)
      null
    }
  }
}