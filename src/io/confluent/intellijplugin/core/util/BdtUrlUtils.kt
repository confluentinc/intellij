package io.confluent.intellijplugin.core.util

import java.net.*

object BdtUrlUtils {

  fun validateUrl(url: String): Throwable? = try {
    convertToUrlObject(url)
    null
  }
  catch (t: Throwable) {
    t
  }

  fun getUrlHandler(protocol: String): URLStreamHandler? {
    protocol.ifEmpty { return null }
    return BdtUrlStreamHandlerFactoryProvider.getForProtocol(protocol)?.getStreamHandler()?.createURLStreamHandler(protocol)
  }

  fun getProtocol(urlString: String): String = urlString.substringBefore("://", "")

  @Throws(URISyntaxException::class, MalformedURLException::class)
  fun convertToUrlObject(url: String): URL {
    val cleanString = url.trim().cleanUrlHttpPrefix().removeSuffix("/")

    //check cast to URI for WebSockets
    URI(cleanString)

    val urlStreamHandler = getUrlHandler(getProtocol(cleanString))
    var urlObject = urlStreamHandler?.let { URL(null, cleanString, it) } ?: URL(cleanString)
    if (urlObject.port == -1) {
      val port = if (urlObject.protocol == "https") 443 else 80
      urlObject = URL(urlObject.protocol, urlObject.host, port, urlObject.file, null)
    }
    return urlObject
  }

  private fun String.cleanUrlHttpPrefix() = when {
    isBlank() -> ""
    contains("://") -> this
    else -> "http://$this"
  }
}